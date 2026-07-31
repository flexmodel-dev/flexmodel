package dev.flexmodel.auth.service;

import dev.flexmodel.auth.dto.ResourceResponse;
import dev.flexmodel.auth.dto.ResourceTreeResponse;
import dev.flexmodel.auth.repository.ResourceRepository;
import dev.flexmodel.common.NotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import dev.flexmodel.codegen.entity.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class ResourceService {

  @Inject
  ResourceRepository resourceRepository;

  public List<Resource> findAll() {
    return resourceRepository.findAll();
  }

  public Resource findById(Long resourceId) {
    return resourceRepository.findById(resourceId);
  }

  public Resource create(Resource resource) {
    return resourceRepository.save(resource);
  }

  public Resource update(Resource resource) {
    Resource existingResource = resourceRepository.findById(resource.getId());
    if (existingResource == null) {
      throw new NotFoundException("Resource not found");
    }
    return resourceRepository.save(resource);
  }

  public void delete(Long resourceId) {
    resourceRepository.delete(resourceId);
  }

  public List<ResourceResponse> findAllResources() {
    return findAll().stream()
      .map(ResourceResponse::fromResource)
      .toList();
  }

  public List<ResourceTreeResponse> findResourceTree() {
    List<Resource> allResources = findAll();
    List<ResourceTreeResponse> allTreeNodes = allResources.stream()
      .map(ResourceTreeResponse::fromResource)
      .toList();

    Map<Long, ResourceTreeResponse> nodeMap = allTreeNodes.stream()
      .collect(Collectors.toMap(ResourceTreeResponse::getId, node -> node));

    List<ResourceTreeResponse> rootNodes = new ArrayList<>();

    for (ResourceTreeResponse node : allTreeNodes) {
      Long parentId = node.getParentId();
      if (parentId == null || parentId == 0) {
        rootNodes.add(node);
      } else {
        ResourceTreeResponse parentNode = nodeMap.get(parentId);
        if (parentNode != null) {
          parentNode.addChild(node);
        }
      }
    }
    return rootNodes;
  }
}
