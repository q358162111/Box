package com.github.tvbox.osc.util;

import org.json.JSONObject;

import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AES {

    public static String rightPadding(String key, String replace, int Length) {
        if (key == null) return null;
        String strReturn = "";
        String strtemp = "";
        int curLength = key.trim().length();
        if (curLength > Length) {
            strReturn = key.trim().substring(0, Length);
        } else if (curLength == Length) {
            strReturn = key.trim();
        } else {
            for (int i = 0; i < (Length - curLength); i++) {
                strtemp = strtemp + replace;
            }
            strReturn = key.trim() + strtemp;
        }
        return strReturn;
    }

    /**
     * AES ECB 解密。Android 不支持 PKCS7Padding，使用 PKCS5Padding（等价）。
     * 修复了原代码 "AES/ECB/PKCS7Padding" 始终抛 NoSuchPaddingException、永远返回 null 的致命 bug。
     */
    public static String ECB(String data, String key) {
        if (data == null || key == null) return null;
        try {
            key = rightPadding(key, "0", 16);
            if (key == null) return null;
            byte[] data2 = toBytes(data);
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return new String(cipher.doFinal(data2));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * AES CBC 解密。同样使用 PKCS5Padding，并校验 key/iv 不为空。
     */
    public static String CBC(String data, String key, String iv) {
        if (data == null || key == null || iv == null) return null;
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(), "AES");
            AlgorithmParameterSpec paramSpec = new IvParameterSpec(iv.getBytes());
            cipher.init(Cipher.DECRYPT_MODE, keySpec, paramSpec);
            return new String(cipher.doFinal(toBytes(data)));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean isJson(String content) {
        try {
            new JSONObject(content);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static byte[] toBytes(String src) {
        if (src == null) return new byte[0];
        int l = src.length() / 2;
        byte[] ret = new byte[l];
        for (int i = 0; i < l; i++) {
            String sub = src.substring(i * 2, i * 2 + 2);
            try {
                ret[i] = Integer.valueOf(sub, 16).byteValue();
            } catch (NumberFormatException e) {
                // 非法 hex 字符返回 0，避免 ArrayIndexOutOfBoundsException/NumberFormatException
                ret[i] = 0;
            }
        }
        return ret;
    }

}