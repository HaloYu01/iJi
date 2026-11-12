package com.iji.app.backup

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 备份加密：AES-256-GCM，密钥存于 **Android Keystore**。
 *
 * 为什么用 Keystore 而不是把密钥写在代码/配置文件里：
 * Keystore 中的密钥由系统密钥库托管，**不会出现在应用沙盒的任何文件里**，
 * 即使设备被 root 或备份文件被拷走，密钥仍在设备上受硬件/系统保护；
 * 代价是密钥无法跨设备迁移，因此备份文件**只能在原设备上还原**
 * （换设备请用配置备份的明文 JSON 通道）。
 *
 * 文件格式：`[1 字节版本][12 字节 IV][密文 + 16 字节 GCM Tag]`
 * - 版本 2（当前写入）：明文是 gzip 流，先压缩再加密（见 [BackupPayload]）
 * - 版本 1（旧备份只读）：明文是原始 JSON，用于兼容升级前写下的备份文件
 */
object BackupCipher {

    private const val TAG = "BackupCipher"
    private const val KEY_ALIAS = "iji_backup_key"
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    /** 取得（或首次生成）备份专用密钥 */
    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    /** 加密（内部先 gzip 压缩）；失败返回 null（调用方需处理，不静默吞掉） */
    fun encrypt(data: ByteArray): ByteArray? = runCatching {
        val payload = BackupPayload.compress(data)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(payload)
        ByteArray(1 + IV_LEN + cipherText.size).also { out ->
            out[0] = BackupPayload.VERSION
            System.arraycopy(iv, 0, out, 1, IV_LEN)
            System.arraycopy(cipherText, 0, out, 1 + IV_LEN, cipherText.size)
        }
    }.onFailure { Log.w(TAG, "备份加密失败", it) }.getOrNull()

    /** 解密（版本 2 会解压，版本 1 原样返回）；密钥不匹配或文件被篡改时返回 null */
    fun decrypt(blob: ByteArray): ByteArray? = runCatching {
        require(blob.size > 1 + IV_LEN) { "备份文件过短" }
        require(BackupPayload.isKnownVersion(blob[0])) { "不支持的备份版本：${blob[0]}" }
        val iv = blob.copyOfRange(1, 1 + IV_LEN)
        val cipherText = blob.copyOfRange(1 + IV_LEN, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        BackupPayload.unpack(blob[0], cipher.doFinal(cipherText))
    }.onFailure { Log.w(TAG, "备份解密失败（密钥不匹配或文件损坏）", it) }.getOrNull()

    /** 便于日志/调试查看是否加密 */
    fun isEncrypted(blob: ByteArray): Boolean =
        blob.isNotEmpty() && BackupPayload.isKnownVersion(blob[0]) && blob.size > 1 + IV_LEN

    fun encodeBase64(data: ByteArray): String = Base64.encodeToString(data, Base64.NO_WRAP)

    fun decodeBase64(text: String): ByteArray? = runCatching { Base64.decode(text, Base64.NO_WRAP) }.getOrNull()
}