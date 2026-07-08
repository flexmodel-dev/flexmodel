package dev.flexmodel.common.config.web.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import dev.flexmodel.common.FlexmodelConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.Date;
import java.util.Map;

/**
 * JWT 签发与验证服务。
 * <p>
 * 密钥通过 {@code flexmodel.jwt.secret} 配置注入，生产环境务必通过环境变量
 * {@code FLEXMODEL_JWT_SECRET} 设置强随机密钥，不要使用默认值。
 * <p>
 * 签名密钥按账户派生：{@code account + secretKey}，保证不同账户的密钥不同。
 *
 * @author cjbi
 */
@ApplicationScoped
public class JwtService {

  //JWT-account
  public static final String ACCOUNT = "upn";

  @Inject
  FlexmodelConfig config;

  /**
   * 校验 token 是否正确
   *
   * @param token JWT token
   * @return true 如果验证通过
   */
  public boolean verify(String token) {
    String secret = getClaim(token, ACCOUNT) + config.jwt().secret();
    Algorithm algorithm = Algorithm.HMAC256(secret);
    JWTVerifier verifier = JWT.require(algorithm)
      .build();
    verifier.verify(token);
    return true;
  }

  /**
   * 获得 Token 中的信息无需 secret 解密也能获得
   *
   * @param token JWT token
   * @param claim claim 名称
   * @return claim 值，解码失败返回 null
   */
  public String getClaim(String token, String claim) {
    try {
      DecodedJWT jwt = JWT.decode(token);
      return jwt.getClaim(claim).asString();
    } catch (JWTDecodeException e) {
      return null;
    }
  }

  /**
   * 生成签名
   *
   * @param account   账号
   * @param expiresIn 有效期
   * @return JWT token
   */
  public String sign(String account, Duration expiresIn) {
    return sign(account, null, expiresIn);
  }

  /**
   * 签发 JWT，携带自定义 payload claims。
   *
   * @param account       帐号，编码在 "upn" claim 中
   * @param payloadClaims 自定义 claims（如 projectId）
   * @param expiresIn     有效期
   */
  public String sign(String account, Map<String, String> payloadClaims, Duration expiresIn) {
    String secret = account + config.jwt().secret();
    Algorithm algorithm = Algorithm.HMAC256(secret);
    Date date = new Date(System.currentTimeMillis() + expiresIn.toMillis());

    JWTCreator.Builder builder = JWT.create()
      .withClaim(ACCOUNT, account)
      .withExpiresAt(date);

    if (payloadClaims != null) {
      for (var entry : payloadClaims.entrySet()) {
        builder.withClaim(entry.getKey(), entry.getValue());
      }
    }

    return builder.sign(algorithm);
  }

  public String getAccount(String token) {
    return getClaim(token, ACCOUNT);
  }

  /**
   * 使用配置的 accessToken 有效期签发 access token。
   */
  public String signAccessToken(String account) {
    return sign(account, config.jwt().accessTokenLifetime());
  }

  /**
   * 使用配置的 refreshToken 有效期签发 refresh token。
   */
  public String signRefreshToken(String account) {
    return sign(account, config.jwt().refreshTokenLifetime());
  }

}
