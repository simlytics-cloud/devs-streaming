package devs.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import devs.iso.time.LongSimTime;
import devs.msg.Branch;
import devs.msg.Run;
import devs.utils.DevsObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileObservationActorTest {

  private final ActorTestKit testKit = ActorTestKit.create();
  private final ObjectMapper objectMapper = DevsObjectMapper.buildObjectMapper();

  @AfterEach
  void shutdown() {
    testKit.shutdownTestKit();
  }

  @Test
  void appendsMessagesToTheirFilesAndCreatesCatalogEntries(@TempDir Path temporaryDirectory)
      throws Exception {
    Path outputDirectory = Files.createDirectory(temporaryDirectory.resolve("observations"));
    String observationType = "example.Value";
    ActorRef<DevsObservationMessage> actor = testKit.spawn(
        FileObservationActor.create(new FileObservationConfig(outputDirectory)));

    actor.tell(Run.builder()
        ._id("run-1")
        .name("file-run")
        .startTime(Instant.parse("2026-01-01T00:00:00Z"))
        .status(Run.RunStatus.RUNNING)
        .putConfig("mode", "test")
        .build());
    actor.tell(Branch.builder()
        ._id("branch-1")
        .runId("run-1")
        .forkTime(0.0)
        .build());
    actor.tell(Observation.<LongSimTime, Integer>builder()
        ._id("observation-1")
        .runId("run-1")
        .branchId("branch-1")
        .time(LongSimTime.create(4L))
        .producerModel("generator")
        .observationType(observationType)
        .payload(42)
        .build());
    actor.tell(ObservationTypeEntry.builder()
        .typeId("declared-type")
        .archiveName("declared-type")
        .build());
    actor.tell(StopLogger.builder().build());
    testKit.createTestProbe().expectTerminated(actor);

    assertEquals("run-1", readOnlyLine(outputDirectory.resolve(FileObservationActor.RUNS_FILE)).get("_id").textValue());
    assertEquals("branch-1",
        readOnlyLine(outputDirectory.resolve(FileObservationActor.BRANCHES_FILE)).get("_id").textValue());
    List<JsonNode> catalogEntries = Files.readAllLines(
        outputDirectory.resolve(FileObservationActor.OBSERVATION_TYPES_FILE)).stream()
        .map(line -> {
          try {
            return objectMapper.readTree(line);
          } catch (Exception exception) {
            throw new IllegalStateException(exception);
          }
        })
        .toList();
    assertEquals(List.of(observationType, "declared-type"),
        catalogEntries.stream().map(entry -> entry.get("_id").textValue()).toList());

    Path observationFile = outputDirectory.resolve(
        new ObservationFileNameStrategy().filenameForObservationType(observationType));
    JsonNode observation = readOnlyLine(observationFile);
    assertEquals("observation-1", observation.get("_id").textValue());
    assertEquals(42, observation.get("payload").intValue());
  }

  @Test
  void appendsAcrossActorLifecyclesWithoutDuplicatingKnownTypeCatalogEntries(@TempDir Path temporaryDirectory)
      throws Exception {
    Path outputDirectory = Files.createDirectory(temporaryDirectory.resolve("observations"));
    String type = "append-test";
    Observation<LongSimTime, String> observation = Observation.<LongSimTime, String>builder()
        ._id(UUID.randomUUID().toString())
        .runId("run")
        .branchId("branch")
        .time(LongSimTime.create(0L))
        .producerModel("model")
        .observationType(type)
        .payload("value")
        .build();

    ActorRef<DevsObservationMessage> first = testKit.spawn(
        FileObservationActor.create(new FileObservationConfig(outputDirectory)), "first");
    first.tell(observation);
    first.tell(StopLogger.builder().build());
    testKit.createTestProbe().expectTerminated(first);

    ActorRef<DevsObservationMessage> second = testKit.spawn(
        FileObservationActor.create(new FileObservationConfig(outputDirectory)), "second");
    second.tell(observation);
    second.tell(StopLogger.builder().build());
    testKit.createTestProbe().expectTerminated(second);

    assertEquals(1, Files.readAllLines(outputDirectory.resolve(FileObservationActor.OBSERVATION_TYPES_FILE)).size());
    Path observationFile = outputDirectory.resolve(new ObservationFileNameStrategy().filenameForObservationType(type));
    assertEquals(2, Files.readAllLines(observationFile).size());
  }

  @Test
  void rejectsMissingOutputDirectoryWithoutCreatingIt(@TempDir Path temporaryDirectory) {
    Path missingDirectory = temporaryDirectory.resolve("missing");

    ActorRef<DevsObservationMessage> actor = testKit.spawn(
        FileObservationActor.create(new FileObservationConfig(missingDirectory)));

    testKit.createTestProbe().expectTerminated(actor);
    assertFalse(Files.exists(missingDirectory));
  }

  @Test
  void rejectsRegularFileAsOutputDirectory(@TempDir Path temporaryDirectory) throws Exception {
    Path outputFile = Files.createFile(temporaryDirectory.resolve("observations.jsonl"));

    ActorRef<DevsObservationMessage> actor = testKit.spawn(
        FileObservationActor.create(new FileObservationConfig(outputFile)));

    testKit.createTestProbe().expectTerminated(actor);
  }

  @Test
  void createsSafeFilenameForHostileObservationTypes() {
    String filename = new ObservationFileNameStrategy().filenameForObservationType("../Unsafe/Type");

    assertFalse(filename.contains("/"));
    assertFalse(filename.contains("\\"));
    assertTrue(filename.endsWith(".jsonl"));
  }

  private JsonNode readOnlyLine(Path path) throws Exception {
    List<String> lines = Files.readAllLines(path);
    assertEquals(1, lines.size());
    return objectMapper.readTree(lines.getFirst());
  }
}
