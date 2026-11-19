package dev.flexmodel.auth.service;

import dev.flexmodel.codegen.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@ApplicationScoped
public class AuthService {

  @Inject
  UserService userService;
  @Inject
  UserRoleService userRoleService;
  @Inject
  RoleService roleService;

  public User login(String username, String password) {
    return userService.login(username, password);
  }

  public User findByUsername(String username) {
    return userService.findByUsername(username);
  }

  public List<String> findPermissions(String userId) {
    List<String> roleIds = userRoleService.findRoleIdsByUserId(userId);
    return roleService.findPermissions(roleIds);
  }

  public User getUser(String userId) {
    return userService.getUser(userId);
  }
}
