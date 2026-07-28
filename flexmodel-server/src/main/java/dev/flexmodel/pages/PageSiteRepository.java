package dev.flexmodel.pages;

import dev.flexmodel.codegen.entity.PageSite;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.session.Session;
import dev.flexmodel.common.AbstractRepository;

import java.util.List;

import static dev.flexmodel.codegen.System.pageSite;

/**
 * @author cjbi
 */
public interface PageSiteRepository {

  PageSite findByProjectId(String projectId);

  PageSite save(String projectId, PageSite pageSite);

  List<PageSite> find(String projectId, Predicate filter, Integer page, Integer size);

  long count(String projectId, Predicate filter);
}
