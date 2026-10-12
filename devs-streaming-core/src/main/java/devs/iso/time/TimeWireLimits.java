package devs.iso.time;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Fixed parsing and serialization resource limits for {@code devs-exact-time-v1} timestamps.
 *
 * <p>These checks bound numeric text and JSON fragments before expensive arbitrary-precision
 * operations. They apply to wire representations, not to local {@link DecimalSimTime} or
 * construction. Readers and writers share the limits so accepted exact
 * values can be serialized within the same resource envelope.
 */
public final class TimeWireLimits {
  /** Maximum decimal coefficient digits. */
  public static final int MAX_DIGITS = 4096;
  /** Maximum absolute decimal scale and magnitude of an exponent in accepted decimal text. */
  public static final int MAX_SCALE = 10000;
  /** Maximum timestamp or key fragment length, measured in source characters or bytes. */
  public static final int MAX_FRAGMENT = 8256;

  private TimeWireLimits() {}

  /**
   * Creates the standard error for an invalid timestamp shape or numeric spelling.
   *
   * @return an exception with the {@code malformed-time} error prefix
   */
  public static IllegalArgumentException malformed() {
    return new IllegalArgumentException("malformed-time: invalid timestamp shape or text");
  }

  /**
   * Creates the standard error for an exact timestamp exceeding the resource envelope.
   *
   * @return an exception with the {@code time-limit-exceeded} error prefix
   */
  public static IllegalArgumentException exceeded() {
    return new IllegalArgumentException("time-limit-exceeded: exact-v1 timestamp exceeds resource limits");
  }

  /**
   * Checks decoded text length without validating its numeric grammar or escaped source spelling.
   *
   * @param value the non-null timestamp text or schedule key
   * @throws IllegalArgumentException if the text exceeds {@link #MAX_FRAGMENT}
   */
  public static void text(String value) {
    if (value.length() > MAX_FRAGMENT) {
      throw exceeded();
    }
  }

  /**
   * Parses bounded decimal text without rounding and removes trailing zeros.
   *
   * <p>The spelling allows an optional minus sign, an integer part without redundant leading
   * zeros, an optional fractional part, and an optional signed exponent. Digit, scale, exponent,
   * and normalized output constraints are checked before returning the value.
   *
   * @param value the non-null finite decimal spelling
   * @return the normalized exact decimal
   * @throws IllegalArgumentException if the spelling is malformed or wire limits are exceeded
   */
  public static BigDecimal decimal(String value) {
    text(value);
    int i = value.startsWith("-") ? 1 : 0;
    int integerStart = i;
    while (i < value.length() && digit(value.charAt(i))) {
      i++;
    }
    int integerDigits = i - integerStart;
    if (integerDigits == 0 || integerDigits > 1 && value.charAt(integerStart) == '0') {
      throw malformed();
    }
    int fractionalDigits = 0;
    if (i < value.length() && value.charAt(i) == '.') {
      int start = ++i;
      while (i < value.length() && digit(value.charAt(i))) {
        i++;
      }
      fractionalDigits = i - start;
      if (fractionalDigits == 0) {
        throw malformed();
      }
    }
    if (integerDigits + fractionalDigits > MAX_DIGITS) {
      throw exceeded();
    }
    int coefficientEnd = i;
    int exponent = 0;
    if (i < value.length() && (value.charAt(i) == 'e' || value.charAt(i) == 'E')) {
      i++;
      boolean negative = i < value.length() && value.charAt(i) == '-';
      if (i < value.length() && (value.charAt(i) == '+' || negative)) {
        i++;
      }
      int start = i;
      while (i < value.length() && digit(value.charAt(i))) {
        exponent = exponent * 10 + value.charAt(i++) - '0';
        if (exponent > MAX_SCALE) {
          throw exceeded();
        }
      }
      if (start == i) {
        throw malformed();
      }
      if (negative) {
        exponent = -exponent;
      }
    }
    if (i != value.length()) {
      throw malformed();
    }
    if (Math.abs(fractionalDigits - exponent) > MAX_SCALE) {
      throw exceeded();
    }
    int firstNonzero = -1;
    int lastNonzero = -1;
    int coefficientDigits = 0;
    for (int position = integerStart; position < coefficientEnd; position++) {
      char c = value.charAt(position);
      if (c != '.') {
        if (c != '0') {
          if (firstNonzero < 0) {
            firstNonzero = coefficientDigits;
          }
          lastNonzero = coefficientDigits;
        }
        coefficientDigits++;
      }
    }
    if (lastNonzero >= 0) {
      int scale = fractionalDigits - exponent - (coefficientDigits - 1 - lastNonzero);
      int precision = lastNonzero - firstNonzero + 1;
      int adjustedExponent = precision - scale - 1;
      if (Math.abs(scale) > MAX_SCALE || (scale < 0 || adjustedExponent < -6)
          && Math.abs(adjustedExponent) > MAX_SCALE) {
        throw exceeded();
      }
    }
    BigDecimal parsed;
    try {
      parsed = new BigDecimal(value);
    } catch (NumberFormatException exception) {
      throw malformed();
    }
    BigDecimal normalized = parsed.stripTrailingZeros();
    validateDecimal(normalized);
    return normalized;
  }

  /**
   * Parses bounded integer text without redundant leading zeros or a plus sign.
   *
   * @param value the non-null integer spelling
   * @param denominator true to require a strictly positive denominator; false to allow a signed
   *     numerator, including zero
   * @return the exact integer
   * @throws IllegalArgumentException if the spelling is malformed or wire limits are exceeded
   */
  public static BigInteger integer(String value, boolean denominator) {
    text(value);
    int start = !denominator && value.startsWith("-") ? 1 : 0;
    int digits = value.length() - start;
    if (digits > MAX_DIGITS) {
      throw exceeded();
    }
    if (digits == 0 || digits > 1 && value.charAt(start) == '0'
        || denominator && value.charAt(start) == '0') {
      throw malformed();
    }
    for (int i = start; i < value.length(); i++) {
      if (!digit(value.charAt(i))) {
        throw malformed();
      }
    }
    return new BigInteger(value);
  }

  /**
   * Checks a decimal's precision, scale, and exponent needed by its string representation.
   *
   * @param value the non-null decimal, normally already stripped of trailing zeros
   * @throws IllegalArgumentException if the decimal exceeds wire numeric limits
   */
  public static void validateDecimal(BigDecimal value) {
    if (value.precision() > MAX_DIGITS || Math.abs((long) value.scale()) > MAX_SCALE) {
      throw exceeded();
    }
    long adjustedExponent = (long) value.precision() - value.scale() - 1;
    if ((value.scale() < 0 || adjustedExponent < -6)
        && Math.abs(adjustedExponent) > MAX_SCALE) {
      throw exceeded();
    }
  }

  /**
   * Checks an integer's decimal digit count, ignoring its sign. This does not check denominator
   * positivity; {@link #integer(String, boolean)} enforce that rule.
   *
   * @param value the non-null integer
   * @throws IllegalArgumentException if the integer exceeds {@link #MAX_DIGITS}
   */
  public static void validateInteger(BigInteger value) {
    // Avoid rendering a potentially enormous local value before rejecting it.
    if (value.bitLength() > 13607) {
      throw exceeded();
    }
    if (value.abs().toString().length() > MAX_DIGITS) {
      throw exceeded();
    }
  }

  private static boolean digit(char c) {
    return c >= '0' && c <= '9';
  }
}