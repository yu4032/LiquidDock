#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <math.h>

#define W 512
#define H 384
static char *readfile(const char *path) {
    FILE *f = fopen(path, "rb"); if (!f) { perror(path); exit(2); }
    fseek(f, 0, SEEK_END); long n = ftell(f); rewind(f);
    char *s = calloc((size_t)n + 1, 1); if (!s || fread(s, 1, n, f) != (size_t)n) exit(2);
    fclose(f); return s;
}
static GLuint compile(GLenum type, const char *filename) {
    char *s = readfile(filename); GLuint shader = glCreateShader(type);
    const char *src = s; glShaderSource(shader, 1, &src, NULL); glCompileShader(shader); free(s);
    GLint ok; glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
    if (!ok) { char log[8192]; glGetShaderInfoLog(shader, sizeof(log), NULL, log); fprintf(stderr, "%s: %s\n", filename, log); exit(3); }
    return shader;
}
static GLuint program(const char *vertex, const char *fragment) {
    GLuint p = glCreateProgram(), v = compile(GL_VERTEX_SHADER, vertex), f = compile(GL_FRAGMENT_SHADER, fragment);
    glAttachShader(p, v); glAttachShader(p, f); glBindAttribLocation(p, 0, "a_position"); glLinkProgram(p);
    GLint ok; glGetProgramiv(p, GL_LINK_STATUS, &ok);
    if (!ok) { char log[8192]; glGetProgramInfoLog(p, sizeof(log), NULL, log); fprintf(stderr, "%s: %s\n", fragment, log); exit(3); }
    glDeleteShader(v); glDeleteShader(f); return p;
}
static void f1(GLuint p, const char *n, float v) { glUniform1f(glGetUniformLocation(p,n),v); }
static void f2(GLuint p, const char *n, float a, float b) { glUniform2f(glGetUniformLocation(p,n),a,b); }
static void f4(GLuint p, const char *n, float a, float b, float c, float d) { glUniform4f(glGetUniformLocation(p,n),a,b,c,d); }
static void init_uniforms(GLuint p, int test) {
    glUseProgram(p);
    glUniform1i(glGetUniformLocation(p,"u_backgroundTexture"),0);
    glUniform1i(glGetUniformLocation(p,"u_blurredTexture"),1);
    glUniform1i(glGetUniformLocation(p,"u_useBlurredTexture"),test % 2);
    f2(p,"u_resolution",W,H); f2(p,"u_mousePos",W*.5f,H*.5f);
    f2(p,"u_glassSize",test % 3 == 0 ? 468.f : 320.f, test % 3 == 0 ? 316.f : 280.f);
    f4(p,"u_cornerRadii",20,40,65,12); f1(p,"u_continuousCorners",test / 12);
    f1(p,"u_refractionInset",20); f1(p,"u_sminSmoothing",(float[]){0,2,24}[test%3]);
    f1(p,"u_heightTransitionWidth",(float[]){5,20,80}[(test/3)%3]);
    f1(p,"u_edgeRefractionFalloff",4); f1(p,"u_ior",1.5); f1(p,"u_glassThickness",15);
    f1(p,"u_normalStrength",1.2); f1(p,"u_displacementScale",1); f1(p,"u_lensRefractionPx",25);
    f1(p,"u_lensDepthEffect",test%2 ? .8f : 0); f1(p,"u_chromaticAberration",test%2 ? .8f : 0);
    f1(p,"u_dispersionR",1.1); f1(p,"u_dispersionB",.9); f1(p,"u_vibrancy",1.2);
    f1(p,"u_liquidDome",test%2 ? 1.f : 0.f); f1(p,"u_fresnelReflect",1);
    f1(p,"u_brightness",1); f4(p,"u_glassColor",.75,.85,.95,.15); f1(p,"u_highlightWidth",1);
    f2(p,"u_lightDir",.3,.6); f1(p,"u_invariantFresnelR0",.04f);
    f2(p,"u_invariantLightXY",.4472136f,.8944272f); f1(p,"u_invariantShadowExtent",.375f);
    glUniform3f(glGetUniformLocation(p,"u_invariantOs4LightDir"),.3162278f,.6324555f,.7071068f);
    glUniform3f(glGetUniformLocation(p,"u_invariantLightPrimary"),.1916f,.3832f,.9036f);
    f1(p,"u_os4EdgeEnabled",1); f1(p,"u_os4EdgeWidthPx",8); f1(p,"u_os4ReflectOffsetPx",7);
    f1(p,"u_os4ReflectionStrength",.7); f1(p,"u_os4ReflectionLighten",.5);
    f1(p,"u_os4DirectionalAngleRange",1); f1(p,"u_os4DirectionalIntensity",.7);
    f1(p,"u_os4DirectionalOppositeIntensity",.3); f1(p,"u_transmittance",.85);
    f4(p,"u_shadowColor",0,0,0,.2); f2(p,"u_backdropSampleScale",1,1);
    f1(p,"u_backdropPinch",1); f2(p,"u_glowCenter",.5,.5); f1(p,"u_pressProgress",test%3==0 ? .4f : 0);
    f1(p,"u_componentSkyHaze",1); f1(p,"u_componentSpecular",1); f1(p,"u_componentCaustics",1);
    f1(p,"u_componentPressGlow",1); f1(p,"u_componentLitRim",1); f1(p,"u_componentOppositeRim",1);
    f1(p,"u_componentCornerRim",1); f1(p,"u_componentFaceSheen",1); f1(p,"u_componentPlainHighlight",1);
    f1(p,"u_causticIntensity",.3); f1(p,"u_specular",.7); f1(p,"u_shininess",16); f1(p,"u_rimStrength",.4);
    f1(p,"u_edgeRasterGuardPx",8); f1(p,"u_backlitWidthPx",4); f1(p,"u_backlitStrength",.3);
    f1(p,"u_backlitSideStart",.4); f1(p,"u_backlitSideSpan",.3); f1(p,"u_castShadowRadiusPx",5);
    f4(p,"u_castShadowColor",0,0,0,.1);
}
static long long nanos(void) { struct timespec t; clock_gettime(CLOCK_MONOTONIC,&t); return (long long)t.tv_sec*1000000000LL+t.tv_nsec; }
static PFNGLGENQUERIESEXTPROC gen_queries;
static PFNGLBEGINQUERYEXTPROC begin_query;
static PFNGLENDQUERYEXTPROC end_query;
static PFNGLGETQUERYOBJECTUI64VEXTPROC query_result;
static PFNGLDELETEQUERIESEXTPROC delete_queries;
typedef struct { double wall, gpu; } Timing;
static Timing bench(GLuint p) {
    const int iterations = 40;
    init_uniforms(p, 0);
    glUniform1i(glGetUniformLocation(p,"u_useBlurredTexture"),1);
    for (int i=0;i<5;i++) glDrawArrays(GL_TRIANGLE_STRIP,0,4);
    glFinish();
    GLuint q=0;
    if (gen_queries && begin_query && end_query && query_result && delete_queries) {
        gen_queries(1,&q); begin_query(GL_TIME_ELAPSED_EXT,q);
    }
    long long start=nanos();
    for (int i=0;i<iterations;i++) glDrawArrays(GL_TRIANGLE_STRIP,0,4);
    if (q) end_query(GL_TIME_ELAPSED_EXT);
    glFinish();
    Timing result={(nanos()-start)/(iterations*1000000.0),-1};
    if(q) {
        GLuint64 gpuNs=0; GLint disjoint=0;
        query_result(q,GL_QUERY_RESULT_EXT,&gpuNs); glGetIntegerv(GL_GPU_DISJOINT_EXT,&disjoint);
        if(!disjoint) result.gpu=gpuNs/(iterations*1000000.0);
        delete_queries(1,&q);
    }
    return result;
}
static int compare_double(const void *a, const void *b) {
    double x=*(const double *)a, y=*(const double *)b; return (x>y)-(x<y);
}
static void compare_timing(GLuint oldp, GLuint newp, const char *name) {
    enum { ROUNDS=9 }; double oldwall[ROUNDS],newwall[ROUNDS],oldgpu[ROUNDS],newgpu[ROUNDS];
    for(int r=0;r<ROUNDS;r++) {
        Timing old, current;
        if(r%2) { current=bench(newp); old=bench(oldp); }
        else { old=bench(oldp); current=bench(newp); }
        oldwall[r]=old.wall; newwall[r]=current.wall; oldgpu[r]=old.gpu; newgpu[r]=current.gpu;
    }
    qsort(oldwall,ROUNDS,sizeof(double),compare_double); qsort(newwall,ROUNDS,sizeof(double),compare_double);
    qsort(oldgpu,ROUNDS,sizeof(double),compare_double); qsort(newgpu,ROUNDS,sizeof(double),compare_double);
    printf("%s median of %d alternating runs (40 frames/run): wall %.3f -> %.3f ms/frame",
            name, ROUNDS, oldwall[ROUNDS/2], newwall[ROUNDS/2]);
    if (oldgpu[ROUNDS/2] >= 0 && newgpu[ROUNDS/2] >= 0)
        printf("; GPU %.3f -> %.3f ms/frame", oldgpu[ROUNDS/2], newgpu[ROUNDS/2]);
    else printf("; GPU timer unavailable");
    putchar('\n');
}
int main(void) {
    EGLDisplay d=eglGetDisplay(EGL_DEFAULT_DISPLAY); EGLint a,b,n;
    if (!eglInitialize(d,&a,&b)) { fprintf(stderr,"eglInitialize: 0x%x\n",eglGetError()); return 2; }
    eglBindAPI(EGL_OPENGL_ES_API);
    EGLint attrs[]={EGL_SURFACE_TYPE,EGL_PBUFFER_BIT,EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_ALPHA_SIZE,8,EGL_NONE};
    EGLConfig config; if (!eglChooseConfig(d,attrs,&config,1,&n) || n<1) return 2;
    EGLint pb[]={EGL_WIDTH,W,EGL_HEIGHT,H,EGL_NONE}; EGLSurface surface=eglCreatePbufferSurface(d,config,pb);
    EGLint ctxattr[]={EGL_CONTEXT_CLIENT_VERSION,2,EGL_NONE}; EGLContext ctx=eglCreateContext(d,config,EGL_NO_CONTEXT,ctxattr);
    if (!eglMakeCurrent(d,surface,surface,ctx)) return 2;
    printf("Renderer: %s / %s\n",glGetString(GL_RENDERER),glGetString(GL_VERSION));
    int timerSupported = strstr((const char *)glGetString(GL_EXTENSIONS), "GL_EXT_disjoint_timer_query") != NULL;
    printf("GPU timer query available: %s\n", timerSupported ? "yes" : "no");
    if (timerSupported) {
        gen_queries=(PFNGLGENQUERIESEXTPROC)eglGetProcAddress("glGenQueriesEXT");
        begin_query=(PFNGLBEGINQUERYEXTPROC)eglGetProcAddress("glBeginQueryEXT");
        end_query=(PFNGLENDQUERYEXTPROC)eglGetProcAddress("glEndQueryEXT");
        query_result=(PFNGLGETQUERYOBJECTUI64VEXTPROC)eglGetProcAddress("glGetQueryObjectui64vEXT");
        delete_queries=(PFNGLDELETEQUERIESEXTPROC)eglGetProcAddress("glDeleteQueriesEXT");
    }
    float vertices[]={-.5f,-.5f,.5f,-.5f,-.5f,.5f,.5f,.5f};
    glVertexAttribPointer(0,2,GL_FLOAT,GL_FALSE,0,vertices); glEnableVertexAttribArray(0);
    glViewport(0,0,W,H); glDisable(GL_DITHER); glDisable(GL_BLEND);
    unsigned char *pixels=malloc(W*H*4), *before=malloc(W*H*4), *after=malloc(W*H*4);
    GLuint tex[2]; glGenTextures(2,tex);
    for(int t=0;t<2;t++) {
        for(int y=0;y<H;y++) for(int x=0;x<W;x++) { int i=(y*W+x)*4;
            pixels[i]=(x*3+y*5+t*37)%256; pixels[i+1]=(x+y*3+t*19)%256;
            pixels[i+2]=((x/17+y/13+t)%2)*220+15; pixels[i+3]=255; }
        glActiveTexture(GL_TEXTURE0+t); glBindTexture(GL_TEXTURE_2D,tex[t]);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,W,H,0,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
    }
    const char *vert[]={"vertex.glsl","decorated-vertex.glsl","decorated-vertex.glsl"};
    const char *old[]={"baseline.frag","decorated.frag","reconstructed.frag"};
    const char *opt[]={"optimized.frag","decorated-optimized.frag","reconstructed-optimized.frag"};
    long long changed=0, sum=0, covered=0; int max=0;
    for(int k=0;k<3;k++) {
        GLuint oldp=program(vert[k],old[k]), newp=program(vert[k],opt[k]);
        for(int t=0;t<24;t++) {
            init_uniforms(oldp,t); glClear(GL_COLOR_BUFFER_BIT); glDrawArrays(GL_TRIANGLE_STRIP,0,4); glReadPixels(0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,before);
            init_uniforms(newp,t); glClear(GL_COLOR_BUFFER_BIT); glDrawArrays(GL_TRIANGLE_STRIP,0,4); glReadPixels(0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,after);
            for(int i=0;i<W*H*4;i++) { int diff=abs(before[i]-after[i]); if(diff) changed++; sum+=diff; if(diff>max) max=diff; }
            for(int i=3;i<W*H*4;i+=4) if(before[i]>0) covered++;
        }
        compare_timing(oldp,newp,old[k]);
        glDeleteProgram(oldp); glDeleteProgram(newp);
    }
    printf("Pixel comparison: max channel delta=%d / 255, changed channels=%lld, sum=%lld\n",max,changed,sum);
    free(pixels); free(before); free(after);
    glDeleteTextures(2, tex);
    eglMakeCurrent(d, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(d, ctx); eglDestroySurface(d, surface); eglTerminate(d);
    if(covered < (long long) W*H*3) { fprintf(stderr,"Glass coverage too small: %lld\n",covered); return 1; }
    if(max>1) { fprintf(stderr,"Pixel difference exceeds 1/255\n"); return 1; }
    puts("PASS"); return 0;
}
