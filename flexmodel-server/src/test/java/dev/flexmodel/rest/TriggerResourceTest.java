package dev.flexmodel.rest;

import dev.flexmodel.SQLiteTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ScheduleResource 集成测试
 *
 * @author cjbi
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
public class TriggerResourceTest {

  @Inject
  TestTokenHelper testTokenHelper;

  @Inject
  Scheduler quartz;

  private static final String BASE_PATH = "/projects/dev_test/triggers";

  // 测试数据中的触发器ID
  private static final String INTERVAL_TRIGGER_ID = "bf492f37-1f01-4eb8-b76d-d319299b4d8e";
  private static final String CRON_TRIGGER_ID = "d8c60d2a-19d8-4c3c-b370-96318733858f";
  private static final String EVENT_AFTER_TRIGGER_ID = "f351b8d9-a450-4f2c-8fff-cf862d690352";
  private static final String ENABLED_EVENT_TRIGGER_ID = "9434666d-6b3b-417a-80e3-3352f40ded71";
  private static final String TEST_JOB_ID = "5c41f37a-87a9-47af-bdba-44d0c27eda89";

  private String createdTriggerId;

  @BeforeEach
  void setUp() {
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
  }

  @AfterEach
  void tearDown() {
    if (createdTriggerId != null) {
      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .delete(BASE_PATH + "/" + createdTriggerId);
      createdTriggerId = null;
    }
  }

