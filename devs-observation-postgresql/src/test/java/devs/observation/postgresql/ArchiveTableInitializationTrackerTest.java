package devs.observation.postgresql;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class ArchiveTableInitializationTrackerTest {

  @Test
  void concurrentInitializationWaitsForTheFirstInitializer() throws Exception {
    ArchiveTableInitializationTracker tracker = new ArchiveTableInitializationTracker();
    AtomicInteger initializerCalls = new AtomicInteger();
    CountDownLatch initializerStarted = new CountDownLatch(1);
    CountDownLatch allowInitializerToFinish = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      Future<?> first = executor.submit(() -> {
        tracker.ensureInitialized("obs_integer", () -> {
          initializerCalls.incrementAndGet();
          initializerStarted.countDown();
          await(allowInitializerToFinish);
        });
        return null;
      });

      Assertions.assertTrue(initializerStarted.await(5, TimeUnit.SECONDS));

      Future<?> second = executor.submit(() -> {
        tracker.ensureInitialized("obs_integer", initializerCalls::incrementAndGet);
        return null;
      });

      Assertions.assertFalse(second.isDone(), "Second initialization should wait for the first one to finish");

      allowInitializerToFinish.countDown();
      first.get(5, TimeUnit.SECONDS);
      second.get(5, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }

    Assertions.assertEquals(1, initializerCalls.get());
  }

  @Test
  void failedInitializationCanBeRetried() {
    ArchiveTableInitializationTracker tracker = new ArchiveTableInitializationTracker();
    AtomicInteger initializerCalls = new AtomicInteger();

    SQLException failure = Assertions.assertThrows(SQLException.class,
        () -> tracker.ensureInitialized("obs_integer", () -> {
          initializerCalls.incrementAndGet();
          throw new SQLException("boom");
        }));

    Assertions.assertEquals("boom", failure.getMessage());

    Assertions.assertDoesNotThrow(
        () -> tracker.ensureInitialized("obs_integer", () -> {
          initializerCalls.incrementAndGet();
        }));
    Assertions.assertEquals(2, initializerCalls.get());
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new AssertionError("Timed out waiting for concurrent test latch");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Interrupted while waiting for concurrent test latch", e);
    }
  }
}