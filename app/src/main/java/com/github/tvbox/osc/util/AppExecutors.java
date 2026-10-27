package com.github.tvbox.osc.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全局共享线程池：收敛散落的一次性 new Thread / 临时线程池，避免频繁创建线程、
 * 无名字线程难以排查。线程均为 daemon，不阻止进程退出。
 * 仅新增合适时用；现有 FastSearch/Detail/Play 的页面级搜索池与
 * SourceViewModel 的分池是刻意设计（pause/resume、防自死锁），不应合并到此处。
 */
public class AppExecutors {

    private static final AtomicInteger IO_SEQ = new AtomicInteger(1);

    // 通用后台 IO 池：缓存清理、配置读写等低并发后台任务。
    private static final ExecutorService IO = Executors.newFixedThreadPool(2, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "app-io-" + IO_SEQ.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    });

    private AppExecutors() {
    }

    /** 在通用后台 IO 池执行一个一次性后台任务。 */
    public static void execute(Runnable runnable) {
        IO.execute(runnable);
    }

    /** 供需要显式并发/可关闭的调用点复用。 */
    public static ExecutorService io() {
        return IO;
    }
}
