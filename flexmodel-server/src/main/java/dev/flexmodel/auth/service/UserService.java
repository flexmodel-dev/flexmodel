package dev.flexmodel.auth.service;

import dev.flexmodel.auth.dto.UserRequest;
import dev.flexmodel.auth.dto.UserResponse;
import dev.flexmodel.auth.exception.AuthException;
import dev.flexmodel.auth.repository.UserRepository;
import dev.flexmodel.codegen.entity.Role;
import dev.flexmodel.codegen.entity.User;
import dev.flexmodel.codegen.enumeration.UserStatus;
import dev.flexmodel.common.InternalServerException;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class UserService {

  @Inject
  UserRepository userRepository;
  @Inject
  RoleService roleService;
  @Inject
  UserRoleService userRoleService;
  @Inject
  UserDepartmentService userDepartmentService;

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

  public User login(String username, String password) {
    User user = userRepository.findByUsername(username);
    if (user == null || !isActive(user) || !validateUser(username, password, user)) {
      throw new AuthException("Wrong username or password");
    }
    return user;
  }

  private boolean validateUser(String username, String password, User user) {
    try {
      return user.getPasswordHash().equals(SecurityUtil.md5(username, password));
    } catch (Exception e) {
      return false;
    }
  }

  public User getUser(String userId) {
    return userRepository.findById(userId);
  }

  public List<UserResponse> findAllUsers() {
    List<User> users = findAll();
    List<String> userIds = users.stream().map(User::getId).toList();
    Map<String, List<String>> roleIdsByUser = userRoleService.findRoleIdsByUserIds(userIds);
    Map<String, List<dev.flexmodel.auth.dto.UserDepartmentResponse>> departmentsByUser =
      userDepartmentService.findUserDepartmentsByUserIds(userIds);
    Map<String, Role> rolesById = roleService.findAll().stream()
      .collect(Collectors.toMap(Role::getId, role -> role));
    Map<String, User> usersById = users.stream()
      .collect(Collectors.toMap(User::getId, user -> user));

    return users.stream()
      .map(user -> UserResponse.fromUser(
        user,
        roleIdsByUser.getOrDefault(user.getId(), List.of()).stream()
          .map(rolesById::get)
          .filter(role -> role != null)
          .toList(),
        departmentsByUser.getOrDefault(user.getId(), List.of()),
        usersById.get(user.getManagerUserId())
      ))
      .toList();
  }

  public UserResponse findUserById(String userId) {
    User user = findById(userId);
    if (user == null) {
      return null;
    }
    List<String> roleIds = userRoleService.findRoleIdsByUserId(userId);
    List<Role> roles = roleService.findAll().stream()
      .filter(role -> roleIds.contains(role.getId()))
      .toList();
    var departments = userDepartmentService.findUserDepartmentsByUserIds(List.of(userId))
      .getOrDefault(userId, List.of());
    User managerUser = user.getManagerUserId() == null ? null : findById(user.getManagerUserId());
    return UserResponse.fromUser(user, roles, departments, managerUser);
  }

  public UserResponse createUser(UserRequest request) {
    UserStatus status = request.getStatus() == null ? UserStatus.ACTIVE : request.getStatus();
    if (status != UserStatus.INVITED && isEmptyPassword(request.getPassword())) {
      throw new ValidationException("Password is required unless the user is invited");
    }

    String userId = isEmpty(request.getId()) ? UUID.randomUUID().toString() : request.getId();
    User user = new User();
    user.setId(userId);
    user.setName(request.getName());
    user.setEmail(request.getEmail());
    user.setStatus(status);
    user.setCreatedBy(request.getCreatedBy());
    user.setUpdatedBy(request.getUpdatedBy());
    user.setManagerUserId(resolveManagerUserId(userId, request.getManagerUserId()));
    user.setPasswordHash(resolvePasswordHash(userId, request.getPassword(), status));

    User savedUser = save(user);
    userRoleService.replaceRelations(savedUser.getId(), request.getRoleIds(), request.getUpdatedBy());
    userDepartmentService.replaceRelations(
      savedUser.getId(),
      request.getDepartments(),
      request.getUpdatedBy()
    );
    return findUserById(savedUser.getId());
  }

  public UserResponse updateUser(UserRequest request, String currentUserId) {
    User existingUser = findById(request.getId());
    if (existingUser == null) {
      throw new NotFoundException("User not found");
    }

    UserStatus status = request.getStatus() == null
      ? normalizeStatus(existingUser.getStatus())
      : request.getStatus();
    if (status == UserStatus.DISABLED && request.getId().equals(currentUserId)) {
      throw new ValidationException("Current user cannot be disabled");
    }
    if (status == UserStatus.ACTIVE
      && normalizeStatus(existingUser.getStatus()) == UserStatus.INVITED
      && isEmptyPassword(request.getPassword())
      && isEmptyPassword(existingUser.getPasswordHash())) {
      throw new ValidationException("Password is required before activating an invited user");
    }

    User user = new User();
    user.setId(request.getId());
    user.setName(request.getName());
    user.setEmail(request.getEmail());
    user.setStatus(status);
    user.setCreatedBy(existingUser.getCreatedBy());
    user.setUpdatedBy(request.getUpdatedBy());
    user.setCreatedAt(existingUser.getCreatedAt());
    user.setManagerUserId(resolveManagerUserId(request.getId(), request.getManagerUserId()));
    user.setPasswordHash(resolvePasswordHash(request.getId(), request.getPassword(), status, existingUser));

    User savedUser = save(user);
    userRoleService.replaceRelations(savedUser.getId(), request.getRoleIds(), request.getUpdatedBy());
    userDepartmentService.replaceRelations(
      savedUser.getId(),
      request.getDepartments(),
      request.getUpdatedBy()
    );
    return findUserById(savedUser.getId());
  }

  public void deleteUser(String userId, String currentUserId) {
    if (userId.equals(currentUserId)) {
      throw new ValidationException("Current user cannot be deleted");
    }
    userRoleService.deleteRelations(userId);
    userDepartmentService.deleteRelations(userId);
    userRepository.delete(userId);
  }

  private String resolvePasswordHash(String userId, String password, UserStatus status) {
    if (isEmptyPassword(password)) {
      if (status == UserStatus.INVITED) {
        return "";
      }
      throw new ValidationException("Password is required unless the user is invited");
    }
    try {
      return SecurityUtil.md5(userId, password);
    } catch (Exception e) {
      throw new InternalServerException("Failed to hash password", e);
    }
  }

  private String resolvePasswordHash(
    String userId,
    String password,
    UserStatus status,
    User existingUser
  ) {
    if (isEmptyPassword(password)) {
      if (status == UserStatus.INVITED) {
        return "";
      }
      if (!isEmptyPassword(existingUser.getPasswordHash())) {
        return existingUser.getPasswordHash();
      }
      throw new ValidationException("Password is required unless the user is invited");
    }
    try {
      return SecurityUtil.md5(userId, password);
    } catch (Exception e) {
      throw new InternalServerException("Failed to hash password", e);
    }
  }

  private boolean isEmptyPassword(String password) {
    return password == null || password.isBlank();
  }

  private String resolveManagerUserId(String userId, String managerUserId) {
    if (managerUserId == null || managerUserId.isBlank()) {
      return null;
    }
    if (managerUserId.equals(userId)) {
      throw new ValidationException("User cannot be their own manager");
    }
    if (userRepository.findById(managerUserId) == null) {
      throw new NotFoundException("Manager user not found");
    }
    return managerUserId;
  }

  private boolean isEmpty(String value) {
    return value == null || value.isBlank();
  }

  private boolean isActive(User user) {
    return normalizeStatus(user.getStatus()) == UserStatus.ACTIVE;
  }

  private UserStatus normalizeStatus(UserStatus status) {
    return status == null ? UserStatus.ACTIVE : status;
  }
}
