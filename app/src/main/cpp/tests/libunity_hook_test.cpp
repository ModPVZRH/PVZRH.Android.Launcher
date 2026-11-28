#include <cassert>
#include <cstdio>
#include <cstdlib>
#include <dlfcn.h>

extern "C" bool try_hook_libunity(const char*, const char*);

int main(int argc, char** argv) {
    assert(argc == 3);
    bool expect_success = std::atoi(argv[2]) != 0;
    void* handle = dlopen(argv[1], RTLD_NOW | RTLD_GLOBAL);
    if (!handle) std::fprintf(stderr, "%s\n", dlerror());
    assert(handle);
    using Address = void* (*)();
    using Invoke = void* (*)(void*, void*, void*, void*, bool);
    using PathCheck = bool (*)(const void*, const char* const*, size_t);
    auto invoke_address = reinterpret_cast<Address>(dlsym(handle, "fixture_invoke_address"));
    auto path_address = reinterpret_cast<Address>(dlsym(handle, "fixture_path_check_address"));
    assert(invoke_address && path_address);
    auto invoke = reinterpret_cast<Invoke>(invoke_address());
    auto check_path = reinterpret_cast<PathCheck>(path_address());
    int object = 42;
    const char* blacklist[] = {"/storage"};
    // Intentionally opaque and not a valid char*: the hook must never read it as a string.
    const void* path_object = reinterpret_cast<const void*>(1);
    assert(invoke(nullptr, &object, nullptr, nullptr, false) == &object);
    if (check_path) assert(check_path(path_object, blacklist, 1));

    assert(try_hook_libunity(argv[1], nullptr) == expect_success);
    if (expect_success) {
        assert(invoke(nullptr, &object, nullptr, nullptr, false) == nullptr);
        assert(invoke(&object, &object, nullptr, nullptr, false) == &object);
        if (check_path) assert(!check_path(path_object, blacklist, 1));
    } else {
        assert(invoke(nullptr, &object, nullptr, nullptr, false) == &object);
        if (check_path) assert(check_path(path_object, blacklist, 1));
    }
    std::puts(expect_success ? "PASS: dynamic symbol hooks and original trampoline; optional missing path hook"
                             : "PASS: mismatched/missing symbols leave all function bytes untouched");
}
