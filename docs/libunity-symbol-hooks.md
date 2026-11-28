# Automatic libunity hook resolution

The optional unstripped Unity path resolves `IsInForbiddenLocation` from
`libunity.sym.so` by its full mangled function name. It no longer assumes a
fixed RVA. The existing `scripting_method_invoke` hook uses the same checked
symbol resolver.

The bundled Unity 2022.3.62f1c1 library has GNU build ID
`3465ee5266828bfb8e83cf66807dcc78f76b4f02`. Its symbol companion identifies
`IsInForbiddenLocation` at RVA `0xA0BDDC`. Disassembly of `IllegalUsageCheck`
confirms calls to that function with the library/install-directory blacklists
containing `/data/data`, `/data/user`, `/storage` and `/sdcard`. The old
`0x69A808` RVA falls inside a particle-system setter's epilogue in this build;
it is not a path-check function entry.

The actual signature is
`bool IsInForbiddenLocation(const core::basic_string<...>&, const char* const*, size_t)`.
The replacement preserves that ABI, returns false and treats the Unity string
object as opaque. It never logs that object as a C string.

Before returning an RVA, the resolver requires:

- Valid ELF headers and bounded program/section, string and symbol tables.
- A matching, nonempty GNU build ID in the library and symbol companion.
- An exact, defined `STT_FUNC` symbol with an aligned entry and known size.
- The full function range inside an executable symbol section and a readable,
  executable, file-backed load segment in the library.
- A unique function definition. Identical SYMTAB/DYNSYM definitions are allowed;
  conflicting definitions are rejected.

Missing or incompatible path-check symbols skip that optional hook with a log
message. There is no fixed-address fallback. A missing or mismatched companion
also prevents installing the existing scripting hook. Builds that remove or
rename this internal function need a verified symbol update before hooking it.
Using the game's original libunity continues to skip these optional hooks.

## Regression checks

Configure a separate Android ARM64 CMake build with `-DFUSION_BUILD_TESTS=ON`.
Build `elf_symbol_test`, `libunity_hook_test`, `unity_symbol_fixture` and
`unity_symbol_fixture_no_path`. These test targets are not packaged in the APK.

Run `elf_symbol_test` on Android. Optionally pass the paths to the extracted
bundled `libunity.so` and `libunity.sym.so` to check the real Unity symbols too.
The fixture cases cover shifted RVAs/file offsets, build-ID mismatch or absence,
missing/ambiguous/undefined/non-function symbols, malformed note/string/symbol
tables, invalid executable ranges, wrong architecture and truncated files.

For the Hook integration test, place the executable, both fixture libraries and
the production `libdobby.so` in a temporary device directory. Copy each fixture
to its companion filename (`libunity_symbol_fixture.sym.so` and
`libunity_symbol_fixture_no_path.sym.so`), then run:

```sh
LD_LIBRARY_PATH=. ./libunity_hook_test ./libunity_symbol_fixture.so 1
LD_LIBRARY_PATH=. ./libunity_hook_test ./libunity_symbol_fixture_no_path.so 1
```

These install real Dobby hooks on the stand-in functions and check the null
guard, original trampoline and opaque path-object ABI. Replacing a companion
with the other fixture's file, or removing it, must pass with expected result
`0`, leaving the stand-in functions unchanged.

The resolver (including the real bundled Unity files) and all four integration
cases passed on the connected ARM64 Android device. This validates lookup and
Hook installation; a full game launch with unstripped Unity remains a separate
acceptance check.
