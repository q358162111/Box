package com.github.tvbox.osc.cache;

import com.github.tvbox.osc.data.AppDataManager;
import com.github.tvbox.osc.subtitle.runtime.AppTaskExecutor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.List;

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

    // 内存镜像：key -> 反序列化后的对象。allowMainThreadQueries 移除后，
    // 同步读路径（播放进度起播判断等）从镜像取值；DB 只在后台线程读写
    // 上限：播放进度/字幕路径随观看量单调增长，原实现只 put 不淘汰，
    // 长期使用的进程内会无限膨胀直至 OOM。改为 accessOrder 的 LRU，超限时淘汰最久未读的条目。
    // 注意：淘汰只影响内存镜像，DB 中的数据不受影响，冷启动会重新预热。
    private static final int MAX_MIRROR_ENTRIES = 2000;
    private static final java.util.Map<String, Object> memoryMirror = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<String, Object>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Object> eldest) {
                    return size() > MAX_MIRROR_ENTRIES;
                }
            });
    // 镜像是否已完成初始全量加载（懒加载，首次 getCache 时在后台预热）
    private static volatile boolean mirrorLoaded = false;

    private static void warmMirrorIfNeed() {
        if (mirrorLoaded) return;
        synchronized (CacheManager.class) {
            if (mirrorLoaded) return;
            // 后台全量预读 cache 表到内存镜像；完成后镜像与 DB 一致
            AppTaskExecutor.deskIO().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        List<Cache> all = AppDataManager.get().getCacheDao().getAll();
                        for (Cache cache : all) {
                            Object value = toObject(cache.data);
                            if (value != null) memoryMirror.put(cache.key, value);
                        }
                    } catch (Throwable ignored) {
                    }
                    mirrorLoaded = true;
                }
            });
        }
    }

    public static <T> void delete(String key, T body) {
        memoryMirror.remove(key);
        AppTaskExecutor.deskIO().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Cache cache = new Cache();
                    cache.key = key;
                    cache.data = toByteArray(body);
                    AppDataManager.get().getCacheDao().delete(cache);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    public static <T> void save(String key, T body) {
        memoryMirror.put(key, body);
        AppTaskExecutor.deskIO().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Cache cache = new Cache();
                    cache.key = key;
                    cache.data = toByteArray(body);
                    AppDataManager.get().getCacheDao().save(cache);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    public static Object getCache(String key) {
        warmMirrorIfNeed();
        // 同步读走内存镜像，不触碰 Room（主线程纪律）；镜像未预热完成时短暂返回 null，
        // 调用方（进度起播判断）对此已有 null 兜底
        return memoryMirror.get(key);
    }
}
