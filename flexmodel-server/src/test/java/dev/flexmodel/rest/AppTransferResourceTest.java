package dev.flexmodel.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.flexmodel.SQLiteTestResource;
import dev.flexmodel.apps.AppTransferExcel;
import dev.flexmodel.apps.dto.AppFormResponse;
import dev.flexmodel.apps.dto.AppTransfer.*;
import dev.flexmodel.data.DataService;
import dev.flexmodel.model.IndexDefinition;
import dev.flexmodel.session.SessionFactory;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.*;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
class AppTransferResourceTest {
  @Inject TestTokenHelper tokens;
  @Inject ObjectMapper json;
  @Inject AppTransferExcel excel;
  @Inject DataService data;
  @Inject SessionFactory factory;
  @Inject dev.flexmodel.common.persistence.ServerSessions sessions;
  static final String BASE = Resources.ROOT_PATH + "/apps/dev_test";
  static final String TRANSFER = BASE + "/transfers";
  String supplier, orders, items;
  final List<String> created = new ArrayList<>();

  @BeforeEach void setup() throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    supplier = "ts_" + suffix; orders = "to_" + suffix; items = "ti_" + suffix;
    create(supplier); create(orders); create(items);
    field(orders, "supplier_id", "REFERENCE", supplier);
    field(items, "order_id", "REFERENCE", orders);
    layout(supplier, List.of(Map.of("type", "FIELD", "name", "name")));
    layout(items, List.of(Map.of("type", "FIELD", "name", "name"), Map.of("type", "FIELD", "name", "order_id")));
    layout(orders, List.of(Map.of("type", "FIELD", "name", "name"), Map.of("type", "FIELD", "name", "supplier_id"),
      Map.of("type", "SUBFORM", "formName", items, "referenceField", "order_id")));
  }
  @AfterEach void cleanup() {
    Collections.reverse(created);
    for (String name : created) given().header("Authorization", tokens.getAuthorizationHeader()).delete(BASE + "/forms/" + name);
    created.clear();
  }

  @Test
  void roundTripsThreeFormsAndDeduplicatesSharedReferences() throws Exception {
    PreviewResponse preview = preview(upload(bundle(false, false), "admin"), "admin");
    assertEquals("READY", preview.status(), preview.errors().toString());
    assertEquals(4, commit(preview, "admin", 200).total());
    var before = rows(orders); String orderId = String.valueOf(before.getFirst().get("id"));
    var request = new ExportRequest(List.of(new FormSelection(orders, "IDS", null, List.of(orderId), null, null, null, null)), true, true);
    byte[] exported = post("/exports", request, 200).asByteArray();
    UploadResponse uploaded = upload(exported, "admin");
    assertEquals(3, uploaded.sheets().size());
    PreviewResponse next = preview(uploaded, "admin");
    assertTrue(next.errors().isEmpty(), next.errors().toString());
    assertEquals(4, commit(next, "admin", 200).total());
    assertEquals(2, rows(supplier).size()); assertEquals(2, rows(orders).size()); assertEquals(4, rows(items).size());
    var clone = rows(orders).stream().filter(row -> !orderId.equals(row.get("id"))).findFirst().orElseThrow();
    assertNotEquals(before.getFirst().get("supplier_id"), clone.get("supplier_id"));
    assertEquals(2, rows(items).stream().filter(row -> clone.get("id").equals(row.get("order_id"))).count());
    assertEquals(next.importId(), commit(next, "admin", 200).importId());
    assertEquals(2, rows(orders).size());
  }

  @Test void ordinaryUsersCanImportExistingReferencesButCannotAccessOtherUsersStages() throws Exception {
    String existing = given().header("Authorization", tokens.getAuthorizationHeader()).contentType("application/json")
      .body(Map.of("name", "已有供应商")).post(BASE + "/forms/" + supplier + "/records").then().statusCode(201).extract().path("id");
    byte[] bytes = excel.write(List.of(new ExportForm(form(orders), List.of("name", "supplier_id"),
      List.of(Map.of("id", "O", "name", "普通用户导入", "supplier_id", existing)))));
    var upload = upload(bytes, "ordinary-transfer-user");
    given().header("Authorization", tokens.getAuthorizationHeader()).contentType("application/json")
      .body(new PreviewRequest(upload.suggestions())).post(TRANSFER + "/imports/" + upload.uploadId() + "/preview").then().statusCode(404);
    var preview = preview(upload, "ordinary-transfer-user");
    commit(preview, "ordinary-transfer-user", 200);
    given().header("Authorization", tokens.getAuthorizationHeader()).get(TRANSFER + "/imports/" + preview.importId()).then().statusCode(404);
    given().get(TRANSFER + "/imports/" + preview.importId()).then().statusCode(401);
    assertEquals("ordinary-transfer-user", rows(orders).getFirst().get("owner_id"));
    assertEquals(existing, rows(orders).getFirst().get("supplier_id"));
    given().header("Authorization", tokens.getAuthorizationHeader("ordinary-transfer-user"))
      .get(TRANSFER + "/imports/" + preview.importId()).then().statusCode(200);
  }

  @Test void rejectsInvalidReferencesAndStaleOrTamperedPlans() throws Exception {
    var invalid = preview(upload(bundle(true, false), "admin"), "admin");
    assertEquals("INVALID", invalid.status()); assertFalse(invalid.errors().isEmpty());
    commit(invalid, "admin", 400); assertTrue(rows(supplier).isEmpty());
    var preview = preview(upload(bundle(false, false), "admin"), "admin");
    post("/imports/" + preview.importId() + "/commit", new CommitRequest("tampered"), 409);
    field(items, "extra", "TEXT", null);
    commit(preview, "admin", 409); assertTrue(rows(supplier).isEmpty());
  }

  @Test
  void rollsBackAllFormsWhenLaterRowViolatesUniqueConstraint() throws Exception {
    try (var session = factory.createSession("dev_test")) {
      session.schema().createIndex(new IndexDefinition(items, "uq_" + items).addField("name").setUnique(true));
    }
    var preview = preview(upload(bundle(false, true), "admin"), "admin");
    assertEquals("READY", preview.status());
    commit(preview, "admin", 400);
    assertTrue(rows(supplier).isEmpty()); assertTrue(rows(orders).isEmpty()); assertTrue(rows(items).isEmpty());
    given().header("Authorization", tokens.getAuthorizationHeader()).get(TRANSFER + "/imports/" + preview.importId())
      .then().statusCode(200).body("status", org.hamcrest.Matchers.equalTo("READY"));
  }

  @Test void concurrentCommitReturnsSameDurableResult() throws Exception {
    var preview = preview(upload(bundle(false, false), "admin"), "admin");
    try (var pool = Executors.newFixedThreadPool(2)) {
      var one = pool.submit(() -> commit(preview, "admin", 200));
      var two = pool.submit(() -> commit(preview, "admin", 200));
      assertEquals(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS));
    }
    assertEquals(1, rows(supplier).size()); assertEquals(1, rows(orders).size()); assertEquals(2, rows(items).size());
  }

  @Test void mapsOrdinaryWorkbookWithNonFirstHeaderAndDownloadsTemplate() throws Exception {
    byte[] bytes;
    try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet("手工表"); sheet.createRow(0).createCell(0).setCellValue("填写说明");
      sheet.createRow(1).createCell(0).setCellValue("名称"); sheet.createRow(2).createCell(0).setCellValue("00123,中文\n换行");
      workbook.write(out); bytes = out.toByteArray();
    }
    var uploaded = upload(bytes, "admin");
    var mapping = new SheetMapping("手工表", supplier, 2, null, List.of(new ColumnMapping(0, "name", "VALUE", null)));
    var preview = json.readValue(post("/imports/" + uploaded.uploadId() + "/preview", new PreviewRequest(List.of(mapping)), 200).asByteArray(), PreviewResponse.class);
    commit(preview, "admin", 200);
    assertEquals("00123,中文\n换行", rows(supplier).getFirst().get("name"));
    byte[] template = post("/templates", new ExportRequest(List.of(new FormSelection(orders, "ALL", null, null, null, null, null, null)), true, false), 200).asByteArray();
    assertEquals(2, upload(template, "admin").sheets().size());
  }

  @Test void filteredExportIncludesOnlyMatchingRootAndItsChildren() throws Exception {
    commit(preview(upload(bundle(false, false), "admin"), "admin"), "admin", 200);
    given().header("Authorization", tokens.getAuthorizationHeader()).contentType("application/json").body(Map.of("name", "不匹配"))
      .post(BASE + "/forms/" + orders + "/records").then().statusCode(201);
    var request = new ExportRequest(List.of(new FormSelection(orders, "FILTERED", null, null, "订单", "all", "name", "ASC")), true, false);
    var uploaded = upload(post("/exports", request, 200).asByteArray(), "admin");
    assertEquals(2, uploaded.sheets().size());
    var preview = preview(uploaded, "admin");
    assertEquals(3, preview.forms().stream().mapToInt(FormSummary::count).sum());
    assertTrue(preview.errors().isEmpty(), preview.errors().toString());
  }

  @Test void remapsTextBasedSubformLinksThatAreNotReferenceFields() throws Exception {
    field(items, "text_link", "TEXT", null);
    layout(orders, List.of(Map.of("type", "FIELD", "name", "name"),
      Map.of("type", "SUBFORM", "formName", items, "referenceField", "text_link")));
    commit(preview(upload(bundle(false, false), "admin"), "admin"), "admin", 200);
    String oldOrder = String.valueOf(rows(orders).getFirst().get("id"));
    for (var row : rows(items)) given().header("Authorization", tokens.getAuthorizationHeader()).contentType("application/json")
      .body(Map.of("text_link", oldOrder)).put(BASE + "/forms/" + items + "/records/" + row.get("id")).then().statusCode(200);
    var request = new ExportRequest(List.of(new FormSelection(orders, "IDS", null, List.of(oldOrder), null, null, null, null)), true, false);
    var preview = preview(upload(post("/exports", request, 200).asByteArray(), "admin"), "admin");
    assertTrue(preview.errors().isEmpty(), preview.errors().toString());
    assertEquals(3, commit(preview, "admin", 200).total());
    String newOrder = String.valueOf(rows(orders).stream().filter(row -> !oldOrder.equals(row.get("id"))).findFirst().orElseThrow().get("id"));
    assertEquals(2, rows(items).stream().filter(row -> newOrder.equals(row.get("text_link"))).count());
  }

  @Test void exportSnapshotRemainsConsistentWhileAnotherRequestWrites() throws Exception {
    commit(preview(upload(bundle(false, false), "admin"), "admin"), "admin", 200);
    String id = String.valueOf(rows(supplier).getFirst().get("id"));
    CompletableFuture<Integer> writer;
    try (var snapshot = sessions.openSnapshot("dev_test")) {
      snapshot.startTransaction();
      assertEquals("供应商", snapshot.data().findById(supplier, id).orElseThrow().get("name"));
      CountDownLatch started = new CountDownLatch(1);
      writer = CompletableFuture.supplyAsync(() -> {
        started.countDown();
        return given().header("Authorization", tokens.getAuthorizationHeader()).contentType("application/json")
          .body(Map.of("name", "并发修改后")).put(BASE + "/forms/" + supplier + "/records/" + id).statusCode();
      });
      assertTrue(started.await(5, TimeUnit.SECONDS));
      assertEquals("供应商", snapshot.data().findById(supplier, id).orElseThrow().get("name"));
      assertEquals(2, snapshot.dsl().selectFrom(items).execute().size());
      snapshot.commit();
    }
    assertEquals(200, writer.get(15, TimeUnit.SECONDS));
    assertEquals("并发修改后", rows(supplier).getFirst().get("name"));
  }

  @Test void includesDescendantsWhenSharedReferenceIsAlsoASelectedSubform() throws Exception {
    String grand = "tg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    create(grand); field(grand, "item_id", "REFERENCE", items);
    field(supplier, "linked", "REFERENCE", items);
    layout(grand, List.of(Map.of("type", "FIELD", "name", "name"), Map.of("type", "FIELD", "name", "item_id")));
    layout(items, List.of(Map.of("type", "FIELD", "name", "name"), Map.of("type", "FIELD", "name", "order_id"),
      Map.of("type", "SUBFORM", "formName", grand, "referenceField", "item_id")));
    layout(supplier, List.of(Map.of("type", "FIELD", "name", "name"), Map.of("type", "FIELD", "name", "linked")));
    commit(preview(upload(bundle(false, false), "admin"), "admin"), "admin", 200);
    String itemId = String.valueOf(rows(items).getFirst().get("id"));
    given().header("Authorization", tokens.getAuthorizationHeader()).contentType("application/json")
      .body(Map.of("name", "孙表记录", "item_id", itemId)).post(BASE + "/forms/" + grand + "/records").then().statusCode(201);
    given().header("Authorization", tokens.getAuthorizationHeader()).contentType("application/json")
      .body(Map.of("linked", itemId)).put(BASE + "/forms/" + supplier + "/records/" + rows(supplier).getFirst().get("id")).then().statusCode(200);
    var request = new ExportRequest(List.of(new FormSelection(supplier, "ALL", null, null, null, null, null, null),
      new FormSelection(orders, "ALL", null, null, null, null, null, null)), true, true);
    var uploaded = upload(post("/exports", request, 200).asByteArray(), "admin");
    assertEquals(4, uploaded.sheets().size());
    var grandSheet = uploaded.suggestions().stream().filter(mapping -> mapping.formName().equals(grand)).findFirst().orElseThrow().sheet();
    assertEquals(2, uploaded.sheets().stream().filter(sheet -> sheet.name().equals(grandSheet)).findFirst().orElseThrow().rowCount());
  }

  byte[] bundle(boolean missing, boolean duplicate) throws Exception {
    return excel.write(List.of(
      new ExportForm(form(supplier), List.of("name"), List.of(Map.of("id", "S1", "name", "供应商"))),
      new ExportForm(form(orders), List.of("name", "supplier_id"), List.of(Map.of("id", "O1", "name", "订单", "supplier_id", missing ? "missing" : "S1"))),
      new ExportForm(form(items), List.of("name", "order_id"), List.of(Map.of("id", "L1", "name", "明细一", "order_id", "O1"),
        Map.of("id", "L2", "name", duplicate ? "明细一" : "明细二", "order_id", "O1")))));
  }
  AppFormResponse form(String name) throws Exception {
    return json.readValue(given().header("Authorization", tokens.getAuthorizationHeader()).get(BASE + "/forms/" + name)
      .then().statusCode(200).extract().asByteArray(), AppFormResponse.class);
  }
  void create(String name) {
    given().header("Authorization", tokens.getAuthorizationHeader()).contentType("application/json")
      .body(Map.of("name", name, "label", name, "createDisplayField", true)).post(BASE + "/forms").then().statusCode(201); created.add(name);
  }
  void field(String name, String field, String type, String target) {
    Map<String, Object> request = new HashMap<>(Map.of("name", field, "label", field, "type", type, "required", false));
    if (target != null) request.put("referenceForm", target);
    given().header("Authorization", tokens.getAuthorizationHeader()).contentType("application/json").body(request)
      .post(BASE + "/forms/" + name + "/fields").then().statusCode(201);
  }
  void layout(String form, List<Map<String, String>> items) {
    given().header("Authorization", tokens.getAuthorizationHeader()).contentType("application/json")
      .body(Map.of("items", items)).put(BASE + "/forms/" + form + "/layout").then().statusCode(200);
  }
  UploadResponse upload(byte[] bytes, String user) throws Exception {
    return json.readValue(given().header("Authorization", tokens.getAuthorizationHeader(user))
      .multiPart("file", "test.xlsx", bytes, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
      .post(TRANSFER + "/imports").then().statusCode(200).extract().asByteArray(), UploadResponse.class);
  }
  PreviewResponse preview(UploadResponse upload, String user) throws Exception {
    return json.readValue(given().header("Authorization", tokens.getAuthorizationHeader(user)).contentType("application/json")
      .body(new PreviewRequest(upload.suggestions())).post(TRANSFER + "/imports/" + upload.uploadId() + "/preview")
      .then().statusCode(200).extract().asByteArray(), PreviewResponse.class);
  }
  TransferResult commit(PreviewResponse preview, String user, int status) throws Exception {
    var response = given().header("Authorization", tokens.getAuthorizationHeader(user)).contentType("application/json")
      .body(new CommitRequest(preview.planHash())).post(TRANSFER + "/imports/" + preview.importId() + "/commit")
      .then().statusCode(status).extract().response();
    return status == 200 ? json.readValue(response.asByteArray(), TransferResult.class) : null;
  }
  Response post(String path, Object request, int status) {
    return given().header("Authorization", tokens.getAuthorizationHeader()).contentType("application/json").body(request)
      .post(TRANSFER + path).then().statusCode(status).extract().response();
  }
  List<Map<String, Object>> rows(String form) { return data.findRecords("dev_test", "dev_test", form, 1, 100, null, null, List.of()); }
}
