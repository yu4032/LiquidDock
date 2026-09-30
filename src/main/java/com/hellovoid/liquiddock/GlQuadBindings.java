package com.hellovoid.liquiddock;

import android.opengl.GLES20;

import java.nio.FloatBuffer;

/** Program-local fullscreen quad attribute locations, resolved once after link. */
final class GlQuadBindings {
    private final int position;
    private final int uv;

    private GlQuadBindings(int position, int uv) {
        this.position = position;
        this.uv = uv;
    }

    static GlQuadBindings resolve(int program) {
        int position = GLES20.glGetAttribLocation(program, "aPosition");
        int uv = GLES20.glGetAttribLocation(program, "aUv");
        if (position < 0 || uv < 0) {
            throw new IllegalStateException("quad attribute unavailable");
        }
        return new GlQuadBindings(position, uv);
    }

    void bind(FloatBuffer quadBuffer) {
        if (quadBuffer == null) throw new IllegalArgumentException("quadBuffer == null");
        quadBuffer.position(0);
        GLES20.glEnableVertexAttribArray(position);
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false,
                4 * Float.BYTES, quadBuffer);
        quadBuffer.position(2);
        GLES20.glEnableVertexAttribArray(uv);
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false,
                4 * Float.BYTES, quadBuffer);
    }

    void unbind() {
        GLES20.glDisableVertexAttribArray(position);
        GLES20.glDisableVertexAttribArray(uv);
    }
}
