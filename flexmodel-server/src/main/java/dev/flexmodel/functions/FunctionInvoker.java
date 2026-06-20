package dev.flexmodel.functions;

import dev.flexmodel.functions.dto.FunctionRuntimeDeployRequest;
import dev.flexmodel.functions.dto.FunctionInvokeRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * HTTP client that communicates with the flexmodel-functions-runtime (Deno process).
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class FunctionInvoker {

    @Inject
    @RestClient
    FunctionRuntimeClient runtimeClient;

    /**
     * Deploy function source files to Deno functions runtime.
     */
    public void deploy(FunctionRuntimeDeployRequest req) {
        try {
            log.debug("Deploying function to runtime: {}:{}", req.getProjectId(), req.getName());
            Response response = runtimeClient.deploy(req);
            if (response.getStatus() >= 200 && response.getStatus() < 300) {
                log.info("Deployed function to runtime: {}:{}", req.getProjectId(), req.getName());
            } else {
                String body = response.readEntity(String.class);
                log.error("Deploy failed: HTTP {} body: {}", response.getStatus(), body);
                throw new RuntimeException("Deploy failed: HTTP " + response.getStatus());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to deploy function to runtime: {}:{}", req.getProjectId(), req.getName(), e);
            throw new RuntimeException("Failed to deploy function to Deno runtime: " + e.getMessage(), e);
        }
    }

    /**
     * Invoke a function via the Deno functions runtime.
     */
    public Response invoke(String projectId, String name, FunctionInvokeRequest req) {
        try {
            return runtimeClient.invoke(projectId, name, req);
        } catch (Exception e) {
            log.error("Failed to invoke function: {}:{}", projectId, name, e);
            throw new RuntimeException("Failed to invoke function: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a function from the Deno functions runtime.
     */
    public boolean delete(String projectId, String name) {
        try {
            Response response = runtimeClient.delete(projectId, name);
            log.info("Deleted function from runtime: {}:{} status={}", projectId, name, response.getStatus());
            return response.getStatus() >= 200 && response.getStatus() < 300;
        } catch (Exception e) {
            log.warn("Failed to delete function from runtime: {}:{}", projectId, name, e);
            return false;
        }
    }

}
