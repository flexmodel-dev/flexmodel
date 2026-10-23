package dev.flexmodel.pages;

import dev.flexmodel.codegen.entity.PageSite;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static dev.flexmodel.codegen.System.pageSite;

/**
 * @author cjbi
 */
@ApplicationScoped
public class PageSiteFmRepository extends AbstractRepository implements PageSiteRepository {

  @Override
  public PageSite findByProjectId(String projectId) {
    try (Session session = openSession()) {
      return session.dsl()
        .select()
        .from(PageSite.class)
        .where(pageSite.projectId.eq(projectId))
        .executeOne();
    }
  }

  @Override
  public PageSite save(String projectId, PageSite pageSite) {
    pageSite.setProjectId(projectId);
    try (Session session = openSession()) {
      session.dsl()
        .mergeInto(PageSite.class)
        .values(pageSite)
        .execute();
    }
    return pageSite;
  }

  @Override
  public List<PageSite> find(String projectId, Predicate filter, Integer page, Integer size) {
    try (Session session = openSession()) {
      return session.dsl()
        .select()
        .from(PageSite.class)
        .where(pageSite.projectId.eq(projectId).and(filter))
        .page(page, size)
        .orderByDesc(pageSite.createdAt)
        .execute();
    }
  }

  @Override
  public long count(String projectId, Predicate filter) {
    try (Session session = openSession()) {
      return session.dsl()
        .select()
        .from(PageSite.class)
        .where(pageSite.projectId.eq(projectId).and(filter))
        .count();
    }
  }
}
