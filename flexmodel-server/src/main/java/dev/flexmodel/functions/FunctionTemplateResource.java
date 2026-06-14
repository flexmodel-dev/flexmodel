package dev.flexmodel.functions;

import dev.flexmodel.codegen.entity.FunctionTemplate;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.session.Session;
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
public class FunctionTemplateResource extends AbstractRepository {

    /**
     * List all function templates ordered by sort_order.
     */
    @GET
    public List<FunctionTemplate> list() {
        try (Session session = sessionFactory.createSession()) {
            return session.dsl()
                .selectFrom(FunctionTemplate.class)
                .orderBy(FunctionTemplate::getSortOrder)
                .execute();
        }
    }
}
