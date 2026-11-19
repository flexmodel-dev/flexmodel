package dev.flexmodel.common.config.web.json.jackson.mixin;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.flexmodel.projectauth.provider.OidcAuthProvider;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = OidcAuthProvider.class, name = "oidc"),
})
public class AuthProviderMixIn {
}
