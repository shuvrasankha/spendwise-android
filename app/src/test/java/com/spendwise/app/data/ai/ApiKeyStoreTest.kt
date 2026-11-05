package com.spendwise.app.data.ai

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.spendwise.app.data.prefs.DevicePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Tests only: a reversible XOR, which is no protection at all. */
private class FakeCipher : SecretCipher {
    var broken = false
    var destroyed = 0
    override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    override fun decrypt(blob: ByteArray): ByteArray = if (broken) error("lost key") else encrypt(blob)
    override fun destroy() { destroyed++ }
}

class ApiKeyStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cipher = FakeCipher()
    private lateinit var prefsFile: java.io.File
    private lateinit var keys: java.io.File
    private lateinit var store: ApiKeyStore
    private lateinit var prefs: DevicePrefs

    @Before fun setUp() {
        prefsFile = tmp.newFile("device_prefs.preferences_pb").also { it.delete() }
        keys = tmp.newFolder("no_backup")
        prefs = DevicePrefs(PreferenceDataStoreFactory.create(scope = scope) { prefsFile })
        store = ApiKeyStore(cipher, keys, prefs)
    }

    @After fun tearDown() = scope.cancel()

    private fun save(key: String) = runBlocking { store.save("nvidia", key.toCharArray()) }

    @Test fun `save then read, and replace overwrites`() = runBlocking {
        save("nvapi-TESTSECRET1234")
        assertEquals("nvapi-TESTSECRET1234", store.read("nvidia"))
        assertEquals("1234", store.mask("nvidia").first())
        save("nvapi-OTHERSECRET9876")
        assertEquals("nvapi-OTHERSECRET9876", store.read("nvidia"))
        assertEquals("9876", store.mask("nvidia").first())
        assertEquals(listOf("byok_nvidia.bin"), keys.list()!!.toList())
    }

    @Test fun `remove deletes the file, the mask and the keystore entry`() = runBlocking {
        save("nvapi-TESTSECRET1234")
        store.remove("nvidia")
        assertNull(store.read("nvidia"))
        assertFalse(store.hasKey("nvidia").first())
        assertTrue(keys.list()!!.isEmpty())
        assertEquals(1, cipher.destroyed)
    }

    @Test fun `an unreadable key throws and leaves nothing behind`() = runBlocking {
        save("nvapi-TESTSECRET1234")
        cipher.broken = true
        try {
            store.read("nvidia")
            fail("expected KeyUnreadableException")
        } catch (e: KeyUnreadableException) {
            assertFalse(e.message.orEmpty().contains("TESTSECRET"))
        }
        assertTrue(keys.list()!!.isEmpty())
        assertNull(store.mask("nvidia").first())
    }

    @Test fun `the key is cleared from the caller's array and never reaches DataStore`() = runBlocking {
        val key = "nvapi-TESTSECRET1234".toCharArray()
        store.save("nvidia", key)
        assertTrue(key.all { it == '\u0000' })
        val onDisk = String(prefsFile.readBytes(), Charsets.ISO_8859_1)
        assertFalse(onDisk.contains("TESTSECRET"))
        assertTrue(onDisk.contains("1234"))
    }

    @Test fun `log-out forgets the key, the consent and the mode`() = runBlocking {
        save("nvapi-TESTSECRET1234")
        prefs.setByokConsent("nvidia", true)
        prefs.setAiMode("byok")
        store.forgetAll()
        assertEquals("worker", prefs.aiMode.first())
        assertFalse(prefs.byokConsent("nvidia").first())
        assertFalse(store.hasKey("nvidia").first())
        assertTrue(keys.list()!!.isEmpty())
        assertEquals(1, cipher.destroyed)
    }

    @Test fun `a short or spaced key is refused and not saved`() = runBlocking {
        for (bad in listOf("abc", "nvapi TESTSECRET")) {
            try { save(bad); fail("expected a refusal for $bad") } catch (e: IllegalArgumentException) { }
        }
        assertFalse(store.hasKey("nvidia").first())
        assertTrue(keys.list()!!.isEmpty())
    }
}
