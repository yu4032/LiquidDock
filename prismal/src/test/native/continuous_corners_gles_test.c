/* Optional real-GLES geometry regression test; run
 * scripts/verify_continuous_corners.sh. */
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
static char *readfile(const char *path) {
  FILE *f = fopen(path, "rb");
  if (!f) {
    perror(path);
    exit(2);
  }
  fseek(f, 0, SEEK_END);
  long n = ftell(f);
  rewind(f);
  char *s = calloc(n + 1, 1);
  fread(s, 1, n, f);
  fclose(f);
  return s;
}
static GLuint compile(GLenum kind, const char *source) {
  GLuint s = glCreateShader(kind);
  glShaderSource(s, 1, &source, NULL);
  glCompileShader(s);
  GLint ok;
  glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
  if (!ok) {
    char log[10000];
    glGetShaderInfoLog(s, sizeof(log), NULL, log);
    puts(log);
    exit(3);
  }
  return s;
}
static GLuint program(const char *path) {
  const char *v = "attribute vec2 a; varying vec2 v_screenTexCoord; varying "
                  "vec2 v_shapeCoord; void main(){gl_Position=vec4(a,0.,1.); "
                  "v_screenTexCoord=a*.5+.5; v_shapeCoord=a*.5;}";
  char *f = readfile(path);
  GLuint p = glCreateProgram(), vs = compile(GL_VERTEX_SHADER, v),
         fs = compile(GL_FRAGMENT_SHADER, f);
  glAttachShader(p, vs);
  glAttachShader(p, fs);
  glBindAttribLocation(p, 0, "a");
  glLinkProgram(p);
  GLint ok;
  glGetProgramiv(p, GL_LINK_STATUS, &ok);
  if (!ok) {
    char log[10000];
    glGetProgramInfoLog(p, sizeof(log), NULL, log);
    puts(log);
    exit(4);
  }
  free(f);
  glDeleteShader(vs);
  glDeleteShader(fs);
  return p;
}
static int continuous_mode = 1;
static float sample(GLuint p, float w, float h, const float *r, float x,
                    float y) {
  glUseProgram(p);
  glUniform1f(glGetUniformLocation(p, "u_continuousCorners"), continuous_mode);
  glUniform1f(glGetUniformLocation(p, "u_sminSmoothing"), 2.f);
  glUniform2f(glGetUniformLocation(p, "u_glassSize"), w, h);
  glUniform1f(glGetUniformLocation(p, "u_heightTransitionWidth"), 19.f);
  glUniform1f(glGetUniformLocation(p, "u_refractionInset"), 20.f);
  glUniform4fv(glGetUniformLocation(p, "u_cornerRadii"), 1, r);
  glUniform2f(glGetUniformLocation(p, "u_testPoint"), x, y);
  glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
  unsigned char b[4];
  glReadPixels(0, 0, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, b);
  return (b[0] * 256 + b[1]) / 64.f - 256.f;
}
int main(int argc, char **argv) {
  if (argc < 2 || chdir(argv[1]) != 0) {
    perror("test directory");
    return 2;
  }
  EGLDisplay d = eglGetDisplay(EGL_DEFAULT_DISPLAY);
  EGLint major, minor;
  if (!eglInitialize(d, &major, &minor)) {
    printf("EGL initialize error %x\n", eglGetError());
    return 2;
  }
  eglBindAPI(EGL_OPENGL_ES_API);
  EGLint attrs[] = {EGL_SURFACE_TYPE,
                    EGL_PBUFFER_BIT,
                    EGL_RENDERABLE_TYPE,
                    EGL_OPENGL_ES2_BIT,
                    EGL_RED_SIZE,
                    8,
                    EGL_GREEN_SIZE,
                    8,
                    EGL_BLUE_SIZE,
                    8,
                    EGL_ALPHA_SIZE,
                    8,
                    EGL_NONE};
  EGLConfig config;
  EGLint count;
  eglChooseConfig(d, attrs, &config, 1, &count);
  EGLint pb[] = {EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE};
  EGLSurface surf = eglCreatePbufferSurface(d, config, pb);
  EGLint ca[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
  EGLContext ctx = eglCreateContext(d, config, EGL_NO_CONTEXT, ca);
  if (!eglMakeCurrent(d, surf, surf, ctx)) {
    puts("make current failed");
    return 2;
  }
  printf("GPU: %s / %s\n", glGetString(GL_RENDERER), glGetString(GL_VERSION));
  float verts[] = {-1, -1, 1, -1, -1, 1, 1, 1};
  glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, verts);
  glEnableVertexAttribArray(0);
  glDisable(GL_DITHER);
  glViewport(0, 0, 1, 1);
  for (int i = 2; i < argc; i++) {
    GLuint p = program(argv[i]);
    printf("Compiled and linked: %s\n", argv[i]);
    glDeleteProgram(p);
  }
  GLuint p = program("distance.frag");
  FILE *f = fopen("cases.txt", "r");
  float w, h, r, x, y, maxerr = 0;
  int n = 0;
  while (fscanf(f, "%f %f %f %f %f", &w, &h, &r, &x, &y) == 5) {
    float radii[] = {r, r, r, r};
    float dist = sample(p, w, h, radii, x, y);
    maxerr = fmaxf(maxerr, fabsf(dist));
    if (fabsf(dist) > .04) {
      printf("Boundary mismatch: %f %f %f (%f,%f): %f\n", w, h, r, x, y, dist);
      return 5;
    }
    n++;
  }
  fclose(f);
  // Every quadrant must pick the same radius for mask and height samples.
  float radii[] = {0, 10, 20, 30};
  float signs[][2] = {{-1, 1}, {1, 1}, {1, -1}, {-1, -1}};
  for (int corner = 0; corner < 4; corner++)
    for (int offset = 0; offset < 16; offset++) {
      float xx = signs[corner][0] * (100 - offset),
            yy = signs[corner][1] * (80 - offset);
      float all[] = {radii[corner], radii[corner], radii[corner],
                     radii[corner]};
      float a = sample(p, 200, 160, radii, xx, yy),
            b = sample(p, 200, 160, all, xx, yy);
      if (fabsf(a - b) > .02) {
        puts("Asymmetric radius mismatch");
        return 6;
      }
    }
  printf("PASS: %d CPU contour points match GPU field (max error %.5f px); 64 "
         "asymmetric-radius samples match.\n",
         n, maxerr);
  GLuint depth = program("depth.frag");
  float asymmetric[] = {0, 40, 0, 0};
  float above = sample(depth, 400, 80, asymmetric, 100, .01),
        below = sample(depth, 400, 80, asymmetric, 100, -.01);
  if (fabsf(above - below) > .05) {
    printf("Body-depth seam: %.5f vs %.5f\n", above, below);
    return 8;
  }
  printf("PASS: asymmetric body depth is continuous (%.5f vs %.5f).\n", above,
         below);
  // Off must reproduce the unmodified upstream field, including smooth-min
  // rounding.
  GLuint reference = program("reference.frag");
  continuous_mode = 0;
  int legacySamples = 0;
  for (int radius = 0; radius <= 40; radius += 10) {
    float same[] = {radius, radius, radius, radius};
    for (int xx = -100; xx <= 100; xx += 5) {
      for (int yy = -40; yy <= 40; yy += 5) {
        float actual = sample(p, 200, 80, same, xx, yy);
        float expected = sample(reference, 200, 80, same, xx, yy);
        if (fabsf(actual - expected) > .02f) {
          printf("Legacy restoration mismatch: %.5f vs %.5f\n", actual,
                 expected);
          return 9;
        }
        legacySamples++;
      }
    }
  }
  printf("PASS: switch off restores upstream distance at %d samples.\n",
         legacySamples);
  continuous_mode = 1;
  if (glGetError() != GL_NO_ERROR) {
    puts("GL error");
    return 7;
  }
  return 0;
}
