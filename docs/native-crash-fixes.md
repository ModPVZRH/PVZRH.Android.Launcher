# ARM64 return buffer and game process lifetime

## Short IL2CPP methods

ARM64 methods can be four or eight bytes long, with another method immediately
following them. The guarded Dobby source is maintained in `E:/Work/PVZRH/dobby`.
Its `build-android-arm64.ps1` script syncs the production library to
`app/src/main/jniLibs/arm64-v8a/libdobby.so` and the header to `app/src/main/cpp/dobby.h`.
The launcher links and packages these local files without the external source tree.
Every ARM64 hook entry uses one four-byte `B`. Far callbacks use a nearby RX
relay, including callbacks that first enter the X8 return-buffer bridge.
Near-allocation failure rejects the hook before changing the target; it cannot
fall back to a longer overwrite. All native hook entry points use this backend.

The regression fixture reproduces the game's contiguous layout: an eight-byte
getter, a four-byte method, and the next method. It verifies unchanged adjacent
bytes, independent neighbouring hooks, original calls, removal, a far callback,
concurrent installation/removal, and forced near-allocation failure.

First run the standalone Dobby script with `-Testing` (it does not sync test
libraries into the APK). Configure a separate native build with
`-DFUSION_BUILD_TESTS=ON` and `-DDOBBY_TEST_LIBRARY=<absolute testing libdobby.so path>`.
Run `short_hook_test` alongside
`return_buffer_test`, using the libfusion.so, libmain.so and libdobby.so from that
same build in LD_LIBRARY_PATH. Both executables passed on the connected ARM64
Android device on 2026-09-22. The test-only allocation switch is not built into
normal APKs.

After upgrading, start a new game process. Already overwritten method bytes in
an old running process cannot be repaired by merely changing the hook policy.

## Layered native hooks and X17

The four-byte entry patch alone is insufficient when another backend already
hooks the same method. In the modded game, Nebula installs an
`ADRP X17; ADD X17; BR X17` entry on `CreatePlant.SetPlant` and
`Plant.TakeDamage`. Relocating only ADRP and returning through
`LDR X17; BR X17` destroys its result, causing the remaining ADD/BR to jump into
the middle of the original method. This was confirmed in live process memory
and an old/new Dobby comparison on the ARM64 device.

Original trampolines use nearby executable storage and return with a
direct `B target+4`. The return jump borrows no register. Allocation checks
reachability in both directions, and an allocation or finalization failure
rejects installation before changing the method entry. Published original
code remains mapped after unhook to support callers still using a trampoline.

`chained_hook_test` covers live X16/X17 results, preinstalled ADRP/ADD/BR and
LDR/BR hooks, integer/FP/stack arguments, repeated hook/unhook while retaining
the first hook, and original-trampoline allocation failure. Linker relaxation
is disabled for this fixture to preserve the actual ADRP instruction sequence.
Run it with `short_hook_test` and `return_buffer_test` from the same
`FUSION_BUILD_TESTS=ON` build. All three passed on the connected ARM64 device
on 2026-09-22. These native fixtures do not replace a full mod gameplay test.

APK 545 was built and installed on the connected device. Its packaged Dobby
also passed the X17 reproducer that failed with APK 544. The user then retested
the same Nebula-modded game and mod combination, planting BloodMoon and waiting
for its ultimate, and reported that it no longer crashed (2026-09-22).

## Capacity while loading many mods

The initial four-byte implementation allocated an entire nearby page for each
relay and original trampoline. Harmony rebuilds a method's wrapper whenever
another mod patches it, so even repeated hook/unhook of the same methods kept
consuming new pages. In a fresh 114-mod run on 2026-09-23, 26 plugins failed at
12 different targets. A live memory snapshot showed no unmapped complete pages
within any failed target's +/-128 MiB branch window. The outer Harmony
`IL Compile Error` was wrapping this native allocation failure.

