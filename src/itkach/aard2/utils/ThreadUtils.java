// SPDX-License-Identifier: Apache-2.0

package itkach.aard2.utils;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

// Copyright 2016 The Android Open Source Project
public class ThreadUtils {
    private static volatile Thread sMainThread;
    private static volatile Handler sMainThreadHandler;
    private static volatile ExecutorService sThreadExecutor;

    /**
     * Returns true if the current thread is the UI thread.
     */
    public static boolean isMainThread() {
        if (sMainThread == null) {
            sMainThread = Looper.getMainLooper().getThread();
        }
        return Thread.currentThread() == sMainThread;
    }

    /**
     * Returns a shared UI thread handler.
     */
    public static Handler getUiThreadHandler() {
        if (sMainThreadHandler == null) {
            sMainThreadHandler = new Handler(Looper.getMainLooper());
        }

        return sMainThreadHandler;
    }

    /**
     * Checks that the current thread is the UI thread. Otherwise, throws an exception.
     */
    public static void ensureMainThread() {
        if (!isMainThread()) {
            throw new RuntimeException("Must be called on the UI thread");
        }
    }

    /**
     * Checks that the current thread is a worker thread. Otherwise, throws an exception.
     */
    public static void ensureWorkerThread() {
        if (isMainThread()) {
            throw new RuntimeException("Must be called on a worker thread");
        }
    }

    /**
     * Posts runnable in background using shared background thread pool.
     *
     * @return A future of the task that can be monitored for updates or cancelled.
     */
    public static Future<?> postOnBackgroundThread(Runnable runnable) {
        return getThreadExecutor().submit(runnable);
    }

    /**
     * Posts callable in background using shared background thread pool.
     *
     * @return A future of the task that can be monitored for updates or cancelled.
     */
    public static <T> Future<T> postOnBackgroundThread(Callable<T> callable) {
        return getThreadExecutor().submit(callable);
    }

    /**
     * Posts the runnable on the main thread.
     */
    public static void postOnMainThread(Runnable runnable) {
        getUiThreadHandler().post(runnable);
    }

    /**
     * Posts the runnable on the main thread with a delay.
     */
    public static void postOnMainThreadDelayed(Runnable runnable, long delayMillis) {
        getUiThreadHandler().postDelayed(runnable, delayMillis);
    }

    private static synchronized ExecutorService getThreadExecutor() {
        if (sThreadExecutor == null) {
            sThreadExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        }
        return sThreadExecutor;
    }
}