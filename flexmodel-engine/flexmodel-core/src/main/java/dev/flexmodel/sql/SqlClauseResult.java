package dev.flexmodel.sql;

import java.util.Map;

/**
 * @author cjbi
 */
public record SqlClauseResult(String sqlClause, Map<String, Object> args) {
}
