package com.kathline.videoedit.util;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ThreadUtil {
    private static volatile ThreadUtil sTaskService;
    private final ExecutorService mExecutorService;
    private final ScheduledExecutorService mScheduledExecutorService;
    private final Handler mHandler;
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors() * 2;

    public ThreadUtil() {
        mExecutorService = Executors.newFixedThreadPool(CPU_COUNT);
        mScheduledExecutorService = Executors.newScheduledThreadPool(CPU_COUNT);
        mHandler = new Handler(Looper.getMainLooper());
    }

    public static ThreadUtil getInstance() {
        if (sTaskService == null) {
            synchronized (ThreadUtil.class) {
                if (sTaskService == null) {
                    sTaskService = new ThreadUtil();
                }
            }
        }
        return sTaskService;
    }

    public void doBackTask(FutureRunnable task) {
        if (task != null) {
            Future<?> executorService = mExecutorService.submit(task);
            task.setFuture(executorService);
        }
    }

    public void doBackTaskDelay(final FutureRunnable task, long delay) {
        if (task != null) {
            if (delay < 0) {
                delay = 0;
            }
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Future<?> executorService = mExecutorService.submit(task);
                    task.setFuture(executorService);
                }
            }, delay);
        }
    }

    public void runOnUiThread(Runnable runnable) {
        if (runnable != null) {
            mHandler.post(runnable);
        }
    }

    public void runOnUiThread(Runnable runnable, long delay) {
        if (runnable != null) {
            mHandler.postDelayed(runnable, delay);
        }
    }

    public abstract static class FutureRunnable implements Runnable {

        private Future<?> future;

        /* Getter and Setter for future */

        public Future<?> getFuture() {
            return future;
        }

        public void setFuture(Future<?> future) {
            this.future = future;
        }
    }

    /**
     * <pre>{@code
     *
     * FutureRunnable runnable = new FutureRunnable() {
     * public void run() {
     * if (true)
     * getFuture().cancel(false);
     * }
     * };
     * }</pre>
     *
     * @param task
     * @param delay
     * @param period
     */
    public void doTimeTask(FutureRunnable task, long delay, long period) {
        ScheduledFuture<?> scheduledFuture = mScheduledExecutorService.scheduleAtFixedRate(task, delay, period, TimeUnit.SECONDS);
        task.setFuture(scheduledFuture);
    }

    public abstract static class TimerRunnable {
        Runnable mLastTicker = null;
        public abstract boolean run();
    }

    public void schedule(final TimerRunnable task, long delay, final long period) {
        if (delay < 0) {
            delay = 0;
        }
        task.mLastTicker = new Runnable() {

            public void run() {
                long now = SystemClock.uptimeMillis();
                long next = now + (period - now % 1000);
                boolean run = task.run();
                if (!run) {
                    mHandler.postAtTime(task.mLastTicker, next);
//                    mHandler.postDelayed(mTicker, interval);
                }
//                Log.e("buder", now + "");
            }
        };
        mHandler.postDelayed(task.mLastTicker, delay);
    }

    public Handler getHandler() {
        return mHandler;
    }

    public void release() {
        mHandler.removeCallbacksAndMessages(null);
    }
}
