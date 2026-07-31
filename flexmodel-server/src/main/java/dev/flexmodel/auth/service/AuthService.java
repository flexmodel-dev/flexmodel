package dev.flexmodel.auth.service;

import dev.flexmodel.codegen.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;

/**
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class AuthService {

  @Inject
  UserService userService;
  @Inject
  RoleService roleService;

  public User login(String username, String password) {
    return userService.login(username, password);
  }

  public List<String> findPermissions(String userId) {
    User user = userService.findById(userId);
    List<String> roleIds = Arrays.stream(user.getRoleIds().split(","))
      .filter(roleId -> !roleId.isEmpty())
      .toList();
    return roleService.findPermissions(roleIds);
  }

  public User getUser(String userId) {
    return userService.getUser(userId);
  }

}
