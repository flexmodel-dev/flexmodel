package dev.flexmodel.common.config.web.json.jackson.mixin;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.flexmodel.idp.provider.ScriptProvider;
import dev.flexmodel.idp.provider.OIDCProvider;

/**
 * @author cjbi
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = OIDCProvider.class, name = "oidc"),
  @JsonSubTypes.Type(value = ScriptProvider.class, name = "script"),
})
public class IdentityProviderProviderMixIn {
}
