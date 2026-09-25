package com.sih.dataservice.common.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class CryptoService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String AES_GCM_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final byte[] hmacKeyBytes;
    private final byte[] aesKeyBytes;
    private final SecureRandom secureRandom = new SecureRandom();

    public CryptoService(
            @Value("${security.hmac.secret}") String hmacSecret,
            @Value("${security.encryption.key}") String aesKey) {
        
        // Decode base64 or treat raw string as bytes
        this.hmacKeyBytes = decodeSecretKey(hmacSecret);
        this.aesKeyBytes = decodeSecretKey(aesKey);
    }

    private byte[] decodeSecretKey(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * Computes HMAC-SHA256 hash returned as a lowercase hex string.
     * Used for keyed lookup of phone numbers and bank accounts (FR-AUTH-5, NFR-SEC-2).
     */
    public String computeHmac(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(hmacKeyBytes, HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] hashBytes = mac.doFinal(rawValue.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC hash", e);
        }
    }

    /**
     * Encrypts plaintext using AES-GCM-128/256 with a random IV.
     * Output format: Base64(IV + CipherText).
     */
    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(aesKeyBytes, "AES");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherBytes.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherBytes);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt data at rest", e);
        }
    }

    /**
     * Decrypts Base64(IV + CipherText) using AES-GCM.
     */
    public String decrypt(String base64CipherText) {
        if (base64CipherText == null) {
            return null;
        }
        try {
            byte[] combinedBytes = Base64.getDecoder().decode(base64CipherText);
            ByteBuffer byteBuffer = ByteBuffer.wrap(combinedBytes);

            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byteBuffer.get(iv);

            byte[] cipherBytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherBytes);

            Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(aesKeyBytes, "AES");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt data", e);
        }
    }
}
