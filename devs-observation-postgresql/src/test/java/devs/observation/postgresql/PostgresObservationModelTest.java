package devs.observation.postgresql;

import com.typesafe.config.ConfigFactory;
import devs.msg.Branch;
import devs.msg.Run;
import devs.observation.DevsObservationMessage;
import devs.observation.Observation;
import devs.observation.ObservationSinkKeys;
import devs.observation.StopLogger;
import devs.iso.time.LongSimTime;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.receptionist.Receptionist;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Disabled("Requires PostgreSQL instance and configured application.conf")
class PostgresObservationModelTest {

  private static final ActorTestKit TEST_KIT = ActorTestKit.create();

  private final PostgresObservationConfig config =
      PostgresObservationConfig.fromConfig(ConfigFactory.load());
  private final PostgresArchiveNameStrategy archiveNameStrategy = new PostgresArchiveNameStrategy();

  @BeforeEach
  void resetSchema() throws SQLException {
    ensureDatabaseExists();
    try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
      statement.execute("DROP SCHEMA IF EXISTS \"" + config.schema() + "\" CASCADE");
      statement.execute("CREATE SCHEMA IF NOT EXISTS \"" + config.schema() + "\"");
    }
  }

  @AfterAll
  static void shutdown() {
    TEST_KIT.shutdownTestKit();
  }

  @Test
  void persistsRunsBranchesTypesAndObservations() throws Exception {
    String runId = UUID.randomUUID().toString();
    String branchId = UUID.randomUUID().toString();
    String integerArchive = archiveNameStrategy.archiveNameForObservationType("Integer");
    String stringArchive = archiveNameStrategy.archiveNameForObservationType("String");

    ActorRef<DevsObservationMessage> actor = TEST_KIT.spawn(PostgresObservationActor.create(config));
    awaitRegistration();

    actor.tell(Run.builder()
        ._id(runId)
        .name("postgres-run")
        .startTime(Instant.now())
        .status(Run.RunStatus.RUNNING)
        .putConfig("mode", "integration")
        .build());

    actor.tell(Branch.builder()
        ._id(branchId)
        .runId(runId)
        .forkTime(0.0)
        .description("root")
        .build());

    actor.tell(Observation.<LongSimTime, Integer>builder()
        ._id(UUID.randomUUID().toString())
        .runId(runId)
        .branchId(branchId)
        .time(LongSimTime.create(0L))
        .producerModel("generator")
        .observationType("Integer")
        .payload(1)
        .build());

    actor.tell(Observation.<LongSimTime, String>builder()
        ._id(UUID.randomUUID().toString())
        .runId(runId)
        .branchId(branchId)
        .time(LongSimTime.create(1L))
        .producerModel("storage")
        .observationType("String")
        .payload("ready")
        .build());

    actor.tell(StopLogger.builder().build());
    TEST_KIT.createTestProbe().expectTerminated(actor, Duration.ofSeconds(10));

    Assertions.assertTrue(tableExists("runs"));
    Assertions.assertTrue(tableExists("branches"));
    Assertions.assertTrue(tableExists("observation_type_catalog"));
    Assertions.assertTrue(tableExists(integerArchive));
    Assertions.assertTrue(tableExists(stringArchive));

    Assertions.assertEquals(1, countRows("runs"));
    Assertions.assertEquals(1, countRows("branches"));
    Assertions.assertEquals(2, countRows("observation_type_catalog"));
    Assertions.assertEquals(1, countRows(integerArchive));
    Assertions.assertEquals(1, countRows(stringArchive));

    Set<String> types = loadObservationTypeIds();
    Assertions.assertEquals(Set.of("Integer", "String"), types);
  }

  @Test
  void reinitializationDoesNotFailOnDuplicateObservationType() throws Exception {
    String archiveName = archiveNameStrategy.archiveNameForObservationType("TestType");
    Observation<LongSimTime, String> observation = Observation.<LongSimTime, String>builder()
        ._id(UUID.randomUUID().toString())
        .runId("run-1")
        .branchId("branch-1")
        .time(LongSimTime.create(0L))
        .producerModel("model-1")
        .observationType("TestType")
        .payload("value")
        .build();

    ActorRef<DevsObservationMessage> actor1 = TEST_KIT.spawn(PostgresObservationActor.create(config));
    awaitRegistration();
    actor1.tell(observation);
    actor1.tell(StopLogger.builder().build());
    TEST_KIT.createTestProbe().expectTerminated(actor1, Duration.ofSeconds(10));

    ActorRef<DevsObservationMessage> actor2 = TEST_KIT.spawn(PostgresObservationActor.create(config));
    awaitRegistration();
    actor2.tell(observation);
    actor2.tell(StopLogger.builder().build());
    TEST_KIT.createTestProbe().expectTerminated(actor2, Duration.ofSeconds(10));

    Assertions.assertEquals(1, countRows("observation_type_catalog"));
    Assertions.assertEquals(1, countRows(archiveName));
  }

  private void awaitRegistration() {
    TEST_KIT.createTestProbe().awaitAssert(() -> {
      org.apache.pekko.actor.testkit.typed.javadsl.TestProbe<Receptionist.Listing> probe =
          TEST_KIT.createTestProbe(Receptionist.Listing.class);
      TEST_KIT.system().receptionist().tell(
          Receptionist.find(ObservationSinkKeys.OBSERVATION_SINK_KEY, probe.getRef()));
      Receptionist.Listing listing = probe.receiveMessage();
      Assertions.assertFalse(
          listing.getServiceInstances(ObservationSinkKeys.OBSERVATION_SINK_KEY).isEmpty(),
          "Actor not registered yet"
      );
      return null;
    });
  }

  private void ensureDatabaseExists() throws SQLException {
    String databaseName = databaseNameFromJdbcUrl(config.jdbcUrl());
    if (databaseName == null || databaseName.equalsIgnoreCase("postgres")) {
      return;
    }

    String existsSql = "SELECT 1 FROM pg_database WHERE datname = ?";
    try (Connection connection = openAdminConnection();
         PreparedStatement statement = connection.prepareStatement(existsSql)) {
      statement.setString(1, databaseName);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (resultSet.next()) {
          return;
        }
      }
    }

    try (Connection connection = openAdminConnection(); Statement statement = connection.createStatement()) {
      statement.execute("CREATE DATABASE " + quotedIdentifier(databaseName));
    }
  }

  private Connection openConnection() throws SQLException {
    return DriverManager.getConnection(config.jdbcUrl(), config.username(), config.password());
  }

  private Connection openAdminConnection() throws SQLException {
    return DriverManager.getConnection(adminJdbcUrl(config.jdbcUrl()), config.username(), config.password());
  }

  private static String adminJdbcUrl(String jdbcUrl) {
    int databaseStart = jdbcUrl.lastIndexOf('/');
    if (databaseStart < 0) {
      throw new IllegalArgumentException("JDBC URL does not include a database name: " + jdbcUrl);
    }

    int queryStart = jdbcUrl.indexOf('?', databaseStart);
    String suffix = queryStart >= 0 ? jdbcUrl.substring(queryStart) : "";
    return jdbcUrl.substring(0, databaseStart + 1) + "postgres" + suffix;
  }

  private static String databaseNameFromJdbcUrl(String jdbcUrl) {
    int databaseStart = jdbcUrl.lastIndexOf('/');
    if (databaseStart < 0 || databaseStart == jdbcUrl.length() - 1) {
      return null;
    }

    int queryStart = jdbcUrl.indexOf('?', databaseStart);
    if (queryStart >= 0) {
      return jdbcUrl.substring(databaseStart + 1, queryStart);
    }
    return jdbcUrl.substring(databaseStart + 1);
  }

  private static String quotedIdentifier(String identifier) {
    return '"' + identifier.replace("\"", "\"\"") + '"';
  }

  private boolean tableExists(String tableName) throws SQLException {
    String sql = "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?)";
    try (Connection connection = openConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, config.schema());
      statement.setString(2, tableName);
      try (ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        return resultSet.getBoolean(1);
      }
    }
  }

  private int countRows(String tableName) throws SQLException {
    String sql = "SELECT COUNT(*) FROM \"" + config.schema() + "\".\"" + tableName + "\"";
    try (Connection connection = openConnection();
         PreparedStatement statement = connection.prepareStatement(sql);
         ResultSet resultSet = statement.executeQuery()) {
      resultSet.next();
      return resultSet.getInt(1);
    }
  }

  private Set<String> loadObservationTypeIds() throws SQLException {
    String sql = "SELECT type_id FROM \"" + config.schema() + "\".\"observation_type_catalog\"";
    Set<String> typeIds = new HashSet<>();
    try (Connection connection = openConnection();
         PreparedStatement statement = connection.prepareStatement(sql);
         ResultSet resultSet = statement.executeQuery()) {
      while (resultSet.next()) {
        typeIds.add(resultSet.getString(1));
      }
    }
    return typeIds;
  }
}