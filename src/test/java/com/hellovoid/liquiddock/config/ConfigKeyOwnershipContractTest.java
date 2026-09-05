package com.hellovoid.liquiddock.config;

import static org.junit.Assert.assertFalse;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.Test;

/** Prevents user-facing code outside the config package from minting schema-external keys. */
public class ConfigKeyOwnershipContractTest {
    @Test
    public void configKeyIsNotPubliclyConstructible() {
        for (Constructor<?> constructor : ConfigKey.class.getDeclaredConstructors()) {
            assertFalse("ConfigKey construction must stay behind the ConfigSchema boundary",
                    Modifier.isPublic(constructor.getModifiers()));
        }
    }
}
