package com.hellovoid.prismal;

import org.junit.Test;
import java.lang.reflect.InvocationTargetException;
import static org.junit.Assert.*;

public class PrismalFrozenBackdropTest {
    @Test public void reblurRequiresAnOwnedSourceRatherThanAnExternalTextureHandle() throws Exception {
        PrismalRenderer renderer = new PrismalRenderer();
        java.lang.reflect.Method reblur = PrismalRenderer.class.getMethod("reblurPreparedBackdrop", PrismalParams.class);
        InvocationTargetException error = assertThrows(InvocationTargetException.class,
                () -> reblur.invoke(renderer, PrismalParams.builder().build()));
        assertTrue(error.getCause() instanceof IllegalStateException);
    }
}
