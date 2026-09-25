#include "latest_only_scheduler.hpp"

#include <cassert>
#include <condition_variable>
#include <mutex>
#include <thread>

using liquiddock::passblur::LatestOnlyScheduler;

int main() {
    LatestOnlyScheduler scheduler;
    int targetA = 0;
    int targetB = 0;
    assert(scheduler.registerInstance(&targetA));
    assert(scheduler.registerInstance(&targetB));
    assert(!scheduler.registerInstance(&targetA));

    auto firstA = scheduler.submit(&targetA);
    auto firstB = scheduler.submit(&targetB);
    auto secondA = scheduler.submit(&targetA);
    assert(firstA && firstB && secondA);
    assert(!LatestOnlyScheduler::shouldRender(firstA));
    assert(LatestOnlyScheduler::shouldRender(firstB));
    assert(LatestOnlyScheduler::shouldRender(secondA));

    int published = 0;
    int canceled = 0;
    assert(!LatestOnlyScheduler::publishOrCancel(
        firstA, [&] { ++published; }, [&] { ++canceled; }));
    assert(published == 0 && canceled == 1);
    assert(LatestOnlyScheduler::publishOrCancel(
        firstB, [&] { ++published; }, [&] { ++canceled; }));
    assert(published == 1 && canceled == 1);

    // A render that was fresh at worker entry can become stale while GPU work
    // runs. The late gate must cancel its buffer.
    auto gpuA = scheduler.submit(&targetA);
    assert(LatestOnlyScheduler::shouldRender(gpuA));
    auto newerA = scheduler.submit(&targetA);
    assert(!LatestOnlyScheduler::publishOrCancel(
        gpuA, [&] { ++published; }, [&] { ++canceled; }));
    assert(LatestOnlyScheduler::publishOrCancel(
        newerA, [&] { ++published; }, [&] { ++canceled; }));
    assert(published == 2 && canceled == 2);

    // Publication is the linearization point: a submission cannot overtake
    // an in-progress queueBuffer call for the same PassBlur identity.
    std::mutex gateMutex;
    std::condition_variable gateCv;
    bool insidePublish = false;
    bool allowFinish = false;
    bool submitted = false;
    auto linearized = scheduler.submit(&targetA);
    std::thread worker([&] {
        assert(LatestOnlyScheduler::publishOrCancel(
            linearized,
            [&] {
                std::unique_lock<std::mutex> lock(gateMutex);
                insidePublish = true;
                gateCv.notify_all();
                gateCv.wait(lock, [&] { return allowFinish; });
            },
            [&] { assert(false); }));
    });
    {
        std::unique_lock<std::mutex> lock(gateMutex);
        gateCv.wait(lock, [&] { return insidePublish; });
    }
    std::thread submitter([&] {
        auto next = scheduler.submit(&targetA);
        assert(next);
        std::lock_guard<std::mutex> lock(gateMutex);
        submitted = true;
        gateCv.notify_all();
    });
    {
        std::lock_guard<std::mutex> lock(gateMutex);
        assert(!submitted);
        allowFinish = true;
    }
    gateCv.notify_all();
    worker.join();
    submitter.join();
    assert(submitted);

    // Retiring a PassBlur rejects captured jobs, including when the address
    // is subsequently reused by a newly registered instance.
    auto retiring = scheduler.submit(&targetB);
    assert(scheduler.unregisterInstance(&targetB));
    assert(!LatestOnlyScheduler::shouldRender(retiring));
    assert(scheduler.registerInstance(&targetB));
    auto replacement = scheduler.submit(&targetB);
    assert(LatestOnlyScheduler::shouldRender(replacement));
    assert(!LatestOnlyScheduler::publishOrCancel(
        retiring, [&] { assert(false); }, [&] { ++canceled; }));
    assert(canceled == 3);

    // std::function cloning changes the closure allocation, while the
    // promise's shared state address stays the same for lookup and erase.
    liquiddock::passblur::JobRegistry jobs;
    int promiseState = 0;
    int anotherPromise = 0;
    assert(jobs.registerInstance(&targetA));
    assert(jobs.capture(&promiseState, &targetA));
    assert(!jobs.capture(&promiseState, &targetA));
    auto captured = jobs.find(&promiseState);
    assert(captured && LatestOnlyScheduler::shouldRender(captured));
    assert(jobs.capture(&anotherPromise, &targetA));
    assert(!LatestOnlyScheduler::shouldRender(jobs.find(&promiseState)));
    assert(jobs.erase(&promiseState));
    assert(!jobs.find(&promiseState));
    assert(jobs.erase(&anotherPromise));
    assert(jobs.unregisterInstance(&targetA));
}
