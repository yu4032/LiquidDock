package com.hellovoid.liquiddock;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class VendorMemberResolverTest {
    static class Base {
        @SuppressWarnings("unused") private int inheritedField = 7;

        String choose(Object value) { return "object"; }
        String choose(Number value) { return "base-number"; }
        String primitive(int value) { return "int"; }
        String nullable(Object value) { return "object"; }
        String ambiguous(CharSequence value) { return "chars"; }
        String ambiguous(Number value) { return "number"; }
        static String staticOnly(Integer value) { return "static"; }
        String instanceOnly(Integer value) { return "instance"; }
    }

    static class Derived extends Base {
        String choose(Integer value) { return "integer"; }
        @Override String choose(Number value) { return "derived-number"; }
        String nullable(String value) { return "string"; }
    }

    @Before public void before() {
        VendorMemberResolver.clearCachesForTests();
    }

    @After public void after() {
        VendorMemberResolver.clearCachesForTests();
    }

    @Test public void exactRuntimeReferenceTypeWins() {
        VendorMemberResolver.MethodResolution resolution = resolve(
                Derived.class, "choose", new Object[]{Integer.valueOf(3)}, false);
        Method method = resolution.method();
        assertEquals(VendorMemberResolver.MethodStatus.RESOLVED, resolution.status());
        assertEquals(Derived.class, method.getDeclaringClass());
        assertArrayEquals(new Class<?>[]{Integer.class}, method.getParameterTypes());
    }

    @Test public void primitiveWrapperExactBeatsAssignableSupertype() {
        VendorMemberResolver.MethodResolution resolution = resolve(
                Derived.class, "primitive", new Object[]{Integer.valueOf(3)}, false);
        assertEquals(VendorMemberResolver.MethodStatus.RESOLVED, resolution.status());
        assertArrayEquals(new Class<?>[]{int.class}, resolution.method().getParameterTypes());
    }

    @Test public void closestAssignableTypeAndNearestOverrideWin() {
        VendorMemberResolver.MethodResolution resolution = resolve(
                Derived.class, "choose", new Object[]{Double.valueOf(3.5)}, false);
        assertEquals(VendorMemberResolver.MethodStatus.RESOLVED, resolution.status());
        assertEquals(Derived.class, resolution.method().getDeclaringClass());
        assertArrayEquals(new Class<?>[]{Number.class}, resolution.method().getParameterTypes());
    }

    @Test public void nullChoosesUniqueMoreSpecificReferenceType() {
        VendorMemberResolver.MethodResolution resolution = resolve(
                Derived.class, "nullable", new Object[]{null}, false);
        assertEquals(VendorMemberResolver.MethodStatus.RESOLVED, resolution.status());
        assertArrayEquals(new Class<?>[]{String.class}, resolution.method().getParameterTypes());
    }

    @Test public void nullAcrossUnrelatedTypesIsAmbiguous() {
        VendorMemberResolver.MethodResolution resolution = resolve(
                Derived.class, "ambiguous", new Object[]{null}, false);
        assertEquals(VendorMemberResolver.MethodStatus.AMBIGUOUS, resolution.status());
        assertEquals(2, resolution.candidateSignatures().size());
    }

    @Test public void staticAndInstanceMethodsDoNotCrossResolve() {
        assertEquals(VendorMemberResolver.MethodStatus.NOT_FOUND,
                resolve(Derived.class, "staticOnly", new Object[]{Integer.valueOf(1)}, false)
                        .status());
        assertEquals(VendorMemberResolver.MethodStatus.NOT_FOUND,
                resolve(Derived.class, "instanceOnly", new Object[]{Integer.valueOf(1)}, true)
                        .status());
        assertEquals(VendorMemberResolver.MethodStatus.RESOLVED,
                resolve(Derived.class, "staticOnly", new Object[]{Integer.valueOf(1)}, true)
                        .status());
    }

    @Test public void missingAmbiguousAndResolvedMethodOutcomesAreCached() {
        VendorMemberResolver.MethodResolution resolved1 = resolve(
                Derived.class, "choose", new Object[]{Integer.valueOf(1)}, false);
        VendorMemberResolver.MethodResolution resolved2 = resolve(
                Derived.class, "choose", new Object[]{Integer.valueOf(1)}, false);
        assertSame(resolved1, resolved2);

        VendorMemberResolver.MethodResolution missing1 = resolve(
                Derived.class, "missing", new Object[0], false);
        VendorMemberResolver.MethodResolution missing2 = resolve(
                Derived.class, "missing", new Object[0], false);
        assertSame(missing1, missing2);

        VendorMemberResolver.MethodResolution ambiguous1 = resolve(
                Derived.class, "ambiguous", new Object[]{null}, false);
        VendorMemberResolver.MethodResolution ambiguous2 = resolve(
                Derived.class, "ambiguous", new Object[]{null}, false);
        assertSame(ambiguous1, ambiguous2);
        assertEquals(3, VendorMemberResolver.methodCacheSizeForTests());
    }

    @Test public void inheritedFieldResolutionIsNearestAndCached() {
        VendorMemberResolver.FieldResolution first =
                VendorMemberResolver.resolveField(Derived.class, "inheritedField");
        VendorMemberResolver.FieldResolution second =
                VendorMemberResolver.resolveField(Derived.class, "inheritedField");
        assertEquals(VendorMemberResolver.FieldStatus.RESOLVED, first.status());
        assertSame(first, second);
        Field field = first.field();
        assertNotNull(field);
        assertEquals(Base.class, field.getDeclaringClass());
        assertEquals(1, VendorMemberResolver.fieldCacheSizeForTests());
    }

    private static VendorMemberResolver.MethodResolution resolve(
            Class<?> type, String name, Object[] args, boolean requireStatic) {
        return VendorMemberResolver.resolveMethod(type, name, args, requireStatic);
    }
}
