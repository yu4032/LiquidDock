#ifndef LD_LAYOUT_PROFILES_H
#define LD_LAYOUT_PROFILES_H
#include "layout_memory.h"
/* Preserve the previous grid's two snapshots before selecting another grid. */
static inline int ld_layout_select_profile(const char *path,int cols,int rows) {
    struct ld_layout_memory *m=calloc(1,sizeof *m); if(!m) return 0;
    int ok=1; char profile[768];
    if(ld_layout_load(path,m) && (m->columns!=cols || m->rows!=rows)) {
        int k=snprintf(profile,sizeof profile,"%s.%dx%d",path,m->columns,m->rows);
        if(k<0 || (size_t)k>=sizeof profile || !ld_layout_save(profile,m)) { ok=0; goto done; }
        k=snprintf(profile,sizeof profile,"%s.%dx%d",path,cols,rows);
        if(k<0 || (size_t)k>=sizeof profile) { ok=0; goto done; }
        if(ld_layout_load(profile,m) && m->columns==cols && m->rows==rows) ok=ld_layout_save(path,m);
    }
done:
    free(m); return ok;
}
#endif
