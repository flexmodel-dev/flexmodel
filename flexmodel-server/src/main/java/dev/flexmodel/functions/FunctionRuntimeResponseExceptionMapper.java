package dev.flexmodel.functions;

import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;

/**
 * Custom exception mapper for {@link FunctionRuntimeClient} that suppresses automatic
 * exception throwing on 4xx/5xx responses, allowing the caller to inspect the
 * full {@link Response} (status code, body) and handle errors explicitly.
 *
 * @author cjbi
 */
public class FunctionRuntimeResponseExceptionMapper implements ResponseExceptionMapper<RuntimeException> {

    @Override
    public RuntimeException toThrowable(Response response) {
        // Return null to prevent the REST client from throwing on error status codes.
        // The caller (FunctionInvoker) will inspect the Response and handle errors.
        return null;
    }
}
