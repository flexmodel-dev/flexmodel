package dev.flexmodel.apps.ai;

import dev.flexmodel.codegen.entity.AppAiConversation;
import dev.flexmodel.codegen.entity.AppAiRobot;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.query.Direction;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static dev.flexmodel.codegen.System.appAiConversation;
import static dev.flexmodel.codegen.System.appAiRobot;

/**
 * 会话不按应用隔离，仅按机器人 + 用户存取（机器人本身归属应用，助手机器人不归属任何应用）；
 * app_id 列保存机器人所属应用（助手为 global），仅供删除应用时级联清理。
 */
@ApplicationScoped
public class AppAiRepository extends AbstractRepository {
  public List<AppAiRobot> robots(String appId) {
    try (var session = openSession()) {
      return session.dsl().selectFrom(AppAiRobot.class).where(appAiRobot.appId.eq(appId))
        .orderBy(appAiRobot.createdAt, Direction.ASC).execute();
    }
  }

  public AppAiRobot robot(String appId, String id) {
    try (var session = openSession()) {
      return session.dsl().selectFrom(AppAiRobot.class)
        .where(appAiRobot.appId.eq(appId).and(appAiRobot.id.eq(id))).executeOne();
    }
  }

  /**
   * 机器人 ID 全局唯一，可仅按 ID 查找（用于统一会话 API 反查机器人所属应用）。
   */
  public AppAiRobot robot(String id) {
    try (var session = openSession()) {
      return session.dsl().selectFrom(AppAiRobot.class).where(appAiRobot.id.eq(id)).executeOne();
    }
  }

  public void save(AppAiRobot robot) {
    try (var session = openSession()) {
      session.dsl().mergeInto(AppAiRobot.class).values(robot).execute();
    }
  }

  public List<AppAiConversation> conversations(String robotId, String userId) {
    try (var session = openSession()) {
      return session.dsl().selectFrom(AppAiConversation.class)
        .where(appAiConversation.robotId.eq(robotId).and(appAiConversation.userId.eq(userId)))
        .orderBy(appAiConversation.updatedAt, Direction.DESC).execute();
    }
  }

  public AppAiConversation conversation(String robotId, String userId, String id) {
    try (var session = openSession()) {
      return session.dsl().selectFrom(AppAiConversation.class)
        .where(appAiConversation.robotId.eq(robotId).and(appAiConversation.userId.eq(userId))
          .and(appAiConversation.id.eq(id))).executeOne();
    }
  }

  public AppAiConversation conversation(String userId, String id) {
    try (var session = openSession()) {
      return session.dsl().selectFrom(AppAiConversation.class)
        .where(appAiConversation.userId.eq(userId).and(appAiConversation.id.eq(id))).executeOne();
    }
  }

  public void save(AppAiConversation conversation) {
    try (var session = openSession()) {
      session.dsl().mergeInto(AppAiConversation.class).values(conversation).execute();
    }
  }

  public void deleteConversation(String userId, String id) {
    try (var session = openSession()) {
      session.dsl().deleteFrom(AppAiConversation.class)
        .where(appAiConversation.userId.eq(userId).and(appAiConversation.id.eq(id))).execute();
    }
  }

  public void deleteRobot(String appId, String id) {
    try (var session = openSession()) {
      session.dsl().deleteFrom(AppAiConversation.class)
        .where(appAiConversation.robotId.eq(id)).execute();
      session.dsl().deleteFrom(AppAiRobot.class)
        .where(appAiRobot.appId.eq(appId).and(appAiRobot.id.eq(id))).execute();
    }
  }

  public void deleteApp(String appId) {
    try (var session = openSession()) {
      session.dsl().deleteFrom(AppAiConversation.class).where(appAiConversation.appId.eq(appId)).execute();
      session.dsl().deleteFrom(AppAiRobot.class).where(appAiRobot.appId.eq(appId)).execute();
    }
  }
}
