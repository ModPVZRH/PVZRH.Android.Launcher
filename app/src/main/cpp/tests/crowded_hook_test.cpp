#include <atomic>
#include <cassert>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <thread>
#include <vector>
#include <sys/mman.h>
#include <unistd.h>

extern "C" void *hook(void *, void *, bool);
extern "C" bool unhook_checked(void *);

using Getter = int (*)();
static int first_replacement() { return 900; }
static int second_replacement() { return 901; }

static uint32_t return_value_instruction(unsigned value) {
    assert(value < 65536);
    return 0x52800000u | (value << 5); // mov w0, #value
}

static void check_entry(const uint8_t *target, const uint8_t *before) {
    assert((*(const uint32_t *)target & 0xFC000000u) == 0x14000000u);
    // Includes the current method's RET and the complete next 8-byte method.
    assert(memcmp(target + 4, before + 4, 12) == 0);
}

int main() {
    setbuf(stdout, nullptr);
    constexpr size_t target_count = 512, cycle_count = 8192, free_pages = 64;
    constexpr size_t unique_replacements = 2048;
    constexpr size_t branch_range = size_t{1} << 27;
    const long native_page_size = sysconf(_SC_PAGESIZE);
    assert(native_page_size > 0);
    const size_t page_size = (size_t)native_page_size;
    const size_t code_bytes = (target_count + 1) * 8;
    const size_t code_pages = (code_bytes + page_size - 1) / page_size * page_size;

    // Every target's full B-immediate window is reserved, except for 64 pages.
    // The former page-per-relay/page-per-original allocator fails after 32 hooks.
    const size_t reservation_size = 2 * branch_range + code_pages
        + free_pages * page_size + page_size;
    auto *reservation = (uint8_t *)mmap(nullptr, reservation_size, PROT_NONE,
        MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    assert(reservation != MAP_FAILED);
    auto *code = reservation + branch_range;
    auto *hook_space = code + code_pages;
    assert(mprotect(code, code_pages, PROT_READ | PROT_WRITE) == 0);
    for (size_t i = 0; i <= target_count; ++i) {
        const uint32_t body[] = {return_value_instruction(7 + (unsigned)i), 0xD65F03C0u};
        memcpy(code + 8 * i, body, sizeof(body));
    }
    __builtin___clear_cache((char *)code, (char *)code + code_bytes);
    assert(mprotect(code, code_pages, PROT_READ | PROT_EXEC) == 0);

    // Allocate outside the reserved window before making its small hole. These
    // distinct destinations model Harmony creating a new delegate per rebuild.
    const size_t replacement_bytes = unique_replacements * 8;
    const size_t replacement_pages = (replacement_bytes + page_size - 1) / page_size * page_size;
    auto *replacements = (uint8_t *)mmap(nullptr, replacement_pages, PROT_READ | PROT_WRITE,
        MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    assert(replacements != MAP_FAILED);
    for (size_t i = 0; i < unique_replacements; ++i) {
        const uint32_t body[] = {return_value_instruction(1000 + (unsigned)i), 0xD65F03C0u};
        memcpy(replacements + 8 * i, body, sizeof(body));
    }
    __builtin___clear_cache((char *)replacements, (char *)replacements + replacement_bytes);
    assert(mprotect(replacements, replacement_pages, PROT_READ | PROT_EXEC) == 0);
    assert(munmap(hook_space, free_pages * page_size) == 0);
    const std::vector<uint8_t> pristine(code, code + code_bytes);

    // Replacements cannot be reached directly; each installation needs a relay.
    for (auto replacement : {first_replacement, second_replacement,
            (Getter)replacements, (Getter)(replacements + replacement_bytes - 8)}) {
        uintptr_t source = (uintptr_t)code, destination = (uintptr_t)replacement;
        assert((source > destination ? source - destination : destination - source)
               >= branch_range);
    }
    std::vector<Getter> originals;
    originals.reserve(target_count + cycle_count + unique_replacements + 1);
    auto install = [&](uint8_t *target, Getter replacement, int expected) {
        uint8_t before[16];
        memcpy(before, target, sizeof(before));
        auto original = (Getter)hook(target, (void *)replacement, false);
        if (!original) fprintf(stderr, "Hook failed after %zu successful installations\n", originals.size());
        assert(original);
        // Original code must remain in the only free part of the branch window.
        assert((uintptr_t)original >= (uintptr_t)hook_space);
        assert((uintptr_t)original < (uintptr_t)hook_space + free_pages * page_size);
        originals.push_back(original);
        assert(original() == expected);
        assert(((Getter)target)() == replacement());
        check_entry(target, before);
        return original;
    };

    Getter first_original = install(code, first_replacement, 7);
    std::atomic<bool> keep_running{true};
    std::atomic<uint64_t> calls{0};
    // Publishing more code in a shared pool must never remove execute permission
    // from a page that contains a trampoline currently used by another thread.
    std::thread in_flight([&] {
        while (keep_running.load(std::memory_order_relaxed)) {
            assert(first_original() == 7);
            calls.fetch_add(1, std::memory_order_relaxed);
        }
    });
    for (size_t i = 1; i < target_count; ++i)
        install(code + 8 * i, first_replacement, 7 + (int)i);
    for (size_t i = 0; i < target_count; ++i) {
        assert(((Getter)(code + 8 * i))() == 900);
        assert(originals[i]() == 7 + (int)i);
        assert(unhook_checked(code + 8 * i));
    }
    assert(memcmp(code, pristine.data(), code_bytes) == 0);
    puts("PASS: 512 simultaneous hooks in a 64-page branch window; only 4 bytes patched");

    for (size_t i = 0; i < cycle_count; ++i) {
        auto replacement = i % 2 ? second_replacement : first_replacement;
        install(code, replacement, 7);
        assert(unhook_checked(code));
        assert(((Getter)code)() == 7);
        if (i % 128 == 0)
            for (size_t j = 0; j < target_count; ++j) assert(originals[j]() == 7 + (int)j);
    }
    for (size_t i = target_count; i < originals.size(); ++i) assert(originals[i]() == 7);
    assert(memcmp(code, pristine.data(), code_bytes) == 0);
    puts("PASS: 8192 hook/unhook cycles; all historical originals and concurrent calls remain valid");

    for (size_t i = 0; i < unique_replacements; ++i) {
        install(code, (Getter)(replacements + 8 * i), 7);
        assert(unhook_checked(code));
    }
    keep_running.store(false, std::memory_order_relaxed);
    in_flight.join();
    assert(calls.load(std::memory_order_relaxed) > 0);
    for (size_t i = target_count; i < originals.size(); ++i) assert(originals[i]() == 7);
    assert(memcmp(code, pristine.data(), code_bytes) == 0);
    puts("PASS: 2048 distinct far destinations also fit; relays use compact allocations");

    // A new first instruction at the same target must create a new original,
    // without overwriting any previously published trampoline.
    assert(mprotect(code, page_size, PROT_READ | PROT_WRITE) == 0);
    *(uint32_t *)code = return_value_instruction(777);
    __builtin___clear_cache((char *)code, (char *)code + 4);
    assert(mprotect(code, page_size, PROT_READ | PROT_EXEC) == 0);
    Getter changed_original = install(code, second_replacement, 777);
    assert(first_original() == 7);
    assert(unhook_checked(code));
    assert(((Getter)code)() == 777 && changed_original() == 777 && first_original() == 7);
    puts("PASS: changed first instruction gets a new original; earlier original stays immutable");
    // Published trampolines intentionally live until process exit.
}
