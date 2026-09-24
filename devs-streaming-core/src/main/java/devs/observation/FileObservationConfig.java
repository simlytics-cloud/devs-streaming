package devs.observation;

import com.typesafe.config.Config;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Configuration for {@link FileObservationActor}.
 *
 * @param outputDirectory existing directory where observation files are written
 */
public record FileObservationConfig(Path outputDirectory) {

  /**
   * Root HOCON path for file observation settings.
   */
  public static final String CONFIG_PATH = "devs.observation.file";

  /**
   * Validates the configured output directory path.
   */
  public FileObservationConfig {
    outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");
    if (outputDirectory.toString().isBlank()) {
      throw new IllegalArgumentException("outputDirectory must not be blank");
    }
  }

  /**
   * Builds a file observation configuration from either root application config or its sub-config.
   *
   * @param config application config or file observation sub-config
   * @return parsed file observation configuration
   */
  public static FileObservationConfig fromConfig(Config config) {
    Config fileConfig = config.hasPath(CONFIG_PATH) ? config.getConfig(CONFIG_PATH) : config;
    return new FileObservationConfig(Path.of(fileConfig.getString("output-directory")));
  }
}
