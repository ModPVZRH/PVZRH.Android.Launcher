// Android arm64 executable: exercise the real Dobby -> stub -> bridge path.
#include <cassert>
#include <cstdint>
#include <cstdio>
#include <thread>

extern "C" void *hook(void *, void *, bool);
extern "C" bool unhook_checked(void *);
extern "C" void *GetReturnBuffer();

struct Result { uint64_t a, b, c; }; // indirect result in X8, not an HFA

__attribute__((noinline, optnone)) static Result inner(uint64_t n)
{
    volatile uint64_t value = n;
    return {value, value + 1, value + 2};
}

static void *inner_callback(uint64_t n)
{
    auto *out = static_cast<Result *>(GetReturnBuffer());
    assert(out);
    *out = {n + 10, n + 20, n + 30};
    return out;
}

// Integer and FP register arguments, plus both kinds of stack arguments.
using Outer = Result (*)(uint64_t, uint64_t, uint64_t, uint64_t, uint64_t,
                        uint64_t, uint64_t, uint64_t, uint64_t, uint64_t,
                        double, double, double, double, double, double,
                        double, double, double);
__attribute__((noinline, optnone)) static Result outer(
    uint64_t a, uint64_t b, uint64_t c, uint64_t d, uint64_t e,
    uint64_t f, uint64_t g, uint64_t h, uint64_t i, uint64_t j,
    double p, double q, double r, double s, double t, double u,
    double v, double w, double x)
{
    volatile uint64_t sum = a+b+c+d+e+f+g+h+i+j;
    return {sum, static_cast<uint64_t>(p+q+r+s+t+u+v+w+x), 0};
}

static void *outer_callback(
    uint64_t a, uint64_t b, uint64_t c, uint64_t d, uint64_t e,
    uint64_t f, uint64_t g, uint64_t h, uint64_t i, uint64_t j,
    double p, double q, double r, double s, double t, double u,
    double v, double w, double x)
{
    // This is the same entry snapshot performed by generated managed callbacks.
    auto *out = static_cast<Result *>(GetReturnBuffer());
    assert(out);
    const auto nested = inner(a);
    assert(nested.a == a + 10 && nested.b == a + 20 && nested.c == a + 30);
    assert(a == 1 && b == 2 && c == 3 && d == 4 && e == 5);
    assert(f == 6 && g == 7 && h == 8 && i == 9 && j == 10);
    assert(p == 1.5 && q == 2.5 && r == 3.5 && s == 4.5 && t == 5.5);
    assert(u == 6.5 && v == 7.5 && w == 8.5 && x == 9.5);
    *out = {55, 49, nested.c};
    return out;
}

static Result invoke(Outer fn)
{
    return fn(1,2,3,4,5,6,7,8,9,10,1.5,2.5,3.5,4.5,5.5,6.5,7.5,8.5,9.5);
}

int main()
{
    assert(!hook(nullptr, reinterpret_cast<void *>(inner_callback), true));
    assert(hook(reinterpret_cast<void *>(inner), reinterpret_cast<void *>(inner_callback), true));
    auto original = reinterpret_cast<Outer>(hook(reinterpret_cast<void *>(outer),
                                                reinterpret_cast<void *>(outer_callback), true));
    assert(original);
    const auto before = invoke(original);
    assert(before.a == 55 && before.b == 49 && before.c == 0);
    auto exercise = [] {
        for (int n = 0; n < 1000; ++n) {
            const auto result = invoke(outer);
            assert(result.a == 55 && result.b == 49 && result.c == 31);
        }
    };
    std::thread first(exercise), second(exercise);
    first.join(); second.join();
    assert(unhook_checked(reinterpret_cast<void *>(outer)));
    assert(!unhook_checked(reinterpret_cast<void *>(outer)));
    assert(unhook_checked(reinterpret_cast<void *>(inner)));
    const auto after = invoke(outer);
    assert(after.a == 55 && after.b == 49 && after.c == 0);
    puts("PASS: X8 result, integer/FP/stack args, nested calls, TLS isolation, original call, unhook");
}