Nearby arenas now allocate immutable code in 16-byte-aligned slots. Relay code
uses 16 bytes, and originals reserve the actual finalized code size, including
literal data. Publishing into an existing arena uses `DobbyCodePatch`, which
keeps execute permission during the RWX write and restores RX afterward. No
live code is overwritten or temporarily made non-executable.

Original trampolines are cached by source address and displaced four-byte
instruction. Relays to the same destination are reused when reachable from
the new source. Changed instructions produce separate originals, preserving
previously returned function pointers. Published blocks remain valid until
process exit; unpublished failed reservations are returned to their arena.
The injected ELF padding is not used by this allocator.

`crowded_hook_test` reserves the complete branch window except for 64 pages.
It verifies 512 simultaneous hooks, 8192 repeated hook/unhook cycles, 2048
distinct far destinations (like newly generated Harmony delegates), unchanged
neighboring instructions, concurrent calls through old originals, and a
changed first instruction at the same address. These tests and the existing
short-hook, chained-hook and return-buffer tests passed on the connected
ARM64 device on 2026-09-23. The old allocator fails the constrained-window
reproducer after 32 hook/unhook cycles.

APK 546 (`1.1.0-ci.546`) was built successfully with this change. Its packaged
Dobby library excludes the test-only allocation-failure switch. Installation
and the full 114-mod acceptance run are pending device reconnection; the
native regression results above do not establish full-game acceptance.

This addresses native hook capacity. The separately diagnosed unhandled
exception in `Class_FromIl2CppType_Hook` is outside this change.

The Android `specialReturnBuffer` path now enters `ReturnBufferBridge` in
`libfusion.so`. It preserves X0–X8, Q0–Q7, LR and the original stack argument
layout while recording X8. Il2CppInterop reads the same library's TLS slot and
snapshots it at managed callback entry, before a nested hook can overwrite it.
This implements the existing indirect-result path for Il2CppSystem.ValueType
wrappers; it is not a redesign of all aggregate/HFA marshalling.

Bridge allocation failures fail the managed hook operation instead of installing
an incompatible callback. Each bridge owns an RX page. Successful bridges and
the Unity/IL2CPP libraries remain mapped until process exit, so unhook/unload
cannot invalidate code still being executed by another thread.

Every launcher/shortcut start terminates the launcher's previous `:game` process
and waits for it to disappear before starting a new game task. Only the current
UID's exact `:game` process name is eligible. Duplicate bootstraps and native
reloads in a surviving process are rejected. Launching again restarts the game;
it does not resume a previous game session.

The game's original `libmain.so` is loaded locally with Android's force-load
linker flag. Its JNI_OnLoad runs before re-registering the launcher's
NativeLoader.load/unload implementations. The original loader's load function
is never called, which avoids loading a second Unity instance.

The managed framework asset must be updated together with the native library.
The extractor hashes the packaged ZIP and replaces core via a staged directory,
preserving plugins and config. It checks again after modpack restoration.

## Validation

Managed hook lifetime/failure checks, from BepInEx.Android:

```powershell
dotnet run --project tests/NativeDetour/NativeDetour.Tests.csproj -c Release
```

Configure the existing Android CMake build with `-DFUSION_BUILD_TESTS=ON` to
build `return_buffer_test`. This opt-in executable exercises real Dobby hooks:
indirect results, integer/FP/stack arguments, nested callbacks, two native
threads, original trampolines and unhook. It is not shipped in normal APKs.
Run it on an ARM64 Android device with libfusion.so, libmain.so and libdobby.so
in the executable's directory and LD_LIBRARY_PATH pointing there.

Device acceptance checks still required:

- Cold launch, exit, launch again and confirm a different :game PID.
- Relaunch while :game survives in the background; launcher PID must survive.
- Desktop shortcut launch and switching modpacks after a previous session.
- Verify original libmain JNI registration succeeds and Fusion still starts.
- Upgrade from an older installed APK; core updates while plugins/config remain.
- Run the native return_buffer_test executable.
