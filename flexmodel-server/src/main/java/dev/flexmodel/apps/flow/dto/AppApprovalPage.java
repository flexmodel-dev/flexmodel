package dev.flexmodel.apps.flow.dto;

import java.util.List;

public record AppApprovalPage<T>(List<T> list, int total) {
}
