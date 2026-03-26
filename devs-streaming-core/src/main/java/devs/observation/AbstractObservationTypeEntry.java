package devs.observation;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import devs.msg.DevsStyle;
import java.util.Map;
import org.immutables.value.Value;

/**
 * Metadata for a specific kind of observation.
 */
@Value.Immutable
@DevsStyle
@JsonSerialize(as = ObservationTypeEntry.class)
@JsonDeserialize(as = ObservationTypeEntry.class)
public abstract class AbstractObservationTypeEntry implements DevsObservationMessage {

  /**
   * Unique identifier (e.g., 'Observation_UnitPosition').
   * Mapped to _id for MongoDB compatibility.
   */
  @com.fasterxml.jackson.annotation.JsonProperty("_id")
  public abstract String getTypeId();

  /**
   * The name of the database table/collection (e.g., 'obs_unit_positions').
   */
  public abstract String getArchiveName();

  /**
   * Optional generic metadata for UI hints (e.g., displayName, category, icon).
   */
  @Value.Default
  public Map<String, Object> getMetadata() {
    return java.util.Collections.emptyMap();
  }
}
