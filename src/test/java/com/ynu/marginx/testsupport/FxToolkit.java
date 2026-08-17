package com.ynu.marginx.testsupport;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import org.junit.jupiter.api.Assumptions;

/**
 * Starts the JavaFX toolkit once for the whole test JVM and runs work on its thread.
 *
 * <p>A build machine without a desktop cannot start it at all. Rather than fail there, the tests
 * that need a scene graph are skipped, and the parts of the GUI that hold the actual rules live in
 * plain classes that need none of this.
 */
public final class FxToolkit {

    private static final long TIMEOUT_SECONDS = 30;

    private static boolean started;
    private static boolean unavailable;

    private FxToolkit() {
    }

    /** Skips the calling test when there is no toolkit to be had. */
    public static synchronized void startOrSkip() {
        if (started) {
            return;
        }
        Assumptions.assumeFalse(unavailable, "JavaFX toolkit is not available here");
        try {
            CountDownLatch ready = new CountDownLatch(1);
            Platform.startup(ready::countDown);
            if (!ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the toolkit did not start within " + TIMEOUT_SECONDS + "s");
            }
            Platform.setImplicitExit(false);
            started = true;
        } catch (IllegalStateException e) {
            // Already running in this JVM, which is fine.
            started = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (RuntimeException | LinkageError e) {
            unavailable = true;
            Assumptions.abort("JavaFX toolkit is not available here: " + e);
        }
    }

    /** Runs the action on the JavaFX thread and waits for it, so a test can assert afterwards. */
    public static <T> T call(Callable<T> action) {
        startOrSkip();
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(action.call());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        try {
            if (!done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the JavaFX thread did not finish within "
                        + TIMEOUT_SECONDS + "s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        if (failure.get() != null) {
            throw failure.get() instanceof RuntimeException runtime
                    ? runtime
                    : new IllegalStateException(failure.get());
        }
        return result.get();
    }

    public static void run(Runnable action) {
        call(() -> {
            action.run();
            return null;
        });
    }
}
