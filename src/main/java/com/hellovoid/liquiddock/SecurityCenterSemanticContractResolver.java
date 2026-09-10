package com.hellovoid.liquiddock;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Capability-driven resolver for the live Security Center TurboLayout member graph. */
final class SecurityCenterSemanticContractResolver {
    private SecurityCenterSemanticContractResolver() {}

    static ResolvedContract resolveForTest(
            Class<?> turboClass, Class<?> viewClass, Set<String> availableResources) {
        return resolve(turboClass, viewClass, availableResources);
    }

    static ResolvedContract resolve(
            Class<?> turboClass, Class<?> viewClass, Set<String> availableResources) {
        if (turboClass == null || viewClass == null) throw reject("missing TurboLayout/View");
        requireResources(availableResources);

        Method configure = uniqueDeclaredMethod(turboClass, method -> {
            Class<?>[] p = method.getParameterTypes();
            return !Modifier.isStatic(method.getModifiers())
                    && method.getReturnType() == void.class
                    && p.length == 8
                    && p[1] == boolean.class
                    && p[2] == String.class
                    && p[3] == int.class
                    && p[5] == boolean.class
                    && p[6] == boolean.class
                    && p[7] == boolean.class;
        }, "configure signature");
        Class<?> wrapperClass = configure.getParameterTypes()[0];
        Class<?> assistantTypeClass = configure.getParameterTypes()[4];

        Method wrapperTurboGetter = uniqueDeclaredMethod(wrapperClass,
                method -> !Modifier.isStatic(method.getModifiers())
                        && method.getParameterCount() == 0
                        && turboClass.isAssignableFrom(method.getReturnType()),
                "wrapper-to-Turbo relation");

        Field managerField = uniqueField(turboClass,
                field -> managerTeardownMethods(field.getType(), wrapperClass).size() == 2,
                "manager teardown authority");
        Class<?> managerClass = managerField.getType();
        List<Method> teardowns = managerTeardownMethods(managerClass, wrapperClass);

        Method discriminator = resolveAssistantTypeDiscriminator(assistantTypeClass);

        Method dockGetter = namedZeroArg(turboClass,
                SecurityCenterHookSpec.DOCK_LAYOUT_GETTER, "dock getter");
        Method appsGetter = namedZeroArg(turboClass,
                SecurityCenterHookSpec.APPS_LAYOUT_GETTER, "all-apps getter");
        Method boxGetter = namedZeroArg(turboClass,
                SecurityCenterHookSpec.BOX_VIEW_GETTER, "box getter");
        Method gameGetter = namedZeroArg(turboClass,
                SecurityCenterHookSpec.GAME_BOX_GETTER, "game-box getter");
        Method videoGetter = namedZeroArg(turboClass,
                SecurityCenterHookSpec.VIDEO_ADAPTER_GETTER, "video-adapter getter");
        requireViewReturn(dockGetter, viewClass, "dock getter");
        requireViewReturn(appsGetter, viewClass, "all-apps getter");
        requireViewReturn(boxGetter, viewClass, "box getter");
        requireViewReturn(gameGetter, viewClass, "game-box getter");

        Class<?> allAppsClass = appsGetter.getReturnType();
        Class<?> gameBoxClass = gameGetter.getReturnType();
        Class<?> videoAdapterClass = videoGetter.getReturnType();
        if (videoAdapterClass.isPrimitive() || videoAdapterClass == Object.class
                || viewClass.isAssignableFrom(videoAdapterClass)) {
            throw reject("video adapter relation changed");
        }

        Method gameMaterialGetter = namedZeroArg(gameBoxClass,
                SecurityCenterHookSpec.GAME_MATERIAL_GETTER, "game material getter");
        Class<?> gameMaterialClass = gameMaterialGetter.getReturnType();
        if (!viewClass.isAssignableFrom(gameMaterialClass) || gameMaterialClass == gameBoxClass) {
            throw reject("game material carrier changed");
        }

        Method gameRestore = namedVoidZeroArg(gameMaterialClass,
                SecurityCenterHookSpec.GAME_MATERIAL_RESTORE_METHOD,
                "game material restore");
        Method videoRestore = namedVoidZeroArg(videoAdapterClass,
                SecurityCenterHookSpec.VIDEO_MATERIAL_RESTORE_METHOD,
                "video material restore");

        Method dockReady = namedVoidZeroArg(turboClass,
                SecurityCenterHookSpec.DOCK_READY_METHOD, "dock-ready authority");
        Method toggle = namedVoidZeroArg(turboClass,
                SecurityCenterHookSpec.TOGGLE_ALL_APPS_METHOD, "all-apps toggle authority");
        Method finalBackground = namedVoidZeroArg(turboClass,
                SecurityCenterHookSpec.FINAL_BACKGROUND_METHOD, "final background authority");
        Field allAppsPresent = namedBooleanField(turboClass,
                SecurityCenterHookSpec.ALL_APPS_PRESENT_FIELD, "all-apps state");
        Field transforming = namedBooleanField(turboClass,
                SecurityCenterHookSpec.TRANSFORMING_FIELD, "transforming state");

        return new ResolvedContract(
                turboClass, wrapperClass, managerClass, assistantTypeClass,
                gameBoxClass, gameMaterialClass, videoAdapterClass, allAppsClass,
                configure, dockReady, toggle, finalBackground, wrapperTurboGetter,
                teardowns.get(0), teardowns.get(1), dockGetter, appsGetter, boxGetter,
                gameGetter, gameMaterialGetter, gameRestore, videoGetter, videoRestore,
                discriminator, allAppsPresent, transforming);
    }

