package dev.flexmodel.pages;

import dev.flexmodel.codegen.entity.PageSite;
import dev.flexmodel.query.Predicate;

import java.util.List;

/**
 * @author cjbi
 */
public interface PageSiteRepository {

  PageSite findByProjectId(String projectId);

  PageSite save(String projectId, PageSite pageSite);

  List<PageSite> find(String projectId, Predicate filter, Integer page, Integer size);

  long count(String projectId, Predicate filter);
}
