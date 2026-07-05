package dev.flexmodel.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * API Key 生成工具。
 * 生成格式：fm_ak_{type}_{random40chars}
 * 存储 SHA-256 哈希，不存原文。
 *
 * <p>{@link SecureRandom} 实例为方法内局部变量，
 * 避免 static 字段在 Native Image 构建期进入 image heap 的问题。
 */
public class ApiKeyGenerator {

  private static final String CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

  public record GeneratedKey(String plainText, String hash, String prefix) {
  }

  /**
   * 生成一个新的 API Key。
   *
   * @param keyType anon / service / custom
   * @return 包含明文、SHA-256 哈希和前缀的 GeneratedKey
   */
  public static GeneratedKey generate(String keyType) {
    SecureRandom random = new SecureRandom();
    StringBuilder sb = new StringBuilder(40);
    for (int i = 0; i < 40; i++) {
      sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
    }
    String randomPart = sb.toString();
    String plainText = "fm_ak_" + keyType + "_" + randomPart;
    String hash = sha256(plainText);
    String prefix = plainText.substring(0, Math.min(plainText.length(), 16));
    return new GeneratedKey(plainText, hash, prefix);
  }

  public static String sha256(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 not available", e);
    }
  }
}
