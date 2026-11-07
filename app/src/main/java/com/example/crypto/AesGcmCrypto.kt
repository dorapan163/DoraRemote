package com.example.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * End-to-End AES-256-GCM Encryption Engine for Scrcpy Remote.
 * Ensures zero-trust transmission security for video stream frames, touch events,
 * clipboard contents, and system control commands.
 */
class AesGcmCrypto(
    private val secretKey: SecretKey
) {
    companion object {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val PBKDF2_ITERATIONS = 10000
        private const val KEY_LENGTH_BITS = 256

        // Fixed app domain salt combined with pairing session
        private val DOMAIN_SALT = "ScrcpyRemote_E2EE_Salt_v1".toByteArray(StandardCharsets.UTF_8)

        /**
         * Derive a 256-bit AES key from a 6-digit pairing code (or custom passphrase) and session salt.
         */
        fun deriveKey(pairCode: String, sessionSalt: ByteArray = DOMAIN_SALT): SecretKey {
            val keySpec = PBEKeySpec(
                pairCode.toCharArray(),
                sessionSalt,
                PBKDF2_ITERATIONS,
                KEY_LENGTH_BITS
            )
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keyBytes = factory.generateSecret(keySpec).encoded
            return SecretKeySpec(keyBytes, ALGORITHM)
        }

        fun generateRandomSalt(): ByteArray {
            val salt = ByteArray(16)
            SecureRandom().nextBytes(salt)
            return salt
        }

        fun generatePairingCode(): String {
            val random = SecureRandom()
            val code = 100000 + random.nextInt(900000)
            return code.toString()
        }
    }

    private val secureRandom = SecureRandom()

    /**
     * Encrypts plaintext bytes using AES-256-GCM.
     * Output format: [12 bytes IV] + [Ciphertext + 16 bytes GCM Auth Tag]
     */
    fun encrypt(plainBytes: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val cipherText = cipher.doFinal(plainBytes)

        val buffer = ByteBuffer.allocate(iv.size + cipherText.size)
        buffer.put(iv)
        buffer.put(cipherText)
        return buffer.array()
    }

    /**
     * Decrypts ciphertext bytes using AES-256-GCM.
     * Input format: [12 bytes IV] + [Ciphertext + 16 bytes GCM Auth Tag]
     */
    fun decrypt(encryptedPacket: ByteArray): ByteArray {
        require(encryptedPacket.size > GCM_IV_LENGTH_BYTES + 16) {
            "Invalid packet length: ${encryptedPacket.size}"
        }

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        System.arraycopy(encryptedPacket, 0, iv, 0, GCM_IV_LENGTH_BYTES)

        val cipherTextLength = encryptedPacket.size - GCM_IV_LENGTH_BYTES
        val cipherText = ByteArray(cipherTextLength)
        System.arraycopy(encryptedPacket, GCM_IV_LENGTH_BYTES, cipherText, 0, cipherTextLength)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        return cipher.doFinal(cipherText)
    }

    fun encryptString(plainText: String): ByteArray {
        return encrypt(plainText.toByteArray(StandardCharsets.UTF_8))
    }

    fun decryptString(encryptedPacket: ByteArray): String {
        return String(decrypt(encryptedPacket), StandardCharsets.UTF_8)
    }
}
