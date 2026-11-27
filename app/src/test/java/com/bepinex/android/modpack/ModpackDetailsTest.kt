package com.bepinex.android.modpack

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModpackDetailsTest {
    @get:Rule val temporary = TemporaryFolder()
    private val packageName = "game.test"
    private val pack = "test-pack"
    private val manager get() = ModpackManager { File(temporary.root, it) }
    private val root get() = File(temporary.root, "$packageName/modpacks/$pack")

    private fun write(path: String, content: String): File = File(root, path).apply {
        parentFile!!.mkdirs()
        writeText(content)
    }

    @Test fun detailsKeepNestedPathsMetadataCaseInsensitiveDllsAndFileSizes() {
        val first = write("plugins/folder/Example.DLL", "12345")
        write("plugins/Other.dll", "abc")
        write("plugins/readme.txt", "ignored")
        write("config/example.cfg", "config")
        write("modpack.json", """{
            "modCount": 99,
            "mods": {
                "folder/Example.DLL": {"displayName":"Example mod", "enabled":false, "category":"Tools"},
                "missing.dll": {"enabled":false}
            }
        }""")

        val details = manager.loadDetails(packageName, pack)
        assertEquals(2, details.mods.size)
        val mod = details.mods.single { it.relativePath == "folder/Example.DLL" }
        assertEquals(first, mod.file)
        assertEquals("Example mod", mod.displayName)
        assertEquals("Tools", mod.category)
        assertFalse(mod.enabled)
        assertEquals(5L, mod.sizeBytes)
        assertEquals(listOf("example.cfg"), details.configFiles.map { it.name })
        val saved = JSONObject(File(root, "modpack.json").readText())
        assertEquals(2, saved.getInt("modCount"))
        assertFalse(saved.getJSONObject("mods").has("missing.dll"))
    }

    @Test fun legacyMetadataMigratesAndExactPathWinsOverFileNameFallback() {
        write("plugins/a/Same.dll", "a")
        write("plugins/b/Same.dll", "b")
        write("modpack.json", """{
            "dllNames": {"Same.dll":"Legacy name"},
            "disabledDlls": ["Same.dll"],
            "mods": {"b/Same.dll":{"displayName":"Exact name", "enabled":true}}
        }""")
        val mods = manager.loadDetails(packageName, pack).mods.associateBy { it.relativePath }
        assertEquals("Legacy name", mods.getValue("a/Same.dll").displayName)
        assertFalse(mods.getValue("a/Same.dll").enabled)
        assertEquals("Exact name", mods.getValue("b/Same.dll").displayName)
        assertTrue(mods.getValue("b/Same.dll").enabled)
        assertFalse(JSONObject(File(root, "modpack.json").readText()).has("dllNames"))
    }

    @Test fun metadataFallbackForMovedDllKeepsFirstMatchingName() {
        write("plugins/new/Example.dll", "dll")
        write("modpack.json", """{
            "mods": {"old/Example.dll":{"displayName":"Moved mod", "enabled":false}}
        }""")
        val mod = manager.loadDetails(packageName, pack).mods.single()
        assertEquals("new/Example.dll", mod.relativePath)
        assertEquals("Moved mod", mod.displayName)
        assertFalse(mod.enabled)
    }

    @Test fun renameCategoryAndBatchTogglePreserveUnrelatedMetadataAndResyncExternalChanges() {
        write("plugins/A.dll", "a")
        write("plugins/nested/B.dll", "b")
        write("modpack.json", """{
            "createdAt":123,
            "gameVersion":"4.0.5",
            "mods":{"nested/B.dll":{"displayName":"B mod", "category":"Tools"}}
        }""")
        assertTrue(manager.setDllDisplayName(packageName, pack, "A.dll", "A mod"))
        assertTrue(manager.setDllCategory(packageName, pack, "A.dll", "Extra"))
        assertTrue(manager.setDllsEnabled(packageName, pack, listOf("A.dll", "nested/B.dll"), false))
        var mods = manager.loadDetails(packageName, pack).mods.associateBy { it.relativePath }
        assertEquals("A mod", mods.getValue("A.dll").displayName)
        assertEquals("Extra", mods.getValue("A.dll").category)
        assertEquals("B mod", mods.getValue("nested/B.dll").displayName)
        assertEquals("Tools", mods.getValue("nested/B.dll").category)
        assertTrue(mods.values.none { it.enabled })
        assertTrue(File(root, "plugins/A.dll").delete())
        write("plugins/New.dll", "new")
        assertTrue(manager.setDllEnabled(packageName, pack, "nested/B.dll", true))
        mods = manager.loadDetails(packageName, pack).mods.associateBy { it.relativePath }
        assertEquals(setOf("nested/B.dll", "New.dll"), mods.keys)
        assertTrue(mods.values.all { it.enabled })
        val saved = JSONObject(File(root, "modpack.json").readText())
        assertEquals(123L, saved.getLong("createdAt"))
        assertEquals("4.0.5", saved.getString("gameVersion"))
        assertEquals(2, saved.getInt("modCount"))
    }

    @Test fun missingOrInvalidMetadataStillListsFilesWithoutLosingThem() {
        write("plugins/Example.dll", "data")
        assertEquals("Example.dll", manager.loadDetails(packageName, pack).mods.single().displayName)
        write("modpack.json", "invalid-json")
        val mod = manager.loadDetails(packageName, pack).mods.single()
        assertEquals("Example.dll", mod.displayName)
        assertTrue(mod.enabled)
        assertEquals(4L, mod.sizeBytes)
    }
}
