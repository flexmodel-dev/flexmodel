package dev.flexmodel.common.config.web.exception;

import dev.flexmodel.auth.AuthException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import dev.flexmodel.common.BusinessException;

import java.util.HashMap;
import java.util.Map;

/**
 * @author cjbi
 */
@Slf4j
@Provider
public class BusinessExceptionMapper implements ExceptionMapper<BusinessException> {

  @Override
  public Response toResponse(BusinessException e) {
    log.error("Handle exception, message={}", e.getMessage(), e);
    if (e instanceof AuthException) {
      Map<String, Object> body = new HashMap<>();
      body.put("code", 401);
      body.put("message", e.getMessage());
      body.put("success", false);
      return Response.status(Response.Status.UNAUTHORIZED).entity(body).build();
    }
    return getDefaultResponse(e);
  }

  public static Response getDefaultResponse(BusinessException e) {
    Map<String, Object> body = new HashMap<>();
    body.put("code", 400);
    body.put("message", e.getMessage());
    body.put("success", false);
    return Response.status(Response.Status.BAD_REQUEST).entity(body).build();
  }

}
