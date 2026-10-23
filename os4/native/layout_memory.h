#ifndef LD_LAYOUT_MEMORY_H
#define LD_LAYOUT_MEMORY_H
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>

#define LD_LAYOUT_MAX_ITEMS 1024
struct ld_layout_item { int64_t id, screen; int32_t x,y,sx,sy; };
struct ld_layout_memory {
    int32_t columns, rows;
    int32_t active_direction, reserved;
    uint32_t count[2];
    struct ld_layout_item items[2][LD_LAYOUT_MAX_ITEMS];
};

static inline int ld_layout_bounds(const struct ld_layout_item *a,int c,int r) {
    return a->id>=0 && a->screen>=0 && a->sx>0 && a->sy>0 && a->x>=0 && a->y>=0
        && (int64_t)a->x+a->sx<=c && (int64_t)a->y+a->sy<=r;
}
static inline int ld_layout_overlap(const struct ld_layout_item *a,const struct ld_layout_item *b) {
    return a->screen==b->screen && (int64_t)a->x+a->sx>b->x && (int64_t)b->x+b->sx>a->x
        && (int64_t)a->y+a->sy>b->y && (int64_t)b->y+b->sy>a->y;
}
static inline int ld_layout_valid(const struct ld_layout_item *a,size_t n,int c,int r) {
    if(n>LD_LAYOUT_MAX_ITEMS || c<2 || c>10 || r<2 || r>10 || (!a && n)) return 0;
    for(size_t i=0;i<n;i++) {
        if(!ld_layout_bounds(&a[i],c,r)) return 0;
        for(size_t j=0;j<i;j++) if(a[i].id==a[j].id || ld_layout_overlap(&a[i],&a[j])) return 0;
    }
    return 1;
}
static inline int ld_layout_free(const struct ld_layout_item *a,const struct ld_layout_item *out,
        const unsigned char *placed,size_t n) {
    for(size_t j=0;j<n;j++) if(placed[j] && ld_layout_overlap(a,&out[j])) return 0;
    return 1;
}
/* Output remains untouched unless every item fits on its existing page. */
static inline int ld_layout_plan(const struct ld_layout_item *source,size_t n,int c,int r,
        const struct ld_layout_item *remembered,size_t rn,struct ld_layout_item *output) {
    if(!source || !output || n>LD_LAYOUT_MAX_ITEMS || rn>LD_LAYOUT_MAX_ITEMS
            || c<2 || c>10 || r<2 || r>10 || (rn && !remembered)) return 0;
    struct ld_layout_item *tmp=calloc(n?n:1,sizeof *tmp);
    unsigned char *placed=calloc(n?n:1,1);
    if(!tmp || !placed) { free(tmp); free(placed); return 0; }
    int ok=0;
    for(size_t i=0;i<n;i++) {
        if(source[i].id<0 || source[i].screen<0 || source[i].sx<=0 || source[i].sy<=0
                || source[i].sx>c || source[i].sy>r) goto done;
        for(size_t j=0;j<i;j++) if(source[i].id==source[j].id) goto done;
        tmp[i]=source[i];
    }
    if(ld_layout_valid(remembered,rn,c,r)) for(size_t i=0;i<n;i++) for(size_t j=0;j<rn;j++) {
        if(source[i].id==remembered[j].id && source[i].screen==remembered[j].screen
                && source[i].sx==remembered[j].sx && source[i].sy==remembered[j].sy) {
            tmp[i]=remembered[j]; placed[i]=1; break;
        }
    }
    for(size_t pass=0;pass<n;pass++) {
        size_t best=n;
        for(size_t i=0;i<n;i++) if(!placed[i] && (best==n
                || tmp[i].sx*tmp[i].sy>tmp[best].sx*tmp[best].sy
                || (tmp[i].sx*tmp[i].sy==tmp[best].sx*tmp[best].sy && tmp[i].id<tmp[best].id))) best=i;
        if(best==n) break;
        struct ld_layout_item a=tmp[best], winner=a;
        int64_t distance=INT64_MAX;
        for(int y=0;y+a.sy<=r;y++) for(int x=0;x+a.sx<=c;x++) {
            a.x=x; a.y=y;
            if(!ld_layout_free(&a,tmp,placed,n)) continue;
            int64_t dx=(int64_t)x-source[best].x, dy=(int64_t)y-source[best].y;
            int64_t d=dx*dx+dy*dy;
            if(d<distance) { distance=d; winner=a; }
        }
        if(distance==INT64_MAX) goto done;
        tmp[best]=winner; placed[best]=1;
    }
    if(!ld_layout_valid(tmp,n,c,r)) goto done;
    memcpy(output,tmp,n*sizeof *tmp); ok=1;
done:
    free(tmp); free(placed);
    /* New rectangular items may need to squeeze remembered icons. Preserve
     * exact positions first, then relax them only when that cannot fit. */
    if(!ok && rn) return ld_layout_plan(source,n,c,r,NULL,0,output);
    return ok;
}
static inline uint32_t ld_layout_checksum(const void *p,size_t n) {
    const unsigned char *b=p; uint32_t h=2166136261u;
    for(size_t i=0;i<n;i++) { h^=b[i]; h*=16777619u; }
    return h;
}
static inline int ld_layout_memory_valid(const struct ld_layout_memory *m) {
    return m->active_direction>=-1 && m->active_direction<=1
        && ld_layout_valid(m->items[0],m->count[0],m->columns,m->rows)
        && ld_layout_valid(m->items[1],m->count[1],m->rows,m->columns);
}
static inline int ld_layout_load(const char *path,struct ld_layout_memory *out) {
    FILE *f=fopen(path,"rb");
    if(!f) {
        char old[512]; size_t len=strlen(path);
        if(len>=sizeof old) return 0;
        memcpy(old,path,len+1); char *v=strstr(old,"-v3.bin");
        if(!v) return 0;
        v[2]='2'; f=fopen(old,"rb"); if(!f) return 0;
    }
    unsigned char magic[8]; uint32_t checksum=0;
    struct ld_layout_memory *m=malloc(sizeof *m); int ok=0;
    if(m && fread(magic,1,8,f)==8 && fread(&checksum,4,1,f)==1) {
        if(!memcmp(magic,"LDLAY03",8)) {
            if(fread(m,sizeof *m,1,f)==1 && checksum==ld_layout_checksum(m,sizeof *m)) ok=1;
        } else if(!memcmp(magic,"LDLAY02",8)) {
            struct legacy { int32_t columns,rows; uint32_t count[2]; struct ld_layout_item items[2][LD_LAYOUT_MAX_ITEMS]; };
            struct legacy *old=malloc(sizeof *old);
            if(old && fread(old,sizeof *old,1,f)==1 && checksum==ld_layout_checksum(old,sizeof *old)) {
                memset(m,0,sizeof *m); m->columns=old->columns; m->rows=old->rows; m->active_direction=-1;
                memcpy(m->count,old->count,sizeof m->count); memcpy(m->items,old->items,sizeof m->items); ok=1;
            }
            free(old);
        }
        if(ok && fgetc(f)==EOF && !ferror(f) && ld_layout_memory_valid(m)) *out=*m;
        else ok=0;
    }
    free(m); fclose(f); return ok;
}
static inline int ld_layout_save(const char *path,const struct ld_layout_memory *m) {
    if(!ld_layout_memory_valid(m)) return 0;
    char temp[512]; int k=snprintf(temp,sizeof temp,"%s.tmp",path);
    if(k<0 || (size_t)k>=sizeof temp) return 0;
    int fd=open(temp,O_WRONLY|O_CREAT|O_TRUNC|O_CLOEXEC,0600); if(fd<0) return 0;
    FILE *f=fdopen(fd,"wb"); if(!f) { close(fd); unlink(temp); return 0; }
    uint32_t checksum=ld_layout_checksum(m,sizeof *m);
    int ok=fwrite("LDLAY03",1,8,f)==8 && fwrite(&checksum,4,1,f)==1
        && fwrite(m,sizeof *m,1,f)==1 && !fflush(f) && !fsync(fd);
    if(fclose(f)) ok=0;
    if(ok && !rename(temp,path)) return 1;
    unlink(temp); return 0;
}
#endif
