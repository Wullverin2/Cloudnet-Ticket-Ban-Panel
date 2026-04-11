package de.speed.ticketconsolecloudban.auth;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class TotpService {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  private static final int SECRET_BYTES = 20;
  private static final int TIME_STEP_SECONDS = 30;
  private static final int DIGITS = 6;

  private TotpService() {
  }

  public static String newSecret() {
    var bytes = new byte[SECRET_BYTES];
    RANDOM.nextBytes(bytes);
    return base32Encode(bytes);
  }

  public static boolean verify(String code, String secret) {
    if (code == null || secret == null || secret.isBlank()) {
      return false;
    }

    var normalized = code.replace(" ", "").trim();
    if (!normalized.matches("\\d{6}")) {
      return false;
    }

    var currentCounter = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
    for (long offset = -1; offset <= 1; offset++) {
      if (generate(secret, currentCounter + offset).equals(normalized)) {
        return true;
      }
    }
    return false;
  }

  public static String otpauthUri(String issuer, String username, String secret) {
    var safeIssuer = issuer == null || issuer.isBlank() ? "Network Control" : issuer.trim();
    var safeUsername = username == null || username.isBlank() ? "panel" : username.trim();
    return "otpauth://totp/"
      + url(safeIssuer + ":" + safeUsername)
      + "?secret=" + url(secret)
      + "&issuer=" + url(safeIssuer)
      + "&algorithm=SHA1&digits=" + DIGITS
      + "&period=" + TIME_STEP_SECONDS;
  }

  private static String generate(String secret, long counter) {
    try {
      var key = base32Decode(secret);
      var data = ByteBuffer.allocate(Long.BYTES).putLong(counter).array();
      var mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(key, "HmacSHA1"));
      var hash = mac.doFinal(data);
      var offset = hash[hash.length - 1] & 0x0f;
      var binary = ((hash[offset] & 0x7f) << 24)
        | ((hash[offset + 1] & 0xff) << 16)
        | ((hash[offset + 2] & 0xff) << 8)
        | (hash[offset + 3] & 0xff);
      var otp = binary % 1_000_000;
      return String.format("%06d", otp);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Authenticator-Code konnte nicht geprüft werden.", exception);
    }
  }

  private static String base32Encode(byte[] bytes) {
    var result = new StringBuilder((bytes.length * 8 + 4) / 5);
    var buffer = 0;
    var bitsLeft = 0;
    for (byte value : bytes) {
      buffer = (buffer << 8) | (value & 0xff);
      bitsLeft += 8;
      while (bitsLeft >= 5) {
        result.append(BASE32_ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 31));
        bitsLeft -= 5;
      }
    }
    if (bitsLeft > 0) {
      result.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 31));
    }
    return result.toString();
  }

  private static byte[] base32Decode(String value) {
    var normalized = value.replace("=", "").replace(" ", "").trim().toUpperCase();
    var output = new byte[normalized.length() * 5 / 8 + 1];
    var buffer = 0;
    var bitsLeft = 0;
    var count = 0;
    for (int index = 0; index < normalized.length(); index++) {
      var alphabetIndex = BASE32_ALPHABET.indexOf(normalized.charAt(index));
      if (alphabetIndex < 0) {
        throw new IllegalArgumentException("Ungültiger Authenticator-Schlüssel.");
      }
      buffer = (buffer << 5) | alphabetIndex;
      bitsLeft += 5;
      if (bitsLeft >= 8) {
        output[count++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xff);
        bitsLeft -= 8;
      }
    }
    return Arrays.copyOf(output, count);
  }

  private static String url(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
