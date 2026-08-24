package com.mobily.qalite.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretCipherServiceSecurityTests {

    @Test
    void encryptedSecretsRoundTripWithoutExposingPlaintext() {
        SecretCipherService secretCipherService = new SecretCipherService("security-test-key");

        String encrypted = secretCipherService.encrypt("oracle-pass");

        assertTrue(encrypted.startsWith("enc:v1:"));
        assertFalse(encrypted.contains("oracle-pass"));
        assertEquals("oracle-pass", secretCipherService.decrypt(encrypted));
    }

    @Test
    void newSecretsCannotBeStoredWithoutEncryptionKey() {
        SecretCipherService secretCipherService = new SecretCipherService("");

        assertThrows(IllegalStateException.class, () -> secretCipherService.encrypt("secret"));
    }

    @Test
    void legacyPlaintextSecretsCanStillBeRead() {
        SecretCipherService secretCipherService = new SecretCipherService("");

        assertEquals("legacy-secret", secretCipherService.decrypt("legacy-secret"));
    }
}
