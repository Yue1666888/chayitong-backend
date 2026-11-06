package com.chayitong.chayitong_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.chayitong.chayitong_backend.PostBroadcastListener.NewPostMessage;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfSystemProperty(named = "db.integration", matches = "true")
class PostBroadcastIntegrationTests {
  @LocalServerPort
  private int port;

  @Autowired
  private PostService postService;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private TransactionTemplate transactions;

  @Autowired
  private SimpleBrokerMessageHandler broker;

  @Test
  void broadcastsCommittedPostsToTwoSubscribers() throws Exception {
    String authorId = "it_" + UUID.randomUUID();
    jdbcTemplate.update("INSERT INTO users (id, display_name) VALUES (?, ?)", authorId, "广播测试用户");
    WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
    client.setMessageConverter(new JacksonJsonMessageConverter());
    StompSession first = null;
    StompSession second = null;
    try {
      first = connect(client);
      second = connect(client);
      BlockingQueue<NewPostMessage> firstMessages = subscribe(first);
      BlockingQueue<NewPostMessage> secondMessages = subscribe(second);
      awaitSubscriptions();

      transactions.executeWithoutResult(status -> {
        postService.publish(authorId, "这条帖子会回滚");
        status.setRollbackOnly();
      });
      PostResponse post = postService.publish(authorId, "这条帖子已发布");

      assertEvent(firstMessages, post.id());
      assertEvent(secondMessages, post.id());
      assertThat(jdbcTemplate.queryForObject(
          "SELECT COUNT(*) FROM posts WHERE author_id = ?", Integer.class, authorId)).isEqualTo(1);
    } finally {
      if (first != null && first.isConnected()) {
        first.disconnect();
      }
      if (second != null && second.isConnected()) {
        second.disconnect();
      }
      client.stop();
      jdbcTemplate.update("DELETE FROM posts WHERE author_id = ?", authorId);
      jdbcTemplate.update("DELETE FROM users WHERE id = ?", authorId);
    }
  }

  private StompSession connect(WebSocketStompClient client) throws Exception {
    return client.connectAsync("ws://127.0.0.1:" + port + "/ws/community",
        new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
  }

  private BlockingQueue<NewPostMessage> subscribe(StompSession session) {
    BlockingQueue<NewPostMessage> messages = new LinkedBlockingQueue<>();
    session.subscribe(CommunityWebSocketConfig.POSTS_TOPIC, new StompFrameHandler() {
      @Override
      public Type getPayloadType(StompHeaders headers) {
        return NewPostMessage.class;
      }

      @Override
      public void handleFrame(StompHeaders headers, Object payload) {
        messages.add((NewPostMessage) payload);
      }
    });
    return messages;
  }

  private void awaitSubscriptions() {
    SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
    headers.setDestination(CommunityWebSocketConfig.POSTS_TOPIC);
    var probe = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    await().atMost(Duration.ofSeconds(5)).until(
        () -> broker.getSubscriptionRegistry().findSubscriptions(probe).size() == 2);
  }

  private void assertEvent(BlockingQueue<NewPostMessage> messages, String postId) throws InterruptedException {
    assertThat(messages.poll(5, TimeUnit.SECONDS)).isEqualTo(new NewPostMessage("POST_PUBLISHED", postId));
  }
}
