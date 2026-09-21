package com.sih.dataservice.common;

import com.sih.dataservice.common.crypto.CryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoServiceTest {

    private CryptoService cryptoService;

    @BeforeEach
    void setUp() {
        String hmacSecret = Base64.getEncoder().encodeToString("c3VwZXJzZWNyZXRobWFja2V5MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=".getBytes());
        String aesKey = Base64.getEncoder().encodeToString("1234567890123456".getBytes());
        cryptoService = new CryptoService(hmacSecret, aesKey);
    }

    @Test
    void computeHmac_producesConsistentDeterministicHash() {
        String input = "+919876543210";
        String hash1 = cryptoService.computeHmac(input);
        String hash2 = cryptoService.computeHmac(input);

        assertThat(hash1).isNotNull();
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // SHA-256 hex string
    }

    @Test
    void computeHmac_differentInputsProduceDifferentHashes() {
        String hash1 = cryptoService.computeHmac("+919876543210");
        String hash2 = cryptoService.computeHmac("+919876543211");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void encryptAndDecrypt_recoversOriginalPlainText() {
        String plainText = "SecretAccountNumber123456789";
        String encrypted = cryptoService.encrypt(plainText);

        assertThat(encrypted).isNotEqualTo(plainText);
        assertThat(encrypted).isNotBlank();

        String decrypted = cryptoService.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(plainText);
    }
}
