package com.github.tvbox.osc.util;

import com.github.tvbox.osc.base.App;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class UA {

    private static final String DEFAULT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36";
    private static final Random RANDOM = new Random();
    // 首次调用时一次性读入内存，避免每次随机取 UA 都打开 asset 文件（主线程 IO + fd 泄漏）
    private static volatile List<String> uaList;

    public static String random() {
        List<String> list = uaList;
        if (list == null) {
            synchronized (UA.class) {
                if (uaList == null) {
                    uaList = load();
                }
                list = uaList;
            }
        }
        if (list == null || list.isEmpty()) return DEFAULT;
        return list.get(RANDOM.nextInt(list.size()));
    }

    private static List<String> load() {
        List<String> result = new ArrayList<>();
        try (InputStream fis = App.getInstance().getAssets().open("ua.db");
             DataInputStream dis = new DataInputStream(fis)) {
            int len = dis.readInt();
            dis.skipBytes(len * 4); // 跳过偏移索引表，字符串数据按序存储
            for (int i = 0; i < len; i++) {
                result.add(dis.readUTF());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }
}
