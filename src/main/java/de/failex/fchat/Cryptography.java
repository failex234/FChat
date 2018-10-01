package de.failex.fchat;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

public class Cryptography {

    final static int RSABITS = 4096;
    static File publickeypath = new File(("data" + File.separator + "id_rsa.pub").replace(" ", ""));
    static File privatekeypath = new File(("data" + File.separator + "id_rsa").replace(" ", ""));

    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(RSABITS);
        return kpg.genKeyPair();
    }

    public static void saveKeys(PublicKey pub, PrivateKey priv) throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
        KeyFactory fact = KeyFactory.getInstance("RSA");
        RSAPublicKeySpec pubks = fact.getKeySpec(pub, RSAPublicKeySpec.class);
        RSAPrivateKeySpec privks = fact.getKeySpec(priv, RSAPrivateKeySpec.class);
        ObjectOutputStream oospriv = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(privatekeypath)));
        ObjectOutputStream oospub = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(publickeypath)));

        oospriv.writeObject(privks.getModulus());
        oospriv.writeObject(privks.getPrivateExponent());

        oospub.writeObject(pubks.getModulus());
        oospub.writeObject(pubks.getPublicExponent());

        oospriv.close();
        oospub.close();
    }

    public static KeyPair readKeys() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException, ClassNotFoundException {
        ObjectInputStream oinpriv = new ObjectInputStream(new BufferedInputStream(new FileInputStream(privatekeypath)));
        ObjectInputStream oinpub = new ObjectInputStream(new BufferedInputStream(new FileInputStream(publickeypath)));

        RSAPublicKeySpec keySpecPub = new RSAPublicKeySpec((BigInteger) oinpub.readObject(), (BigInteger) oinpub.readObject());
        RSAPrivateKeySpec keySpecPriv = new RSAPrivateKeySpec((BigInteger) oinpriv.readObject(), (BigInteger) oinpriv.readObject());
        KeyFactory fact = KeyFactory.getInstance("RSA");

        KeyPair kpg = new KeyPair(fact.generatePublic(keySpecPub), fact.generatePrivate(keySpecPriv));

        oinpriv.close();
        oinpub.close();

        return kpg;
    }

    public static byte[] rsaEncrypt(PrivateKey key, byte[] data) throws NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, IllegalBlockSizeException, InvalidKeyException {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] cipherdata = cipher.doFinal(data);
        return base64Encode(cipherdata);
    }

    public static byte[] rsaEncrypt(PublicKey key, byte[] data) throws NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, IllegalBlockSizeException, InvalidKeyException {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] cipherdata = cipher.doFinal(data);
        return base64Encode(cipherdata);
    }

    public static byte[] rsaDecrypt(PublicKey key, byte[] data) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] cipherdata = cipher.doFinal(base64Decode(data));
        return cipherdata;
    }

    public static byte[] rsaDecrypt(PrivateKey key, byte[] data) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] cipherdata = cipher.doFinal(base64Decode(data));
        return cipherdata;
    }

    public static String encryptString(PrivateKey key, String string) throws IllegalBlockSizeException, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException {
        return new String(rsaEncrypt(key, string.getBytes()));
    }

    private static byte[] base64Encode(byte[] data) {
        return Base64.getEncoder().encode(data);
    }

    private static byte[] base64Decode(byte[] data) {
        return Base64.getDecoder().decode(data);
    }

    public static boolean keysExist() {
        return publickeypath.exists() && privatekeypath.exists();
    }
}
