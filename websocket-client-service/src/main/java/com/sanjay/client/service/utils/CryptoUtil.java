package com.sanjay.client.service.utils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class CryptoUtil {
    private static final String ALGO = "AES";
    private static volatile SecretKey secretKey; // ← volatile,

    public static void setSecretKey(SecretKey key) {
        secretKey = key;
    }

    public static void setSecretKey(byte[] keyBytes) {
        secretKey = new SecretKeySpec(keyBytes, ALGO);
    }

    public static String encrypt(String plainText) throws Exception {
        if (secretKey == null) throw new IllegalStateException("SecretKey not initialized.");
        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return Base64.getEncoder().encodeToString(
                cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8))
        );
    }

    public static String decrypt(String cipherText) throws Exception {
        if (secretKey == null) throw new IllegalStateException("SecretKey not initialized.");
        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        return new String(cipher.doFinal(Base64.getDecoder().decode(cipherText)), StandardCharsets.UTF_8);
    }
}