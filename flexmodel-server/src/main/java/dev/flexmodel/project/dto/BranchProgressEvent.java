package dev.flexmodel.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 分支操作 SSE 进度事件。
 *
 * @author cjbi
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BranchProgressEvent {

  public enum Operation {
    CREATE,
    MERGE
  }

  public enum Type {
    STARTED,
    STAGE_COMPLETED,
    MODEL_STARTED,
    MODEL_COMPLETED,
    MODEL_SKIPPED,
    MODEL_FAILED,
    COMPLETED,
    FAILED
  }

  public enum Stage {
    VALIDATING,
    RESOLVING_SOURCE,
    CREATING_SCHEMA,
    COPYING_SCHEMA,
    MIGRATING_DATA,
    SAVING_RECORDS,
    REFRESHING_GRAPHQL,
    MERGING_SCHEMA,
    MERGING_DATA
  }

  private Operation operation;
  private Type type;
  private Stage stage;
  private String modelName;
  private int totalModels;
  private int processedModels;
  private int progress;
  private long sourceRecords;
  private long targetRecords;
  private long insertedRecords;
  private long updatedRecords;
  private String message;
  private Object result;
  private Instant timestamp;
}
