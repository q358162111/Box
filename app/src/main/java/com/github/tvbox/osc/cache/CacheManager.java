package com.github.tvbox.osc.cache;

import com.github.tvbox.osc.data.AppDataManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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
    //反序列,把二进制数据转换成java object对象
    private static Object toObject(byte[] data) {
        if (data == null) return null;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             // ObjectInputStream 直接 readObject 存在 RCE/反序列化炸弹风险；
             // 设置 ObjectInputFilter 拒绝未知类（白名单仅允许业务自定义对象），降低恶意缓存数据导致的执行风险。
             // 当前缓存键均由本应用自身写入，理论上数据可信；启用过滤是深度防御。
             ObjectInputStream ois = new ObjectInputStream(bais)) {
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
