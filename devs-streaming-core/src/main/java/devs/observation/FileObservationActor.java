package devs.observation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import devs.msg.Branch;
import devs.msg.Run;
import devs.utils.DevsObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.receptionist.Receptionist;

/**
 * Observation sink actor that writes append-only JSON Lines files to a configured directory.
 */
public final class FileObservationActor extends AbstractBehavior<DevsObservationMessage> {

  public static final String RUNS_FILE = "runs.jsonl";
  public static final String BRANCHES_FILE = "branches.jsonl";
  public static final String OBSERVATION_TYPES_FILE = "observation_types.jsonl";

  private final ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();
  private final ObservationFileNameStrategy filenameStrategy;
  private final Path outputDirectory;
  private final Set<String> knownObservationTypes;

  /**
   * Creates a file-backed observation sink behavior from parsed configuration.
   *
   * @param config file sink configuration
   * @return configured observation sink behavior
   */
  public static Behavior<DevsObservationMessage> create(FileObservationConfig config) {
    return Behaviors.setup(context -> new FileObservationActor(
        context, config, new ObservationFileNameStrategy()));
  }

  /**
   * Creates a file-backed observation sink behavior from HOCON configuration.
   *
   * @param config root application config or file observation sub-config
   * @return configured observation sink behavior
   */
  public static Behavior<DevsObservationMessage> create(Config config) {
    return create(FileObservationConfig.fromConfig(config));
  }

  FileObservationActor(
      ActorContext<DevsObservationMessage> context,
      FileObservationConfig config,
      ObservationFileNameStrategy filenameStrategy
  ) {
    super(context);
    this.filenameStrategy = filenameStrategy;
    this.outputDirectory = validateOutputDirectory(config.outputDirectory());
    this.knownObservationTypes = loadKnownObservationTypes();
    context.getSystem().receptionist().tell(
        Receptionist.register(ObservationSinkKeys.OBSERVATION_SINK_KEY, context.getSelf()));
    context.getLog().info("Initialized file observation actor at {}", outputDirectory);
  }

  @Override
  public Receive<DevsObservationMessage> createReceive() {
    return newReceiveBuilder()
        .onMessage(Observation.class, this::onObservation)
        .onMessage(Run.class, this::onRun)
        .onMessage(Branch.class, this::onBranch)
        .onMessage(ObservationTypeEntry.class, this::onObservationTypeEntry)
        .onMessage(StopLogger.class, ignored -> Behaviors.stopped())
        .build();
  }

  private Behavior<DevsObservationMessage> onObservation(Observation<?, ?> observation) {
    String observationType = observation.getObservationType();
    if (knownObservationTypes.add(observationType)) {
      append(ObservationTypeEntry.builder()
          .typeId(observationType)
          .archiveName(observationType)
          .build(), OBSERVATION_TYPES_FILE);
    }
    append(observation, filenameStrategy.filenameForObservationType(observationType));
    return Behaviors.same();
  }

  private Behavior<DevsObservationMessage> onRun(Run run) {
    append(run, RUNS_FILE);
    return Behaviors.same();
  }

  private Behavior<DevsObservationMessage> onBranch(Branch branch) {
    append(branch, BRANCHES_FILE);
    return Behaviors.same();
  }

  private Behavior<DevsObservationMessage> onObservationTypeEntry(ObservationTypeEntry entry) {
    knownObservationTypes.add(entry.getTypeId());
    append(entry, OBSERVATION_TYPES_FILE);
    return Behaviors.same();
  }

  private Path validateOutputDirectory(Path configuredDirectory) {
    Path normalizedDirectory = configuredDirectory.toAbsolutePath().normalize();
    if (!Files.exists(normalizedDirectory)) {
      throw new IllegalArgumentException("Observation output directory does not exist: " + normalizedDirectory);
    }
    if (!Files.isDirectory(normalizedDirectory)) {
      throw new IllegalArgumentException("Observation output path is not a directory: " + normalizedDirectory);
    }
    if (!Files.isWritable(normalizedDirectory)) {
      throw new IllegalArgumentException("Observation output directory is not writable: " + normalizedDirectory);
    }
    return normalizedDirectory;
  }

  private Set<String> loadKnownObservationTypes() {
    Path catalog = resolve(OBSERVATION_TYPES_FILE);
    if (!Files.exists(catalog)) {
      return new HashSet<>();
    }

    Set<String> types = new HashSet<>();
    try (Stream<String> lines = Files.lines(catalog, StandardCharsets.UTF_8)) {
      lines.filter(line -> !line.isBlank()).forEach(line -> {
        try {
          JsonNode entry = objectMapper.readTree(line);
          JsonNode typeId = entry.get("_id");
          if (typeId != null && typeId.isTextual()) {
            types.add(typeId.textValue());
          } else {
            getContext().getLog().warn("Ignoring observation type catalog entry without _id in {}", catalog);
          }
        } catch (JsonProcessingException exception) {
          getContext().getLog().warn("Ignoring malformed observation type catalog entry in {}", catalog,
              exception);
        }
      });
      return types;
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to read observation type catalog: " + catalog, exception);
    }
  }

  private void append(DevsObservationMessage message, String filename) {
    Path target = resolve(filename);
    try {
      Files.writeString(
          target,
          objectMapper.writeValueAsString(message) + "\n",
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND
      );
      getContext().getLog().debug("Appended {} to {}", message.getClass().getSimpleName(), target);
    } catch (IOException exception) {
      getContext().getLog().error("Unable to append {} to {}", message.getClass().getSimpleName(), target,
          exception);
    }
  }

  private Path resolve(String filename) {
    Path target = outputDirectory.resolve(filename).normalize();
    if (!target.getParent().equals(outputDirectory)) {
      throw new IllegalArgumentException("Observation filename escapes output directory: " + filename);
    }
    return target;
  }
}
