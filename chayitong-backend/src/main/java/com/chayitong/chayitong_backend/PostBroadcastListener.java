package com.chayitong.chayitong_backend;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PostBroadcastListener {
  private final SimpMessagingTemplate messaging;

  public PostBroadcastListener(SimpMessagingTemplate messaging) {
    this.messaging = messaging;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPostPublished(PostPublishedEvent event) {
    messaging.convertAndSend(CommunityWebSocketConfig.POSTS_TOPIC,
        new NewPostMessage("POST_PUBLISHED", event.postId()));
  }

  public record NewPostMessage(String type, String postId) {}
}
