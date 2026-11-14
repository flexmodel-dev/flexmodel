package dev.flexmodel.auth;

import dev.flexmodel.auth.dto.LoginLogResponse;
import dev.flexmodel.auth.service.AuthService;
import dev.flexmodel.auth.service.LoginLogService;
import dev.flexmodel.codegen.entity.User;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.config.web.jwt.JwtService;
import dev.flexmodel.common.config.web.response.UserinfoResponse;
import dev.flexmodel.common.dto.PageDTO;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * @author cjbi
 */
@Tag(name = "认证", description = "认证授权管理")
@Slf4j
@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

  @Inject
  AuthService authService;

  @Inject
  JwtService jwtService;

  @Inject
  LoginLogService loginLogService;

  @Inject
  SessionContext sessionContext;

  @POST
  @Path("/login")
  @PermitAll
  public Response login(LoginRequest req, @Context HttpHeaders headers) {
    String clientIp = clientIp(headers);
    String userAgent = headers.getHeaderString(HttpHeaders.USER_AGENT);
    User user;
    try {
      user = authService.login(req.username, req.password);
    } catch (RuntimeException e) {
      User existing = req == null || req.username == null ? null : authService.findByUsername(req.username);
      loginLogService.record(
        req == null ? null : req.username,
        existing == null ? null : existing.getId(),
        false,
        e.getMessage(),
        clientIp,
        userAgent
      );
      throw e;
    }
    loginLogService.record(req.username, user.getId(), true, null, clientIp, userAgent);
    // 签发 accessToken
    String accessToken = jwtService.signAccessToken(user.getId());
    // 签发 refreshToken
    String refreshToken = jwtService.signRefreshToken(user.getId());

    NewCookie cookie = new NewCookie
      .Builder("refreshToken")
      .value(refreshToken)
      .httpOnly(true)
      .path("/")          // 根据前端路径决定
      .maxAge(7 * 24 * 3600)
      .build();
    return Response.ok(buildUserInfo(accessToken, user))
      .cookie(cookie).build();
  }

  @POST
  @Path("/refresh")
  @PermitAll
  public Response refresh(@CookieParam("refreshToken") String refreshToken) {
    if (refreshToken == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      String userId = jwtService.getClaim(refreshToken, JwtService.ACCOUNT);

      User user = authService.getUser(userId);
      // 签发新 accessToken
      String newAccess = jwtService.signAccessToken(userId);

      //refresh Token 即将到期续约功能
      // 旋转 refreshToken：签发新 refreshToken 并更新存储
//    String newRefresh = Jwt.issuer(ui.getBaseUri().toString())
//      .upn("admin")
//      .expiresIn(Duration.ofDays(7))
//      .sign();
//
//    NewCookie newCookie = new NewCookie.Builder("refreshToken")
//      .value(newRefresh)
//      .httpOnly(true)
//      .path("/")
//      .maxAge(7 * 24 * 3600)
//      .build();

      return Response.ok(buildUserInfo(newAccess, user))
//      .cookie(newCookie)
        .build();
    } catch (Exception e) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

  }

  @GET
  @Path("/whoami")
  public Response getUserInfo(@HeaderParam("Authorization") String authorization) {
    try {
      String accessToken = authorization.replace("Bearer ", "");
      String userId = jwtService.getClaim(accessToken, JwtService.ACCOUNT);
      User user = authService.getUser(userId);
      return Response.ok(buildUserInfo(accessToken, user)).build();
    } catch (Exception e) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
  }


  @GET
  @Path("/login-logs")
  public PageDTO<LoginLogResponse> getLoginLogs(
    @QueryParam("page") @DefaultValue("1") int page,
    @QueryParam("size") @DefaultValue("20") int size
  ) {
    String userId = sessionContext.getUserId();
    if (userId == null || userId.isBlank()) {
      throw new dev.flexmodel.auth.exception.AuthException("Token is missing");
    }
    return loginLogService.findByUserId(userId, page, size);
  }

  private String clientIp(HttpHeaders headers) {
    String forwarded = headers.getHeaderString("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    String realIp = headers.getHeaderString("X-Real-IP");
    return realIp == null || realIp.isBlank() ? null : realIp.trim();
  }

  private UserinfoResponse buildUserInfo(String accessToken, User user) {
    UserinfoResponse userinfo = new UserinfoResponse();
    userinfo.setToken(accessToken);
    userinfo.setExpiresIn(300000L);
    userinfo.setUser(new UserinfoResponse.UserResponse(user.getId(), user.getName(), user.getEmail(), user.getAvatar()));
    userinfo.setPermissions(authService.findPermissions(user.getId()));
    return userinfo;
  }

  public record LoginRequest(String username, String password) {
  }

}
