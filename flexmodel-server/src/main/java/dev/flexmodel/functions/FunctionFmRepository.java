package dev.flexmodel.functions;

import dev.flexmodel.codegen.entity.Function;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static dev.flexmodel.codegen.System.function;

/**
 * @author cjbi
 */
@ApplicationScoped
public class FunctionFmRepository extends AbstractRepository implements FunctionRepository {

    @Override
    public Function findById(String projectId, String id) {
        try (Session session = getProjectSession(projectId)) {
            return session.dsl()
                .select()
                .from(Function.class)
              .where(function.id.eq(id))
                .executeOne();
        }
    }

    @Override
    public Function findByName(String projectId, String name) {
        try (Session session = getProjectSession(projectId)) {
            return session.dsl()
                .select()
                .from(Function.class)
              .where(function.name.eq(name))
                .executeOne();
        }
    }

    @Override
    public Function save(String projectId, Function function) {
        try (Session session = getProjectSession(projectId)) {
            session.dsl()
                .mergeInto(Function.class)
                .values(function)
                .execute();
        }
        return function;
    }

    @Override
    public void deleteById(String projectId, String id) {
        try (Session session = getProjectSession(projectId)) {
            session.dsl()
                .deleteFrom(Function.class)
              .where(function.id.eq(id))
                .execute();
        }
    }

    @Override
    public List<Function> find(String projectId, Predicate filter, Integer page, Integer size) {
        try (Session session = getProjectSession(projectId)) {
            return session.dsl()
                .select()
                .from(Function.class)
                .where(filter)
                .page(page, size)
              .orderByDesc(function.createdAt)
                .execute();
        }
    }

    @Override
    public long count(String projectId, Predicate filter) {
        try (Session session = getProjectSession(projectId)) {
            return session.dsl()
                .select()
                .from(Function.class)
                .where(filter)
                .count();
        }
    }
}
