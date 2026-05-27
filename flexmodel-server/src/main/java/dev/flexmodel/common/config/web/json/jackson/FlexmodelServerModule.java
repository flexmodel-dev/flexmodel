package dev.flexmodel.common.config.web.json.jackson;

import dev.flexmodel.connect.database.Database;
import dev.flexmodel.idp.provider.Provider;
import dev.flexmodel.scheduling.config.TriggerConfig;
import dev.flexmodel.common.config.web.json.jackson.mixin.DatasourceDatabaseMixIn;
import dev.flexmodel.common.config.web.json.jackson.mixin.IdentityProviderProviderMixIn;
import dev.flexmodel.common.config.web.json.jackson.mixin.ScheduledTriggerConfigMixIn;
import dev.flexmodel.supports.jackson.FlexmodelCoreModule;

/**
 * @author cjbi
 */
public class FlexmodelServerModule extends FlexmodelCoreModule {

  public FlexmodelServerModule() {
    super();
    this.setMixInAnnotation(Database.class, DatasourceDatabaseMixIn.class);
    this.setMixInAnnotation(Provider.class, IdentityProviderProviderMixIn.class);
    this.setMixInAnnotation(TriggerConfig.class, ScheduledTriggerConfigMixIn.class);
  }
}
