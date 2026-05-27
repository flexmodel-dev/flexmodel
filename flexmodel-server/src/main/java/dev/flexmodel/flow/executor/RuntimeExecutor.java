package dev.flexmodel.flow.executor;


import dev.flexmodel.flow.repository.*;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.flexmodel.flow.exception.ProcessException;
import dev.flexmodel.flow.plugin.IdGeneratorPlugin;
import dev.flexmodel.flow.plugin.manager.PluginManager;
import dev.flexmodel.flow.common.RuntimeContext;
import dev.flexmodel.flow.common.util.IdGenerator;
import dev.flexmodel.flow.common.util.StrongUuidGenerator;

import java.util.List;

public abstract class RuntimeExecutor {

  protected static final Logger LOGGER = LoggerFactory.getLogger(RuntimeExecutor.class);

  @Inject
  protected InstanceDataRepository instanceDataRepository;

  @Inject
  protected NodeInstanceRepository nodeInstanceRepository;

  @Inject
  protected FlowInstanceRepository processInstanceRepository;

  @Inject
  protected NodeInstanceLogRepository nodeInstanceLogRepository;

  private IdGenerator ID_GENERATOR;
  @Inject
  protected FlowInstanceMappingRepository flowInstanceMappingRepository;

  @Inject
  protected PluginManager pluginManager;

  protected String genId() {
    if (null == ID_GENERATOR) {
      List<IdGeneratorPlugin> idGeneratorPlugins = pluginManager.getPluginsFor(IdGeneratorPlugin.class);
      if (!idGeneratorPlugins.isEmpty()) {
        ID_GENERATOR = idGeneratorPlugins.get(0).getIdGenerator();
      } else {
        ID_GENERATOR = new StrongUuidGenerator();
      }
    }
    return ID_GENERATOR.getNextId();
  }

  public abstract void execute(RuntimeContext runtimeContext) throws ProcessException;

  public abstract void commit(RuntimeContext runtimeContext) throws ProcessException;

  public abstract void rollback(RuntimeContext runtimeContext) throws ProcessException;

  protected abstract boolean isCompleted(RuntimeContext runtimeContext) throws ProcessException;

  protected boolean isSubFlowInstance(RuntimeContext runtimeContext) throws ProcessException {
    return runtimeContext.getParentRuntimeContext() != null;
  }

  protected abstract RuntimeExecutor getExecuteExecutor(RuntimeContext runtimeContext) throws ProcessException;

  protected abstract RuntimeExecutor getRollbackExecutor(RuntimeContext runtimeContext) throws ProcessException;
}
