/*
 * BepInEx.Android — libunity.so hooks
 *
 * Ported from FusionCore (fusion/src/hooking/libunity.cpp).
 *
 * Hooks scripting_method_invoke to prevent crashes when using unstripped
 * libunity.so. Reads the .sym.so companion file to locate the target
 * function RVA, then installs a null-guard hook via DobbyHook.
 */

#include "fusion.h"
#include "utilities/elf.h"
#include "dobby.h"
#include <dlfcn.h>
#include <string>
#include <cstring>
#include <android/log.h>
#include <filesystem>

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

// Path check hook (sub_69A808)
// Original: checks if libunity.so path contains "/data/data", "/data/user", "/storage", "/sdcard"
//           If match → triggers UnityPlayer.kill() → SIGKILL 9
// Hooked: always returns false (0) - no path match

using path_check_fn = bool (*)(const char* path);
static path_check_fn g_original_path_check = nullptr;

static bool path_check_hook(const char* path)
{
    LOGI("path_check_hook called with: %s — returning false", path ? path : "(null)");
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
    fs::path libunity_path(libUnityPath);
    fs::path sym_path = libunity_path.replace_extension("sym.so");
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

    uintptr_t rva = get_rva_from_sym_file(sym_path.c_str(), mangled);
    if (rva == 0) {
        LOGE("Failed to find scripting_method_invoke in libunity.sym.so");
        return false;
    }

    // 4. Get base address of loaded libunity
    uintptr_t base = get_module_base(libUnityPath, "JNI_OnLoad");
    if (base == 0) {
        LOGE("Failed to find base address of libunity");
        return false;
    }

    // 5. Calculate target and hook
    void *target = reinterpret_cast<void *>(base + rva);
    if (!target) {
        LOGE("Failed to find target function for scripting_method_invoke_hook");
        return false;
    }

    LOGI("scripting_method_invoke @ %p (base=%p, rva=0x%zx)", target, (void*)base, rva);

    int ret = DobbyHook(
        target,
        reinterpret_cast<void *>(scripting_method_invoke_hook),
        reinterpret_cast<void **>(&g_original_scripting_method_invoke));

    if (ret != 0) {
        LOGE("DobbyHook scripting_method_invoke failed: %d", ret);
        return false;
    }

    LOGI("scripting_method_invoke hook installed");

    // 6. Hook path check function (sub_69A808 @ RVA 0x69A808)
    //    This function checks if libunity.so path contains "/data/data", "/data/user", "/storage", "/sdcard"
    //    If match → triggers UnityPlayer.kill() → SIGKILL 9
    //    Hook it to always return false (0) - no path match
    constexpr uintptr_t path_check_rva = 0x69A808;
    void *path_check_target = reinterpret_cast<void *>(base + path_check_rva);

    LOGI("path_check @ %p (base=%p, rva=0x%zx)", path_check_target, (void*)base, path_check_rva);

    int ret2 = DobbyHook(
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
