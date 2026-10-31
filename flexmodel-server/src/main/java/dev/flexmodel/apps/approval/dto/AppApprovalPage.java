package dev.flexmodel.apps.approval.dto;

import java.util.List;

public record AppApprovalPage<T>(List<T> list, int total) {
}