    private static void requireResources(Set<String> resources) {
        if (resources == null
                || !resources.contains("dimen:" + SecurityCenterHookSpec.ALL_APPS_RADIUS_RESOURCE)
                || !resources.contains("dimen:" + SecurityCenterHookSpec.GAME_RADIUS_RESOURCE)
                || !resources.contains("id:" + SecurityCenterHookSpec.VIDEO_CONTENT_RESOURCE)) {
            throw reject("required resource capability unavailable");
        }
    }

    private static Method resolveAssistantTypeDiscriminator(Class<?> typeClass) {
        List<Method> setters = declaredMethods(typeClass, method ->
                !Modifier.isStatic(method.getModifiers())
                        && method.getReturnType() == void.class
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == int.class);
        if (setters.size() != 1) throw reject("assistant type setter ambiguous");
        Method setter = accessible(setters.get(0));

        List<Method> candidates = declaredMethods(typeClass, method ->
                !Modifier.isStatic(method.getModifiers())
                        && method.getReturnType() == int.class
                        && method.getParameterCount() == 0);
        List<Method> matches = new ArrayList<>();
        for (Method candidate : candidates) {
            if (roundTrips(typeClass, setter, candidate)) matches.add(accessible(candidate));
        }
        if (matches.size() != 1) throw reject("assistant type discriminator ambiguous");
        return matches.get(0);
    }

    private static boolean roundTrips(Class<?> typeClass, Method setter, Method getter) {
        try {
            Constructor<?> ctor = typeClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object probe = ctor.newInstance();
            setter.setAccessible(true);
            getter.setAccessible(true);
            for (int expected : new int[]{1, 3, 4}) {
                setter.invoke(probe, expected);
                Object observed = getter.invoke(probe);
                if (!(observed instanceof Number)
                        || ((Number) observed).intValue() != expected) return false;
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static List<Method> managerTeardownMethods(Class<?> type, Class<?> wrapperClass) {
        return declaredMethods(type, method -> {
            Class<?>[] p = method.getParameterTypes();
            return !Modifier.isStatic(method.getModifiers())
                    && Modifier.isPrivate(method.getModifiers())
                    && method.getReturnType() == void.class
                    && p.length == 2
                    && p[0] == wrapperClass
                    && p[1] == boolean.class;
        });
    }

    private static Method namedZeroArg(Class<?> type, String name, String capability) {
        List<Method> methods = declaredMethods(type, method ->
                method.getName().equals(name)
                        && !Modifier.isStatic(method.getModifiers())
                        && method.getParameterCount() == 0);
        if (methods.size() != 1) throw reject(capability + " missing or ambiguous");
        return accessible(methods.get(0));
    }

    private static Method namedVoidZeroArg(Class<?> type, String name, String capability) {
        Method method = namedZeroArg(type, name, capability);
        if (method.getReturnType() != void.class) throw reject(capability + " shape changed");
        return method;
    }

    private static Field namedBooleanField(Class<?> type, String name, String capability) {
        List<Field> fields = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())
                    && field.getName().equals(name)
                    && field.getType() == boolean.class) fields.add(field);
        }
        if (fields.size() != 1) throw reject(capability + " missing or ambiguous");
        fields.get(0).setAccessible(true);
        return fields.get(0);
    }

    private static void requireViewReturn(Method method, Class<?> viewClass, String capability) {
        if (!viewClass.isAssignableFrom(method.getReturnType())) {
            throw reject(capability + " is not View-compatible");
        }
    }

    private static Method uniqueDeclaredMethod(
            Class<?> type, MethodPredicate predicate, String capability) {
        List<Method> methods = declaredMethods(type, predicate);
        if (methods.size() != 1) throw reject(capability + " missing or ambiguous");
        return accessible(methods.get(0));
    }

