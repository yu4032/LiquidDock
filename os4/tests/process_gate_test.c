#include "../native/process_gate.h"

#include <assert.h>
#include <stddef.h>
#include <stdio.h>

int main(void) {
    static const char exact[] = "com.miui.home";
    static const char with_arg[] = "com.miui.home\0extra\0";
    static const char remote[] = "com.miui.home:remote";
    static const char prefix[] = "com.miui.hom";
    static const char foreign[] = "com.android.systemui";
    static const char malformed[] = "com.miui.homeX";
    assert(os4_matches_launcher_cmdline(exact, sizeof(exact)));
    assert(os4_matches_launcher_cmdline(with_arg, sizeof(with_arg)));
    assert(!os4_matches_launcher_cmdline(remote, sizeof(remote)));
    assert(!os4_matches_launcher_cmdline(prefix, sizeof(prefix)));
    assert(!os4_matches_launcher_cmdline(foreign, sizeof(foreign)));
    assert(!os4_matches_launcher_cmdline(malformed, sizeof(malformed)));
    assert(!os4_matches_launcher_cmdline(exact, sizeof(exact) - 1));
    assert(!os4_matches_launcher_cmdline(NULL, 0));
    puts("OS4 process gate PASS");
    return 0;
}
