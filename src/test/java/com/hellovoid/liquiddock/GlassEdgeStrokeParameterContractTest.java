package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class GlassEdgeStrokeParameterContractTest {
    @Test
    public void independentStrokeParametersReachPortableRenderer() throws Exception {
        String params = Files.readString(Path.of("prismal/src/main/java/com/hellovoid/prismal/PrismalParams.java"));
        String adapter = Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/Miuix307PrismalAdapter.java"));
        String renderer = Files.readString(Path.of("prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java"));
        for (String field : new String[]{"strokeEnabled", "strokeFillDiff", "strokeFillDiffWidthPx",
                "strokeStandardWidthPx", "strokeR", "strokeG", "strokeB", "strokeA"}) {
            assertTrue(field, params.contains(field));
            assertTrue(field, adapter.contains(field));
        }
        for (String uniform : new String[]{"u_glassStrokeEnabled", "u_glassStrokeFillDiff",
                "u_glassStrokeFillDiffWidth", "u_glassStrokeStandardWidth", "u_glassStrokeColor"}) {
            assertTrue(uniform, renderer.contains(uniform));
        }
    }
}
