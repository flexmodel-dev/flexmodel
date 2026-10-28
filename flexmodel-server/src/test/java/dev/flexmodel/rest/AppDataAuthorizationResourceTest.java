package dev.flexmodel.rest;

import dev.flexmodel.SQLiteTestResource;
import dev.flexmodel.session.SessionFactory;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Method;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * 当前仅验证配置持久化、配置管理权限和所有者字段，不执行业务数据授权。
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
class AppDataAuthorizationResourceTest {
  @Inject
  TestTokenHelper tokens;
  @Inject
  SessionFactory sessions;

  record Fixture(String app, String user) {
    String forms() {
      return "/apps/" + app + "/forms";
    }

    String rules() {
      return forms() + "/orders/authorizations";
    }

    String records() {
      return forms() + "/orders/records";
    }
  }

  Fixture fixture() {
    String suffix = Long.toHexString(System.nanoTime());
    var fixture = new Fixture("perm_" + suffix, "u_" + suffix);
    try (var session = sessions.createSession()) {
      session.dsl().insertInto("f_user").values(new LinkedHashMap<>(Map.of("id", fixture.user(), "name", fixture.user(), "password_hash", "unused", "status", "ACTIVE"))).execute();
    }
    call("admin", Method.POST, "/apps", Map.of("projectId", fixture.app(), "name", "权限配置回归"), 201);
    call("admin", Method.POST, fixture.forms(), Map.of("name", "orders", "label", "订单", "createDisplayField", true), 201);
    call("admin", Method.PUT, fixture.forms() + "/orders/layout", Map.of("items", List.of(item("name"), item("owner_id"), item("created_by"))), 200);
    return fixture;
  }

  Map<String, Object> item(String name) {
    return Map.of("type", "FIELD", "name", name);
  }