    private static Field uniqueField(Class<?> type, FieldPredicate predicate, String capability) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && predicate.matches(field)) fields.add(field);
            }
        }
        if (fields.size() != 1) throw reject(capability + " missing or ambiguous");
        fields.get(0).setAccessible(true);
        return fields.get(0);
    }

    private static List<Method> declaredMethods(Class<?> type, MethodPredicate predicate) {
        List<Method> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Method method : type.getDeclaredMethods()) {
            String key = method.getName() + java.util.Arrays.toString(method.getParameterTypes())
                    + method.getReturnType().getName();
            if (seen.add(key) && predicate.matches(method)) out.add(method);
        }
        return out;
    }

    private static Method accessible(Method method) {
        method.setAccessible(true);
        return method;
    }

    private static IllegalStateException reject(String reason) {
        return new IllegalStateException("Security Center semantic contract rejected: " + reason);
    }

    private interface MethodPredicate { boolean matches(Method method); }
    private interface FieldPredicate { boolean matches(Field field); }

    static final class ResolvedContract {
        private final Class<?> turboClass;
        private final Class<?> wrapperClass;
        private final Class<?> managerClass;
        private final Class<?> assistantTypeClass;
        private final Class<?> gameBoxClass;
        private final Class<?> gameMaterialClass;
        private final Class<?> videoAdapterClass;
        private final Class<?> allAppsClass;
        private final Method configure;
        private final Method dockReady;
        private final Method toggleAllApps;
        private final Method finalBackground;
        private final Method wrapperTurboGetter;
        private final Method removeAnimated;
        private final Method removeWithoutAnimation;
        private final Method dockGetter;
        private final Method appsGetter;
        private final Method boxGetter;
        private final Method gameGetter;
        private final Method gameMaterialGetter;
        private final Method gameMaterialRestore;
        private final Method videoAdapterGetter;
        private final Method videoMaterialRestore;
        private final Method assistantTypeDiscriminator;
        private final Field allAppsPresent;
        private final Field transforming;

        ResolvedContract(
                Class<?> turboClass, Class<?> wrapperClass, Class<?> managerClass,
                Class<?> assistantTypeClass, Class<?> gameBoxClass, Class<?> gameMaterialClass,
                Class<?> videoAdapterClass, Class<?> allAppsClass, Method configure,
                Method dockReady, Method toggleAllApps, Method finalBackground,
                Method wrapperTurboGetter, Method removeAnimated, Method removeWithoutAnimation,
                Method dockGetter, Method appsGetter, Method boxGetter, Method gameGetter,
                Method gameMaterialGetter, Method gameMaterialRestore, Method videoAdapterGetter,
                Method videoMaterialRestore, Method assistantTypeDiscriminator,
                Field allAppsPresent, Field transforming) {
            this.turboClass = turboClass;
            this.wrapperClass = wrapperClass;
            this.managerClass = managerClass;
            this.assistantTypeClass = assistantTypeClass;
            this.gameBoxClass = gameBoxClass;
            this.gameMaterialClass = gameMaterialClass;
            this.videoAdapterClass = videoAdapterClass;
            this.allAppsClass = allAppsClass;
            this.configure = configure;
            this.dockReady = dockReady;
            this.toggleAllApps = toggleAllApps;
            this.finalBackground = finalBackground;
            this.wrapperTurboGetter = wrapperTurboGetter;
            this.removeAnimated = removeAnimated;
            this.removeWithoutAnimation = removeWithoutAnimation;
            this.dockGetter = dockGetter;
            this.appsGetter = appsGetter;
            this.boxGetter = boxGetter;
            this.gameGetter = gameGetter;
            this.gameMaterialGetter = gameMaterialGetter;
            this.gameMaterialRestore = gameMaterialRestore;
            this.videoAdapterGetter = videoAdapterGetter;
            this.videoMaterialRestore = videoMaterialRestore;
            this.assistantTypeDiscriminator = assistantTypeDiscriminator;
            this.allAppsPresent = allAppsPresent;
            this.transforming = transforming;
        }

        Class<?> turboClass() { return turboClass; }
        Class<?> wrapperClass() { return wrapperClass; }
        Class<?> managerClass() { return managerClass; }
        Class<?> assistantTypeClass() { return assistantTypeClass; }
        Class<?> gameBoxClass() { return gameBoxClass; }
        Class<?> gameMaterialClass() { return gameMaterialClass; }
        Class<?> videoAdapterClass() { return videoAdapterClass; }
        Class<?> allAppsClass() { return allAppsClass; }
        Method configure() { return configure; }
        Method dockReady() { return dockReady; }
        Method toggleAllApps() { return toggleAllApps; }
        Method finalBackground() { return finalBackground; }
        Method wrapperTurboGetter() { return wrapperTurboGetter; }
        Method removeAnimated() { return removeAnimated; }
        Method removeWithoutAnimation() { return removeWithoutAnimation; }
        Method dockGetter() { return dockGetter; }
        Method appsGetter() { return appsGetter; }
        Method boxGetter() { return boxGetter; }
        Method gameGetter() { return gameGetter; }
        Method gameMaterialGetter() { return gameMaterialGetter; }
        Method gameMaterialRestore() { return gameMaterialRestore; }
        Method videoAdapterGetter() { return videoAdapterGetter; }
        Method videoMaterialRestore() { return videoMaterialRestore; }
        Method assistantTypeDiscriminator() { return assistantTypeDiscriminator; }
        Field allAppsPresent() { return allAppsPresent; }
        Field transforming() { return transforming; }
    }
}
