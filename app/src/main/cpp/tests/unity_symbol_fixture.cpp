#include <cstddef>

// A tiny Unity stand-in: the real mangled names, no Unity/game dependencies.
extern "C" int JNI_OnLoad(void*, void*) { return 0x00010006; }

extern "C" void* fixture_invoke(void*, void*, void*, void*, bool)
    asm("_Z23scripting_method_invoke18ScriptingMethodPtr18ScriptingObjectPtrR18ScriptingArgumentsP21ScriptingExceptionPtrb");
extern "C" __attribute__((noinline, visibility("hidden")))
void* fixture_invoke(void*, void* object, void*, void*, bool) { return object; }
extern "C" void* fixture_invoke_address() { return reinterpret_cast<void*>(fixture_invoke); }

#ifndef OMIT_PATH_CHECK
extern "C" bool fixture_path_check(const void*, const char* const*, size_t)
    asm("_ZL21IsInForbiddenLocationRKN4core12basic_stringIcNS_20StringStorageDefaultIcEEEEPKPKcm");
extern "C" __attribute__((noinline, visibility("hidden")))
bool fixture_path_check(const void* path, const char* const* blacklist, size_t count) {
    return path && blacklist && count != 0;
}
extern "C" void* fixture_path_check_address() { return reinterpret_cast<void*>(fixture_path_check); }
#else
extern "C" void* fixture_path_check_address() { return nullptr; }
#endif
