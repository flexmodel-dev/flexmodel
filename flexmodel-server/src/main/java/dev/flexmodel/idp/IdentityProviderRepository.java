package dev.flexmodel.idp;

import dev.flexmodel.codegen.entity.IdentityProvider;

import java.util.List;

/**
 * @author cjbi
 */
public interface IdentityProviderRepository {

  List<IdentityProvider> findAll();

  IdentityProvider find(String name);

  IdentityProvider save(IdentityProvider identityProvider);

  void delete(String id);

}
