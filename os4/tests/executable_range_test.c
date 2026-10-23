#include <assert.h>
#include <string.h>
#include "../native/executable_range.h"
static int check(const char *text,uintptr_t p,size_t n) { FILE *f=fmemopen((void *)text,strlen(text),"r"); assert(f); int ok=ld_executable_maps(f,p,n); fclose(f); return ok; }
int main(void) {
 assert(check("1000-2000 r-xp\n2000-3000 rwxp\n",0x1fdc,2192));
 assert(!check("1000-2000 r-xp\n2100-3000 r-xp\n",0x1fdc,2192));
 assert(!check("1000-2000 r-xp\n2000-3000 rw-p\n",0x1fdc,2192));
 assert(check("1000-2000 r-xp\n",0x1f00,0x100));
 assert(!check("1000-2000 r-xp\n",UINTPTR_MAX-10,100));
 puts("PASS function verification across adjacent executable VMAs, with holes and NX rejected");
}
