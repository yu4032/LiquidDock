#include "sha256_region.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static void expect(const char *message, const char *shahex)
{
    struct ld_sha256 s;
    unsigned char hash[32];
    char actual[65];
    ld_sha256_init(&s);
    ld_sha256_update(&s,message,strlen(message));
    ld_sha256_final(&s,hash);
    for(unsigned i=0;i<32;i++)sprintf(actual+i*2,"%02x",hash[i]);
    if(strcmp(actual,shahex)!=0) {
        fprintf(stderr,"FAIL: SHA256 %s\n",message);
        exit(1);
    }
}
int main(void)
{
    expect("","e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    expect("abc","ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    expect("The quick brown fox jumps over the lazy dog",
           "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592");
    puts("PASS: SHA-256 published vectors");
    return 0;
}
