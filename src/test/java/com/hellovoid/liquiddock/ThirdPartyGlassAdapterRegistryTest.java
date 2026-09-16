package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ThirdPartyGlassAdapterRegistryTest {
    @Test
    public void registryContainsOnlyCodeOwnedKnownAdapters() {
        ThirdPartyGlassAdapterRegistry.Registration gboard =
                ThirdPartyGlassAdapterRegistry.find("com.google.android.inputmethod.latin");
        ThirdPartyGlassAdapterRegistry.Registration searchbox =
                ThirdPartyGlassAdapterRegistry.find("com.android.quicksearchbox");

        assertNotNull(gboard);
        assertNotNull(searchbox);
        assertEquals("gboard.floating", gboard.profileId);
        assertEquals("miui.searchbox", searchbox.profileId);
        assertTrue(ThirdPartyGlassAdapterRegistry.handles("com.android.quicksearchbox"));
        assertFalse(ThirdPartyGlassAdapterRegistry.handles("com.example.unregistered"));
    }

    @Test
    public void registrationSurfaceDoesNotContainConfigurableHookNames() {
        for (ThirdPartyGlassAdapterRegistry.Registration registration
                : ThirdPartyGlassAdapterRegistry.registrations()) {
            assertNotNull(registration.packageName);
            assertNotNull(registration.profileId);
            assertNotNull(registration.installer);
            ThirdPartyGlassProfiles.validateProfileId(registration.profileId);
        }
    }
}