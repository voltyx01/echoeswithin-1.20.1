package com.voltyx.efw.client;

import com.mojang.logging.LogUtils;
import java.util.concurrent.CountDownLatch;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;

/** Runs WaterMedia's FFmpeg start on a background thread (see WaterMediaAsyncMixin). */
public final class FfmpegAsync {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final CountDownLatch DONE = new CountDownLatch(1);
    private static volatile Thread worker;

    private FfmpegAsync() {
    }

    /** @return true when the start was handed to the background thread, false when the caller is that thread and must run it. */
    public static synchronized boolean launch(BooleanSupplier start) {
        if (Thread.currentThread() == worker) {
            return false;
        }
        if (worker != null) {
            return true;
        }
        Thread t = new Thread(() -> {
            long t0 = System.nanoTime();
            try {
                start.getAsBoolean();
            } catch (Throwable e) {
                LOGGER.error("WaterMedia FFmpeg start failed", e);
            } finally {
                DONE.countDown();
                LOGGER.info("WaterMedia FFmpeg started in background in {} ms", (System.nanoTime() - t0) / 1_000_000);
            }
        }, "EFW FFmpeg bootstrap");
        t.setDaemon(true);
        t.setContextClassLoader(Thread.currentThread().getContextClassLoader());
        worker = t;
        t.start();
        return true;
    }

    public static void await() {
        if (worker == null || Thread.currentThread() == worker || DONE.getCount() == 0) {
            return;
        }
        try {
            DONE.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
