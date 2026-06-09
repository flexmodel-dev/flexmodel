package dev.flexmodel.functions;

import dev.flexmodel.codegen.entity.FunctionVersion;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static dev.flexmodel.query.Expressions.field;

/**
 * @author cjbi
 */
@ApplicationScoped
public class FunctionVersionFmRepository extends AbstractRepository implements FunctionVersionRepository {

    @Override
    public FunctionVersion findByFunctionAndVersion(String functionId, int version) {
        try (Session session = getProjectSession(null)) {
            return session.dsl()
                .select()
                .from(FunctionVersion.class)
                .where(field(FunctionVersion::getFunctionId).eq(functionId)
                    .and(field(FunctionVersion::getVersion).eq(version)))
                .executeOne();
        }
    }

    @Override
    public List<FunctionVersion> findByFunctionId(String projectId, String functionId) {
        try (Session session = getProjectSession(projectId)) {
            return session.dsl()
                .select()
                .from(FunctionVersion.class)
                .where(field(FunctionVersion::getFunctionId).eq(functionId))
                .orderByDesc(FunctionVersion::getVersion)
                .execute();
        }
    }

    @Override
    public FunctionVersion save(String projectId, FunctionVersion version) {
        try (Session session = getProjectSession(projectId)) {
            session.dsl()
                .mergeInto(FunctionVersion.class)
                .values(version)
                .execute();
        }
        return version;
    }

    @Override
    public void deleteByFunctionId(String projectId, String functionId) {
        try (Session session = getProjectSession(projectId)) {
            session.dsl()
                .deleteFrom(FunctionVersion.class)
                .where(field(FunctionVersion::getFunctionId).eq(functionId))
                .execute();
        }
    }
}
