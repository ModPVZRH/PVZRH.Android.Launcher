# ARM64 return buffer and game process lifetime

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
