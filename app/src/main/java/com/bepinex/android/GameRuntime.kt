package com.bepinex.android

/**
 * Describes a target Unity IL2CPP game for the launcher.
 */
data class GameRuntime(
    /** Android package name (e.g. "com.innersloth.spacemafia") */
    val id: String,

    /** Human-readable display name (e.g. "Among Us") */
    val name: String,

    /** Unity engine version (e.g. "2022.3.62f3") */
    val unityVersion: String = "",

    /** Java package containing UnityPlayer/NativeLoader classes */
    val unityJavaPackage: String = "com.unity3d.player"
)
