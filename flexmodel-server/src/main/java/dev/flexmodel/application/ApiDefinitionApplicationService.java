package dev.flexmodel.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import dev.flexmodel.*;
import dev.flexmodel.application.dto.ApiDefinitionTreeDTO;
import dev.flexmodel.codegen.entity.ApiDefinition;
import dev.flexmodel.codegen.entity.ApiDefinitionHistory;
import dev.flexmodel.domain.model.api.ApiDefinitionService;

import java.util.ArrayList;
import java.util.List;

/**
 * @author cjbi
 */
@ApplicationScoped
public class ApiDefinitionApplicationService {

  @Inject
  ApiDefinitionService apiDefinitionService;

  public List<ApiDefinitionTreeDTO> findApiDefinitionTree(String projectId) {
    List<ApiDefinition> list = apiDefinitionService.findList(projectId);
    List<ApiDefinitionTreeDTO> root = list.stream()
      .filter(apiDefinition -> apiDefinition.getParentId() == null)
      .map(ApiDefinitionTreeDTO::new).toList();
    for (ApiDefinitionTreeDTO treeDTO : root) {
      treeDTO.setChildren(getChildren(treeDTO, list));
    }
    return root;
  }

  /**
   * 查询API定义历史
   *
   * @param projectId
   * @param apiDefinitionId
   * @return
   */
  public List<ApiDefinitionHistory> findApiDefinitionHistories(String projectId, String apiDefinitionId) {
    return apiDefinitionService.findApiDefinitionHistories(projectId, apiDefinitionId);
  }

  private List<ApiDefinitionTreeDTO> getChildren(ApiDefinitionTreeDTO treeDTO, List<ApiDefinition> list) {
    List<ApiDefinitionTreeDTO> result = new ArrayList<>();
    for (ApiDefinition apiDefinition : list) {
      if (treeDTO.getId().equals(apiDefinition.getParentId())) {
        ApiDefinitionTreeDTO dto = new ApiDefinitionTreeDTO(apiDefinition);
        dto.setChildren(getChildren(dto, list));
        result.add(dto);
      }
    }
    return result;
  }

  public ApiDefinition createApiDefinition(String projectId, ApiDefinition apiDefinition) {
    return apiDefinitionService.create(apiDefinition);
  }

  public ApiDefinition updateApiDefinition(String projectId, ApiDefinition apiDefinition) {
    return apiDefinitionService.update(apiDefinition);
  }

  public void deleteApiDefinition(String projectId, String id) {
    apiDefinitionService.delete(projectId, id);
  }

  public ApiDefinition findApiDefinition(String projectId, String id) {
    return apiDefinitionService.findApiDefinition(projectId, id);
  }

  @Transactional
  public ApiDefinitionHistory restoreApiDefinition(String projectId, String historyId) {
    ApiDefinitionHistory apiDefinitionHistory = apiDefinitionService.findApiDefinitionHistory(projectId, historyId);
    if (apiDefinitionHistory != null) {
      ApiDefinition apiDefinition = JsonUtils.convertValue(apiDefinitionHistory, ApiDefinition.class);
      apiDefinition.setId(apiDefinitionHistory.getApiDefinitionId());
      apiDefinition.setProjectId(projectId);
      apiDefinitionService.update(apiDefinition);
    }
    return apiDefinitionHistory;
  }
}
