package dev.flexmodel.auth.dto;

import dev.flexmodel.codegen.enumeration.UserStatus;

public record UserPatchRequest(
  UserStatus status,
  String avatar
) {
}