  Map<String, Object> control(String form, Map<String, Object> condition) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("formName", form);
    result.put("readAll", false);
    result.put("writeAll", false);
    result.put("allowDelete", false);
    result.put("fieldControls", Map.of("name", Map.of("read", true, "write", false)));
    result.put("condition", condition);
    return result;
  }

  Map<String, Object> rule(Fixture fixture, List<Map<String, Object>> controls) {
    return new LinkedHashMap<>(Map.of("name", "本人只读配置", "enabled", true,
      "subjects", List.of(Map.of("type", "USER", "ids", List.of(fixture.user()))),
      "dataScope", List.of(Map.of("type", "SELF")), "controls", controls));
  }

  @Test
  void configurationRoundTripsAndDoesNotChangeRecordAccess() {
    var f = fixture();
    try {
      call("admin", Method.GET, f.rules(), null, 200).then().body("size()", equalTo(0));
      var condition = Map.<String, Object>of("field", "name", "operator", "EQ", "value", "不匹配的条件");
      var request = rule(f, List.of(control("orders", condition)));
      String id = call("admin", Method.POST, f.rules(), request, 200).then()
        .body("controls.size()", equalTo(1)).body("controls[0].condition.value", equalTo("不匹配的条件"))
        .extract().path("id");
      call("admin", Method.GET, f.rules(), null, 200).then().body("size()", equalTo(1))
        .body("[0].subjects[0].ids[0]", equalTo(f.user())).body("[0].dataScope[0].type", equalTo("SELF"));

      String record = call("admin", Method.POST, f.records(), Map.of("name", "业务数据"), 201).path("id");
      // 即使范围、字段和条件均不允许，已保存的配置目前也不介入记录 CRUD。
      call(f.user(), Method.GET, f.records() + "/" + record, null, 200).then().body("name", equalTo("业务数据"));
      call(f.user(), Method.PUT, f.records() + "/" + record, Map.of("name", "正常修改"), 200).then().body("name", equalTo("正常修改"));
      call(f.user(), Method.DELETE, f.records() + "/" + record, null, 204);
      request.put("name", "已停用配置");
      request.put("enabled", false);
      call("admin", Method.PUT, f.rules() + "/" + id, request, 200).then().body("enabled", equalTo(false));
      call("admin", Method.DELETE, f.rules() + "/" + id, null, 204);
      call("admin", Method.GET, f.rules(), null, 200).then().body("size()", equalTo(0));
      call("admin", Method.POST, "/apps/" + f.app() + "/data-authorization/activate", null, 404);
    } finally {
      call("admin", Method.DELETE, "/apps/" + f.app(), null, 204);
    }
  }

  @Test
  void ownerIsAnImmutableBuiltinAndDefaultsToCreator() {
    var f = fixture();
    try {
      call("admin", Method.GET, f.forms() + "/orders", null, 200).then()
        .body("fields.find { it.name == 'owner_id' }.label", equalTo("所有者"))
        .body("fields.find { it.name == 'owner_id' }.type", equalTo("REFERENCE"))
        .body("fields.find { it.name == 'owner_id' }.builtin", equalTo(true))
        .body("fields.find { it.name == 'owner_id' }.referenceForm", equalTo("f_user"))
        .body("$", not(hasKey("dataAuthorizationEnabled")));
      String record = call(f.user(), Method.POST, f.records(), Map.of("name", "用户创建"), 201).then()
        .body("owner_id.id", equalTo(f.user())).body("created_by.id", equalTo(f.user())).extract().path("id");
      call("admin", Method.PUT, f.records() + "/" + record, Map.of("name", "管理员更新"), 200).then()
        .body("owner_id.id", equalTo(f.user())).body("created_by.id", equalTo(f.user()));
      call(f.user(), Method.POST, f.records(), Map.of("name", "伪造", "owner_id", "admin"), 400);
      call(f.user(), Method.PUT, f.records() + "/" + record, Map.of("owner_id", "admin"), 400);
    } finally {
      call("admin", Method.DELETE, "/apps/" + f.app(), null, 204);
    }
  }

  @Test
  void onlyApplicationOwnerManagesValidConfiguration() {
    var f = fixture();
    try {
      var request = rule(f, List.of(control("orders", null)));
      call(f.user(), Method.GET, f.rules(), null, 403);
      call(f.user(), Method.POST, f.rules(), request, 403);
      request.put("unexpected", true);
      call("admin", Method.POST, f.rules(), request, 400);
      request.remove("unexpected");
      request.put("controls", List.of(control("orders", Map.of("field", "missing", "operator", "EQ", "value", "x"))));
      call("admin", Method.POST, f.rules(), request, 400);
      request.put("controls", List.of(control("orders", null), control("orders", null)));
      call("admin", Method.POST, f.rules(), request, 400);
      request.put("controls", List.of(control("orders", null)));
      request.put("dataScope", List.of(Map.of("type", "ALL"), Map.of("type", "SELF")));
      call("admin", Method.POST, f.rules(), request, 400);
    } finally {
      call("admin", Method.DELETE, "/apps/" + f.app(), null, 204);
    }
  }

  @Test
  void childConfigurationFollowsCurrentFormDefinitions() {
    var f = fixture();
    try {
      call("admin", Method.POST, f.forms(), Map.of("name", "contacts", "label", "联系人", "createDisplayField", true), 201);
      call("admin", Method.POST, f.forms() + "/contacts/fields", Map.of("name", "parent_id", "label", "所属订单", "type", "REFERENCE", "referenceForm", "orders"), 201);
      var relation = Map.<String, Object>of("type", "SUBFORM", "formName", "contacts", "referenceField", "parent_id");
      call("admin", Method.PUT, f.forms() + "/orders/layout", Map.of("items", List.of(item("name"), relation)), 200);
      String id = call("admin", Method.POST, f.rules(), rule(f, List.of(control("orders", null), control("contacts", null))), 200).then()
        .body("controls.size()", equalTo(2)).extract().path("id");
      call("admin", Method.PUT, f.forms() + "/orders/layout", Map.of("items", List.of(item("name"))), 200);
      call("admin", Method.PUT, f.forms() + "/orders/layout", Map.of("items", List.of(item("name"), relation)), 200);
      call("admin", Method.GET, f.rules(), null, 200).then().body("find { it.id == '" + id + "' }.controls.size()", equalTo(1));
    } finally {
      call("admin", Method.DELETE, "/apps/" + f.app(), null, 204);
    }
  }

  private Response call(String user, Method method, String path, Object body, int status) {
    var request = given().header("Authorization", tokens.getAuthorizationHeader(user));
    if (body != null) request.contentType("application/json").body(body);
    Response response = request.request(method, path);
    response.then().statusCode(status);
    return response;
  }
}
