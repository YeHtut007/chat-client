package com.example.chatclient;

import java.time.Instant;
import java.util.UUID;

public record ChatMessage(UUID conversationId, String sender, String content, Instant sentAt) {}
