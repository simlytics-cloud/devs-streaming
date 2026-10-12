package devs.time;

import devs.iso.time.DecimalSimTime;

/** Exact decimal codec for decimal-native enhanced clocks. */
public final class DecimalTimeCodec implements NativeTimeCodec<DecimalSimTime> {
  @Override public DecimalSimTime decode(DecimalSimTime value) { return value; }
  @Override public DecimalSimTime encode(DecimalSimTime value) { return value; }
}
