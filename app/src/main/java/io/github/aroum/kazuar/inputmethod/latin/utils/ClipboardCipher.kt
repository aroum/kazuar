// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.utils

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Pinned clips use an app-local, non-exportable key and authenticated encryption. */
internal object ClipboardCipher {
    private const val ALIAS = "kazuar.pinned-clips"
    private val magic = byteArrayOf(0x4b, 0x43, 0x01)

    private fun key(): SecretKey {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            "Encrypted clipboard persistence requires Android 6 or later"
        }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build())
        }.generateKey()
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return magic + cipher.iv + cipher.doFinal(plain)
    }

    fun decrypt(encoded: ByteArray): ByteArray {
        require(encoded.size >= magic.size + 12 + 16 &&
                encoded.copyOfRange(0, magic.size).contentEquals(magic)) {
            "Invalid encrypted clipboard file"
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(),
                GCMParameterSpec(128, encoded.copyOfRange(magic.size, magic.size + 12)))
        return cipher.doFinal(encoded, magic.size + 12, encoded.size - magic.size - 12)
    }
}
