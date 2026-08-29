package dev.flexmodel.flow.repository;

import dev.flexmodel.codegen.entity.NodeInstanceLog;

import java.util.List;

/**
 * @author cjbi
 */
public interface NodeInstanceLogRepository {

  boolean insertList(String projectId, List<NodeInstanceLog> nodeInstanceLogList);

  /**
   * 按链路追踪ID查询节点执行日志（用于链路详情关联查询）。
   *
   * @param projectId 项目ID
   * @param traceId   链路追踪ID
   * @param limit     最大返回条数
   * @return 节点执行日志列表
   */
  List<NodeInstanceLog> findByTraceId(String projectId, String traceId, int limit);

}
