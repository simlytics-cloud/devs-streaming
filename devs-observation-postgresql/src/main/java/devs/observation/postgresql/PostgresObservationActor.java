package devs.observation.postgresql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import devs.msg.Branch;
import devs.msg.Run;
import devs.observation.DevsObservationMessage;
import devs.observation.Observation;
import devs.observation.ObservationSinkKeys;
import devs.observation.ObservationTypeEntry;
import devs.observation.StopLogger;
import devs.utils.DevsObjectMapper;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.StashBuffer;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;
import org.apache.pekko.actor.typed.receptionist.Receptionist;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Observation sink actor that persists shared observation messages into PostgreSQL tables.
 */
public class PostgresObservationActor extends AbstractBehavior<DevsObservationMessage> {

  private static final String RUNS_TABLE = "runs";
  private static final String BRANCHES_TABLE = "branches";
  private static final String OBSERVATION_TYPE_CATALOG_TABLE = "observation_type_catalog";
  private static final Duration STOP_RETRY_INTERVAL = Duration.ofSeconds(1);

  private final ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();
  private final PostgresObservationConfig config;
  private final PostgresArchiveNameStrategy archiveNameStrategy;
  private final TimerScheduler<DevsObservationMessage> timers;
  private final StashBuffer<DevsObservationMessage> stash;
  private final ActorRef<DevsObservationMessage> selfRef;
  private final HikariDataSource dataSource;
  private final ExecutorService databaseExecutor;
  private final String schema;
  private final ArchiveTableInitializationTracker archiveTableInitializationTracker =
      new ArchiveTableInitializationTracker();
  private final Map<String, String> knownObservationTypes = new ConcurrentHashMap<>();

  private int pendingWrites;
  private boolean stopRequested;

  /**
   * Creates a PostgreSQL-backed observation sink behavior from a parsed configuration.
   *
   * @param config PostgreSQL sink configuration
   * @return configured observation sink behavior
   */
  public static Behavior<DevsObservationMessage> create(PostgresObservationConfig config) {
    return Behaviors.setup(context -> Behaviors.withTimers(timers ->
        Behaviors.withStash(1000, stash ->
            new PostgresObservationActor(context, timers, stash, config, new PostgresArchiveNameStrategy()))));
  }

  /**
   * Creates a PostgreSQL-backed observation sink behavior from HOCON configuration.
   *
   * @param config root application config or PostgreSQL sub-config
   * @return configured observation sink behavior
   */
  public static Behavior<DevsObservationMessage> create(Config config) {
    return create(PostgresObservationConfig.fromConfig(config));
  }

  /**
   * Creates a PostgreSQL-backed observation sink actor.
   *
   * @param context actor context
   * @param timers timer scheduler used for shutdown retries
   * @param stash stash used while initialization is still in progress
   * @param config PostgreSQL sink configuration
   * @param archiveNameStrategy naming strategy for tables and indexes
   */
  PostgresObservationActor(
      ActorContext<DevsObservationMessage> context,
      TimerScheduler<DevsObservationMessage> timers,
      StashBuffer<DevsObservationMessage> stash,
      PostgresObservationConfig config,
      PostgresArchiveNameStrategy archiveNameStrategy
  ) {
    super(context);
    this.timers = timers;
    this.stash = stash;
    this.selfRef = context.getSelf();
    this.config = config;
    this.archiveNameStrategy = archiveNameStrategy;
    this.schema = requireSchema(config.schema());
    this.dataSource = buildDataSource(config);
    this.databaseExecutor = Executors.newFixedThreadPool(
        config.maximumPoolSize(),
        new ObservationThreadFactory());
    initializeAsync();
  }

  @Override
  public Receive<DevsObservationMessage> createReceive() {
    return newReceiveBuilder()
        .onMessage(InitializationSucceeded.class, this::onInitializationSucceeded)
        .onMessage(InitializationFailed.class, this::onInitializationFailed)
        .onMessage(DevsObservationMessage.class, message -> {
          stash.stash(message);
          return Behaviors.same();
        })
        .build();
  }

