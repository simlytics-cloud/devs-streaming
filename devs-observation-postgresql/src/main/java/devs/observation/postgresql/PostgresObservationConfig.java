package devs.observation.postgresql;

import com.typesafe.config.Config;

/**
 * PostgreSQL observation sink configuration loaded from HOCON.
 *
 * @param jdbcUrl JDBC connection URL for the target database
 * @param username database username used by the sink actor
 * @param password database password used by the sink actor
 * @param schema target schema where archive tables are created
 * @param dropSchemaOnShutdown whether the schema should be dropped when the actor stops
 * @param maximumPoolSize maximum Hikari connection pool size
 */
public record PostgresObservationConfig(
    String jdbcUrl,
    String username,
    String password,
    String schema,
    boolean dropSchemaOnShutdown,
    int maximumPoolSize
) {

  /**
   * Root HOCON path for PostgreSQL observation settings.
   */
  public static final String CONFIG_PATH = "devs.observation.postgresql";

  /**
   * Validates the normalized configuration values.
   */
  public PostgresObservationConfig {
    jdbcUrl = requireText(jdbcUrl, "jdbc-url");
    username = requireText(username, "username");
    password = password == null ? "" : password;
    schema = requireText(schema, "schema");
    if (maximumPoolSize < 1) {
      throw new IllegalArgumentException("maximum-pool-size must be at least 1");
    }
  }

  /**
   * Builds a PostgreSQL observation configuration from either the root config or the PostgreSQL sub-config.
   *
   * @param config application config or PostgreSQL observation sub-config
   * @return parsed PostgreSQL observation configuration
   */
  public static PostgresObservationConfig fromConfig(Config config) {
    Config postgresConfig = config.hasPath(CONFIG_PATH) ? config.getConfig(CONFIG_PATH) : config;
    return new PostgresObservationConfig(
        postgresConfig.getString("jdbc-url"),
        postgresConfig.getString("username"),
        postgresConfig.getString("password"),
        postgresConfig.getString("schema"),
        postgresConfig.getBoolean("drop-schema-on-shutdown"),
        postgresConfig.getInt("maximum-pool-size")
    );
  }

  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }
}