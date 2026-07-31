package dev.flexmodel.auth.service;

import dev.flexmodel.auth.dto.UserRequest;
import dev.flexmodel.auth.dto.UserResponse;
import dev.flexmodel.auth.exception.AuthException;
import dev.flexmodel.auth.repository.UserRepository;
import dev.flexmodel.common.InternalServerException;
import dev.flexmodel.common.NotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import dev.flexmodel.codegen.entity.Role;
import dev.flexmodel.codegen.entity.User;

import java.util.List;

/**
 * @author cjbi
 */
@ApplicationScoped
public class UserService {

  @Inject
  UserRepository userRepository;
  @Inject
  RoleService roleService;

  public User findByUsername(String username) {
    return userRepository.findByUsername(username);
  }

  public User findById(String userId) {
    return userRepository.findById(userId);
  }

  public List<User> findAll() {
    return userRepository.findAll();
  }

  public User save(User user) {
    return userRepository.save(user);
  }

  public void delete(String userId) {
    userRepository.delete(userId);
  }

  public User login(String username, String password) {
    User user = userRepository.findByUsername(username);
    if (user == null || !validateUser(username, password, user)) {
      throw new AuthException("Wrong username or password");
    }
    return user;
  }

  private boolean validateUser(String username, String password, User user) {
    try {
      return (user.getPasswordHash().equals(SecurityUtil.md5(username, password)));
    } catch (Exception e) {
      return false;
    }
  }

  public User getUser(String userId) {
    return userRepository.findById(userId);
  }

  public List<UserResponse> findAllUsers() {
    List<Role> allRoles = roleService.findAll();
    return findAll().stream()
      .map(user -> UserResponse.fromUser(user, allRoles))
      .toList();
  }

  public UserResponse findUserById(String userId) {
    User user = findById(userId);
    List<Role> allRoles = roleService.findAll();
    return user != null ? UserResponse.fromUser(user, allRoles) : null;
  }

  public UserResponse createUser(UserRequest request) {
    User user = new User();
    user.setId(request.getId());
    user.setName(request.getName());
    user.setEmail(request.getEmail());
    user.setCreatedBy(request.getCreatedBy());
    user.setUpdatedBy(request.getUpdatedBy());
    user.setRoleIds(String.join(",", request.getRoleIds()));

    if (request.getPassword() != null && !request.getPassword().isEmpty()) {
      try {
        user.setPasswordHash(SecurityUtil.md5(request.getId(), request.getPassword()));
      } catch (Exception e) {
        throw new InternalServerException("Failed to hash password", e);
      }
    }

    User savedUser = save(user);
    List<Role> allRoles = roleService.findAll();
    return UserResponse.fromUser(savedUser, allRoles);
  }

  public UserResponse updateUser(UserRequest request) {
    User existingUser = findById(request.getId());
    if (existingUser == null) {
      throw new NotFoundException("User not found");
    }

    User user = new User();
    user.setId(request.getId());
    user.setName(request.getName());
    user.setEmail(request.getEmail());
    user.setCreatedBy(existingUser.getCreatedBy());
    user.setUpdatedBy(request.getUpdatedBy());
    user.setCreatedAt(existingUser.getCreatedAt());
    user.setRoleIds(String.join(",", request.getRoleIds()));

    if (request.getPassword() != null && !request.getPassword().isEmpty()) {
      try {
        user.setPasswordHash(SecurityUtil.md5(request.getId(), request.getPassword()));
      } catch (Exception e) {
        throw new InternalServerException("Failed to hash password", e);
      }
    } else {
      user.setPasswordHash(existingUser.getPasswordHash());
    }

    User savedUser = save(user);
    List<Role> allRoles = roleService.findAll();
    return UserResponse.fromUser(savedUser, allRoles);
  }

  public void deleteUser(String userId) {
    delete(userId);
  }
}
