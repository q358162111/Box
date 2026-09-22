package com.github.tvbox.osc.util;

import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


/**
 * <b>类名称：</b> MD5 <br/>
 * <b>类描述：</b> MD5值计算<br/>
 * <b>创建人：</b> 林肯 <br/>
 * <b>修改人：</b> 编辑人 <br/>
 * <b>修改时间：</b> 2015年08月11日 下午2:41 <br/>
 * <b>修改备注：</b> <br/>
 *
 * @version 1.0.0 <br/>
 */
public class MD5 {
    private static final char hexDigits[] = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'a', 'b', 'c', 'd', 'e', 'f'};
    /**
     * 消息摘要.
     * ThreadLocal<MessageDigest>：MessageDigest 非线程安全，必须每个线程独立实例。
     * 修复了之前共享 static 实例导致哈希值错乱（高并发下 digest() 内部状态破坏）的严重 bug。
     */
    private static final ThreadLocal<MessageDigest> sDigest = new ThreadLocal<MessageDigest>() {
        @Override
        protected MessageDigest initialValue() {
            try {
                return MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                Log.e("获取MD5信息摘要失败", e.getMessage());
                return null;
            }
        }
    };

    /**
     * MD5值计算
     * MD5的算法在RFC1321 中定义:
     * 在RFC 1321中，给出了Test suite用来检验你的实现是否正确：
     * MD5 ("") = d41d8cd98f00b204e9800998ecf8427e
     * MD5 ("a") = 0cc175b9c0f1b6a831c399e269772661
     * MD5 ("abc") = 900150983cd24fb0d6963f7d28e17f72
     * MD5 ("message digest") = f96b697d7cb7938d525a2f31aaf161d0
     * MD5 ("abcdefghijklmnopqrstuvwxyz") = c3fcd3d76192e4007dfb496cca67e13b
     *
     * @param res 源字符串
     * @return md5值
     */
    public static String encode(String res) {
        if (res == null) return null;
        return encode(res.getBytes());
    }

    private static String encode(byte[] bytes) {
        if (bytes == null) return null;
        try {
            MessageDigest digest = sDigest.get();
            if (digest == null) return null;
            // 必须先 reset 防止 ThreadLocal 残留上次摘要状态污染本次结果
            digest.reset();
            digest.update(bytes);
            byte[] md = digest.digest();
            int j = md.length;
            char str[] = new char[j * 2];
            int k = 0;
            for (byte byte0 : md) {
                str[k++] = hexDigits[byte0 >>> 4 & 0xf];
                str[k++] = hexDigits[byte0 & 0xf];
            }
            return new String(str);
        } catch (Exception e) {
            return null;
        }
    }

    public static String getFileMd5(File f) {
        if (f == null) return "";
        StringBuffer sb = new StringBuffer("");
        FileInputStream fis = null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[4096];
            fis = new FileInputStream(f);
            int len;
            while ((len = fis.read(buffer)) != -1) {
                md.update(buffer, 0, len);
            }
            byte b[] = md.digest();
            int d;
            for (int i = 0; i < b.length; i++) {
                d = b[i];
                if (d < 0) {
                    d = b[i] & 0xff;
                }
                if (d < 16)
                    sb.append("0");
                sb.append(Integer.toHexString(d));
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ignored) {
                }
            }
        }
        return sb.toString();
    }

    /**
     * MD5加码 生成32位md5码
     */
    public static String string2MD5(String inStr) {
        if (TextUtils.isEmpty(inStr)) {
            Log.e("MD5", "参数strSource不能为空");
            return null;
        }
        try {
            MessageDigest digest = sDigest.get();
            if (digest == null) {
                Log.e("MD5", "MD5信息摘要初始化失败");
                return null;
            }
            // 防止 ThreadLocal 残留状态污染
            digest.reset();
            byte[] byteArray = inStr.getBytes();
            byte[] md5Bytes = digest.digest(byteArray);
            StringBuilder hexValue = new StringBuilder();
            for (byte md5Byte : md5Bytes) {
                int val = ((int) md5Byte) & 0xff;
                if (val < 16)
                    hexValue.append("0");
                hexValue.append(Integer.toHexString(val));
            }
            return hexValue.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 先使用MD5进行加密，再使用Base64进行编码， 若不支持此类字符集合的加密，返回null.
     *
     * @param strSource 待加密的源字符串
     * @return 加密后的字符串，不支持此类字符集合返回null
     */
    public static String encrypt(final String strSource) {
        if (TextUtils.isEmpty(strSource)) {
            Log.e("MD5", "参数strSource不能为空");
            return null;
        }
        try {
            MessageDigest digest = sDigest.get();
            if (digest == null) {
                Log.e("MD5", "MD5信息摘要初始化失败");
                return null;
            }
            digest.reset();
            byte[] md5Bytes = digest.digest(strSource.getBytes("utf-8"));
            byte[] encryptBytes = Base64.encode(md5Bytes, Base64.DEFAULT);
            String strEncrypt = new String(encryptBytes, "utf-8");
            // 截断Base64产生的换行符（若长度>0再截，否则 index=0 会抛 StringIndexOutOfBoundsException）
            return strEncrypt.length() > 0 ? strEncrypt.substring(0, strEncrypt.length() - 1) : strEncrypt;
        } catch (UnsupportedEncodingException e) {
            Log.e("MD5", "加密模块暂不支持此字符集合" + e);
        }
        return null;
    }

    public static String encrypt4login(final String strSource, String appSecert) {
        String str = encrypt(strSource) + appSecert;
        return string2MD5(str);
    }
}