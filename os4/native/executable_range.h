#ifndef LD_EXECUTABLE_RANGE_H
#define LD_EXECUTABLE_RANGE_H
#include <stdint.h>
#include <stdio.h>
#include <stdbool.h>
static inline bool ld_executable_maps(FILE *maps,uintptr_t address,size_t size) {
 if(!maps || !size || size>UINTPTR_MAX-address) return false;
 uintptr_t cursor=address,limit=address+size; char line[512];
 while(fgets(line,sizeof line,maps)) {
  unsigned long start,end; char permissions[8];
  if(sscanf(line,"%lx-%lx %7s",&start,&end,permissions)!=3 || end<=cursor) continue;
  if(start>cursor || permissions[0]!='r' || permissions[2]!='x') return false;
  cursor=end; if(cursor>=limit) return true;
 }
 return false;
}
#endif
