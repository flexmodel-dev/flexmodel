package dev.flexmodel.apps;

import dev.flexmodel.SQLiteTestResource;
import dev.flexmodel.apps.dto.AppCreateRequest;
import dev.flexmodel.apps.dto.AppFormCreateRequest;
import dev.flexmodel.apps.dto.AppFormFieldCreateRequest;
import dev.flexmodel.apps.dto.AppFormResponse;
import dev.flexmodel.apps.dto.WorkbenchChartData;
import dev.flexmodel.apps.dto.WorkbenchChartConfig;
import dev.flexmodel.apps.dto.WorkbenchComponentDataResponse;
import dev.flexmodel.apps.dto.WorkbenchComponentRecord;
import dev.flexmodel.apps.dto.WorkbenchCreateRequest;
import dev.flexmodel.apps.dto.WorkbenchDataLoaderConfig;
import dev.flexmodel.apps.dto.WorkbenchDataLoaderData;
import dev.flexmodel.apps.dto.WorkbenchLayoutRecord;
import dev.flexmodel.apps.dto.WorkbenchMetricConfig;
import dev.flexmodel.apps.dto.WorkbenchMetricData;
import dev.flexmodel.apps.dto.WorkbenchPlacementRecord;
import dev.flexmodel.apps.dto.WorkbenchResponse;
import dev.flexmodel.apps.dto.WorkbenchTasksConfig;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.ValidationException;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
class WorkbenchDataServiceTest {

  private static final String SUFFIX = Long.toHexString(System.nanoTime());

  @Inject
  AppService appService;

  @Inject
  AppFormService appFormService;

  @Inject
  AppRecordService appRecordService;

  @Inject
  WorkbenchService workbenchService;

  @Inject
  WorkbenchDataService workbenchDataService;

  @Test
  void metricCountSumAndAverageUseFormRecords() {
    String appId = uniqueAppId("metrics");
    try {
      AppFormResponse form = createAppAndForm(appId);
      createRecords(appId, form.name());
      WorkbenchResponse workbench = createWorkbench(appId, form.name(), "count");

      WorkbenchComponentDataResponse count = workbenchDataService.findComponentData(
        appId, workbench.id(), "metric", null, null, null);
      assertInstanceOf(WorkbenchMetricData.class, count.data());
      assertEquals(3, ((WorkbenchMetricData) count.data()).value());

      WorkbenchResponse sumWorkbench = createWorkbench(appId, form.name(), "sum");
      WorkbenchComponentDataResponse sum = workbenchDataService.findComponentData(
        appId, sumWorkbench.id(), "metric", null, null, null);
      assertInstanceOf(WorkbenchMetricData.class, sum.data());
      assertEquals(400, ((WorkbenchMetricData) sum.data()).value());

      WorkbenchResponse averageWorkbench = createWorkbench(appId, form.name(), "avg");
      WorkbenchComponentDataResponse average = workbenchDataService.findComponentData(
        appId, averageWorkbench.id(), "metric", null, null, null);
      assertInstanceOf(WorkbenchMetricData.class, average.data());
      assertEquals(400.0 / 3, ((WorkbenchMetricData) average.data()).value().doubleValue(), 0.000001);
    } finally {
      deleteAppQuietly(appId);
    }
  }

  @Test
  void chartGroupsByScalarDimensionAndDataloaderSupportsPagingAndSearch() {
    String appId = uniqueAppId("records");
    try {
      AppFormResponse form = createAppAndForm(appId);
      createRecords(appId, form.name());
      WorkbenchResponse workbench = createWorkbench(appId, form.name(), "count");

      WorkbenchComponentDataResponse chart = workbenchDataService.findComponentData(
        appId, workbench.id(), "chart", null, null, null);
      assertInstanceOf(WorkbenchChartData.class, chart.data());
      assertEquals(2, ((WorkbenchChartData) chart.data()).rows().size());
      assertEquals("Alpha", ((WorkbenchChartData) chart.data()).rows().getFirst().dimension());
      assertEquals(350, ((WorkbenchChartData) chart.data()).rows().getFirst().value());

      WorkbenchComponentDataResponse firstPage = workbenchDataService.findComponentData(
        appId, workbench.id(), "records", 1, 2, null);
      assertInstanceOf(WorkbenchDataLoaderData.class, firstPage.data());
      assertEquals(2, ((WorkbenchDataLoaderData) firstPage.data()).list().size());
      assertEquals(3, ((WorkbenchDataLoaderData) firstPage.data()).total());

      WorkbenchComponentDataResponse searchResult = workbenchDataService.findComponentData(
        appId, workbench.id(), "records", 1, 20, "Alpha");
      assertInstanceOf(WorkbenchDataLoaderData.class, searchResult.data());
      assertEquals(2, ((WorkbenchDataLoaderData) searchResult.data()).total());
    } finally {
      deleteAppQuietly(appId);
    }
  }

  @Test
  void validatesUnsupportedComponentAndPageSize() {
    String appId = uniqueAppId("validation");
    try {
      AppFormResponse form = createAppAndForm(appId);
      WorkbenchResponse workbench = createWorkbench(appId, form.name(), "count");

      assertThrows(ValidationException.class, () -> workbenchDataService.findComponentData(
        appId, workbench.id(), "records", 1, 51, null));
      assertThrows(ValidationException.class, () -> workbenchDataService.findComponentData(
        appId, workbench.id(), "tasks", null, null, null));
      assertThrows(NotFoundException.class, () -> workbenchDataService.findComponentData(
        appId, workbench.id(), "missing", null, null, null));
    } finally {
      deleteAppQuietly(appId);
    }
  }

