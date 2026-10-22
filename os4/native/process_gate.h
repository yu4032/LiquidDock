#ifndef LIQUIDDOCK_OS4_PROCESS_GATE_H
#define LIQUIDDOCK_OS4_PROCESS_GATE_H

#include <stddef.h>

/* Compares argv[0] as a NUL-terminated name inside a bounded cmdline buffer. */
int os4_matches_launcher_cmdline(const char *buffer, size_t bytes);
int os4_is_launcher_process(void);

#endif
