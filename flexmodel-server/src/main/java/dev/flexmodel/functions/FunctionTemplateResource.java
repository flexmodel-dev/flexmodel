package dev.flexmodel.functions;

import dev.flexmodel.codegen.entity.FunctionTemplate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * Platform-level REST API for function templates (read-only).
 *
 * @author cjbi
 */
@ApplicationScoped
@Path("/function-templates")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FunctionTemplateResource {

    @Inject
    FunctionTemplateRepository functionTemplateRepository;

    /**
     * List all function templates ordered by sort_order.
     */
    @GET
    public List<FunctionTemplate> list() {
        return functionTemplateRepository.list();
    }
}
