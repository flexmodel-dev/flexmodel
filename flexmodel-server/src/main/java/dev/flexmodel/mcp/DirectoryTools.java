package dev.flexmodel.mcp;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.directory.DirectoryService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * General directory tools shared by application design and member selection.
 */
@Slf4j
public class DirectoryTools {
  @Inject
  DirectoryService directoryService;
  @Inject
  McpToolAccess access;

  @Tool(description = """
    Get the shared system directory: active members, roles, organizations and their hierarchy, \
    member roleIds, organizationIds and managerId. No appId is required. Only selection \
    metadata is returned, never credentials. Use the returned IDs when configuring approval \
    rules. memberRule describes the contract accepted by resolve_members and approval nodes.\
    """)
  public String get_directory() {
    try {
      access.requireDirectory();
      var result = new LinkedHashMap<>(directoryService.directory());
      result.put("memberRule", Map.of(
        "sources", DirectoryService.SOURCES,
        "scopes", DirectoryService.SCOPES,
        "values", "User, role, organization or upstream node IDs; variable paths for source=variable",
        "includeChildren", "Expand child organizations for source=organizations",
        "context", "source=initiator uses initiatorId; source=node uses variables.nodeMembers; source=variable reads variables",
        "example", Map.of("id", "manager", "source", "initiator", "values", java.util.List.of(), "scope", "manager", "includeChildren", false)));
      return JsonUtils.toJsonString(result);
    } catch (Exception exception) {
      log.error("get_directory failed", exception);
      return "Error: get_directory failed - " + exception.getMessage();
    }
  }

  @Tool(description = """
    Preview member rules using the real system directory. Returns ordered, deduplicated \
    active user IDs (at most 1000). Call get_directory for source and scope enums. \
    initiatorId and variablesJson are preview context only; this does not submit or approve \
    any record. Empty results must be fixed before running an approval.\
    """)
  public String resolve_members(
    @ToolArg(description = "JSON array of member rules: id, source, values (string array), scope, includeChildren") String rulesJson,
    @ToolArg(description = "Initiator user ID for relative rules, optional", required = false) String initiatorId,
    @ToolArg(description = "JSON object of preview variables, optional; node rules use nodeMembers", required = false) String variablesJson
  ) {
    try {
      access.requireDirectory();
      return JsonUtils.toJsonString(directoryService.resolve(JsonUtils.parseToMapList(rulesJson),
        initiatorId == null ? "" : initiatorId,
        variablesJson == null || variablesJson.isBlank() ? Map.of() : JsonUtils.parseToMap(variablesJson)));
    } catch (Exception exception) {
      log.error("resolve_members failed", exception);
      return "Error: resolve_members failed - " + exception.getMessage();
    }
  }
}
