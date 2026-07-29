package dev.flexmodel.common.config.web.exception;

import dev.flexmodel.auth.exception.AuthException;
import dev.flexmodel.common.BusinessException;
import dev.flexmodel.common.InternalServerException;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.flow.exception.TurboException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

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
    if (e instanceof AuthException) {
      log.warn("Handle exception, message={}", e.getMessage());
    } else if (e instanceof TurboException te) {
      log.warn("Handle TurboException, errNo={}, errMsg={}", te.getErrNo(), te.getErrMsg());
    } else if (e instanceof NotFoundException) {
      log.warn("Resource not found, message={}", e.getMessage());
    } else if (e instanceof ValidationException) {
      log.warn("Validation failed, message={}", e.getMessage());
    } else if (e instanceof InternalServerException) {
      log.error("Internal server error, message={}", e.getMessage(), e);
    } else {
      log.error("Handle exception, message={}", e.getMessage(), e);
    }
    if (e instanceof AuthException) {
      return buildResponse(Response.Status.UNAUTHORIZED, 401, e.getMessage());
    }
    if (e instanceof NotFoundException) {
      return buildResponse(Response.Status.NOT_FOUND, 404, e.getMessage());
    }
    if (e instanceof TurboException te) {
      return buildResponse(Response.Status.BAD_REQUEST, te.getErrNo(), te.getErrMsg());
    }
    if (e instanceof InternalServerException) {
      return buildResponse(Response.Status.INTERNAL_SERVER_ERROR, 500, e.getMessage());
    }
    return getDefaultResponse(e);
  }

  private Response buildResponse(Response.Status status, int code, String message) {
    Map<String, Object> body = new HashMap<>();
    body.put("code", code);
    body.put("message", message);
    body.put("success", false);
    return Response.status(status).entity(body).build();
  }

  public static Response getDefaultResponse(BusinessException e) {
    Map<String, Object> body = new HashMap<>();
    body.put("code", 400);
    body.put("message", e.getMessage());
    body.put("success", false);
    return Response.status(Response.Status.BAD_REQUEST).entity(body).build();
  }

}
