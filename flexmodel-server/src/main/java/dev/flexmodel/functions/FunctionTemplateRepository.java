package dev.flexmodel.functions;

import dev.flexmodel.codegen.entity.FunctionTemplate;

import java.util.List;

/**
 * Repository for platform-level function templates.
 * Unlike project-level repositories, methods do not require a projectId parameter
 * because function templates are shared across all projects.
 *
 * @author cjbi
 */
public interface FunctionTemplateRepository {

    List<FunctionTemplate> list();

}
