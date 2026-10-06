package devs.time;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/** A normalized, exact rational number. */
public final class Rational implements Comparable<Rational> {
  /** The exact additive identity. */
  public static final Rational ZERO = of(0);
  /** The exact multiplicative identity. */
  public static final Rational ONE = of(1);

  private final BigInteger numerator;
  private final BigInteger denominator;

  private Rational(BigInteger numerator, BigInteger denominator) {
    if (denominator.signum() == 0) {
      throw new ArithmeticException("Zero denominator");
    }
    BigInteger gcd = numerator.gcd(denominator);
    if (denominator.signum() < 0) {
      gcd = gcd.negate();
    }
    this.numerator = numerator.divide(gcd);
    this.denominator = denominator.divide(gcd);
  }

  /** Returns the exact integer value with denominator one. */
  public static Rational of(long value) {
    return of(BigInteger.valueOf(value), BigInteger.ONE);
  }

  /**
   * Returns the fraction reduced to lowest terms with a positive denominator.
   * @throws ArithmeticException if the denominator is zero
   */
  public static Rational of(long numerator, long denominator) {
    return of(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
  }

  /**
   * Returns the fraction reduced to lowest terms with a positive denominator.
   * @throws NullPointerException if either argument is null
   * @throws ArithmeticException if the denominator is zero
   */
  public static Rational of(BigInteger numerator, BigInteger denominator) {
    return new Rational(Objects.requireNonNull(numerator), Objects.requireNonNull(denominator));
  }

  /**
   * Converts the decimal's unscaled value and scale exactly, without rounding.
   * @throws ArithmeticException if a negative scale cannot be negated as an int
   */
  public static Rational of(BigDecimal value) {
    int scale = value.scale();
    return scale >= 0 ? of(value.unscaledValue(), BigInteger.TEN.pow(scale))
        : of(value.unscaledValue().multiply(BigInteger.TEN.pow(Math.negateExact(scale))), BigInteger.ONE);
  }

  /** Decodes the binary value, not the shortest decimal rendering of a double. */
  public static Rational of(double value) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("A rational must be finite");
    }
    long bits = Double.doubleToRawLongBits(value);
    int exponent = (int) ((bits >>> 52) & 0x7ff);
    long significand = bits & 0xfffffffffffffL;
    if (exponent != 0) {
      significand |= 1L << 52;
    }
    BigInteger n = BigInteger.valueOf(significand);
    if (bits < 0) {
      n = n.negate();
    }
    int shift = exponent == 0 ? -1074 : exponent - 1075;
    return shift >= 0 ? of(n.shiftLeft(shift), BigInteger.ONE)
        : of(n, BigInteger.ONE.shiftLeft(-shift));
  }

  /** Returns the signed numerator of the reduced fraction. */
  public BigInteger numerator() {
    return numerator;
  }

  /** Returns the strictly positive denominator of the reduced fraction. */
  public BigInteger denominator() {
    return denominator;
  }

  /** Returns the exact sum, without rounding or fixed-width overflow. */
  public Rational add(Rational operand) {
    return of(numerator.multiply(operand.denominator).add(operand.numerator.multiply(denominator)),
        denominator.multiply(operand.denominator));
  }

  /** Returns the exact difference. */
  public Rational subtract(Rational operand) {
    return add(operand.negate());
  }

  /** Returns the exact product. */
  public Rational multiply(Rational operand) {
    return of(numerator.multiply(operand.numerator), denominator.multiply(operand.denominator));
  }

  /**
   * Returns the exact quotient.
   * @throws ArithmeticException if the operand is zero
   */
  public Rational divide(Rational operand) {
    return of(numerator.multiply(operand.denominator), denominator.multiply(operand.numerator));
  }

  /** Returns the additive inverse. */
  public Rational negate() {
    return of(numerator.negate(), denominator);
  }

  /** Returns -1, 0, or 1 for a negative, zero, or positive value, respectively. */
  public int signum() {
    return numerator.signum();
  }

  /** Returns whether the reduced denominator is one. */
  public boolean isInteger() {
    return denominator.equals(BigInteger.ONE);
  }

  /** Returns the least integer greater than or equal to this value, including for negatives. */
  public BigInteger ceil() {
    BigInteger[] parts = numerator.divideAndRemainder(denominator);
    return parts[1].signum() > 0 ? parts[0].add(BigInteger.ONE) : parts[0];
  }

  /** Returns the greatest integer less than or equal to this value, including for negatives. */
  public BigInteger floor() {
    return negate().ceil().negate();
  }

  /**
   * Tests membership in {@code origin + k * quantum} for any integer {@code k}.
   * All arguments use the same units as this value; quantum is a mathematical grid spacing,
   * not a model declaration.
   * @throws IllegalArgumentException if quantum is not strictly positive
   */
  public boolean isOnLattice(Rational origin, Rational quantum) {
    requirePositive(quantum);
    return subtract(origin).divide(quantum).isInteger();
  }

  /**
   * Returns the least point on {@code origin + k * quantum} not below this value.
   * A value already on the lattice is unchanged; all arguments use the same units.
   * @throws IllegalArgumentException if quantum is not strictly positive
   */
  public Rational ceilToLattice(Rational origin, Rational quantum) {
    requirePositive(quantum);
    return origin.add(quantum.multiply(of(subtract(origin).divide(quantum).ceil(), BigInteger.ONE)));
  }

  /** Rejects zero or negative units and lattice spacing. */
  static void requirePositive(Rational value) {
    if (value.signum() <= 0) {
      throw new IllegalArgumentException("Scale and spacing must be positive");
    }
  }

  /** Least lattice point >= lower also on the encoding grid; null if the grids never meet. */
  static Rational intersectionCeiling(Rational lower, Rational origin, Rational quantum,
      Rational encodingQuantum) {
    requirePositive(encodingQuantum);
    if (quantum == null) {
      return lower.ceilToLattice(ZERO, encodingQuantum);
    }
    requirePositive(quantum);
    Rational a = origin.divide(encodingQuantum);
    Rational q = quantum.divide(encodingQuantum);
    BigInteger scale = a.denominator.multiply(q.denominator)
        .divide(a.denominator.gcd(q.denominator));
    BigInteger offset = a.numerator.multiply(scale.divide(a.denominator));
    BigInteger step = q.numerator.multiply(scale.divide(q.denominator));
    BigInteger gcd = step.gcd(scale);
    if (!offset.mod(gcd).equals(BigInteger.ZERO)) {
      return null;
    }
    BigInteger period = scale.divide(gcd);
    BigInteger first = period.equals(BigInteger.ONE) ? BigInteger.ZERO
        : offset.negate().divide(gcd).multiply(step.divide(gcd).modInverse(period)).mod(period);
    Rational base = origin.add(quantum.multiply(of(first, BigInteger.ONE)));
    return lower.ceilToLattice(base, quantum.multiply(of(period, BigInteger.ONE)));
  }

  /**
   * Returns an exact decimal, without a rounding context.
   * @throws ArithmeticException if the fraction has a nonterminating decimal expansion
   */
  public BigDecimal toBigDecimal() {
    return new BigDecimal(numerator).divide(new BigDecimal(denominator));
  }

  /** Compares exact numerical values by cross multiplication. */
  @Override
  public int compareTo(Rational operand) {
    return numerator.multiply(operand.denominator).compareTo(operand.numerator.multiply(denominator));
  }

  /** Returns whether the object is a rational with the same exact value. */
  @Override
  public boolean equals(Object object) {
    return object instanceof Rational other && numerator.equals(other.numerator)
        && denominator.equals(other.denominator);
  }

  /** Returns a hash of the normalized numerator and denominator. */
  @Override
  public int hashCode() {
    return Objects.hash(numerator, denominator);
  }

  /** Returns an integer string or a reduced {@code numerator/denominator} string. */
  @Override
  public String toString() {
    return isInteger() ? numerator.toString() : numerator + "/" + denominator;
  }
}