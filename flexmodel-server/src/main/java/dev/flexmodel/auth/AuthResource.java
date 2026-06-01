package dev.flexmodel.auth;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import dev.flexmodel.codegen.entity.User;
import dev.flexmodel.common.config.web.jwt.JwtUtil;
import dev.flexmodel.common.config.web.response.UserinfoResponse;

import java.time.Duration;

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

  @POST
  @Path("/login")
  @PermitAll
  public Response login(LoginRequest req) {
    User user = authService.login(req.username, req.password);
    // 签发 accessToken
    String accessToken = JwtUtil.sign(user.getId(), Duration.ofDays(7));
    // 签发 refreshToken
    String refreshToken = JwtUtil.sign(user.getId(), Duration.ofDays(30));

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
      String userId = JwtUtil.getClaim(refreshToken, JwtUtil.ACCOUNT);

      User user = authService.getUser(userId);
      // 签发新 accessToken
      String newAccess = JwtUtil.sign(userId, Duration.ofMinutes(5));

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
      String userId = JwtUtil.getClaim(accessToken, JwtUtil.ACCOUNT);
      User user = authService.getUser(userId);
      return Response.ok(buildUserInfo(accessToken, user)).build();
    } catch (Exception e) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
  }


  private UserinfoResponse buildUserInfo(String accessToken, User user) {
    UserinfoResponse userinfo = new UserinfoResponse();
    userinfo.setToken(accessToken);
    userinfo.setExpiresIn(300000L);
    userinfo.setUser(new UserinfoResponse.UserResponse(user.getId(), user.getName(), user.getEmail()));
    userinfo.setPermissions(authService.findPermissions(user.getId()));
    return userinfo;
  }

  public record LoginRequest(String username, String password) {
  }

}
