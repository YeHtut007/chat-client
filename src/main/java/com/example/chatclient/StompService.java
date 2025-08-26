package com.example.chatclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.lang.reflect.Type;
import java.util.UUID;
import java.util.function.Consumer;

public class StompService {
  private final WebSocketStompClient client;
  private StompSession session;

  public StompService() {
	  var wsClient = new org.springframework.web.socket.client.standard.StandardWebSocketClient();
	  this.client = new org.springframework.web.socket.messaging.WebSocketStompClient(wsClient);

	  var mapper = new ObjectMapper()
	      .registerModule(new JavaTimeModule())
	      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
	      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

	  var conv = new MappingJackson2MessageConverter();
	  conv.setObjectMapper(mapper);
	  this.client.setMessageConverter(conv);
	}


  public void connect(String url, String token, String conversationId,
                      Consumer<ChatMessage> onMessage, Runnable onConnected, Consumer<Throwable> onError) {

    var hs = new WebSocketHttpHeaders();
    hs.add("Authorization", "Bearer " + token);

    var ch = new StompHeaders();
    ch.add("Authorization", "Bearer " + token);

    client.connect(url, hs, ch, new StompSessionHandlerAdapter() {
      @Override public void afterConnected(StompSession s, StompHeaders h) {
        session = s;

        // ✅ subscribe to the SAME destination your server publishes to:
        // broker.convertAndSend("/topic/chat." + conversationId, ...)
        s.subscribe("/topic/chat." + conversationId, new StompFrameHandler() {
          @Override public Type getPayloadType(StompHeaders headers) { return ChatMessage.class; }
          @Override public void handleFrame(StompHeaders headers, Object payload) {
            onMessage.accept((ChatMessage) payload);
          }
        });

        if (onConnected != null) onConnected.run();
      }

      @Override public void handleTransportError(StompSession s, Throwable ex) {
        if (onError != null) onError.accept(ex);
      }
    }).addCallback(new ListenableFutureCallback<>() {
      @Override public void onFailure(Throwable ex) { if (onError != null) onError.accept(ex); }
      @Override public void onSuccess(StompSession r) {}
    });
  }

  public void send(String conversationId, String content) {
    if (session == null || !session.isConnected()) throw new IllegalStateException("Not connected");
    var msg = new ChatMessage(UUID.fromString(conversationId), "", content, null); // server fills sender + timestamp
    session.send("/app/send", msg);
  }
}
