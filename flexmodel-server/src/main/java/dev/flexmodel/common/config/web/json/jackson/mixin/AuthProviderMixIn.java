package dev.flexmodel.common.config.web.json.jackson.mixin;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.flexmodel.projectauth.provider.OidcAuthProvider;
import dev.flexmodel.projectauth.provider.FunctionAuthProvider;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = OidcAuthProvider.class, name = "oidc"),
  @JsonSubTypes.Type(value = FunctionAuthProvider.class, name = "function"),
})
public class AuthProviderMixIn {
}
