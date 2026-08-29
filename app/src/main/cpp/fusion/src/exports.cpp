/* P/Invoke exports for managed BepInExFusion code. */

#include <android/log.h>
#include <dlfcn.h>
#include <cstdint>
#include <cstdbool>

extern "C" {

// Logging (used by AndroidLogListener)

void write_log(const char *text)
{
    __android_log_write(ANDROID_LOG_INFO, "BepInEx", text);
}

void write_log_level(int level, const char *text)
{
    // Pass-through: managed side converts BepInEx bit-flags to Android log levels.
    __android_log_write(level, "BepInEx", text);
}

// Hook management (used by FusionInterop.hook/unhook)

void *hook(void *target, void *detour, bool specialReturnBuffer)
{
    (void)specialReturnBuffer;
    // Use Dobby to install the hook
    void *dobby = dlopen("libdobby.so", RTLD_NOLOAD);
    if (!dobby) return nullptr;

    typedef int (*DobbyHook_t)(void *, void *, void **);
    auto DH = (DobbyHook_t)dlsym(dobby, "DobbyHook");
    if (!DH) return nullptr;

    void *original = nullptr;
    int rc = DH(target, detour, &original);
    if (rc != 0) return nullptr;

    return original;  // Return the trampoline (original function)
}

void unhook(void *target)
{
    void *dobby = dlopen("libdobby.so", RTLD_NOLOAD);
    if (!dobby) return;

    typedef int (*DobbyDestroy_t)(void *);
    auto DD = (DobbyDestroy_t)dlsym(dobby, "DobbyDestroy");
    if (DD) DD(target);
}

} /* extern "C" */
