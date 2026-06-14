package dev.flexmodel.functions;

import dev.flexmodel.codegen.entity.Function;
import dev.flexmodel.query.Predicate;

import java.util.List;

/**
 * @author cjbi
 */
public interface FunctionRepository {

    Function findById(String projectId, String id);

    Function findByName(String projectId, String name);

    Function save(String projectId, Function function);

    void deleteById(String projectId, String id);

    List<Function> find(String projectId, Predicate filter, Integer page, Integer size);

    long count(String projectId, Predicate filter);
}
