package com.github.tvbox.osc.util;

import android.app.Activity;

import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class AppManager {
    /**
     * 改用 CopyOnWriteArrayList，避免 Stack 在并发环境下迭代/修改报 ConcurrentModificationException
     * 同时避免 Vector/Stack 的方法级锁带来的额外开销与不一致的同步语义
     */
    private static final CopyOnWriteArrayList<Activity> activityList = new CopyOnWriteArrayList<>();

    private AppManager() {
    }

    private static class SingleHolder {
        private static AppManager instance = new AppManager();
    }

    public static AppManager getInstance() {
        return SingleHolder.instance;
    }

    /**
     * 添加Activity到堆栈
     */
    public void addActivity(Activity activity) {
        if (activity != null) {
            activityList.add(activity);
        }
    }

    /**
     * 是否有activity
     */
    public boolean isActivity() {
        return !activityList.isEmpty();
    }

    /**
     * 获取当前Activity（堆栈中最后一个压入的）
     */
    public Activity currentActivity() {
        if (activityList.isEmpty()) return null;
        return activityList.get(activityList.size() - 1);
    }

    /**
     * 结束当前Activity（堆栈中最后一个压入的）
     */
    public void finishActivity() {
        Activity activity = currentActivity();
        if (activity != null && !activity.isFinishing()) {
            // 先从列表移除，避免 finish 后 isFinishing 检查与 onDestroy 的 finishActivity(this) 出现竞态
            activityList.remove(activity);
            activity.finish();
        }
    }

    public void finishActivity(Activity activity) {
        if (activity != null) {
            activityList.remove(activity);
        }
    }


    /**
     * 结束指定类名的Activity
     */
    public void finishActivity(Class<?> cls) {
        if (cls == null) return;
        for (Activity activity : activityList) {
            if (activity != null && activity.getClass().equals(cls)) {
                if (!activity.isFinishing()) {
                    activity.finish();
                }
                break;
            }
        }
    }

    /**
     * 回到指定类名的Activity（中间栈顶 Activity 全部 finish）
     */
    public void backActivity(Class<?> cls) {
        if (cls == null) return;
        // 倒序遍历：从栈顶往栈底找，找到目标 Activity 后将其上面的 Activity 全部 finish
        // 目标 Activity 保留在栈中
        for (int i = activityList.size() - 1; i >= 0; i--) {
            Activity activity = activityList.get(i);
            if (activity == null) continue;
            if (activity.getClass().equals(cls)) {
                return;
            }
            if (!activity.isFinishing()) {
                activity.finish();
            }
        }
    }

    /**
     * 结束所有Activity
     */
    public void finishAllActivity() {
        Iterator<Activity> it = activityList.iterator();
        while (it.hasNext()) {
            Activity activity = it.next();
            if (activity != null && !activity.isFinishing()) {
                activity.finish();
            }
        }
        activityList.clear();
    }

    /**
     * 获取指定的Activity
     */
    public Activity getActivity(Class<?> cls) {
        if (cls == null) return null;
        for (Activity activity : activityList) {
            if (activity != null && activity.getClass().equals(cls)) {
                return activity;
            }
        }
        return null;
    }

    public void appExit(int code) {
        try {
            finishAllActivity();
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(code);
        } catch (Exception e) {
            activityList.clear();
            e.printStackTrace();
        }
    }
}