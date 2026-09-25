#pragma once

// Phase-1 scheduler core, independent of Android-private ABI. This is not
// linked into SurfaceFlinger until the binary hook/lifetime contract is proven.

#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <unordered_map>

namespace liquiddock::passblur {

class LatestOnlyScheduler {
public:
    struct State {
        std::mutex publicationMutex;
        std::atomic<std::uint64_t> generation{0};
        std::atomic<bool> retired{false};
    };

    struct Job {
        std::shared_ptr<State> state;
        std::uint64_t generation = 0;

        explicit operator bool() const noexcept { return static_cast<bool>(state); }
    };

    // Must be called by the same verified lifecycle as PassBlur construction.
    bool registerInstance(const void* identity) {
        std::lock_guard<std::mutex> guard(registryMutex_);
        return registry_.emplace(identity, std::make_shared<State>()).second;
    }

    // Retiring a state also rejects any closures still holding it. A reused
    // address gets a distinct State. The closure's original strong PassBlur
    // reference remains the native lifetime authority.
    bool unregisterInstance(const void* identity) {
        std::lock_guard<std::mutex> registryGuard(registryMutex_);
        auto it = registry_.find(identity);
        if (it == registry_.end()) return false;
        {
            std::lock_guard<std::mutex> publicationGuard(it->second->publicationMutex);
            it->second->retired.store(true, std::memory_order_release);
        }
        registry_.erase(it);
        return true;
    }

    // This is the submission linearization point. An absent registration
    // returns an invalid Job; the native adapter must retain original
    // behavior rather than dropping the frame in that situation.
    Job submit(const void* identity) {
        std::lock_guard<std::mutex> registryGuard(registryMutex_);
        auto it = registry_.find(identity);
        if (it == registry_.end()) return {};
        std::shared_ptr<State> state = it->second;
        std::lock_guard<std::mutex> publicationGuard(state->publicationMutex);
        if (state->retired.load(std::memory_order_acquire)) return {};
        return {state, state->generation.fetch_add(1, std::memory_order_acq_rel) + 1};
    }

    // Call before any layer filtering/dequeue. False means the adapter must
    // complete the existing promise with false and run its full cleanup path.
    static bool shouldRender(const Job& job) noexcept {
        return !job || (!job.state->retired.load(std::memory_order_acquire) &&
                        job.generation == job.state->generation.load(std::memory_order_acquire));
    }

    // Call after RenderEngine and before queueBuffer. publish() and cancel()
    // must each consume exactly one already-dequeued buffer and fence. Holding
    // the publication lock across publish() orders queue publication before
    // the next accepted submission. The native adapter must establish that
    // publish() cannot re-enter any registry/lifecycle method and measure
    // contention before use.
    template <typename Publish, typename Cancel>
    static bool publishOrCancel(const Job& job, Publish&& publish, Cancel&& cancel) {
        if (!job) {
            publish(); // fail open when this binary has no registered state
            return true;
        }
        std::unique_lock<std::mutex> guard(job.state->publicationMutex);
        if (!job.state->retired.load(std::memory_order_acquire) &&
            job.generation == job.state->generation.load(std::memory_order_acquire)) {
            publish();
            return true;
        }
        guard.unlock();
        cancel();
        return false;
    }

private:
    std::mutex registryMutex_;
    std::unordered_map<const void*, std::shared_ptr<State>> registry_;
};

} // namespace liquiddock::passblur
