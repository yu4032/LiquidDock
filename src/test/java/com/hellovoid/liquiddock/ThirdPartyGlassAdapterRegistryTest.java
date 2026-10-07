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
        ThirdPartyGlassAdapterRegistry.Registration baidu =
                ThirdPartyGlassAdapterRegistry.find("com.baidu.input_mi");
        ThirdPartyGlassAdapterRegistry.Registration searchbox =
                ThirdPartyGlassAdapterRegistry.find("com.android.quicksearchbox");

        assertNotNull(gboard);
        assertNotNull(baidu);
        assertNotNull(searchbox);
        assertEquals("gboard.floating", gboard.profileId);
        assertEquals("baidu.floating", baidu.profileId);
        assertEquals("miui.searchbox", searchbox.profileId);
        assertTrue(ThirdPartyGlassAdapterRegistry.handles("com.android.quicksearchbox"));
        assertTrue(ThirdPartyGlassAdapterRegistry.handles("com.baidu.input_mi"));
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