  private Behavior<DevsObservationMessage> onInitializationSucceeded(InitializationSucceeded message) {
    knownObservationTypes.putAll(message.knownObservationTypes());
    getContext().getSystem().receptionist().tell(
        Receptionist.register(ObservationSinkKeys.OBSERVATION_SINK_KEY, selfRef));
    getContext().getLog().info("Initialized PostgreSQL observation actor with {} observation types",
        knownObservationTypes.size());
    return stash.unstashAll(ready());
  }

  private Behavior<DevsObservationMessage> onInitializationFailed(InitializationFailed message) {
    getContext().getLog().error("PostgreSQL observation actor initialization failed: {}", message.reason());
    closeResources();
    return Behaviors.stopped();
  }

  private Receive<DevsObservationMessage> ready() {
    return newReceiveBuilder()
        .onMessage(Observation.class, this::onObservation)
        .onMessage(Run.class, this::onRun)
        .onMessage(Branch.class, this::onBranch)
        .onMessage(ObservationTypeEntry.class, this::onObservationTypeEntry)
        .onMessage(WriteCompleted.class, this::onWriteCompleted)
        .onMessage(WriteFailed.class, this::onWriteFailed)
        .onMessage(StopLogger.class, this::onStopLogger)
        .build();
  }

  private Behavior<DevsObservationMessage> onObservation(Observation<?, ?> observation) {
    String observationType = observation.getObservationType();
    String archiveName = knownObservationTypes.computeIfAbsent(
        observationType,
        archiveNameStrategy::archiveNameForObservationType
    );
    incrementPendingWrites();
    CompletableFuture.runAsync(() -> writeObservation(observation, archiveName), databaseExecutor)
        .whenComplete((unused, throwable) -> notifyCompletion(
            "observation " + observation._id(),
            throwable == null ? null : unwrapThrowable(throwable)));
    return Behaviors.same();
  }

  private Behavior<DevsObservationMessage> onObservationTypeEntry(ObservationTypeEntry entry) {
    String archiveName = archiveNameStrategy.sanitizeArchiveName(entry.getArchiveName());
    String existingArchiveName = knownObservationTypes.putIfAbsent(entry.getTypeId(), archiveName);
    if (existingArchiveName != null && !existingArchiveName.equals(archiveName)) {
      getContext().getLog().warn(
          "Observation type {} already mapped to archive {}, ignoring new archive {}",
          entry.getTypeId(),
          existingArchiveName,
          archiveName
      );
      return Behaviors.same();
    }

    ObservationTypeEntry sanitizedEntry = buildObservationTypeEntry(
        entry.getTypeId(),
        archiveName,
        entry.getMetadata()
    );
    incrementPendingWrites();
    CompletableFuture.runAsync(() -> ensureObservationType(sanitizedEntry), databaseExecutor)
        .whenComplete((unused, throwable) -> notifyCompletion(
            "observation type " + entry.getTypeId(),
            throwable == null ? null : unwrapThrowable(throwable)));
    return Behaviors.same();
  }

  private Behavior<DevsObservationMessage> onRun(Run run) {
    incrementPendingWrites();
    CompletableFuture.runAsync(() -> writeRun(run), databaseExecutor)
        .whenComplete((unused, throwable) -> notifyCompletion(
            "run " + run._id(),
            throwable == null ? null : unwrapThrowable(throwable)));
    return Behaviors.same();
  }

  private Behavior<DevsObservationMessage> onBranch(Branch branch) {
    incrementPendingWrites();
    CompletableFuture.runAsync(() -> writeBranch(branch), databaseExecutor)
        .whenComplete((unused, throwable) -> notifyCompletion(
            "branch " + branch._id(),
            throwable == null ? null : unwrapThrowable(throwable)));
    return Behaviors.same();
  }

  private Behavior<DevsObservationMessage> onWriteCompleted(WriteCompleted message) {
    pendingWrites = Math.max(0, pendingWrites - 1);
    getContext().getLog().debug("Completed {}", message.description());
    return maybeStopAfterPendingWrites();
  }

