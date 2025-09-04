// ChatMessage.java
package com.example.chatclient;

import java.time.Instant;
import java.util.UUID;

public record ChatMessage(Long id, UUID conversationId, String sender, String content, Instant sentAt) {
  public boolean mine(String currentUser) {
    return currentUser != null && sender() != null && sender().equalsIgnoreCase(currentUser);
  }
}