  /**
   * 测试获取触发器列表 - 无过滤条件
   */
  @Test
  void testFindPageWithoutFilter() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(200)
      .body("total", greaterThanOrEqualTo(5))
      .body("list", hasSize(greaterThanOrEqualTo(5)))
      .body("list[0].id", notNullValue())
      .body("list[0].name", notNullValue())
      .body("list[0].type", notNullValue());
  }

  /**
   * 测试获取触发器列表 - 按名称过滤
   */
  @Test
  void testFindPageWithNameFilter() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .queryParam("name", "定时触发-间隔触发")
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(200)
      .body("total", equalTo(1))
      .body("list", hasSize(1))
      .body("list[0].name", equalTo("定时触发-间隔触发"));
  }

  /**
   * 测试获取触发器列表 - 分页
   */
  @Test
  void testFindPageWithPagination() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .queryParam("page", 1)
      .queryParam("size", 2)
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(200)
      .body("list", hasSize(lessThanOrEqualTo(2)))
      .body("total", greaterThanOrEqualTo(5));
  }

  /**
   * 测试创建触发器 - 间隔触发（state=true，应被Quartz调度）
   */
  @Test
  void testCreateIntervalTrigger() {
    String triggerJson = """
      {
          "name": "测试间隔触发",
          "description": "测试描述",
          "type": "SCHEDULED",
          "config": {
              "type": "interval",
              "interval": 5,
              "intervalUnit": "minute",
              "repeatCount": 10
          },
          "jobId": "%s",
          "jobType": "FLOW",
          "state": true
      }
      """.formatted(TEST_JOB_ID);

    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body(triggerJson)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .body("id", notNullValue())
      .body("name", equalTo("测试间隔触发"))
      .body("description", equalTo("测试描述"))
      .body("type", equalTo("SCHEDULED"))
      .body("state", equalTo(true))
      .body("jobId", equalTo(TEST_JOB_ID))
      .body("jobType", equalTo("FLOW"))
      .body("config.interval", equalTo(5))
      .body("config.intervalUnit", equalTo("minute"))
      .body("config.repeatCount", equalTo(10));
  }

  /**
   * 测试创建触发器 - Cron表达式触发（state=true，应被Quartz调度）
   */
  @Test
  void testCreateCronTrigger() {
    String triggerJson = """
      {
          "name": "测试Cron触发",
          "description": "测试Cron描述",
          "type": "SCHEDULED",
          "config": {
              "type": "cron",
              "cronExpression": "0 0 8 * * ?"
          },
          "jobId": "%s",
          "jobType": "FLOW",
          "state": true
      }
      """.formatted(TEST_JOB_ID);

    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body(triggerJson)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .body("id", notNullValue())
      .body("name", equalTo("测试Cron触发"))
      .body("type", equalTo("SCHEDULED"))
      .body("config.cronExpression", equalTo("0 0 8 * * ?"));
  }

  /**
   * 测试创建触发器 - 事件触发（非Quartz调度，仅DB记录）
   */
  @Test
  void testCreateEventTrigger() {
    String triggerJson = """
      {
          "name": "测试事件触发",
          "description": "测试事件描述",
          "type": "EVENT",
          "config": {
              "type": "event",
              "modelName": "Student",
              "mutationTypes": ["create", "update"],
              "triggerTiming": "after"
          },
          "jobId": "%s",
          "jobType": "FLOW",
          "state": true
      }
      """.formatted(TEST_JOB_ID);

    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body(triggerJson)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .body("id", notNullValue())
      .body("name", equalTo("测试事件触发"))
      .body("type", equalTo("EVENT"))
      .body("config.modelName", equalTo("Student"))
      .body("config.triggerTiming", equalTo("after"));
  }

  /**
   * 测试创建触发器 - state=false，不应被Quartz调度
   */
  @Test
  void testCreateDisabledTriggerNotScheduled() {
    String triggerJson = """
      {
          "name": "禁用的间隔触发",
          "type": "SCHEDULED",
          "config": {
              "type": "interval",
              "interval": 1,
              "intervalUnit": "minute",
              "repeatCount": 1
          },
          "jobId": "%s",
          "jobType": "FLOW",
          "state": false
      }
      """.formatted(TEST_JOB_ID);

    String triggerId = given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body(triggerJson)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .body("state", equalTo(false))
      .extract()
      .path("id");
    createdTriggerId = triggerId;

    // 验证可以被查询到
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH + "/" + triggerId)
      .then()
      .statusCode(200)
      .body("state", equalTo(false));
  }

  /**
   * 测试更新触发器 - 更新配置和状态
   */
  @Test
  void testUpdateTrigger() {
    String updateJson = """
      {
          "id": "%s",
          "name": "更新后的触发器名称",
          "description": "更新后的描述",
          "type": "SCHEDULED",
          "config": {
              "type": "interval",
              "interval": 10,
              "intervalUnit": "minute",
              "repeatCount": 20
          },
          "jobId": "%s",
          "jobType": "FLOW",
          "state": false
      }
      """.formatted(INTERVAL_TRIGGER_ID, TEST_JOB_ID);

    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body(updateJson)
      .when()
      .put(BASE_PATH + "/" + INTERVAL_TRIGGER_ID)
      .then()
      .statusCode(200)
      .body("id", equalTo(INTERVAL_TRIGGER_ID))
      .body("name", equalTo("更新后的触发器名称"))
      .body("description", equalTo("更新后的描述"))
      .body("state", equalTo(false))
      .body("config.interval", equalTo(10))
      .body("config.repeatCount", equalTo(20));
  }

  /**
   * 测试部分更新触发器 - 只更新状态为false
   */
  @Test
  void testPatchTriggerDisable() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        { "state": false }
        """)
      .when()
      .patch(BASE_PATH + "/" + CRON_TRIGGER_ID)
      .then()
      .statusCode(200)
      .body("id", equalTo(CRON_TRIGGER_ID))
      .body("name", equalTo("定时触发-Cron表达式"))
      .body("state", equalTo(false));
  }

  /**
   * 测试部分更新触发器 - 启用触发器（state: false → true）
   */
  @Test
  void testPatchTriggerEnable() {
    // INTERVAL_TRIGGER_ID 是seed中state=false的SCHEDULED触发器
    // PATCH state=true → TriggerService会调度到Quartz
    String patchJson = """
      { "state": true }
      """;

    String triggerId = given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body(patchJson)
      .when()
      .patch(BASE_PATH + "/" + INTERVAL_TRIGGER_ID)
      .then()
      .statusCode(200)
      .body("id", equalTo(INTERVAL_TRIGGER_ID))
      .body("state", equalTo(true))
      .extract()
      .path("id");

    // 验证状态已持久化
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH + "/" + triggerId)
      .then()
      .statusCode(200)
      .body("state", equalTo(true));

    // 恢复为禁用状态
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        { "state": false }
        """)
      .when()
      .patch(BASE_PATH + "/" + INTERVAL_TRIGGER_ID)
      .then()
      .statusCode(200)
      .body("state", equalTo(false));
  }

  /**
   * 测试立即执行触发器 - 已禁用的触发器应返回400
   */
  @Test
  void testExecuteNowDisabledTrigger() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .post(BASE_PATH + "/" + INTERVAL_TRIGGER_ID + "/execute")
      .then()
      .statusCode(400);
  }

  /**
   * 测试立即执行触发器 - 启用态的触发器应返回200
   */
  @Test
  void testExecuteNowEnabledTrigger() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .post(BASE_PATH + "/" + ENABLED_EVENT_TRIGGER_ID + "/execute")
      .then()
      .statusCode(200);
  }

  /**
   * 测试Quartz Scheduler元数据 - 验证Scheduler已启动且可用
   */
  @Test
  void testQuartzSchedulerIsRunning() throws SchedulerException {
    assertNotNull(quartz, "Quartz Scheduler 应被注入");
    assertTrue(quartz.isStarted(), "Quartz Scheduler 应已启动");
    assertFalse(quartz.isInStandbyMode(), "Quartz Scheduler 不应处于待机模式");
    assertNotNull(quartz.getMetaData(), "SchedulerMetaData 不应为 null");
  }

  /**
   * 测试删除触发器
   */
  @Test
  void testDeleteTrigger() {
    // 先创建一个触发器用于删除测试
    String triggerJson = """
      {
          "name": "待删除的触发器",
          "description": "用于删除测试",
          "type": "SCHEDULED",
          "config": {
              "type": "interval",
              "interval": 1,
              "intervalUnit": "minute",
              "repeatCount": 1
          },
          "jobId": "%s",
          "jobType": "FLOW",
          "state": true
      }
      """.formatted(TEST_JOB_ID);

    String createdTriggerId = given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body(triggerJson)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .extract()
      .path("id");

    // 删除创建的触发器
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + createdTriggerId)
      .then()
      .statusCode(204);

    // 验证触发器已被删除
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH + "/" + createdTriggerId)
      .then()
      .statusCode(204);
  }

  /**
   * 测试验证不同类型的触发器配置
   */
  @Test
  void testValidateDifferentTriggerTypes() {
    // 验证间隔触发配置
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH + "/" + INTERVAL_TRIGGER_ID)
      .then()
      .statusCode(200)
      .body("type", equalTo("SCHEDULED"))
      .body("config.interval", notNullValue())
      .body("config.intervalUnit", notNullValue())
      .body("config.repeatCount", notNullValue());

    // 验证Cron触发配置
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH + "/" + CRON_TRIGGER_ID)
      .then()
      .statusCode(200)
      .body("type", equalTo("SCHEDULED"))
      .body("config.cronExpression", equalTo("0 0 * * * ? *"));

    // 验证事件触发配置
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH + "/" + EVENT_AFTER_TRIGGER_ID)
      .then()
      .statusCode(200)
      .body("type", equalTo("EVENT"))
      .body("config.modelName", equalTo("Classes"))
      .body("config.mutationTypes", notNullValue())
      .body("config.triggerTiming", equalTo("after"));
  }

}
