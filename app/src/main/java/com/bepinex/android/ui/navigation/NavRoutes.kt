package com.bepinex.android.ui.navigation

/**
 * Navigation route constants for Compose Navigation.
 */
object NavRoutes {
    const val MAIN = "main"
    const val MODPACK_DETAIL = "modpack_detail/{packageName}/{modpackName}"
    const val ABOUT = "about"
    const val CREDITS = "credits"
    const val LOG_VIEWER = "log_viewer/{packageName}/{modpackName}"
    const val CONFIG_EDITOR = "config_editor/{filePath}"
    const val MOD_FILE_BROWSER = "mod_file_browser/{packageName}/{modpackName}"
    const val SAVE_IMPORT = "save_import/{packageName}"

    fun modpackDetail(packageName: String, modpackName: String) =
        "modpack_detail/$packageName/$modpackName"
    fun settings(packageName: String) = "settings/$packageName"
    fun logViewer(packageName: String, modpackName: String) = "log_viewer/$packageName/$modpackName"
    fun configEditor(filePath: String) = "config_editor/${java.net.URLEncoder.encode(filePath, "UTF-8")}"
    fun modFileBrowser(packageName: String, modpackName: String) =
        "mod_file_browser/$packageName/$modpackName"
    fun saveImport(packageName: String) = "save_import/$packageName"
}
