/* P/Invoke exports for managed BepInExFusion code. */

#include <android/log.h>
#include <dlfcn.h>
#include <cstdint>
#include <cstdbool>
#include <cstring>
#include <mutex>
#include <sys/mman.h>
#include <unistd.h>
#include "dobby.h"

namespace {
std::mutex hookMutex;
thread_local void *returnBuffer = nullptr;
}

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

void ReturnBufferBridge();

void SetReturnBuffer(void *value) { returnBuffer = value; }
void *GetReturnBuffer() { return returnBuffer; }

// One RX page per bridge: never remove execute permission from a page containing
// another live hook. Successful bridges stay mapped until process exit, since an
// in-flight invocation can still be using one when its hook is removed.
static void *create_return_buffer_bridge(void *detour, size_t pageSize)
{
    void *page = mmap(nullptr, pageSize, PROT_READ | PROT_WRITE,
                      MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (page == MAP_FAILED) return nullptr;
    const uint32_t instructions[] = {
        0x58000090, // ldr x16, [pc, #16] : managed destination
        0x580000B1, // ldr x17, [pc, #20] : common bridge
        0xD61F0220, // br x17
        0xD503201F  // nop (literal alignment)
    };
    memcpy(page, instructions, sizeof(instructions));
    const uintptr_t destinations[] = {
        reinterpret_cast<uintptr_t>(detour),
        reinterpret_cast<uintptr_t>(&ReturnBufferBridge)
    };
    memcpy(static_cast<char *>(page) + 16, destinations, sizeof(destinations));
    __builtin___clear_cache(static_cast<char *>(page), static_cast<char *>(page) + 32);
    if (mprotect(page, pageSize, PROT_READ | PROT_EXEC) != 0) {
        munmap(page, pageSize);
        return nullptr;
    }
    return page;
}

void *hook(void *target, void *detour, bool specialReturnBuffer)
{
    std::lock_guard<std::mutex> guard(hookMutex);
    if (!target || !detour) return nullptr;
    const long pageSize = sysconf(_SC_PAGESIZE);
    if (pageSize <= 0) return nullptr;
    void *bridge = specialReturnBuffer
        ? create_return_buffer_bridge(detour, static_cast<size_t>(pageSize)) : nullptr;
    if (specialReturnBuffer && !bridge) {
        __android_log_write(ANDROID_LOG_ERROR, "Fusion", "Cannot allocate ARM64 return-buffer bridge");
        return nullptr;
    }
    void *original = nullptr;
    int rc = DobbyHook(target, bridge ? bridge : detour, &original);
    if (rc != 0) {
        if (bridge) munmap(bridge, static_cast<size_t>(pageSize));
        __android_log_print(ANDROID_LOG_ERROR, "Fusion", "DobbyHook failed at %p: %d", target, rc);
        return nullptr;
    }
    return original;
}

bool unhook_checked(void *target)
{
    std::lock_guard<std::mutex> guard(hookMutex);
    return target && DobbyDestroy(target) == 0;
}

void unhook(void *target) { (void)unhook_checked(target); }

} /* extern "C" */
