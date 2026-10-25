package com.github.tvbox.osc.cache;

import com.github.tvbox.osc.data.AppDataManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

/**
 * 类描述:
 *
 * @author pj567
 * @since 2020/5/15
 */
public class CacheManager {
    // 反序列化白名单：缓存值只会是 Long（播放进度）/String（字幕路径）及 Cache 包装类。
    // 通过 resolveClass 校验类名，非白名单类直接拒绝——效果等同 ObjectInputFilter，
    // 且不依赖 API 28+ 的 java.io.ObjectInputFilter（desugaring 环境下不可用）
    private static final String[] ALLOWED_CLASSES = {
            "java.lang.Long",
            "java.lang.String",
            "java.lang.Object",
            Cache.class.getName(),
    };

    //反序列,把二进制数据转换成java object对象
    private static Object toObject(byte[] data) {
        if (data == null) return null;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             // ObjectInputStream 直接 readObject 存在 RCE/反序列化炸弹风险；
             // resolveClass 白名单拒绝未知类，降低恶意缓存数据导致的执行风险
             ObjectInputStream ois = new ObjectInputStream(bais) {
                 @Override
                 protected Class<?> resolveClass(java.io.ObjectStreamClass desc) throws java.io.IOException, ClassNotFoundException {
                     boolean allowed = false;
                     for (String name : ALLOWED_CLASSES) {
                         if (name.equals(desc.getName())) {
                             allowed = true;
                             break;
                         }
                     }
                     if (!allowed) {
                         throw new InvalidClassException("Class not allowed in cache deserialization: " + desc.getName());
                     }
                     return super.resolveClass(desc);
                 }
             }) {
            return ois.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    //序列化存储数据需要转换成二进制
    private static <T> byte[] toByteArray(T body) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(body);
            oos.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new byte[0];
    }

    public static <T> void delete(String key, T body) {
        Cache cache = new Cache();
        cache.key = key;
        cache.data = toByteArray(body);
        AppDataManager.get().getCacheDao().delete(cache);
    }

    public static <T> void save(String key, T body) {
        Cache cache = new Cache();
        cache.key = key;
        cache.data = toByteArray(body);
        AppDataManager.get().getCacheDao().save(cache);
    }

    public static Object getCache(String key) {
        Cache cache = AppDataManager.get().getCacheDao().getCache(key);
        if (cache != null && cache.data != null) {
            return toObject(cache.data);
        }
        return null;
    }
}
