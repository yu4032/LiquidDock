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

// The original std::function closure is cloned while bgDrawPassBlur moves
// through SmallVector. Its allocation address is not a stable job key. Both
// copies retain the same promise shared state, so a verified native adapter
// can pass that state address here without changing the 0x208 closure layout.
// The adapter must remove the record on every normal completion path.
class JobRegistry {
public:
    bool registerInstance(const void* identity) { return scheduler_.registerInstance(identity); }
    bool unregisterInstance(const void* identity) { return scheduler_.unregisterInstance(identity); }

    bool capture(const void* promiseState, const void* passBlurIdentity) {
        if (!promiseState) return false;
        std::lock_guard<std::mutex> guard(jobsMutex_);
        // Allocate the job record *before* advancing generation. Otherwise
        // an allocation failure could stale the previous job with no newer
        // job actually in the queue.
        auto [it, inserted] = jobs_.emplace(promiseState, LatestOnlyScheduler::Job{});
        if (!inserted) return false;
        auto job = scheduler_.submit(passBlurIdentity);
        if (!job) {
            jobs_.erase(it);
            return false;
        }
        it->second = std::move(job);
        return true;
    }

    LatestOnlyScheduler::Job find(const void* promiseState) const {
        std::lock_guard<std::mutex> guard(jobsMutex_);
        auto it = jobs_.find(promiseState);
        return it == jobs_.end() ? LatestOnlyScheduler::Job{} : it->second;
    }

    bool erase(const void* promiseState) {
        std::lock_guard<std::mutex> guard(jobsMutex_);
        return jobs_.erase(promiseState) == 1;
    }

private:
    LatestOnlyScheduler scheduler_;
    mutable std::mutex jobsMutex_;
    std::unordered_map<const void*, LatestOnlyScheduler::Job> jobs_;
};

} // namespace liquiddock::passblur