  @Test
  void rejectsNonNumericMetricFieldAndNonGroupableChartDimension() {
    String appId = uniqueAppId("config_validation");
    try {
      appService.createApp(new AppCreateRequest(
        "Workbench data test", "", appId, "Boxes", "indigo"));
      AppFormResponse form = appFormService.createFormWithFields(
        appId,
        new AppFormCreateRequest("Orders", formName(appId), "", false),
        List.of(
          new AppFormFieldCreateRequest("Amount", "amount", "DECIMAL", true, null, null),
          new AppFormFieldCreateRequest("Customer", "customer", "TEXT", true, null, null),
          new AppFormFieldCreateRequest("Receipt", "receipt", "IMAGE", false, null, null)));

      assertThrows(ValidationException.class, () -> createWorkbenchWithMetric(
        appId, form.name(), "sum", "customer"));
      assertThrows(ValidationException.class, () -> createWorkbenchWithChart(
        appId, form.name(), "receipt"));
    } finally {
      deleteAppQuietly(appId);
    }
  }

  private AppFormResponse createAppAndForm(String appId) {
    appService.createApp(new AppCreateRequest(
      "Workbench data test", "", appId, "Boxes", "indigo"));
    return appFormService.createFormWithFields(
      appId,
      new AppFormCreateRequest("Orders", formName(appId), "", false),
      List.of(
        new AppFormFieldCreateRequest("Amount", "amount", "DECIMAL", true, null, null),
        new AppFormFieldCreateRequest("Customer", "customer", "TEXT", true, null, null),
        new AppFormFieldCreateRequest("Category", "category", "TEXT", false, null, null)));
  }

  private void createRecords(String appId, String formName) {
    appRecordService.createRecord(appId, formName, Map.of(
      "amount", 100, "customer", "Alpha customer", "category", "Alpha"), null, null, null);
    appRecordService.createRecord(appId, formName, Map.of(
      "amount", 250, "customer", "Alpha vip", "category", "Alpha"), null, null, null);
    appRecordService.createRecord(appId, formName, Map.of(
      "amount", 50, "customer", "Beta customer", "category", "Beta"), null, null, null);
  }

  private WorkbenchResponse createWorkbench(String appId, String formName, String aggregation) {
    WorkbenchMetricConfig metric = new WorkbenchMetricConfig(
      formName, aggregation, "count".equals(aggregation) ? null : "amount");
    WorkbenchChartConfig chart = new WorkbenchChartConfig(
      "bar", formName, "category", "amount", "sum", true, true, false, "primary");
    WorkbenchDataLoaderConfig records = new WorkbenchDataLoaderConfig(
      formName, List.of("id", "customer", "amount", "category"));
    return saveWorkbench(appId, metric, chart, records);
  }

  private WorkbenchResponse createWorkbenchWithMetric(
    String appId,
    String formName,
    String aggregation,
    String field
  ) {
    WorkbenchMetricConfig metric = new WorkbenchMetricConfig(formName, aggregation, field);
    WorkbenchDataLoaderConfig records = new WorkbenchDataLoaderConfig(
      formName, List.of("id", "customer", "amount", "category"));
    return saveWorkbench(appId, metric, null, records);
  }

  private WorkbenchResponse createWorkbenchWithChart(String appId, String formName, String dimensionField) {
    WorkbenchChartConfig chart = new WorkbenchChartConfig(
      "bar", formName, dimensionField, null, "count", true, true, false, "primary");
    return saveWorkbench(appId, null, chart, null);
  }

  private WorkbenchResponse saveWorkbench(
    String appId,
    WorkbenchMetricConfig metric,
    WorkbenchChartConfig chart,
    WorkbenchDataLoaderConfig records
  ) {
    List<WorkbenchComponentRecord> components = new ArrayList<>();
    if (metric != null) {
      components.add(new WorkbenchComponentRecord(
        "metric", "Metric", new WorkbenchPlacementRecord(0, 0, 3, 3), metric));
    }
    if (chart != null) {
      components.add(new WorkbenchComponentRecord(
        "chart", "Chart", new WorkbenchPlacementRecord(3, 0, 6, 4), chart));
    }
    if (records != null) {
      components.add(new WorkbenchComponentRecord(
        "records", "Records", new WorkbenchPlacementRecord(0, 4, 12, 5), records));
    }
    components.add(new WorkbenchComponentRecord(
      "tasks", "Tasks", new WorkbenchPlacementRecord(9, 0, 3, 3), new WorkbenchTasksConfig()));
    WorkbenchCreateRequest request = new WorkbenchCreateRequest(
      "Workbench data-" + UUID.randomUUID(),
      null,
      new WorkbenchLayoutRecord(WorkbenchLayoutRecord.CURRENT_SCHEMA_VERSION, components));
    return workbenchService.createWorkbench(appId, request);
  }

  private String uniqueAppId(String name) {
    return ("wb_data_" + name + "_" + SUFFIX).toLowerCase();
  }

  private String formName(String appId) {
    return "orders_" + SUFFIX + "_" + appId.length();
  }

  private void deleteAppQuietly(String appId) {
    try {
      appService.deleteApp(appId);
    } catch (Exception ignored) {
      // 应用未创建或已被服务端清理时不阻断测试结果。
    }
  }
}
