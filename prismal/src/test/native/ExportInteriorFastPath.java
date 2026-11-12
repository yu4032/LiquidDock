package com.hellovoid.prismal;

import java.nio.file.Files;
import java.nio.file.Path;

/** Exports the actual generated production programs, before and after the optimization. */
public final class ExportInteriorFastPath {
    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        String original = PrismalOs4InvariantShader.apply(PrismalComponentGateShader.apply(
                PrismalOpticalEdgeShader.apply(PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT))));
        String optimized = PrismalInteriorFastPath.apply(original);
        Files.writeString(dir.resolve("vertex.glsl"), PrismalRasterGuardShader.apply(PrismalShaderSources.VERTEX));
        Files.writeString(dir.resolve("baseline.frag"), original);
        Files.writeString(dir.resolve("optimized.frag"), optimized);
        Files.writeString(dir.resolve("decorated-vertex.glsl"),
                PrismalRasterGuardShader.applyWithEdgeGuard(PrismalShaderSources.VERTEX));
        String decorated = PrismalEdgeDecorationShader.apply(original);
        String decoratedOptimized = PrismalEdgeDecorationShader.apply(optimized);
        Files.writeString(dir.resolve("decorated.frag"), decorated);
        Files.writeString(dir.resolve("decorated-optimized.frag"), decoratedOptimized);
        Files.writeString(dir.resolve("reconstructed.frag"), PrismalReconstructed27Shader.apply(decorated));
        Files.writeString(dir.resolve("reconstructed-optimized.frag"),
                PrismalReconstructed27Shader.apply(decoratedOptimized));
    }
}
