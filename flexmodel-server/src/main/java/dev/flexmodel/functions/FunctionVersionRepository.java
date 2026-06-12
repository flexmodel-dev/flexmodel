package dev.flexmodel.functions;

import dev.flexmodel.codegen.entity.FunctionVersion;

import java.util.List;

/**
 * @author cjbi
 */
public interface FunctionVersionRepository {

    FunctionVersion findByFunctionAndVersion(String functionId, int version);

    List<FunctionVersion> findByFunctionId(String projectId, String functionId);

    FunctionVersion save(String projectId, FunctionVersion version);

    void deleteByFunctionId(String projectId, String functionId);
}
