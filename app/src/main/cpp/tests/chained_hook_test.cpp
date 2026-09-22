#include <cassert>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <initializer_list>

extern "C" void *hook(void *, void *, bool);
extern "C" bool unhook_checked(void *);
extern "C" void DobbyTestFailNearAllocation(bool);
extern "C" int live_x17();
extern "C" int live_x16();
static int replacement() { return 900; }

// Eight integer-register arguments, two stack arguments and FP registers.
#define PARAMETERS int64_t a, int64_t b, int64_t c, int64_t d, int64_t e, \
    int64_t f, int64_t g, int64_t h, int64_t i, int64_t j, double p, double q
using Chain = int64_t (*)(PARAMETERS);
extern "C" int64_t chain_adrp_x17(PARAMETERS);
extern "C" int64_t chain_adrp_x16(PARAMETERS);
extern "C" int64_t chain_ldr_x17(PARAMETERS);
extern "C" int64_t chain_ldr_x16(PARAMETERS);
extern "C" int64_t prior_hook(PARAMETERS) {
    return a+2*b+3*c+4*d+5*e+6*f+7*g+8*h+9*i+10*j+(int64_t)(10*p+100*q);
}
static Chain original_chain = nullptr;
static int64_t outer_hook(PARAMETERS) {
    return original_chain(a,b,c,d,e,f,g,h,i,j,p,q)+1000;
}
#undef PARAMETERS
static int64_t invoke(Chain function) {
    return function(1,2,3,4,5,6,7,8,9,10,1.5,2.5);
}

int main() {
    // Linker relaxation must not replace the ADRP/ADD sequence with NOP/ADR.
    assert((*(uint32_t *)(void *)chain_adrp_x17 & 0x9F00001Fu) == 0x90000011u);
    assert((*(uint32_t *)(void *)chain_adrp_x16 & 0x9F00001Fu) == 0x90000010u);
    // Check before any successful hook can cache this method's original.
    // The entry destination is nearby, so failure occurs while allocating the
    // original trampoline, not the forward relay. No entry bytes may change.
    const uintptr_t from = (uintptr_t)live_x17, to = (uintptr_t)replacement;
    assert((from > to ? from-to : to-from) < 0x08000000);
    uint8_t before[12];
    memcpy(before, (void *)live_x17, sizeof(before));
    DobbyTestFailNearAllocation(true);
    assert(hook((void *)live_x17, (void *)replacement, false) == nullptr);
    DobbyTestFailNearAllocation(false);
    assert(memcmp(before, (void *)live_x17, sizeof(before)) == 0);
    assert(live_x17() == 169);
    assert(!unhook_checked((void *)live_x17));

    // A register swap in the return jump is not sufficient: either IP register
    // may hold the value produced by the displaced instruction.
    for (auto function : {live_x17, live_x16}) {
        const int expected = function();
        auto original = (int (*)())hook((void *)function, (void *)replacement, false);
        assert(original);
        assert(original() == expected);
        assert(function() == 900);
        assert(unhook_checked((void *)function));
        assert(function() == expected);
    }

    for (Chain function : {chain_adrp_x17, chain_adrp_x16, chain_ldr_x17, chain_ldr_x16}) {
        uint8_t before[16];
        memcpy(before, (void *)function, sizeof(before));
        assert(invoke(function) == 650);
        for (int iteration = 0; iteration < 3; ++iteration) {
            original_chain = (Chain)hook((void *)function, (void *)outer_hook, false);
            assert(original_chain);
            assert(invoke(original_chain) == 650);
            assert(invoke(function) == 1650);
            assert(memcmp(before+4, (uint8_t *)function+4, sizeof(before)-4) == 0);
            assert(unhook_checked((void *)function));
            assert(memcmp(before, (void *)function, sizeof(before)) == 0);
            assert(invoke(function) == 650); // The first hook remains installed.
            // An in-flight caller may still hold the original trampoline.
            assert(invoke(original_chain) == 650);
        }
    }

    puts("PASS: X16/X17 values, layered ADRP and LDR hooks, integer/FP/stack args, repeated hook/unhook, allocation failure");
}
