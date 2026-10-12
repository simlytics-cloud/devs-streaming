package devs.time;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Decimal fixtures used by migrated tests. */
public final class DecimalTestValues {
  private DecimalTestValues() {}
  public static BigDecimal of(long value) { return BigDecimal.valueOf(value); }
  public static BigDecimal of(double value) { return BigDecimal.valueOf(value); }
  public static BigDecimal of(BigDecimal value) { return value; }
  public static BigDecimal of(BigInteger value) { return new BigDecimal(value); }
  public static BigDecimal of(long numerator, long denominator) {
    return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator));
  }
  public static BigDecimal of(BigInteger numerator, BigInteger denominator) {
    return new BigDecimal(numerator).divide(new BigDecimal(denominator));
  }
}
