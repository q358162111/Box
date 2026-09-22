package com.github.tvbox.osc.subtitle.runtime;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author AveryZhong.
 */

public class DefaultTaskExecutor extends TaskExecutor {

    @Nullable
    private volatile Handler mMainHandler;
    private final Object mLock = new Object();
    private final ExecutorService mDeskIO = Executors.newFixedThreadPool(3);
    // 标记线程池是否已 shutdown，App 退出/低内存时避免重复关闭
    private volatile boolean mShutdown = false;

    @Override
    public void executeOnDeskIO(final Runnable task) {
        if (task == null || mShutdown) return;
        try {
            mDeskIO.execute(task);
        } catch (Exception e) {
            // 线程池被 shutdown 后 execute 抛 RejectedExecutionException，吞掉避免崩溃
        }
    }

    @Override
    public void postToMainThread(final Runnable task) {
        if (task == null) return;
        // 双重检查锁：避免并发创建多个 Handler 实例
        if (mMainHandler == null) {
            synchronized (mLock) {
                if (mMainHandler == null) {
                    mMainHandler = new Handler(Looper.getMainLooper());
                }
            }
        }
        if (mMainHandler != null) {
            mMainHandler.post(task);
        }
    }

    @Override
    public boolean isMainThread() {
        try {
            return Thread.currentThread() == Looper.getMainLooper().getThread();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 关闭后台线程池。建议在 App.onTerminate 或进程被系统清理时调用，
     * 防止 Executors.newFixedThreadPool 创建的线程阻止进程退出。
     */
    public void shutdown() {
        if (mShutdown) return;
        synchronized (mLock) {
            if (mShutdown) return;
            mShutdown = true;
            try {
                mDeskIO.shutdownNow();
                if (!mDeskIO.awaitTermination(1, TimeUnit.SECONDS)) {
                    mDeskIO.shutdownNow();
                }
            } catch (InterruptedException ie) {
                mDeskIO.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
