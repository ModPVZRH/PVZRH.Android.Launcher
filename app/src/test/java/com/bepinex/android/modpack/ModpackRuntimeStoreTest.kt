package com.bepinex.android.modpack

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModpackRuntimeStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun write(root: File, path: String, text: String = path): File = File(root, path).apply {
        parentFile!!.mkdirs()
        writeText(text)
    }

    private fun pack(root: File, name: String) {
        write(root, "modpacks/$name/modpack.json", "{}")
        write(root, "modpacks/$name/plugins/$name.dll")
        write(root, "modpacks/$name/config/$name.cfg")
    }

    @Test fun switchingRemovesOwnedExtrasButPreservesFrameworkAndUserFiles() {
        val root = temporary.newFolder()
        pack(root, "A")
        pack(root, "B")
        write(root, "modpacks/A/patchers/A.dll")
        write(root, "modpacks/B/patchers/B.dll")
        write(root, "modpacks/A/interop/forbidden.dll")
        write(root, "BepInEx/core/framework.dll")
        write(root, "BepInEx/patchers/user.txt")
        val plugin = write(root, "launcher/PVZRH.LauncherUi.dll")
        val store = ModpackRuntimeStore(root)
        store.locked { apply("A", null, emptySet(), plugin) }
        assertTrue(File(root, "BepInEx/patchers/A.dll").isFile)
        store.locked { apply("B", "A", emptySet(), plugin) }
        assertFalse(File(root, "BepInEx/patchers/A.dll").exists())
        assertTrue(File(root, "BepInEx/patchers/B.dll").isFile)
        store.locked { apply(null, "B", emptySet(), plugin) }
        assertFalse(File(root, "BepInEx/patchers/B.dll").exists())
        assertTrue(File(root, "BepInEx/patchers/user.txt").isFile)
        assertTrue(File(root, "BepInEx/core/framework.dll").isFile)
        assertFalse(File(root, "BepInEx/interop/forbidden.dll").exists())
        assertEquals(listOf(plugin.name), File(root, "BepInEx/plugins").list()!!.toList())
        assertNull(store.locked { owner("stale preference") })
    }

    @Test fun legacyOwnerExtrasAreMigratedAndDisabledDllsStayDisabled() {
        val root = temporary.newFolder()
        pack(root, "A")
        pack(root, "B")
        write(root, "modpacks/A/patchers/old.dll")
        write(root, "BepInEx/patchers/old.dll")
        write(root, "BepInEx/config/live.cfg", "latest")
        val store = ModpackRuntimeStore(root)
        store.locked { apply("B", "A", setOf("B.dll"), File(root, "absent.dll")) }
        assertFalse(File(root, "BepInEx/patchers/old.dll").exists())
        assertFalse(File(root, "BepInEx/plugins/B.dll").exists())
        assertEquals("latest", File(root, "modpacks/A/config/live.cfg").readText())
    }

    @Test fun stagingFailureLeavesOwnerAndRuntimeIntact() {
        val root = temporary.newFolder()
        pack(root, "A")
        pack(root, "B")
        val store = ModpackRuntimeStore(root)
        val plugin = File(root, "absent.dll")
        store.locked { apply("A", null, emptySet(), plugin) }
        write(root, "BepInEx/config/custom.cfg", "user configuration")
        assertThrows(IllegalArgumentException::class.java) {
            store.locked { apply("B", "A", setOf("../../outside"), plugin) }
        }
        assertEquals("A", store.locked { owner(null) })
        assertEquals("user configuration", File(root, "BepInEx/config/custom.cfg").readText())
        assertTrue(File(root, "BepInEx/plugins/A.dll").isFile)
        assertFalse(File(root, "BepInEx/plugins/B.dll").exists())
    }

    @Test fun lateSaveFromOldGameCannotOverwriteAnotherPacksConfiguration() {
        val root = temporary.newFolder()
        pack(root, "A")
        pack(root, "B")
        val store = ModpackRuntimeStore(root)
        val plugin = File(root, "absent.dll")
        store.locked { apply("A", null, emptySet(), plugin) }
        write(root, "BepInEx/config/A.cfg", "latest A")
        store.locked { apply("B", "A", emptySet(), plugin) }
        store.locked { save("A") }
        assertEquals("latest A", File(root, "modpacks/A/config/A.cfg").readText())
        assertFalse(File(root, "modpacks/A/config/B.cfg").exists())
    }

    @Test fun deletedConfigurationDoesNotReappearAfterSavingAndSwitchingBack() {
        val root = temporary.newFolder()
        pack(root, "A")
        val store = ModpackRuntimeStore(root)
        val plugin = File(root, "absent.dll")
        store.locked { apply("A", null, emptySet(), plugin) }
        assertTrue(File(root, "BepInEx/config/A.cfg").delete())
        store.locked { apply(null, "A", emptySet(), plugin) }
        store.locked { apply("A", null, emptySet(), plugin) }
        assertFalse(File(root, "BepInEx/config/A.cfg").exists())
    }

    @Test fun missingRuntimeDoesNotEraseSavedConfigurationDuringMigration() {
        val root = temporary.newFolder()
        pack(root, "A")
        val store = ModpackRuntimeStore(root)
        store.locked { apply("A", "A", emptySet(), File(root, "absent.dll")) }
        assertEquals("modpacks/A/config/A.cfg", File(root, "BepInEx/config/A.cfg").readText())
    }

    @Test fun renamedOwnerSurvivesStalePreferences() {
        val root = temporary.newFolder()
        pack(root, "A")
        val store = ModpackRuntimeStore(root)
        store.locked { apply("A", null, emptySet(), File(root, "absent.dll")) }
        store.locked {
            assertTrue(File(root, "modpacks/A").renameTo(File(root, "modpacks/Renamed")))
            renameOwner("A", "Renamed")
        }
        assertEquals("Renamed", store.locked { owner("A") })
        store.locked { save("A") }
        assertFalse(File(root, "modpacks/A").exists())
    }

    @Test fun separateStoreInstancesSerializeOperations() {
        val root = temporary.newFolder()
        val pool = Executors.newFixedThreadPool(2)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            val first = pool.submit {
                ModpackRuntimeStore(root).locked {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    write(root, "order", "first")
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val second = pool.submit {
                ModpackRuntimeStore(root).locked {
                    assertEquals("first", File(root, "order").readText())
                    write(root, "order", "second")
                }
            }
            release.countDown()
            first.get(5, TimeUnit.SECONDS)
            second.get(5, TimeUnit.SECONDS)
            assertEquals("second", File(root, "order").readText())
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }
}