  private Behavior<DevsObservationMessage> onWriteFailed(WriteFailed message) {
    pendingWrites = Math.max(0, pendingWrites - 1);
    getContext().getLog().error("Failed {}: {}", message.description(), message.reason());
    return maybeStopAfterPendingWrites();
  }

  private Behavior<DevsObservationMessage> onStopLogger(StopLogger stopLogger) {
    stopRequested = true;
    return maybeStopAfterPendingWrites();
  }

  private Behavior<DevsObservationMessage> maybeStopAfterPendingWrites() {
    if (!stopRequested) {
      return Behaviors.same();
    }
    if (pendingWrites > 0) {
      timers.startSingleTimer(StopLogger.builder().build(), STOP_RETRY_INTERVAL);
      getContext().getLog().info("Waiting for {} pending PostgreSQL writes before shutdown", pendingWrites);
      return Behaviors.same();
    }

    closeResources();
    getContext().getLog().info("PostgreSQL observation actor stopped");
    return Behaviors.stopped();
  }

  private void initializeAsync() {
    CompletableFuture.supplyAsync(this::initializeDatabase, databaseExecutor)
        .whenComplete((knownTypes, throwable) -> {
          if (throwable == null) {
            selfRef.tell(new InitializationSucceeded(knownTypes));
          } else {
            Throwable rootCause = unwrapThrowable(throwable);
            String reason = rootCause.getMessage() == null ? rootCause.getClass().getName() : rootCause.getMessage();
            selfRef.tell(new InitializationFailed(reason));
          }
        });
  }

