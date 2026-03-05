package com.sanjay.utils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class CryptoUtil {
    private static final String ALGO = "AES";
    private static final ThreadLocal<SecretKey> secretKey = new ThreadLocal<>(); // ← fix

    public static void setSecretKey(SecretKey key) {
        secretKey.set(key); // ← fix
    }

    public static void setSecretKey(byte[] keyBytes) {
        secretKey.set(new SecretKeySpec(keyBytes, ALGO)); // ← fix
    }

    public static String encrypt(String plainText) throws Exception {
        if (secretKey.get() == null) { // ← fix
            throw new IllegalStateException("SecretKey not initialized.");
        }
        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey.get()); // ← fix
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public static String decrypt(String cipherText) throws Exception {
        if (secretKey.get() == null) { // ← fix
            throw new IllegalStateException("SecretKey not initialized.");
        }
        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.DECRYPT_MODE, secretKey.get()); // ← fix
        byte[] decoded = Base64.getDecoder().decode(cipherText);
        byte[] decrypted = cipher.doFinal(decoded);
        return new String(decrypted, StandardCharsets.UTF_8);
    }
}