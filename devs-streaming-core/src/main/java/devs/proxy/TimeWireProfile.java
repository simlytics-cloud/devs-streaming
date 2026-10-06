package devs.proxy;

/** Wire policy for message time fields and schedule keys. */
public enum TimeWireProfile {
  /** Numeric long and double timestamps without an exact-time envelope marker. */
  LEGACY_NUMERIC_V1,
  /** Supports tagged exact timestamps alongside numeric long and double timestamps. */
  EXACT_TIME_V1
}