/* Hooks scripting_method_invoke and path_check in libunity.so. */

#include "fusion.h"
#include "utilities/elf.h"
#include <dlfcn.h>
#include <string>
#include <cstring>
#include <android/log.h>
#include <filesystem>
#include <limits>

#define TAG "LibUnityHook"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace fs = std::filesystem;

// scripting_method_invoke hook

using scripting_method_invoke_fn = void* (*)(void* method, void* obj,
                                              void* args, void* exc, bool something);
static scripting_method_invoke_fn g_original_scripting_method_invoke = nullptr;

static void* scripting_method_invoke_hook(void* method, void* obj,
                                           void* args, void* exc, bool something)
{
    if (!method) {
        return nullptr;
    }
    return g_original_scripting_method_invoke(method, obj, args, exc, something);
}

// IsInForbiddenLocation(const core::basic_string<...>&, const char* const*, size_t).
// Keep the Unity string opaque: it is not a null-terminated char*.

using path_check_fn = bool (*)(const void* path, const char* const* blacklist, size_t count);
static path_check_fn g_original_path_check = nullptr;

static bool path_check_hook(const void* path, const char* const*, size_t)
{
    LOGI("IsInForbiddenLocation called with string object %p — returning false", path);
    return false;
}

// Utility: get module base address via dlsym + dladdr

static uintptr_t get_module_base(const char* lib_name, const char* known_export_symbol) {
    void* handle = dlopen(lib_name, RTLD_NOLOAD | RTLD_LAZY);
    if (!handle) {
        handle = dlopen(lib_name, RTLD_NOW);
    }
    if (!handle) return 0;

    void* symbol_addr = dlsym(handle, known_export_symbol);
    dlclose(handle);

    if (!symbol_addr) return 0;

    Dl_info info;
    if (dladdr(symbol_addr, &info) && info.dli_fbase) {
        return reinterpret_cast<uintptr_t>(info.dli_fbase);
    }

    return 0;
}

// Public API

extern "C" {

bool try_hook_libunity(const char *libUnityPath, const char *fallbackLibUnityPath)
{
    LOGI("try_hook_libunity: %s", libUnityPath);

    // 1. Load the unstripped libunity.so
    void *handle = dlopen(libUnityPath, RTLD_NOW | RTLD_GLOBAL);
    if (!handle) {
        LOGE("Failed to load libunity for hooking: %s. Error: %s", libUnityPath, dlerror());
        return false;
    }

    // 2. Check for companion .sym.so file
    const fs::path libunity_path(libUnityPath);
    fs::path sym_path(libUnityPath);
    sym_path.replace_extension(".sym.so");
    if (!fs::exists(libunity_path) || !fs::exists(sym_path)) {
        LOGE("Failed to find libunity or libunity.sym.so at %s", libUnityPath);
        return false;
    }

    LOGI("Found libunity at %s", libUnityPath);
    LOGI("Found libunity.sym.so at %s", sym_path.c_str());

    // 3. Read RVA from .sym.so symbol table
    const char *mangled =
        "_Z23scripting_method_invoke18ScriptingMethodPtr18ScriptingObjectPtr"
        "R18ScriptingArgumentsP21ScriptingExceptionPtrb";

    uintptr_t rva = get_function_rva_from_files(libUnityPath, sym_path.c_str(), mangled);
    if (rva == 0) {
        LOGE("No unique executable scripting_method_invoke in matching libunity/symbol files");
        return false;
    }

    // 4. Get base address of loaded libunity
    uintptr_t base = get_module_base(libUnityPath, "JNI_OnLoad");
    if (base == 0) {
        LOGE("Failed to find base address of libunity");
        return false;
    }

    // 5. Calculate target and hook
    if (rva > std::numeric_limits<uintptr_t>::max() - base) {
        LOGE("Failed to find target function for scripting_method_invoke_hook");
        return false;
    }
    void *target = reinterpret_cast<void *>(base + rva);

    LOGI("scripting_method_invoke @ %p (base=%p, rva=0x%zx)", target, (void*)base, rva);

    int ret = safehook_install(
        target,
        reinterpret_cast<void *>(scripting_method_invoke_hook),
        reinterpret_cast<void **>(&g_original_scripting_method_invoke));

    if (ret != 0) {
        LOGE("DobbyHook scripting_method_invoke failed: %d", ret);
        return false;
    }

    LOGI("scripting_method_invoke hook installed");

    const char* path_check_symbol =
        "_ZL21IsInForbiddenLocationRKN4core12basic_stringIcNS_20StringStorageDefaultIcEEEEPKPKcm";
    uintptr_t path_check_rva = get_function_rva_from_files(
        libUnityPath, sym_path.c_str(), path_check_symbol);
    if (path_check_rva == 0 || path_check_rva > std::numeric_limits<uintptr_t>::max() - base) {
        LOGI("Skipping path check hook: no unique IsInForbiddenLocation in matching executable symbols");
        return true;
    }
    void *path_check_target = reinterpret_cast<void *>(base + path_check_rva);

    LOGI("path_check @ %p (base=%p, rva=0x%zx)", path_check_target, (void*)base, path_check_rva);

    int ret2 = safehook_install(
        path_check_target,
        reinterpret_cast<void *>(path_check_hook),
        reinterpret_cast<void **>(&g_original_path_check));

    if (ret2 != 0) {
        LOGE("DobbyHook path_check failed: %d", ret2);
    } else {
        LOGI("path_check hook installed");
    }

    return true;
}

} /* extern "C" */
