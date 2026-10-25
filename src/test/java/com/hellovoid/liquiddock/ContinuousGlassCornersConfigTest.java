package com.hellovoid.liquiddock;

import com.hellovoid.prismal.PrismalParams;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class ContinuousGlassCornersConfigTest {
    @Test public void oldPreferencesKeepLegacyCorners() {
        LiquidDockConfig.Glass glass = new LiquidDockConfig.Glass(new ConfigReader(Map.of()));
        assertFalse(glass.continuousCorners);
        assertFalse(Miuix307PrismalAdapter.toPortable(
                Miuix307PrismalMaterial.fromConfig(glass, 2f)).continuousCorners);
    }

    @Test public void switchReachesRendererAndSurvivesParameterCopies() {
        for (boolean enabled : new boolean[]{true, false}) {
            LiquidDockConfig.Glass glass = new LiquidDockConfig.Glass(
                    new ConfigReader(Map.of("liquid_continuous_corners", enabled)));
            PrismalParams params = Miuix307PrismalAdapter.toPortable(
                    Miuix307PrismalMaterial.fromConfig(glass, 2f));
            assertEquals(enabled, params.continuousCorners);
            assertEquals(enabled, PrismalParams.builder(params).build().continuousCorners);
        }
    }
}
