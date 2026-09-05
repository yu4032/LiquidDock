package com.hellovoid.liquiddock.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;

/** Architecture gate: persisted ConfigKey construction is owned by ConfigSchema. */
public class ConfigKeyOwnershipContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void configKeyConstructionRequiresPrivateConfigSchemaAuthority() throws Exception {
        for (Constructor<?> constructor : ConfigKey.class.getDeclaredConstructors()) {
            assertTrue("ConfigKey constructors must be private",
                    Modifier.isPrivate(constructor.getModifiers()));
        }

        final Class<?> authority;
        try {
            authority = Class.forName(ConfigSchema.class.getName() + "$RegistrationAuthority");
        } catch (ClassNotFoundException error) {
            fail("ConfigSchema must own an unforgeable ConfigKey registration authority");
            return;
        }

        assertFalse("registration authority type must not be public",
                Modifier.isPublic(authority.getModifiers()));
        for (Constructor<?> constructor : authority.getDeclaredConstructors()) {
            assertTrue("registration authority constructors must be private",
                    Modifier.isPrivate(constructor.getModifiers()));
        }

        boolean foundFactory = false;
        for (Method method : ConfigKey.class.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) continue;
            if (!ConfigKey.class.isAssignableFrom(method.getReturnType())) continue;
            foundFactory = true;
            assertFalse("ConfigKey registration factory must not be public",
                    Modifier.isPublic(method.getModifiers()));
            assertFalse("ConfigKey registration factory must not be protected",
                    Modifier.isProtected(method.getModifiers()));
            assertTrue("ConfigKey factory must require ConfigSchema registration authority first",
                    method.getParameterCount() > 0
                            && method.getParameterTypes()[0].equals(authority));
        }
        assertTrue("ConfigKey must expose one authority-gated schema registration factory",
                foundFactory);

        boolean foundAuthorityInstance = false;
        for (Field field : ConfigSchema.class.getDeclaredFields()) {
            if (!field.getType().equals(authority)) continue;
            foundAuthorityInstance = true;
            assertTrue("ConfigSchema registration authority instance must be private",
                    Modifier.isPrivate(field.getModifiers()));
            assertTrue("ConfigSchema registration authority instance must be static",
                    Modifier.isStatic(field.getModifiers()));
            assertTrue("ConfigSchema registration authority instance must be final",
                    Modifier.isFinal(field.getModifiers()));
        }
        assertTrue("ConfigSchema must own the registration authority instance",
                foundAuthorityInstance);

        for (Method method : ConfigSchema.class.getDeclaredMethods()) {
            if (!method.getReturnType().equals(authority)) continue;
            assertTrue("ConfigSchema must never expose its registration authority",
                    Modifier.isPrivate(method.getModifiers()));
        }
    }

    @Test
    public void productionRegistrationCallsStayInsideConfigSchema() throws Exception {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : (Iterable<Path>) files
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    ::iterator) {
                if (file.getFileName().toString().equals("ConfigSchema.java")) continue;
                String source = Files.readString(file);
                if (source.contains("ConfigKey.register(")) {
                    offenders.add(MAIN.relativize(file).toString());
                }
            }
        }
        assertTrue("persisted ConfigKey registration must remain centralized in ConfigSchema: "
                        + offenders,
                offenders.isEmpty());
    }
}
