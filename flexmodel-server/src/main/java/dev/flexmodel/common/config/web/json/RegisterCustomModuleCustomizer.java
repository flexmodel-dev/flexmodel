package dev.flexmodel.common.config.web.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;
import dev.flexmodel.common.config.web.json.jackson.FlexmodelServerModule;

@Singleton
public class RegisterCustomModuleCustomizer implements ObjectMapperCustomizer {

  @Override
  public void customize(ObjectMapper objectMapper) {
    objectMapper.registerModule(new FlexmodelServerModule());
  }
}