  private Map<String, String> initializeDatabase() {
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(true);
      try (Statement statement = connection.createStatement()) {
        statement.execute("CREATE SCHEMA IF NOT EXISTS " + quotedIdentifier(schema));
      }

      createBaseTables(connection);
      return loadExistingObservationTypes(connection);
    } catch (SQLException e) {
      throw new IllegalStateException("Unable to initialize PostgreSQL observation schema", e);
    }
  }

  private void writeRun(Run run) {
    String sql = "INSERT INTO " + qualifiedTable(RUNS_TABLE)
        + " (id, document) VALUES (?, ?::jsonb) ON CONFLICT (id) DO NOTHING";
    try (Connection connection = dataSource.getConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, run._id());
      statement.setString(2, writeJson(run));
      statement.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException("Unable to write run " + run._id(), e);
    }
  }

  private void writeBranch(Branch branch) {
    String sql = "INSERT INTO " + qualifiedTable(BRANCHES_TABLE)
        + " (id, run_id, document) VALUES (?, ?, ?::jsonb) ON CONFLICT (id) DO NOTHING";
    try (Connection connection = dataSource.getConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, branch._id());
      statement.setString(2, branch.getRunId());
      statement.setString(3, writeJson(branch));
      statement.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException("Unable to write branch " + branch._id(), e);
    }
  }

  private void writeObservation(Observation<?, ?> observation, String archiveName) {
    ObservationTypeEntry entry = buildObservationTypeEntry(
        observation.getObservationType(),
        archiveName,
        Map.of()
    );

    try (Connection connection = dataSource.getConnection()) {
      ensureObservationType(connection, entry);
      ensureArchiveTable(connection, archiveName);

      String sql = "INSERT INTO " + qualifiedTable(archiveName)
          + " (id, run_id, branch_id, producer_model, observation_type, time, payload, document)"
          + " VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb)"
          + " ON CONFLICT (id) DO NOTHING";
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, observation._id());
        statement.setString(2, observation.getRunId());
        statement.setString(3, observation.getBranchId());
        statement.setString(4, observation.getProducerModel());
        statement.setString(5, observation.getObservationType());
        statement.setString(6, writeJson(observation.getTime()));
        statement.setString(7, writeJson(observation.getPayload()));
        statement.setString(8, writeJson(observation));
        statement.executeUpdate();
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Unable to write observation " + observation._id(), e);
    }
  }

  private void ensureObservationType(ObservationTypeEntry entry) {
    try (Connection connection = dataSource.getConnection()) {
      ensureObservationType(connection, entry);
      ensureArchiveTable(connection, entry.getArchiveName());
    } catch (SQLException e) {
      throw new IllegalStateException("Unable to write observation type " + entry.getTypeId(), e);
    }
  }

  private void ensureObservationType(Connection connection, ObservationTypeEntry entry) throws SQLException {
    String safeArchiveName = archiveNameStrategy.sanitizeArchiveName(entry.getArchiveName());
    ObservationTypeEntry sanitizedEntry = buildObservationTypeEntry(
        entry.getTypeId(),
        safeArchiveName,
        entry.getMetadata()
    );
    String sql = "INSERT INTO " + qualifiedTable(OBSERVATION_TYPE_CATALOG_TABLE)
        + " (type_id, archive_name, document) VALUES (?, ?, ?::jsonb)"
        + " ON CONFLICT (type_id) DO NOTHING";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, sanitizedEntry.getTypeId());
      statement.setString(2, sanitizedEntry.getArchiveName());
      statement.setString(3, writeJson(sanitizedEntry));
      statement.executeUpdate();
    }
    knownObservationTypes.put(sanitizedEntry.getTypeId(), sanitizedEntry.getArchiveName());
  }

  private void createBaseTables(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("""
          CREATE TABLE IF NOT EXISTS %s (
            id text PRIMARY KEY,
            document jsonb NOT NULL
          )
          """.formatted(qualifiedTable(RUNS_TABLE)));

      statement.execute("""
          CREATE TABLE IF NOT EXISTS %s (
            id text PRIMARY KEY,
            run_id text,
            document jsonb NOT NULL
          )
          """.formatted(qualifiedTable(BRANCHES_TABLE)));

      statement.execute("CREATE INDEX IF NOT EXISTS "
          + quotedIdentifier(archiveNameStrategy.auxiliaryName("idx", BRANCHES_TABLE + "_run_id"))
          + " ON " + qualifiedTable(BRANCHES_TABLE) + " (run_id)");

      statement.execute("""
          CREATE TABLE IF NOT EXISTS %s (
            type_id text PRIMARY KEY,
            archive_name text NOT NULL UNIQUE,
            document jsonb NOT NULL
          )
          """.formatted(qualifiedTable(OBSERVATION_TYPE_CATALOG_TABLE)));
    }
  }

  private Map<String, String> loadExistingObservationTypes(Connection connection) throws SQLException {
    Map<String, String> result = new HashMap<>();
    String sql = "SELECT type_id, archive_name FROM " + qualifiedTable(OBSERVATION_TYPE_CATALOG_TABLE);
    try (PreparedStatement statement = connection.prepareStatement(sql);
         ResultSet resultSet = statement.executeQuery()) {
      while (resultSet.next()) {
        result.put(resultSet.getString("type_id"), resultSet.getString("archive_name"));
      }
    }
    return result;
  }

  private void ensureArchiveTable(Connection connection, String archiveName) throws SQLException {
    String safeArchiveName = requireIdentifier(archiveNameStrategy.sanitizeArchiveName(archiveName), "archive table");
    archiveTableInitializationTracker.ensureInitialized(safeArchiveName, () -> createArchiveTable(connection, safeArchiveName));
  }

  private void createArchiveTable(Connection connection, String safeArchiveName) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("""
          CREATE TABLE IF NOT EXISTS %s (
            id text PRIMARY KEY,
            run_id text NOT NULL,
            branch_id text NOT NULL,
            producer_model text NOT NULL,
            observation_type text NOT NULL,
            time jsonb NOT NULL,
            payload jsonb NOT NULL,
            document jsonb NOT NULL
          )
          """.formatted(qualifiedTable(safeArchiveName)));

      statement.execute("CREATE INDEX IF NOT EXISTS "
          + quotedIdentifier(archiveNameStrategy.auxiliaryName("idx", safeArchiveName + "_run_branch"))
          + " ON " + qualifiedTable(safeArchiveName) + " (run_id, branch_id)");
      statement.execute("CREATE INDEX IF NOT EXISTS "
          + quotedIdentifier(archiveNameStrategy.auxiliaryName("idx", safeArchiveName + "_run_branch_type"))
          + " ON " + qualifiedTable(safeArchiveName) + " (run_id, branch_id, observation_type)");
      statement.execute("CREATE INDEX IF NOT EXISTS "
          + quotedIdentifier(archiveNameStrategy.auxiliaryName("idx", safeArchiveName + "_payload_gin"))
          + " ON " + qualifiedTable(safeArchiveName) + " USING GIN (payload)");
    }
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Unable to serialize observation payload", e);
    }
  }

  private void notifyCompletion(String description, Throwable throwable) {
    if (throwable == null) {
      selfRef.tell(new WriteCompleted(description));
    } else {
      String reason = throwable.getMessage() == null ? throwable.getClass().getName() : throwable.getMessage();
      selfRef.tell(new WriteFailed(description, reason));
    }
  }

  private void incrementPendingWrites() {
    pendingWrites++;
  }

  private ObservationTypeEntry buildObservationTypeEntry(
      String typeId,
      String archiveName,
      Map<String, Object> metadata
  ) {
    return ObservationTypeEntry.builder()
        .typeId(typeId)
        .archiveName(archiveName)
        .build();
  }

  private void closeResources() {
    try {
      if (config.dropSchemaOnShutdown()) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
          statement.execute("DROP SCHEMA IF EXISTS " + quotedIdentifier(schema) + " CASCADE");
        }
      }
    } catch (SQLException e) {
      getContext().getLog().warn("Failed to drop schema {} on shutdown", schema, e);
    }

    databaseExecutor.shutdown();
    dataSource.close();
  }

  private static Throwable unwrapThrowable(Throwable throwable) {
    return throwable.getCause() == null ? throwable : throwable.getCause();
  }

  private static HikariDataSource buildDataSource(PostgresObservationConfig config) {
    HikariConfig hikariConfig = new HikariConfig();
    hikariConfig.setJdbcUrl(config.jdbcUrl());
    hikariConfig.setUsername(config.username());
    hikariConfig.setPassword(config.password());
    hikariConfig.setMaximumPoolSize(config.maximumPoolSize());
    hikariConfig.setPoolName("devs-observation-postgresql");
    hikariConfig.setAutoCommit(true);
    return new HikariDataSource(hikariConfig);
  }

  private String qualifiedTable(String tableName) {
    return quotedIdentifier(schema) + "." + quotedIdentifier(requireIdentifier(tableName, "table"));
  }

  private String requireSchema(String schemaName) {
    return requireIdentifier(schemaName, "schema");
  }

  private String requireIdentifier(String identifier, String label) {
    if (!archiveNameStrategy.isSafeIdentifier(identifier)) {
      throw new IllegalArgumentException("Unsafe PostgreSQL " + label + " identifier: " + identifier);
    }
    return identifier;
  }

  private static String quotedIdentifier(String identifier) {
    return '"' + identifier + '"';
  }

  /**
   * Reports that actor startup completed successfully and includes the known observation type mapping.
   *
   * @param knownObservationTypes logical observation types mapped to archive table names
   */
  private record InitializationSucceeded(Map<String, String> knownObservationTypes)
      implements DevsObservationMessage {
  }

  /**
   * Reports that actor startup failed before registration completed.
   *
   * @param reason startup failure description
   */
  private record InitializationFailed(String reason) implements DevsObservationMessage {
  }

  /**
   * Reports that an asynchronous database write completed successfully.
   *
   * @param description short description of the completed write
   */
  private record WriteCompleted(String description) implements DevsObservationMessage {
  }

  /**
   * Reports that an asynchronous database write failed.
   *
   * @param description short description of the failed write
   * @param reason failure description
   */
  private record WriteFailed(String description, String reason) implements DevsObservationMessage {
  }

  /**
   * Creates daemon threads for blocking PostgreSQL work off the actor thread.
   */
  private static class ObservationThreadFactory implements ThreadFactory {
    private int counter = 1;

    @Override
    public synchronized Thread newThread(Runnable runnable) {
      Thread thread = new Thread(runnable, "postgres-observation-" + counter++);
      thread.setDaemon(true);
      return thread;
    }
  }
}