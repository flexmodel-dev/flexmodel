package dev.flexmodel.functions;

import dev.flexmodel.codegen.entity.FunctionTemplate;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * @author cjbi
 */
@ApplicationScoped
public class FunctionTemplateFmRepository extends AbstractRepository implements FunctionTemplateRepository {

    @Override
    public List<FunctionTemplate> list() {
        try (Session session = sessionFactory.createSession()) {
            return session.dsl()
                .selectFrom(FunctionTemplate.class)
              .orderBy("sortOrder")
                .execute();
        }
    }
}
