#include <cassert>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <thread>
#include <sys/mman.h>
#include <unistd.h>

extern "C" int tiny_getter();
extern "C" int tiny_attribute(int);
extern "C" int tiny_super();
extern "C" int tiny_tail();
extern "C" void *hook(void *, void *, bool);
extern "C" bool unhook_checked(void *);
extern "C" void DobbyTestFailNearAllocation(bool);
using Getter = int (*)();
static int replacement() { return 900; }
static int attribute_replacement(int n) { return n + 1; }

static void check_neighbours(const uint8_t *before, void *target, size_t size) {
    assert(memcmp(before + 4, (uint8_t *)target + 4, size - 4) == 0);
    assert((*(uint32_t *)target & 0xFC000000u) == 0x14000000u);
}

int main() {
    auto target = reinterpret_cast<void *>(tiny_getter);
    uint8_t before[24];
    memcpy(before, target, sizeof(before));
    assert((uintptr_t)tiny_attribute - (uintptr_t)tiny_getter == 8);
    assert((uintptr_t)tiny_super - (uintptr_t)tiny_getter == 12);
    assert(tiny_getter() == 169 && tiny_attribute(33) == 33 && tiny_super() == 77);

    auto original = reinterpret_cast<Getter>(hook(target, (void *)replacement, false));
    assert(original && original() == 169 && tiny_getter() == 900);
    check_neighbours(before, target, sizeof(before));
    assert(tiny_attribute(33) == 33 && tiny_super() == 77);
    assert(hook(target, (void *)replacement, false) == nullptr);
    check_neighbours(before, target, sizeof(before));

    auto attribute_original = reinterpret_cast<int (*)(int)>(
        hook((void *)tiny_attribute, (void *)attribute_replacement, false));
    assert(attribute_original && attribute_original(33) == 33 && tiny_attribute(33) == 34);
    auto super_original = reinterpret_cast<Getter>(hook((void *)tiny_super, (void *)replacement, false));
    assert(super_original && super_original() == 77 && tiny_super() == 900);
    assert(unhook_checked(target));
    assert(tiny_getter() == 169 && tiny_attribute(33) == 34 && tiny_super() == 900);
    assert(unhook_checked((void *)tiny_attribute));
    assert(unhook_checked((void *)tiny_super));
    assert(memcmp(before, target, sizeof(before)) == 0);
    auto tail_original = reinterpret_cast<Getter>(hook((void *)tiny_tail, (void *)replacement, false));
    assert(tail_original && tail_original() == 77 && tiny_tail() == 900);
    assert(unhook_checked((void *)tiny_tail));

    // A far destination forces relay allocation. Failure must leave every byte untouched.
    auto far = (void *)((uintptr_t)target + 0x10000000);
    DobbyTestFailNearAllocation(true);
    assert(hook(target, far, false) == nullptr);
    DobbyTestFailNearAllocation(false);
    assert(memcmp(before, target, sizeof(before)) == 0);
    assert(tiny_getter() == 169 && tiny_super() == 77);
    assert(!unhook_checked(target));
    assert(hook((uint8_t *)target + 1, (void *)replacement, false) == nullptr);

    // Exercise actual far code, with a forward relay and an original trampoline.
    const size_t page_size = (size_t)sysconf(_SC_PAGESIZE);
    void *page = MAP_FAILED;
    for (uintptr_t offset = 0x10000000; offset <= 0x70000000; offset += 0x10000000) {
        auto hint = ((uintptr_t)target + offset) / page_size * page_size;
        page = mmap((void *)hint, page_size, PROT_READ | PROT_WRITE,
                    MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
        if (page == MAP_FAILED) continue;
        const auto address = (uintptr_t)page;
        const auto distance = address > (uintptr_t)target ? address - (uintptr_t)target
                                                        : (uintptr_t)target - address;
        if (distance >= 0x08000000) break;
        munmap(page, page_size);
        page = MAP_FAILED;
    }
    assert(page != MAP_FAILED);
    const uint32_t far_code[] = {0x52804D20, 0xD65F03C0}; // mov w0, #617; ret
    memcpy(page, far_code, sizeof(far_code));
    __builtin___clear_cache((char *)page, (char *)page + sizeof(far_code));
    assert(mprotect(page, page_size, PROT_READ | PROT_EXEC) == 0);
    original = reinterpret_cast<Getter>(hook(target, page, false));
    assert(original && original() == 169 && tiny_getter() == 617);
    check_neighbours(before, target, sizeof(before));
    assert(unhook_checked(target));
    assert(memcmp(before, target, sizeof(before)) == 0);

    // Registration and removal share one backend lock even for different API callers.
    auto cycle = [](void *function) {
        for (int i = 0; i < 50; ++i) {
            assert(hook(function, (void *)replacement, false));
            assert(unhook_checked(function));
        }
    };
    std::thread first(cycle, (void *)tiny_getter), second(cycle, (void *)tiny_super);
    first.join(); second.join();
    assert(memcmp(before, target, sizeof(before)) == 0);
    puts("PASS: 8-byte getter, 4-byte method, adjacent hooks, original calls, far relay, allocation failure, concurrent hooks, unhook");
}
