package com.sentinelvault.security;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256 encryption using GCM mode.
 *
 * GCM is better than the default AES (ECB) mode because:
 *  - each encryption uses a fresh random IV, so identical files look different when encrypted
 *  - it also authenticates the data, so tampering with an encrypted file is detected on decrypt
 *
 * Stored format of every encrypted blob:  [12-byte IV][ciphertext + 16-byte auth tag]
 */
public class EncryptionUtil {

    private static final int IV_LENGTH = 12;      // 96-bit IV, recommended for GCM
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    // ---------- Keys ----------

    public static SecretKey generateKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        return keyGen.generateKey();
    }

    /** Turn a key into text so it can be stored in the database. */
    public static String keyToString(SecretKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /** Turn stored text back into a usable key. */
    public static SecretKey stringToKey(String encodedKey) {
        byte[] decoded = Base64.getDecoder().decode(encodedKey);
        return new SecretKeySpec(decoded, "AES");
    }

    // ---------- Raw bytes (the core operation everything else uses) ----------

    public static byte[] encryptBytes(byte[] data, SecretKey key) throws Exception {
        byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        byte[] cipherText = cipher.doFinal(data);

        // Prepend the IV so we can find it again when decrypting
        return ByteBuffer.allocate(iv.length + cipherText.length)
                .put(iv).put(cipherText).array();
    }

    public static byte[] decryptBytes(byte[] encrypted, SecretKey key) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(encrypted);
        byte[] iv = new byte[IV_LENGTH];
        buffer.get(iv);
        byte[] cipherText = new byte[buffer.remaining()];
        buffer.get(cipherText);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        return cipher.doFinal(cipherText);   // throws if the key is wrong or the data was tampered with
    }

    // ---------- Strings (handy for quick testing) ----------

    public static String encrypt(String data, SecretKey key) throws Exception {
        byte[] encrypted = encryptBytes(data.getBytes("UTF-8"), key);
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public static String decrypt(String encryptedData, SecretKey key) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(encryptedData);
        return new String(decryptBytes(decoded, key), "UTF-8");
    }

    // ---------- Files ----------

    public static void encryptFile(File input, File output, SecretKey key) throws Exception {
        byte[] data = Files.readAllBytes(input.toPath());
        Files.write(output.toPath(), encryptBytes(data, key));
    }

    public static void decryptFile(File input, File output, SecretKey key) throws Exception {
        byte[] data = Files.readAllBytes(input.toPath());
        Files.write(output.toPath(), decryptBytes(data, key));
    }
}
