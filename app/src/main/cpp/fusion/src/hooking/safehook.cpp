/* All ARM64 hook entries are patched by the standalone four-byte-only Dobby. */
#include "fusion.h"
#include "dobby.h"
#include <android/log.h>

extern "C" {
bool safehook_initialize(void *lib_handle, uintptr_t, void *(*)(void *, void *, size_t)) {
    return lib_handle != nullptr;
}

int safehook_install(void *target, void *replacement, void **original) {
    // Dobby serializes registry access and rejects failed near jumps before writing code.
    int result = DobbyHook(target, replacement, original);
    if (result != 0)
        __android_log_print(ANDROID_LOG_ERROR, "SafeHook",
                            "Cannot install 4-byte ARM64 hook at %p", target);
    return result;
}

bool safehook_remove(void *target) {
    return target && DobbyDestroy(target) == 0;
}
}
