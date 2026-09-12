package com.hellovoid.liquiddock;

import java.util.concurrent.atomic.AtomicBoolean;

/** Android-free one-shot compatibility tip policy. */
final class SecurityCenterOneShotTipPolicy {
    private final AtomicBoolean emitted = new AtomicBoolean(false);

    SecurityCenterOneShotTipPolicy() {}

    boolean shouldEmit() {
        return emitted.compareAndSet(false, true);
    }
}
