package com.mobily.qalite.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SecretCipherService {

    private static final String ENCRYPTED_PREFIX = "enc:v1:";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final byte[] key;
    private final SecureRandom secureRandom;

    @Autowired
    public SecretCipherService(@Value("${qalite.secrets.encryption-key:}") String encryptionKey) {
        this(encryptionKey, new SecureRandom());
    }

    SecretCipherService(String encryptionKey, SecureRandom secureRandom) {
        this.key = StringUtils.hasText(encryptionKey) ? deriveKey(encryptionKey.trim()) : null;
        this.secureRandom = secureRandom;
    }

    public String encrypt(String plainText) {
        requireConfiguredKey();

        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);

        try {
            byte[] cipherText = cipher(Cipher.ENCRYPT_MODE, iv).doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + cipherText.length)
                    .put(iv)
                    .put(cipherText)
                    .array();

            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Secret encryption failed", exception);
        }
    }

    public String decrypt(String storedValue) {
        if (!StringUtils.hasText(storedValue) || !storedValue.startsWith(ENCRYPTED_PREFIX)) {
            return storedValue;
        }

        requireConfiguredKey();

        try {
            byte[] payload = Base64.getDecoder().decode(storedValue.substring(ENCRYPTED_PREFIX.length()));
            if (payload.length <= IV_BYTES) {
                throw new IllegalArgumentException("Encrypted secret payload is invalid");
            }

            byte[] iv = new byte[IV_BYTES];
            byte[] cipherText = new byte[payload.length - IV_BYTES];
            ByteBuffer.wrap(payload)
                    .get(iv)
                    .get(cipherText);

            return new String(cipher(Cipher.DECRYPT_MODE, iv).doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Stored secret could not be decrypted", exception);
        }
    }

    private Cipher cipher(int mode, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher;
    }

    private void requireConfiguredKey() {
        if (key == null) {
            throw new IllegalStateException("QALITE_SECRET_KEY must be configured before storing encrypted secrets");
        }
    }

    private static byte[] deriveKey(String encryptionKey) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Secret key derivation failed", exception);
        }
    }
}
