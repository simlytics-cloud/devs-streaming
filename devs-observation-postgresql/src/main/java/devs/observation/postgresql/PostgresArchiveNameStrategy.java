package devs.observation.postgresql;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Produces PostgreSQL-safe archive and auxiliary identifier names.
 */
public class PostgresArchiveNameStrategy {

  /**
   * Maximum identifier length supported by PostgreSQL.
   */
  public static final int MAX_IDENTIFIER_LENGTH = 63;

  private static final Pattern INVALID_CHARACTERS = Pattern.compile("[^a-z0-9_]");
  private static final Pattern MULTIPLE_UNDERSCORES = Pattern.compile("_+");
  private static final Pattern VALID_IDENTIFIER =
      Pattern.compile("[a-z_][a-z0-9_]{0," + (MAX_IDENTIFIER_LENGTH - 1) + "}");

  /**
   * Returns the archive table name for a logical observation type.
   *
   * @param observationType logical observation type identifier
   * @return sanitized PostgreSQL table name
   */
  public String archiveNameForObservationType(String observationType) {
    return sanitizeArchiveName(observationType);
  }

  /**
   * Sanitizes an archive table name candidate for safe PostgreSQL use.
   *
   * @param candidate proposed archive name
   * @return PostgreSQL-safe archive name prefixed with `obs_`
   */
  public String sanitizeArchiveName(String candidate) {
    String normalized = normalize(candidate);
    if (!normalized.startsWith("obs_")) {
      normalized = "obs_" + normalized;
    }
    return shortenIfNeeded(normalized);
  }

  /**
   * Builds a sanitized auxiliary identifier, such as an index name, with the supplied prefix.
   *
   * @param prefix identifier prefix to apply
   * @param candidate identifier body to sanitize
   * @return PostgreSQL-safe auxiliary identifier
   */
  public String auxiliaryName(String prefix, String candidate) {
    String normalizedPrefix = normalizePrefix(prefix);
    String normalized = normalize(candidate);
    if (!normalized.startsWith(normalizedPrefix)) {
      normalized = normalizedPrefix + normalized;
    }
    return shortenIfNeeded(normalized);
  }

  /**
   * Checks whether an identifier already satisfies the strategy's PostgreSQL safety rules.
   *
   * @param identifier identifier to validate
   * @return {@code true} when the identifier can be used safely in generated SQL
   */
  public boolean isSafeIdentifier(String identifier) {
    return identifier != null && VALID_IDENTIFIER.matcher(identifier).matches();
  }

  private String normalizePrefix(String prefix) {
    String normalizedPrefix = normalize(prefix);
    if (!normalizedPrefix.endsWith("_")) {
      normalizedPrefix = normalizedPrefix + "_";
    }
    return normalizedPrefix;
  }

  private String normalize(String value) {
    String candidate = Objects.requireNonNullElse(value, "").toLowerCase(Locale.ROOT);
    candidate = INVALID_CHARACTERS.matcher(candidate).replaceAll("_");
    candidate = MULTIPLE_UNDERSCORES.matcher(candidate).replaceAll("_");
    candidate = candidate.replaceAll("^_+", "");
    candidate = candidate.replaceAll("_+$", "");
    if (candidate.isBlank()) {
      candidate = "value";
    }
    if (!Character.isLetter(candidate.charAt(0)) && candidate.charAt(0) != '_') {
      candidate = "_" + candidate;
    }
    return candidate;
  }

  private String shortenIfNeeded(String identifier) {
    if (identifier.length() <= MAX_IDENTIFIER_LENGTH) {
      return identifier;
    }

    String hashSuffix = shortHash(identifier);
    int maxBaseLength = MAX_IDENTIFIER_LENGTH - hashSuffix.length() - 1;
    String base = identifier.substring(0, maxBaseLength);
    base = base.replaceAll("_+$", "");
    if (base.isBlank()) {
      base = "obs";
    }
    return base + "_" + hashSuffix;
  }

  private String shortHash(String identifier) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(identifier.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash, 0, 6);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}