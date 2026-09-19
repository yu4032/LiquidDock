package com.hellovoid.liquiddock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads source-contract fixtures with platform-independent line endings. */
final class SourceContractText {
    private SourceContractText() {}

    static String read(Path path) throws IOException {
        return Files.readString(path)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
