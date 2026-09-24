package devs.observation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Creates safe, deterministic filenames for observation-type archives.
 */
public final class ObservationFileNameStrategy {

  private static final Pattern UNSAFE_CHARACTERS = Pattern.compile("[^a-z0-9._-]+");
  private static final Pattern SEPARATORS = Pattern.compile("[-_.]{2,}");

  /**
   * Returns the JSON Lines filename for an observation type.
   *
   * @param observationType logical observation type
   * @return safe filename contained in the configured observation directory
   */
  public String filenameForObservationType(String observationType) {
    String original = Objects.requireNonNull(observationType, "observationType must not be null");
    if (original.isBlank()) {
      throw new IllegalArgumentException("observationType must not be blank");
    }

    String normalized = UNSAFE_CHARACTERS.matcher(original.toLowerCase(Locale.ROOT)).replaceAll("_");
    normalized = SEPARATORS.matcher(normalized).replaceAll("_");
    normalized = normalized.replaceAll("^[_\\-.]+|[_\\-.]+$", "");
    if (normalized.isBlank()) {
      normalized = "observation";
    }
    return normalized + "-" + shortHash(original) + ".jsonl";
  }

  private String shortHash(String value) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash, 0, 6);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
