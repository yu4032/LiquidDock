#ifndef LIQUIDDOCK_OS4_SHA256_REGION_H
#define LIQUIDDOCK_OS4_SHA256_REGION_H

/* Small, dependency-free SHA-256 for attesting vendor ELF bytes directly from
 * the exact APK mapping. No external process, libcrypto ABI, or shell call.
 * This is intentionally used only once before invasive database changes. */
#include <stdint.h>
#include <stddef.h>
#include <stdio.h>
#include <string.h>
#include <limits.h>

struct ld_sha256 {
    uint32_t state[8];
    uint64_t total;
    unsigned char block[64];
    size_t fill;
};

static const uint32_t ld_sha256_k[64] = {
    0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
    0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
    0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
    0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
    0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
    0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
    0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
    0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
};

static inline uint32_t ld_sha256_rotr(uint32_t x, unsigned n)
{
    return (x >> n) | (x << (32 - n));
}

static inline void ld_sha256_init(struct ld_sha256 *s)
{
    static const uint32_t initial[8] = {
        0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,
        0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19
    };
    memcpy(s->state, initial, sizeof initial);
    s->total = 0;
    s->fill = 0;
}

static inline void ld_sha256_compress(struct ld_sha256 *s)
{
    uint32_t w[64];
    for (unsigned i = 0; i < 16; i++) {
        const unsigned char *p = s->block + i * 4;
        w[i] = ((uint32_t)p[0] << 24) | ((uint32_t)p[1] << 16) |
               ((uint32_t)p[2] << 8) | p[3];
    }
    for (unsigned i = 16; i < 64; i++) {
        uint32_t v = w[i-15], u = w[i-2];
        uint32_t a = ld_sha256_rotr(v,7) ^ ld_sha256_rotr(v,18) ^ (v >> 3);
        uint32_t b = ld_sha256_rotr(u,17) ^ ld_sha256_rotr(u,19) ^ (u >> 10);
        w[i] = w[i-16] + a + w[i-7] + b;
    }
    uint32_t a=s->state[0],b=s->state[1],c=s->state[2],d=s->state[3];
    uint32_t e=s->state[4],f=s->state[5],g=s->state[6],h=s->state[7];
    for (unsigned i=0;i<64;i++) {
        uint32_t sum1=ld_sha256_rotr(e,6)^ld_sha256_rotr(e,11)^ld_sha256_rotr(e,25);
        uint32_t ch=(e&f)^(~e&g);
        uint32_t t1=h+sum1+ch+ld_sha256_k[i]+w[i];
        uint32_t sum0=ld_sha256_rotr(a,2)^ld_sha256_rotr(a,13)^ld_sha256_rotr(a,22);
        uint32_t maj=(a&b)^(a&c)^(b&c);
        uint32_t t2=sum0+maj;
        h=g;g=f;f=e;e=d+t1;d=c;c=b;b=a;a=t1+t2;
    }
    uint32_t end[]={a,b,c,d,e,f,g,h};
    for (unsigned i=0;i<8;i++)s->state[i]+=end[i];
}

static inline void ld_sha256_update(struct ld_sha256 *s, const void *data, size_t n)
{
    const unsigned char *p=(const unsigned char *)data;
    s->total+=n;
    while(n) {
        size_t part=64-s->fill;
        if(part>n)part=n;
        memcpy(s->block+s->fill,p,part);
        s->fill+=part;p+=part;n-=part;
        if(s->fill==64){ld_sha256_compress(s);s->fill=0;}
    }
}

static inline void ld_sha256_final(struct ld_sha256 *s, unsigned char out[32])
{
    uint64_t bits=s->total*8;
    unsigned char one=0x80,zero=0;
    ld_sha256_update(s,&one,1);
    while(s->fill!=56)ld_sha256_update(s,&zero,1);
    unsigned char length[8];
    for(unsigned i=0;i<8;i++)length[7-i]=(unsigned char)(bits>>(i*8));
    ld_sha256_update(s,length,8);
    for(unsigned i=0;i<8;i++)
        for(unsigned j=0;j<4;j++)out[i*4+j]=(unsigned char)(s->state[i]>>(24-j*8));
}

/* File region must be exactly the known byte length of the vendor ELF. */
static inline int ld_sha256_region(const char *path, size_t offset, size_t bytes, unsigned char out[32])
{
    if (!path || bytes==0 || offset>(size_t)LONG_MAX) return 0;
    FILE *f=fopen(path,"rb");
    if(!f)return 0;
    if(fseek(f,(long)offset,SEEK_SET)!=0){fclose(f);return 0;}
    struct ld_sha256 sha;
    ld_sha256_init(&sha);
    unsigned char block[65536];
    size_t left=bytes;
    int ok=1;
    while(left) {
        size_t wanted=left<sizeof block?left:sizeof block;
        if(fread(block,1,wanted,f)!=wanted){ok=0;break;}
        ld_sha256_update(&sha,block,wanted);
        left-=wanted;
    }
    fclose(f);
    if(ok)ld_sha256_final(&sha,out);
    return ok;
}

#endif
