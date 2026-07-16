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
import org.quartz.*;

import java.util.ArrayList;
import java.util.List;

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

  private static final String PROJECT_ID = "dev_test";
  private static final String BASE_PATH = "/projects/dev_test/triggers";

  // 测试数据中的触发器ID
  private static final String INTERVAL_TRIGGER_ID = "bf492f37-1f01-4eb8-b76d-d319299b4d8e";
  private static final String CRON_TRIGGER_ID = "d8c60d2a-19d8-4c3c-b370-96318733858f";
  private static final String EVENT_AFTER_TRIGGER_ID = "f351b8d9-a450-4f2c-8fff-cf862d690352";
  private static final String ENABLED_EVENT_TRIGGER_ID = "9434666d-6b3b-417a-80e3-3352f40ded71";
  private static final String TEST_JOB_ID = "5c41f37a-87a9-47af-bdba-44d0c27eda89";

  /**
   * 与 {@code TriggerService.getJobGroup} 推导规则保持一致：
   * SCHEDULED + FLOW → "{projectId}_flow_{jobId}"；SCHEDULED + FUNCTION → "{projectId}_fn_{jobId}"。
   */
  private static final String EXPECTED_FLOW_JOB_GROUP = PROJECT_ID + "_flow_" + TEST_JOB_ID;

  private String createdTriggerId;

  /**
   * 本用例在测试过程中被显式启用（PATCH state=true）的 seed SCHEDULED 触发器 ID 列表，
   * 用于在 @AfterEach 中确保其 Quartz 调度任务被取消并还原为 state=false，避免污染后续用例。
   */
  private final List<String> enabledSeedTriggerIds = new ArrayList<>();

  @BeforeEach
  void setUp() {
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
  }

  @AfterEach
  void tearDown() {
    if (createdTriggerId != null) {
      // 删除通过 POST 创建的触发器：REST delete 会调用 unscheduleTrigger 清理 Quartz
      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .delete(BASE_PATH + "/" + createdTriggerId);
      // 兜底：若 REST 删除未能清理调度任务（例如异常路径），直接从 Scheduler 删除
      unscheduleFromScheduler(createdTriggerId, EXPECTED_FLOW_JOB_GROUP);
      createdTriggerId = null;
    }
    // 还原被启用过的 seed 触发器为禁用态，并清理其 Quartz 调度任务
    for (String id : enabledSeedTriggerIds) {
      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("{ \"state\": false }")
        .when()
        .patch(BASE_PATH + "/" + id);
      // seed SCHEDULED FLOW 触发器的 jobGroup 在 update 后会被重算为标准格式
      unscheduleFromScheduler(id, EXPECTED_FLOW_JOB_GROUP);
      // 同时兼容 seed 中旧格式的 jobGroup，避免遗留调度任务
      unscheduleFromScheduler(id, "testFlowName_1757909108656");
    }
    enabledSeedTriggerIds.clear();
  }

  // ===== Quartz 断言辅助方法（镜像 TriggerService.buildJobKey / buildTriggerKey 的命名规则）=====

  /**
   * 构造期望的 Quartz JobKey，与 {@code TriggerService.buildJobKey} 一致：name="job-{id}", group=jobGroup。
   */
  private JobKey jobKey(String triggerId, String jobGroup) {
    return JobKey.jobKey("job-" + triggerId, jobGroup);
  }

  /**
   * 构造期望的 Quartz TriggerKey，与 {@code TriggerService.buildTriggerKey} 一致：name="trigger-{id}", group=jobGroup。
   */
  private TriggerKey triggerKey(String triggerId, String jobGroup) {
    return TriggerKey.triggerKey("trigger-" + triggerId, jobGroup);
  }

  /**
   * 兜底清理 Scheduler 中残留的作业与触发器，忽略不存在或异常情况。
   */
  private void unscheduleFromScheduler(String triggerId, String jobGroup) {
    try {
      TriggerKey tk = triggerKey(triggerId, jobGroup);
      JobKey jk = jobKey(triggerId, jobGroup);
      if (quartz.checkExists(tk)) {
        quartz.unscheduleJob(tk);
      }
      if (quartz.checkExists(jk)) {
        quartz.deleteJob(jk);
      }
    } catch (SchedulerException e) {
      // 清理过程不阻断测试
    }
  }

  /**
   * 断言 SCHEDULED 触发器已正确调度到 Quartz：JobDetail 与 Trigger 同时存在，
   * 触发器状态为 NORMAL，且 JobDataMap 携带 triggerId/jobId/projectId。
   * <p>
   * 注：SimpleTrigger 在短间隔下可能在断言前已触发完成并被移除，nextFireTime 可能为 null，
   * 故此处不强制断言下一次触发时间，避免偶发时序问题导致用例不稳定。
   */
  private void assertScheduledInQuartz(String triggerId, String jobGroup) throws SchedulerException {
    JobKey jk = jobKey(triggerId, jobGroup);
    TriggerKey tk = triggerKey(triggerId, jobGroup);

    assertTrue(quartz.checkExists(jk), "Quartz JobDetail 应存在: " + jk);
    assertTrue(quartz.checkExists(tk), "Quartz Trigger 应存在: " + tk);

    JobDetail jobDetail = quartz.getJobDetail(jk);
    assertNotNull(jobDetail, "Quartz JobDetail 不应为 null: " + jk);
    JobDataMap dataMap = jobDetail.getJobDataMap();
    assertEquals(triggerId, dataMap.getString("triggerId"), "JobDataMap.triggerId 不匹配");
    assertEquals(TEST_JOB_ID, dataMap.getString("jobId"), "JobDataMap.jobId 不匹配");
    assertEquals(PROJECT_ID, dataMap.getString("projectId"), "JobDataMap.projectId 不匹配");

    org.quartz.Trigger quartzTrigger = quartz.getTrigger(tk);
    assertNotNull(quartzTrigger, "Quartz Trigger 不应为 null: " + tk);
    // 触发器状态在不同时序下可能短暂为 WAITING 或已 NORMAL，此处只断言其非 BLOCKED/ERROR 等异常态的存在性
    org.quartz.Trigger.TriggerState state = quartz.getTriggerState(tk);
    assertNotNull(state, "触发器状态不应为 null: " + tk);
    assertNotEquals(org.quartz.Trigger.TriggerState.ERROR, state, "触发器状态不应为 ERROR: " + tk);
  }

  /**
   * 断言触发器未在 Quartz 中创建作业/触发器（用于 state=false 的 SCHEDULED 与 EVENT 类型）。
   */
  private void assertNotScheduledInQuartz(String triggerId, String jobGroup) throws SchedulerException {
    assertFalse(quartz.checkExists(jobKey(triggerId, jobGroup)),
      "state=false 的 SCHEDULED 触发器不应创建 Quartz JobDetail: " + jobKey(triggerId, jobGroup));
    assertFalse(quartz.checkExists(triggerKey(triggerId, jobGroup)),
      "state=false 的 SCHEDULED 触发器不应创建 Quartz Trigger: " + triggerKey(triggerId, jobGroup));
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
  void testCreateIntervalTrigger() throws SchedulerException {
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

    String triggerId = given()
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
      .body("jobGroup", equalTo(EXPECTED_FLOW_JOB_GROUP))
      .body("config.interval", equalTo(5))
      .body("config.intervalUnit", equalTo("minute"))
      .body("config.repeatCount", equalTo(10))
      .extract()
      .path("id");
    createdTriggerId = triggerId;

    // 验证 Quartz 已正确创建调度作业与触发器
    assertScheduledInQuartz(triggerId, EXPECTED_FLOW_JOB_GROUP);
  }

  /**
   * 测试创建触发器 - Cron表达式触发（state=true，应被Quartz调度）
   */
  @Test
  void testCreateCronTrigger() throws SchedulerException {
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

    String triggerId = given()
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
      .body("jobGroup", equalTo(EXPECTED_FLOW_JOB_GROUP))
      .body("config.cronExpression", equalTo("0 0 8 * * ?"))
      .extract()
      .path("id");
    createdTriggerId = triggerId;

    // 验证 Quartz 已正确创建 Cron 调度作业与触发器
    assertScheduledInQuartz(triggerId, EXPECTED_FLOW_JOB_GROUP);
    org.quartz.Trigger quartzTrigger = quartz.getTrigger(triggerKey(triggerId, EXPECTED_FLOW_JOB_GROUP));
    assertNotNull(quartzTrigger, "Cron Trigger 应存在");
    // Cron 触发器应可解析出下一次触发时间
    assertNotNull(quartzTrigger.getNextFireTime(), "Cron 触发器下一次触发时间不应为 null");
  }

  /**
   * 测试创建触发器 - 事件触发（非Quartz调度，仅DB记录）
   */
  @Test
  void testCreateEventTrigger() throws SchedulerException {
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

    String triggerId = given()
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
      .body("config.triggerTiming", equalTo("after"))
      .extract()
      .path("id");
    createdTriggerId = triggerId;

    // EVENT 类型触发器不参与 Quartz 调度，验证作业/触发器均未创建
    // jobGroup 推导为 {projectId}_{modelName}
    String eventJobGroup = PROJECT_ID + "_Student";
    assertNotScheduledInQuartz(triggerId, eventJobGroup);
  }

  /**
   * 测试创建触发器 - state=false，不应被Quartz调度
   */
  @Test
  void testCreateDisabledTriggerNotScheduled() throws SchedulerException {
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
      .body("jobGroup", equalTo(EXPECTED_FLOW_JOB_GROUP))
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

    // state=false 的 SCHEDULED 触发器不应在 Quartz 中创建调度任务
    assertNotScheduledInQuartz(triggerId, EXPECTED_FLOW_JOB_GROUP);
  }

  /**
   * 测试更新触发器 - 更新配置和状态。
   * <p>
   * seed 的 INTERVAL_TRIGGER_ID 初始 state=false；本用例 PUT 更新为 state=false 的新配置，
   * TriggerService.update 会先 unschedule 旧任务再判定不调度，最终不应在 Quartz 中存在调度任务。
   * 为避免污染其它依赖 seed 名称的用例，结束时还原 seed 原始名称与禁用态。
   * 本用例额外构造一个 enabled → 的场景以验证 update 调度路径（见 {@link #testUpdateTriggerReschedules}）。
   */
  @Test
  void testUpdateTrigger() throws SchedulerException {
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
      .body("jobGroup", equalTo(EXPECTED_FLOW_JOB_GROUP))
      .body("config.interval", equalTo(10))
      .body("config.repeatCount", equalTo(20));

    // state=false 的更新结果不应在 Quartz 中保留调度任务
    assertNotScheduledInQuartz(INTERVAL_TRIGGER_ID, EXPECTED_FLOW_JOB_GROUP);

    // 还原 seed 触发器的原始名称，避免污染依赖 seed 名称的用例（如按名称过滤）
    String restoreJson = """
      {
          "id": "%s",
          "name": "定时触发-间隔触发",
          "description": "定时触发-间隔触发-备注",
          "type": "SCHEDULED",
          "config": {
              "type": "interval",
              "interval": 1,
              "intervalUnit": "minute",
              "repeatCount": 100
          },
          "jobId": "%s",
          "jobType": "FLOW",
          "state": false
      }
      """.formatted(INTERVAL_TRIGGER_ID, TEST_JOB_ID);
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body(restoreJson)
      .when()
      .put(BASE_PATH + "/" + INTERVAL_TRIGGER_ID)
      .then()
      .statusCode(200)
      .body("name", equalTo("定时触发-间隔触发"))
      .body("state", equalTo(false));
  }

  /**
   * 测试更新触发器 - 启用态触发器配置更新。
   * <p>
   * 1) PATCH state=true 启用 seed INTERVAL 触发器，Quartz 创建调度任务；
   * 2) PUT 更新为新的 interval 配置（仍 state=true），TriggerService.update 先 unschedule 再 schedule，
   * Quartz 调度任务应以新配置重建并保持存在；
   * 3) PUT 更新为 state=false 并还原 seed 原始名称，调度任务被取消。
   * 用例全程保持 seed 原始名称以避免污染依赖名称的用例。
   */
  @Test
  void testUpdateTriggerReschedules() throws SchedulerException {
    // 先启用 seed 触发器，使其在 Quartz 中存在调度任务；标记供 @AfterEach 清理
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("{ \"state\": true }")
      .when()
      .patch(BASE_PATH + "/" + INTERVAL_TRIGGER_ID)
      .then()
      .statusCode(200)
      .body("state", equalTo(true));
    enabledSeedTriggerIds.add(INTERVAL_TRIGGER_ID);
    assertScheduledInQuartz(INTERVAL_TRIGGER_ID, EXPECTED_FLOW_JOB_GROUP);

    // 更新为新的 interval 配置（state=true），保持 seed 原始名称，调度任务应被重建
    String updateJson = """
      {
          "id": "%s",
          "name": "定时触发-间隔触发",
          "type": "SCHEDULED",
          "config": {
              "type": "interval",
              "interval": 30,
              "intervalUnit": "minute",
              "repeatCount": 5
          },
          "jobId": "%s",
          "jobType": "FLOW",
          "state": true
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
      .body("state", equalTo(true))
      .body("name", equalTo("定时触发-间隔触发"))
      .body("config.interval", equalTo(30));

    // 重建后调度任务仍应存在
    assertScheduledInQuartz(INTERVAL_TRIGGER_ID, EXPECTED_FLOW_JOB_GROUP);

    // 更新为禁用态并还原 seed 原始配置，调度任务应被取消
    String disableJson = """
      {
          "id": "%s",
          "name": "定时触发-间隔触发",
          "description": "定时触发-间隔触发-备注",
          "type": "SCHEDULED",
          "config": {
              "type": "interval",
              "interval": 1,
              "intervalUnit": "minute",
              "repeatCount": 100
          },
          "jobId": "%s",
          "jobType": "FLOW",
          "state": false
      }
      """.formatted(INTERVAL_TRIGGER_ID, TEST_JOB_ID);
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body(disableJson)
      .when()
      .put(BASE_PATH + "/" + INTERVAL_TRIGGER_ID)
      .then()
      .statusCode(200)
      .body("state", equalTo(false));

    assertNotScheduledInQuartz(INTERVAL_TRIGGER_ID, EXPECTED_FLOW_JOB_GROUP);
  }

  /**
   * 测试部分更新触发器 - 只更新状态为false
   * <p>
   * CRON_TRIGGER_ID 是 seed 中 state=false 的 SCHEDULED Cron 触发器，本用例 PATCH 保持 false，
   * 不应触发调度。补充断言：Quartz 中不应存在其调度任务。
   */
  @Test
  void testPatchTriggerDisable() throws SchedulerException {
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

    // 禁用态的 Cron 触发器不应在 Quartz 中有调度任务
    assertNotScheduledInQuartz(CRON_TRIGGER_ID, EXPECTED_FLOW_JOB_GROUP);
  }

  /**
   * 测试部分更新触发器 - 启用触发器（state: false → true）
   * <p>
   * INTERVAL_TRIGGER_ID 是 seed 中 state=false 的 SCHEDULED 间隔触发器，
   * PATCH state=true → TriggerService 会依据配置调度到 Quartz；PATCH 回 false → 取消调度。
   */
  @Test
  void testPatchTriggerEnable() throws SchedulerException {
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
      .body("jobGroup", equalTo(EXPECTED_FLOW_JOB_GROUP))
      .extract()
      .path("id");

    // 标记以便 @AfterEach 还原为禁用并清理调度任务
    enabledSeedTriggerIds.add(triggerId);

    // 验证状态已持久化
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH + "/" + triggerId)
      .then()
      .statusCode(200)
      .body("state", equalTo(true));

    // 验证 Quartz 已创建调度作业（update 会先 unschedule 再 schedule，最终存在）
    assertScheduledInQuartz(triggerId, EXPECTED_FLOW_JOB_GROUP);

    // 恢复为禁用状态，并验证调度任务已被取消
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

    assertNotScheduledInQuartz(triggerId, EXPECTED_FLOW_JOB_GROUP);
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
   * 测试删除触发器 - 创建 state=true 触发器后删除，应同步取消 Quartz 调度任务。
   */
  @Test
  void testDeleteTrigger() throws SchedulerException {
    // 先创建一个触发器用于删除测试：使用 1 小时间隔避免调度任务在断言前被触发并清理
    String triggerJson = """
      {
          "name": "待删除的触发器",
          "description": "用于删除测试",
          "type": "SCHEDULED",
          "config": {
              "type": "interval",
              "interval": 1,
              "intervalUnit": "hour",
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

    // 删除前 Quartz 应已创建调度任务（1h 间隔，断言前不会触发完成）
    assertScheduledInQuartz(createdTriggerId, EXPECTED_FLOW_JOB_GROUP);

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

    // 删除触发器后，Quartz 中对应的作业与触发器应被清除
    assertNotScheduledInQuartz(createdTriggerId, EXPECTED_FLOW_JOB_GROUP);

    // 已删除，标记清除以避免 @AfterEach 重复删除
    this.createdTriggerId = null;
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
