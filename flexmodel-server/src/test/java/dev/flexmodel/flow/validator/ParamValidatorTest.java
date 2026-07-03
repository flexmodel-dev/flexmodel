package dev.flexmodel.flow.validator;

import dev.flexmodel.flow.dto.param.CreateFlowParam;
import dev.flexmodel.flow.dto.param.DeployFlowParam;
import dev.flexmodel.flow.dto.param.UpdateFlowParam;
import dev.flexmodel.flow.exception.ParamException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * ParamValidator 参数校验测试
 *
 * @author cjbi
 */
public class ParamValidatorTest {

  /**
   * 测试 UpdateFlowParam 校验 - projectId 为 null 时抛出异常
   */
  @Test
  void validateUpdateFlowParam_projectIdNull_throwsParamException() {
    UpdateFlowParam param = new UpdateFlowParam(null, "testCaller");
    param.setFlowModuleId("module-1");
    param.setFlowModel("{\"flowElementList\":[]}");

    ParamException exception = Assertions.assertThrows(ParamException.class,
      () -> ParamValidator.validate(param));
    Assertions.assertEquals("projectId is null", exception.getErrMsg());
  }

  /**
   * 测试 UpdateFlowParam 校验 - projectId 为空字符串时抛出异常
   */
  @Test
  void validateUpdateFlowParam_projectIdBlank_throwsParamException() {
    UpdateFlowParam param = new UpdateFlowParam("", "testCaller");
    param.setFlowModuleId("module-1");
    param.setFlowModel("{\"flowElementList\":[]}");

    ParamException exception = Assertions.assertThrows(ParamException.class,
      () -> ParamValidator.validate(param));
    Assertions.assertEquals("projectId is null", exception.getErrMsg());
  }

  /**
   * 测试 UpdateFlowParam 校验 - caller 为 null 时抛出异常
   */
  @Test
  void validateUpdateFlowParam_callerNull_throwsParamException() {
    UpdateFlowParam param = new UpdateFlowParam("dev_test", null);
    param.setFlowModuleId("module-1");
    param.setFlowModel("{\"flowElementList\":[]}");

    ParamException exception = Assertions.assertThrows(ParamException.class,
      () -> ParamValidator.validate(param));
    Assertions.assertEquals("caller is null", exception.getErrMsg());
  }

  /**
   * 测试 UpdateFlowParam 校验 - flowModuleId 为 null 时抛出异常
   */
  @Test
  void validateUpdateFlowParam_flowModuleIdNull_throwsParamException() {
    UpdateFlowParam param = new UpdateFlowParam("dev_test", "testCaller");
    param.setFlowModuleId(null);
    param.setFlowModel("{\"flowElementList\":[]}");

    ParamException exception = Assertions.assertThrows(ParamException.class,
      () -> ParamValidator.validate(param));
    Assertions.assertEquals("flowModuleId is null", exception.getErrMsg());
  }

  /**
   * 测试 UpdateFlowParam 校验 - flowModel 为 null 时抛出异常
   */
  @Test
  void validateUpdateFlowParam_flowModelNull_throwsParamException() {
    UpdateFlowParam param = new UpdateFlowParam("dev_test", "testCaller");
    param.setFlowModuleId("module-1");
    param.setFlowModel(null);

    ParamException exception = Assertions.assertThrows(ParamException.class,
      () -> ParamValidator.validate(param));
    Assertions.assertEquals("flowModel is null", exception.getErrMsg());
  }

  /**
   * 测试 UpdateFlowParam 校验 - param 为 null 时抛出异常
   */
  @Test
  void validateUpdateFlowParam_paramNull_throwsParamException() {
    ParamException exception = Assertions.assertThrows(ParamException.class,
      () -> ParamValidator.validate((UpdateFlowParam) null));
    Assertions.assertEquals("param is null", exception.getErrMsg());
  }

  /**
   * 测试 UpdateFlowParam 校验 - 所有参数合法时通过校验
   */
  @Test
  void validateUpdateFlowParam_validParams_noException() {
    UpdateFlowParam param = new UpdateFlowParam("dev_test", "testCaller");
    param.setFlowModuleId("module-1");
    param.setFlowModel("{\"flowElementList\":[]}");

    Assertions.assertDoesNotThrow(() -> ParamValidator.validate(param));
  }

  /**
   * 测试 CreateFlowParam 校验 - projectId 为 null 时抛出异常
   */
  @Test
  void validateCreateFlowParam_projectIdNull_throwsParamException() {
    CreateFlowParam param = new CreateFlowParam(null, "testCaller");

    ParamException exception = Assertions.assertThrows(ParamException.class,
      () -> ParamValidator.validate(param));
    Assertions.assertEquals("projectId is null", exception.getErrMsg());
  }

  /**
   * 测试 CreateFlowParam 校验 - caller 为 null 时抛出异常
   */
  @Test
  void validateCreateFlowParam_callerNull_throwsParamException() {
    CreateFlowParam param = new CreateFlowParam("dev_test", null);

    ParamException exception = Assertions.assertThrows(ParamException.class,
      () -> ParamValidator.validate(param));
    Assertions.assertEquals("caller is null", exception.getErrMsg());
  }

  /**
   * 测试 CreateFlowParam 校验 - 所有参数合法时通过校验
   */
  @Test
  void validateCreateFlowParam_validParams_noException() {
    CreateFlowParam param = new CreateFlowParam("dev_test", "testCaller");

    Assertions.assertDoesNotThrow(() -> ParamValidator.validate(param));
  }

  /**
   * 测试 DeployFlowParam 校验 - projectId 为 null 时抛出异常
   */
  @Test
  void validateDeployFlowParam_projectIdNull_throwsParamException() {
    DeployFlowParam param = new DeployFlowParam(null, "testCaller");
    param.setFlowModuleId("module-1");

    ParamException exception = Assertions.assertThrows(ParamException.class,
      () -> ParamValidator.validate(param));
    Assertions.assertEquals("projectId is null", exception.getErrMsg());
  }

  /**
   * 测试 DeployFlowParam 校验 - flowModuleId 为 null 时抛出异常
   */
  @Test
  void validateDeployFlowParam_flowModuleIdNull_throwsParamException() {
    DeployFlowParam param = new DeployFlowParam("dev_test", "testCaller");
    param.setFlowModuleId(null);

    ParamException exception = Assertions.assertThrows(ParamException.class,
      () -> ParamValidator.validate(param));
    Assertions.assertEquals("flowModuleId is null", exception.getErrMsg());
  }

  /**
   * 测试 DeployFlowParam 校验 - 所有参数合法时通过校验
   */
  @Test
  void validateDeployFlowParam_validParams_noException() {
    DeployFlowParam param = new DeployFlowParam("dev_test", "testCaller");
    param.setFlowModuleId("module-1");

    Assertions.assertDoesNotThrow(() -> ParamValidator.validate(param));
  }
}
