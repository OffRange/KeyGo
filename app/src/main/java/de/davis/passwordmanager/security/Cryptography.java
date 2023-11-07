package de.davis.passwordmanager.security;

import static at.favre.lib.crypto.bcrypt.BCrypt.Version.VERSION_2A;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.spec.KeySpec;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import at.favre.lib.crypto.bcrypt.BCrypt;
import at.favre.lib.crypto.bcrypt.LongPasswordStrategies;
import de.davis.passwordmanager.utils.KeyUtil;

public class Cryptography {

    private static final int IV_SIZE = 12;
    private static final BCrypt.Hasher BCRYPT_HASHER = BCrypt.with(LongPasswordStrategies.hashSha512(VERSION_2A));

    public static byte[] bcrypt(String data){
        return BCRYPT_HASHER.hash(12, data.getBytes());
    }

    public static boolean checkBcryptHash(String plaintext, byte[] hash){
        if (hash == null || hash.length == 0) return false;
        return BCrypt.verifyer(VERSION_2A, LongPasswordStrategies.hashSha512(VERSION_2A)).verify(plaintext.getBytes(), hash).verified;
    }

    public static byte[] encryptWithPwd(byte[] data, String pwd) throws GeneralSecurityException{
        byte[] salt = new byte[16];
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(pwd.toCharArray(), salt, 65536, 256);
        SecretKey secretKey = factory.generateSecret(spec);
        SecretKeySpec secret = new SecretKeySpec(secretKey.getEncoded(), "AES");

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secret);

        return encryptWithIV(cipher, data);
    }

    public static byte[] decryptWithPwd(byte[] data, String pwd) throws GeneralSecurityException{
        byte[] salt = new byte[16];
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(pwd.toCharArray(), salt, 65536, 256);
        SecretKey secretKey = factory.generateSecret(spec);
        SecretKeySpec secret = new SecretKeySpec(secretKey.getEncoded(), "AES");

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secret, new GCMParameterSpec(128, data, 0, IV_SIZE));

        return cipher.doFinal(data, IV_SIZE, data.length-IV_SIZE);
    }

    private static byte[] encryptWithIV(Cipher cipher, byte[] data) throws IllegalBlockSizeException, BadPaddingException {
        byte[] iv = cipher.getIV();

        byte[] encrypted = cipher.doFinal(data);
        byte[] encryptedData = Arrays.copyOf(iv, iv.length + encrypted.length );
        System.arraycopy(encrypted, 0, encryptedData, iv.length, encrypted.length);

        return encryptedData;
    }

    public static byte[] encryptAES(byte[] data) {
        try {
            Cipher cipher = KeyUtil.getCipher();

            cipher.init(Cipher.ENCRYPT_MODE, KeyUtil.getSecretKey());
            return encryptWithIV(cipher, data);
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
