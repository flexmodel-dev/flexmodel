package dev.flexmodel.common.config.web.exception;

import dev.flexmodel.sql.DatabaseBusyException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.HashMap;
import java.util.Map;

/**
 * 数据库忙（锁等待超时）：返回可重试的 503。
 */
@Provider
public class DatabaseBusyExceptionMapper implements ExceptionMapper<DatabaseBusyException> {

  @Override
  public Response toResponse(DatabaseBusyException e) {
    Map<String, Object> body = new HashMap<>();
    body.put("code", -1);
    body.put("message", "数据库正忙，请稍后重试");
    body.put("success", false);
    return Response.status(Response.Status.SERVICE_UNAVAILABLE).type(MediaType.APPLICATION_JSON).entity(body).build();
  }
}
