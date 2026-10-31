package dev.flexmodel.apps.record;

import dev.flexmodel.SQLiteTestResource;
import dev.flexmodel.common.messaging.ReliableMessages;
import dev.flexmodel.notification.NotificationRepository;
import dev.flexmodel.rest.TestTokenHelper;
import dev.flexmodel.session.SessionFactory;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(AppRecordDiscussionNotificationTest.Profile.class)
@QuarkusTestResource(SQLiteTestResource.class)
class AppRecordDiscussionNotificationTest {
  public static class Profile implements QuarkusTestProfile {
    public Map<String, String> getConfigOverrides() {
      return Map.of("quarkus.scheduler.enabled", "false");
    }
  }

  @Inject
  TestTokenHelper tokens;
  @Inject
  SessionFactory sessions;
  @Inject
  ReliableMessages messages;
  @Inject
  NotificationRepository inbox;

  private io.restassured.specification.RequestSpecification api(String user) {
    return given().header("Authorization", tokens.getAuthorizationHeader(user)).contentType("application/json");
  }

  private String user() {
    String id = "discussion_" + UUID.randomUUID().toString().replace("-", "");
    try (var session = sessions.createSession()) {
      session.data().insert("f_user", new HashMap<>(Map.of("id", id, "name", id, "password_hash", "unused", "status", "ACTIVE")));
    }
    return id;
  }

  private String post(String user, String path, Map<String, Object> body) {
    return api(user).body(body).post(path).then().statusCode(201).extract().path("id");
  }

  private void count(String user, int count) {
    api(user).get("/notifications?status=all&category=discussion&appId=dev_test")
      .then().statusCode(200).body("total", equalTo(count));
  }

  @Test
  void mentionsAndRepliesReachTheRightInboxAndCreateRealtimeEvents() {
    String author = user(), member = user(), third = user();
    String formName = "notice_" + UUID.randomUUID().toString().replace("-", "");
    api("admin").body(Map.of("name", formName, "label", "讨论通知测试", "createDisplayField", true))
      .post("/apps/dev_test/forms").then().statusCode(201);
    String records = "/apps/dev_test/forms/" + formName + "/records";
    try {
      String record = post("admin", records, Map.of("name", "通知测试记录"));
      String path = records + "/" + record + "/discussions";
      String root = post(author, path, Map.of("content", "@成员 请查收", "mentionedUserIds", List.of(member, member, author)));
      messages.drain("dev_test", 100);
      count(member, 1);
      count(author, 0);
      api(member).get("/notifications?category=discussion&appId=dev_test").then().statusCode(200)
        .body("list[0].discussionId", equalTo(root)).body("list[0].title", endsWith("提到了你"));

      String reply = post(member, path, Map.of("content", "回复主评论", "parentId", root));
      messages.drain("dev_test", 100);
      count(author, 1);
      count(member, 1);
      String notificationId = api(author).get("/notifications?category=discussion&appId=dev_test")
        .then().statusCode(200).body("list[0].title", endsWith("回复了你的讨论"))
        .body("list[0].discussionId", equalTo(reply)).extract().path("list[0].id");
      api(author).get("/notifications/" + notificationId).then().statusCode(200)
        .body("canNavigate", equalTo(true)).body("formName", equalTo(formName)).body("recordId", equalTo(record));

      // 同时回复和提及同一人只收一条；其他被提及者仍收到通知。
      String nested = post(third, path, Map.of("content", "回复并提及", "parentId", root, "replyToId", reply,
        "mentionedUserIds", List.of(member, author, member, third)));
      messages.drain("dev_test", 100);
      count(author, 2);
      count(member, 2);
      count(third, 0);
      var events = inbox.events(member, 0, 100);
      assertEquals(2, events.size());
      assertEquals("notification.created", events.getLast().get("type"));
      assertTrue(events.getLast().get("payload").toString().contains(nested));
      assertEquals(2L, NotificationRepository.number(inbox.state(member), "unread"));
      messages.drain("dev_test", 100);
      count(member, 2);

      // parentId直接指向二级回复也通知其作者，而不是线程根作者。
      post(third, path, Map.of("content", "只回复二级作者", "parentId", reply));
      messages.drain("dev_test", 100);
      count(member, 3);
      count(author, 2);
      post(member, path, Map.of("content", "回复自己", "parentId", root, "replyToId", reply,
        "mentionedUserIds", List.of(member)));
      messages.drain("dev_test", 100);
      count(member, 3);
      count(author, 2);
    } finally {
      api("admin").delete("/apps/dev_test/forms/" + formName).then().statusCode(204);
    }
  }
}
