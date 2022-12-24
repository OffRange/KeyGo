package de.davis.passwordmanager.security;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;

import at.favre.lib.crypto.bcrypt.BCrypt;
import at.favre.lib.crypto.bcrypt.LongPasswordStrategies;
import de.davis.passwordmanager.utils.KeyUtil;

public class Cryptography {

    private static final int IV_SIZE = 12;
    private static final BCrypt.Hasher BCRYPT_HASHER = BCrypt.with(LongPasswordStrategies.hashSha512(BCrypt.Version.VERSION_2A));

    public static byte[] bcrypt(String data){
        return BCRYPT_HASHER.hash(12, data.getBytes());
    }

    public static boolean checkBcryptHash(String plaintext, byte[] hash){
        return BCrypt.verifyer().verify(plaintext.getBytes(), hash).verified;
    }

    public static byte[] encryptAES(byte[] data) {
        try {
            Cipher cipher = KeyUtil.getCipher();

            cipher.init(Cipher.ENCRYPT_MODE, KeyUtil.getSecretKey());
            byte[] iv = cipher.getIV();

            byte[] encrypted = cipher.doFinal(data);
            byte[] encryptedData = Arrays.copyOf(iv, iv.length + encrypted.length );
            System.arraycopy(encrypted, 0, encryptedData, iv.length, encrypted.length);

            return encryptedData;
        }catch (GeneralSecurityException | IOException e){
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] decryptAES(byte[] data) {
        try {
            Cipher cipher = KeyUtil.getCipher();
            cipher.init(Cipher.DECRYPT_MODE, KeyUtil.getSecretKey(), new GCMParameterSpec(128, data, 0, IV_SIZE));
            return cipher.doFinal(data, IV_SIZE, data.length-IV_SIZE);
        }catch (GeneralSecurityException | IOException e){
            e.printStackTrace();
        }
        return null;
    }
}
