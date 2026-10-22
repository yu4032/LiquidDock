#include "process_gate.h"

#include <fcntl.h>
#include <string.h>
#include <unistd.h>

int os4_matches_launcher_cmdline(const char *buffer, size_t bytes) {
    static const char name[] = "com.miui.home";
    if (buffer == NULL || bytes < sizeof(name)) return 0;
    /* Do not confuse com.miui.home:remote, malformed or partial argv[0]
       with the only process permitted to run launcher hooks. */
    const char *nul = memchr(buffer, '\0', bytes);
    if (nul == NULL || (size_t)(nul - buffer) != sizeof(name) - 1) return 0;
    return memcmp(buffer, name, sizeof(name) - 1) == 0;
}

int os4_is_launcher_process(void) {
    char buffer[256];
    int fd = open("/proc/self/cmdline", O_RDONLY | O_CLOEXEC);
    if (fd < 0) return 0;
    ssize_t count = read(fd, buffer, sizeof(buffer));
    (void)close(fd);
    return count > 0 && os4_matches_launcher_cmdline(buffer, (size_t)count);
}
