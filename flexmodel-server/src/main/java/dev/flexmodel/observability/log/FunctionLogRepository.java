package dev.flexmodel.observability.log;

import dev.flexmodel.codegen.entity.FunctionLog;
import dev.flexmodel.query.Predicate;

import java.util.List;

/**
 * 函数执行日志查询仓储。
 *
 * @author cjbi
 */
public interface FunctionLogRepository {

  List<FunctionLog> find(String projectId, Predicate filter, Integer page, Integer size);

  long count(String projectId, Predicate filter);

  void delete(String projectId, Predicate filter);

}
