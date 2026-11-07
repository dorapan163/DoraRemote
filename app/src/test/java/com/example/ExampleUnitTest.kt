package com.example

import com.example.crypto.AesGcmCrypto
import com.example.model.HostPermissions
import com.example.model.NetworkTier
import com.example.model.QualityPreset
import com.example.streaming.AdaptiveBitrateController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testAes256GcmEncryptionAndDecryption() {
        val pairCode = "849203"
        val key = AesGcmCrypto.deriveKey(pairCode)
        val crypto = AesGcmCrypto(key)

        val secretMessage = "ScrcpyRemote_E2EE_Payload_Test_123456"
        val encrypted = crypto.encryptString(secretMessage)

        // Verify that ciphertext is not equal to plaintext
        assertFalse(String(encrypted).contains("ScrcpyRemote"))

        val decrypted = crypto.decryptString(encrypted)
        assertEquals(secretMessage, decrypted)
    }

    @Test
    fun testAes256BinaryFrameEncryption() {
        val crypto = AesGcmCrypto(AesGcmCrypto.deriveKey("123456"))
        val frameBytes = ByteArray(1024) { (it % 256).toByte() }

        val encrypted = crypto.encrypt(frameBytes)
        // IV (12) + GCM tag (16) + payload (1024) = 1052 bytes
        assertEquals(1052, encrypted.size)

        val decrypted = crypto.decrypt(encrypted)
        assertEquals(frameBytes.size, decrypted.size)
        assertTrue(frameBytes.contentEquals(decrypted))
    }

    @Test(expected = Exception::class)
    fun testAes256TamperingFailsIntegrityCheck() {
        val crypto = AesGcmCrypto(AesGcmCrypto.deriveKey("999888"))
        val encrypted = crypto.encryptString("Sensitive Control Command")

        // Tamper with one byte in the ciphertext
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1].toInt() xor 0xFF).toByte()

        // Decryption must throw exception due to invalid GCM auth tag
        crypto.decrypt(encrypted)
    }

    @Test
    fun testHostPermissionsMapConversion() {
        val permissions = HostPermissions(
            allowTouch = true,
            allowKeys = false,
            allowKeyboard = true,
            allowClipboard = false,
            allowAudio = true
        )
        val map = permissions.toMap()
        val restored = HostPermissions.fromMap(map)

        assertEquals(permissions.allowTouch, restored.allowTouch)
        assertEquals(permissions.allowKeys, restored.allowKeys)
        assertEquals(permissions.allowKeyboard, restored.allowKeyboard)
        assertEquals(permissions.allowClipboard, restored.allowClipboard)
        assertEquals(permissions.allowAudio, restored.allowAudio)
    }

    @Test
    fun testAdaptiveBitrateWeakNetworkTuning() {
        val controller = AdaptiveBitrateController()
        controller.setPreset(QualityPreset.ADAPTIVE)

        // Low latency -> EXCELLENT
        controller.onRttUpdated(25)
        assertEquals(NetworkTier.EXCELLENT, controller.telemetry.value.networkTier)
        assertEquals(35, controller.currentTargetFps)

        // Weak network -> WEAK
        controller.onRttUpdated(200)
        assertEquals(NetworkTier.WEAK, controller.telemetry.value.networkTier)
        assertEquals(15, controller.currentTargetFps)
        assertEquals(0.35f, controller.currentScale, 0.01f)
        assertEquals(35, controller.currentJpegQuality)
    }

    @Test
    fun testQualityPresets() {
        assertEquals(5, QualityPreset.ALL.size)
        assertTrue(QualityPreset.ADAPTIVE.isAdaptive)
        assertFalse(QualityPreset.ULTRA.isAdaptive)
        assertEquals(60, QualityPreset.ULTRA.targetFps)
        assertEquals(15, QualityPreset.WEAK_NET.targetFps)
    }
}
