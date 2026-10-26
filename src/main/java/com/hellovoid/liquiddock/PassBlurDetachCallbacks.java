package com.hellovoid.liquiddock;

import java.util.List;
import java.util.function.Consumer;

/** Drain independent owner continuations even when one callback fails. Pure JVM. */
final class PassBlurDetachCallbacks {
    private PassBlurDetachCallbacks() {}

    static void dispatch(List<Runnable> callbacks, Consumer<Throwable> onFailure) {
        if (callbacks == null) return;
        for (Runnable callback : callbacks) {
            if (callback == null) continue;
            try {
                callback.run();
            } catch (Throwable error) {
                try {
                    if (onFailure != null) onFailure.accept(error);
                } catch (Throwable ignored) {
                    // Diagnostics must not prevent the remaining owners from cleaning up.
                }
            }
        }
    }
}
