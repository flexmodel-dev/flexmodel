package dev.flexmodel.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import dev.flexmodel.codegen.entity.AiChatMessage;
import dev.flexmodel.domain.model.ai.AiChatMessageRepository;
import dev.flexmodel.query.Direction;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.session.Session;

import java.util.List;

import static dev.flexmodel.query.Expressions.field;

@ApplicationScoped
public class AiChatMessageFmRepository extends AbstractRepository implements AiChatMessageRepository {

  @Override
  public List<AiChatMessage> findAll(String projectId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(AiChatMessage.class)
        .where(field(AiChatMessage::getProjectId).eq(projectId))
        .execute();
    }
  }

  @Override
  public List<AiChatMessage> find(String projectId, Predicate filter) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(AiChatMessage.class)
        .where(field(AiChatMessage::getProjectId).eq(projectId).and(filter))
        .execute();
    }
  }

  @Override
  public List<AiChatMessage> findByConversationId(String projectId, String conversationId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(AiChatMessage.class)
        .where(field(AiChatMessage::getProjectId).eq(projectId).and(field(AiChatMessage::getConversationId).eq(conversationId)))
        .orderBy("created_at", Direction.ASC)
        .execute();
    }
  }

  @Override
  public AiChatMessage findById(String projectId, String id) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(AiChatMessage.class)
        .where(field(AiChatMessage::getProjectId).eq(projectId).and(field(AiChatMessage::getId).eq(id)))
        .executeOne();
    }
  }

  @Override
  public AiChatMessage save(String projectId, AiChatMessage message) {
    try (Session session = getProjectSession(projectId)) {
      session.dsl()
        .mergeInto(AiChatMessage.class)
        .values(message)
        .execute();
    }
    return message;
  }

  @Override
  public void delete(String projectId, String id) {
    try (Session session = getProjectSession(projectId)) {
      session.dsl().deleteFrom(AiChatMessage.class)
        .where(field(AiChatMessage::getProjectId).eq(projectId).and(field(AiChatMessage::getId).eq(id)))
        .execute();
    }
  }

  @Override
  public void deleteByConversationId(String projectId, String conversationId) {
    try (Session session = getProjectSession(projectId)) {
      session.dsl().deleteFrom(AiChatMessage.class)
        .where(field(AiChatMessage::getProjectId).eq(projectId).and(field(AiChatMessage::getConversationId).eq(conversationId)))
        .execute();
    }
  }
}
