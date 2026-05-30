package dev.flexmodel.idp;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import dev.flexmodel.codegen.entity.IdentityProvider;

import java.util.List;


/**
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class IdentityProviderService {

  @Inject
  IdentityProviderRepository identityProviderRepository;

  public List<IdentityProvider> findAll() {
    return identityProviderRepository.findAll();
  }

  public void delete(String id) {
    identityProviderRepository.delete(id);
  }


  public IdentityProvider find(String providerName) {
    return identityProviderRepository.find(providerName);
  }


  public List<IdentityProvider> findAll(String projectId) {
    return identityProviderRepository.findAll();
  }

  public IdentityProvider createProvider(String projectId, IdentityProvider identityProvider) {
    return identityProviderRepository.save(identityProvider);
  }

  public IdentityProvider updateProvider(String projectId, IdentityProvider identityProvider) {
    IdentityProvider older = identityProviderRepository.find(identityProvider.getName());
    if (older == null) {
      return identityProvider;
    }
    identityProvider.setCreatedAt(older.getCreatedAt());
    return identityProviderRepository.save(identityProvider);
  }

  public void deleteProvider(String id, String name) {
    identityProviderRepository.delete(id);
  }

}
