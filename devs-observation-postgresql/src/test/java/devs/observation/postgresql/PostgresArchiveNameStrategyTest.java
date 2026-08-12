package devs.observation.postgresql;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PostgresArchiveNameStrategyTest {

  private final PostgresArchiveNameStrategy strategy = new PostgresArchiveNameStrategy();

  @Test
  void sanitizesSimpleTypeName() {
    Assertions.assertEquals("obs_temperature", strategy.archiveNameForObservationType("Temperature"));
  }

  @Test
  void sanitizesFullyQualifiedTypeName() {
    Assertions.assertEquals(
        "obs_com_example_measurement_state",
        strategy.archiveNameForObservationType("com.example.Measurement$State")
    );
  }

  @Test
  void sanitizesWeirdSymbols() {
    Assertions.assertEquals("obs_sensor_value", strategy.archiveNameForObservationType("sensor value!@#"));
  }

  @Test
  void prefixesTypesStartingWithDigits() {
    Assertions.assertEquals("obs__123temperature", strategy.archiveNameForObservationType("123temperature"));
  }

  @Test
  void shortensLongNamesSafely() {
    String archiveName = strategy.archiveNameForObservationType(
        "ExtremelyLongObservationTypeNameThatShouldBeShortenedToFitWithinPostgresqlIdentifierLimits"
            + "AndStillRemainStableAcrossRuns"
    );

    Assertions.assertTrue(archiveName.startsWith("obs_"));
    Assertions.assertTrue(archiveName.length() <= PostgresArchiveNameStrategy.MAX_IDENTIFIER_LENGTH);
    Assertions.assertTrue(strategy.isSafeIdentifier(archiveName));
  }
}