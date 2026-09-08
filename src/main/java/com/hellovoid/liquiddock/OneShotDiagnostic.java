package com.hellovoid.liquiddock;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Emits each structured diagnostic key at most once per owner instance. */
final class OneShotDiagnostic {
    private final Consumer<String> sink;
    private final Set<String> emitted = ConcurrentHashMap.newKeySet();

    OneShotDiagnostic(Consumer<String> sink) {
        this.sink = Objects.requireNonNull(sink);
    }

    void emit(String key, String message) {
        if (emitted.add(key)) sink.accept(message);
    }
}
