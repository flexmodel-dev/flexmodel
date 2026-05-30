package dev.flexmodel.scheduling.dto;

import lombok.Getter;
import lombok.Setter;
import dev.flexmodel.codegen.entity.Trigger;

import java.time.LocalDateTime;

/**
 * @author cjbi
 */
@Getter
@Setter
public class TriggerDTO extends Trigger {

  private String jobName;
  private LocalDateTime nextFireTime;
  private LocalDateTime previousFireTime;

}
