package dev.flexmodel.common.config.web.json.jackson;

import dev.flexmodel.projectauth.provider.AuthProvider;
import dev.flexmodel.scheduling.config.TriggerConfig;
import dev.flexmodel.common.config.web.json.jackson.mixin.AuthProviderMixIn;
import dev.flexmodel.common.config.web.json.jackson.mixin.ScheduledTriggerConfigMixIn;
import dev.flexmodel.supports.jackson.FlexmodelCoreModule;

/**
 * @author cjbi
 */
public class FlexmodelServerModule extends FlexmodelCoreModule {

  public FlexmodelServerModule() {
    super();
    this.setMixInAnnotation(AuthProvider.class, AuthProviderMixIn.class);
    this.setMixInAnnotation(TriggerConfig.class, ScheduledTriggerConfigMixIn.class);
  }
}
