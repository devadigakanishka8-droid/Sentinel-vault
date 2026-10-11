package com.sentinelvault.security;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Protects the per-file encryption keys ("key wrapping").
 *
 * The master key is NEVER stored. It is derived from a passphrase with PBKDF2 (210,000 rounds)
 * every time the app starts. Each file's key is stored in the database only in encrypted
 * ("wrapped") form, so:
 *   - stealing the database alone reveals nothing,
 *   - stealing the encrypted files alone reveals nothing,
 *   - both together are still useless without the passphrase.
 */
public class KeyVault {

    private static final File DIR = new File("vault_storage");
    private static final File SALT_FILE = new File(DIR, "master.salt");
    private static final File CHECK_FILE = new File(DIR, "master.check");
    private static final byte[] CHECK_TEXT = "SENTINELVAULT-KEY-OK".getBytes();
    private static final int ITERATIONS = 210_000;

    private static SecretKey masterKey;

    /** Call once when the app starts. Throws SecurityException if the passphrase is wrong. */
    public static synchronized void unlock(char[] passphrase) throws Exception {
        if (passphrase == null || passphrase.length < 8) {
            throw new IllegalArgumentException("The master passphrase must be at least 8 characters.");
        }
        DIR.mkdirs();

        byte[] salt;
        if (SALT_FILE.exists()) {
            salt = Files.readAllBytes(SALT_FILE.toPath());
        } else {
            salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            Files.write(SALT_FILE.toPath(), salt);
        }

        SecretKey derived;
        PBEKeySpec spec = new PBEKeySpec(passphrase, salt, ITERATIONS, 256);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            derived = new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
        } finally {
            spec.clearPassword();
            Arrays.fill(passphrase, '\0');   // wipe the passphrase from memory
        }

        if (CHECK_FILE.exists()) {
            // A passphrase that derives the wrong key cannot decrypt the check file.
            try {
                byte[] plain = EncryptionUtil.decryptBytes(Files.readAllBytes(CHECK_FILE.toPath()), derived);
                if (!Arrays.equals(plain, CHECK_TEXT)) throw new SecurityException("Wrong master passphrase.");
            } catch (SecurityException e) {
                throw e;
            } catch (Exception e) {
                throw new SecurityException("Wrong master passphrase.");
            }
        } else {
            Files.write(CHECK_FILE.toPath(), EncryptionUtil.encryptBytes(CHECK_TEXT, derived));
        }
        masterKey = derived;
    }

    public static synchronized void lock() {
        masterKey = null;
    }

    public static synchronized boolean isUnlocked() {
        return masterKey != null;
    }

    /** Encrypts a file key so it can be stored safely in the database. */
    public static synchronized String wrap(SecretKey fileKey) throws Exception {
        requireUnlocked();
        byte[] wrapped = EncryptionUtil.encryptBytes(fileKey.getEncoded(), masterKey);
        return Base64.getEncoder().encodeToString(wrapped);
    }

    /** Recovers a file key from its wrapped form. */
    public static synchronized SecretKey unwrap(String wrappedKey) throws Exception {
        requireUnlocked();
        if (wrappedKey == null || wrappedKey.isEmpty()) {
            throw new SecurityException("This file's key has been destroyed. It cannot be recovered.");
        }
        byte[] raw = EncryptionUtil.decryptBytes(Base64.getDecoder().decode(wrappedKey), masterKey);
        return new SecretKeySpec(raw, "AES");
    }

    private static void requireUnlocked() {
        if (masterKey == null) {
            throw new IllegalStateException("The vault is locked. Call KeyVault.unlock(passphrase) first.");
        }
    }
}
