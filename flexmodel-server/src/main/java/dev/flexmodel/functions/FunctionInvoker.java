package dev.flexmodel.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.flexmodel.functions.dto.SidecarDeployRequest;
import dev.flexmodel.functions.dto.FunctionInvokeRequest;
import dev.flexmodel.functions.dto.FunctionInvokeResponse;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP client that communicates with the flexmodel-sidecar (Deno process).
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class FunctionInvoker {

    @Inject
    @ConfigProperty(name = "flexmodel.sidecar.host", defaultValue = "http://127.0.0.1")
    private String sidecarHost;

    @Inject
    @ConfigProperty(name = "flexmodel.sidecar.port", defaultValue = "9999")
    private int sidecarPort;

    @Inject
    ObjectMapper objectMapper;

    private HttpClient httpClient;

    @PostConstruct
    void init() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    private String baseUrl() {
        return sidecarHost + ":" + sidecarPort;
    }

    /**
     * Deploy function source files to Deno sidecar.
     */
    public void deploy(SidecarDeployRequest req) {
        try {
            String json = objectMapper.writeValueAsString(req);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/functions/deploy"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(10))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Deployed function to sidecar: {}:{}", req.getProjectId(), req.getName());
            } else {
                log.error("Deploy failed: HTTP {} body: {}", response.statusCode(), response.body());
                throw new RuntimeException("Deploy failed: HTTP " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            log.error("Failed to deploy function to sidecar: {}:{}", req.getProjectId(), req.getName(), e);
            throw new RuntimeException("Failed to deploy function to Deno sidecar: " + e.getMessage(), e);
        }
    }

    /**
     * Invoke a function via the Deno sidecar.
     */
    public FunctionInvokeResponse invoke(String projectId, String name, FunctionInvokeRequest req) {
        try {
            String json = objectMapper.writeValueAsString(req);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/functions/" + projectId + "/" + name + "/invoke"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(60))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return objectMapper.readValue(response.body(), FunctionInvokeResponse.class);
        } catch (IOException | InterruptedException e) {
            log.error("Failed to invoke function: {}:{}", projectId, name, e);
            throw new RuntimeException("Failed to invoke function: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a function from the Deno sidecar.
     */
    public boolean delete(String projectId, String name) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/functions/" + projectId + "/" + name))
                .DELETE()
                .timeout(Duration.ofSeconds(10))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Deleted function from sidecar: {}:{} status={}", projectId, name, response.statusCode());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            log.warn("Failed to delete function from sidecar: {}:{}", projectId, name, e);
            return false;
        }
    }

}
