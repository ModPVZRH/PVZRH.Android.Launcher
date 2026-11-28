# Interop cache invalidation

The launcher refreshes its private `Data_copy` when the installed game's
version code or update time changes. Its external metadata override is bound
to the accepted game version to prevent an old override from replacing a new
game's metadata.

The bundled BepInEx runtime decides whether interop needs regeneration. Its
cache hash now includes the contents of the metadata file actually passed to
Cpp2IL, using the same resolution of `GlobalMetadataPath`, relative paths and
`{BepInEx}`, `{ProcessName}` and `{GameDataPath}` placeholders. Metadata changes
therefore trigger regeneration even when `libil2cpp.so`, file size and file
timestamps are unchanged. Unchanged metadata does not force regeneration.

The new hash invalidates the previous cache once on the first launch with
automatic interop updates enabled. `UpdateInteropAssemblies = false` remains
an explicit opt-out and prevents automatic regeneration.

The fix is maintained in the BepInEx.Android source repository at
`Runtimes/Unity/BepInEx.Unity.IL2CPP/Il2CppInteropManager.cs`. The launcher's
`app/src/main/assets/BepInEx.Android.zip` contains the rebuilt
`core/BepInEx.Unity.IL2CPP.dll`. The extractor detects the changed ZIP and
updates the framework core on launch.

The BepInEx.Android `tests/InteropCache` harness exercises the built runtime or
an extracted launcher's framework package. See its README for build and run
commands. The original launcher package reproduces the metadata-only cache
failure; the updated package passes the same checks. Device acceptance should
confirm that one generation occurs after the upgrade, an unchanged second
launch reuses interop, and replacing metadata triggers generation again.
