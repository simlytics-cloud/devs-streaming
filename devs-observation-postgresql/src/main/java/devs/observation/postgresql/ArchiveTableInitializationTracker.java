package devs.observation.postgresql;

import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinates one-time archive table initialization across concurrent writers.
 */
final class ArchiveTableInitializationTracker {

  private final Set<String> initializedTables = ConcurrentHashMap.newKeySet();
  private final ConcurrentHashMap<String, CompletableFuture<Void>> inFlightInitializations = new ConcurrentHashMap<>();

  void ensureInitialized(String tableName, SqlRunnable initializer) throws SQLException {
    if (initializedTables.contains(tableName)) {
      return;
    }

    CompletableFuture<Void> initializationFuture = new CompletableFuture<>();
    CompletableFuture<Void> existingFuture = inFlightInitializations.putIfAbsent(tableName, initializationFuture);
    if (existingFuture != null) {
      awaitInitialization(tableName, existingFuture);
      return;
    }

    try {
      initializer.run();
      initializedTables.add(tableName);
      initializationFuture.complete(null);
    } catch (SQLException e) {
      initializationFuture.completeExceptionally(e);
      throw e;
    } catch (RuntimeException e) {
      initializationFuture.completeExceptionally(e);
      throw e;
    } finally {
      inFlightInitializations.remove(tableName, initializationFuture);
    }
  }

  private void awaitInitialization(String tableName, CompletableFuture<Void> initializationFuture) throws SQLException {
    try {
      initializationFuture.join();
    } catch (CompletionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof SQLException sqlException) {
        throw sqlException;
      }
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException("Unable to initialize archive table " + tableName, cause);
    }
  }

  @FunctionalInterface
  interface SqlRunnable {
    void run() throws SQLException;
  }
}