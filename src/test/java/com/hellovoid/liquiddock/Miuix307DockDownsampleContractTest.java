package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Dock must reuse the Workspace physical-downsample control without changing logical glass space. */
public class Miuix307DockDownsampleContractTest {
    private static final Path VIEW = Path.of(
            "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java");

    @Test public void dockConsumesWorkspaceCaptureScaleForPhysicalBackdropOnly() throws Exception {
        String source = Files.readString(VIEW);

        assertTrue("Dock must read the same GUI-backed capture scale as Workspace",
                source.contains("passBlurCaptureScalePercent = glassConfig.passBlurCaptureScalePercent"));
        assertTrue("Dock physical FBO must use the shared logical-to-physical render domain",
                source.contains("PassBlurRenderDomain.resolve(")
                        && source.contains("passBlurCaptureScalePercent"));
        assertTrue("Stage-A normalization target must use downsampled physical dimensions",
                source.contains("ensureFboSizeExact(domain.renderWidth, domain.renderHeight)"));
        assertTrue("Prismal must receive physical backdrop dimensions while keeping logical output dimensions",
                source.contains("prismalRenderer.prepareBackdrop(")
                        && source.contains("domain.renderWidth, domain.renderHeight")
                        && source.contains("mapping.sampleWidth, mapping.sampleHeight"));
    }
}
