package com.example.chatclient;

import javafx.application.Application; import javafx.application.Platform;
import javafx.geometry.Insets; import javafx.scene.Scene;
import javafx.scene.control.*; import javafx.scene.layout.*; import javafx.stage.Stage;
import java.time.*;
import java.time.format.DateTimeFormatter;


public class ChatApp extends Application {
	
	// at top of class
	private static final String API_BASE = System.getProperty("apiBase", "https://chat-server-wot9.onrender.com");
	private static final String WS_URL   = System.getProperty("wsUrl",   "wss://chat-server-wot9.onrender.com/ws-native");
	private static final DateTimeFormatter TS =
		    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

	// fields:
	private final ApiClient api = new ApiClient(API_BASE);
	private final StompService stomp = new StompService();

//  private long lastSeenEpochMs = 0L;

  @Override public void start(Stage stage) {
	  stage.setTitle("JavaFX Chat");

	  // Shared
	  final String[] token = new String[1];
	  var messages = new TextArea(); messages.setEditable(false); messages.setPrefRowCount(16);

	  // ----- LOGIN PANE -----
	  var loginUser = new TextField("alice"); loginUser.setPromptText("username");
	  var loginPass = new PasswordField();    loginPass.setPromptText("password");
	  var loginBtn  = new Button("Login");
	  var toSignup  = new Hyperlink("No account? Sign up");
	  var loginStatus = new Label();
	  var loginPane = new VBox(8, new Label("Log in"),
	      loginUser, loginPass, loginBtn, toSignup, loginStatus);
	  loginPane.setPadding(new Insets(12));

	  // ----- SIGNUP PANE -----
	  var suUser  = new TextField(); suUser.setPromptText("username");
	  var suName  = new TextField(); suName.setPromptText("display name");
	  var suPass  = new PasswordField(); suPass.setPromptText("password");
	  var signupBtn = new Button("Create account");
	  var toLogin   = new Hyperlink("Have an account? Log in");
	  var signupStatus = new Label();
	  var signupPane = new VBox(8, new Label("Sign up"),
	      suUser, suName, suPass, signupBtn, toLogin, signupStatus);
	  signupPane.setPadding(new Insets(12));
	  signupPane.setVisible(false); signupPane.setManaged(false);

	  // ----- CHAT PANE -----
	  var conv = new TextField("11111111-1111-1111-1111-111111111111");
	  var connectBtn = new Button("Connect");
	  var input = new TextField(); input.setPromptText("message");
	  var sendBtn = new Button("Send");
	  var chatPane = new VBox(8, new HBox(8, conv, connectBtn), messages, new HBox(8, input, sendBtn));
	  chatPane.setPadding(new Insets(12));
	  chatPane.setVisible(false); chatPane.setManaged(false);

	  // Root with all panes stacked; we toggle visibility/managed
	  var root = new StackPane(chatPane, signupPane, loginPane);
	  stage.setScene(new Scene(root, 680, 520));
	  stage.show();

	  // --- helpers to toggle panes ---
	  Runnable showLogin = () -> {
	    signupPane.setVisible(false); signupPane.setManaged(false);
	    chatPane.setVisible(false); chatPane.setManaged(false);
	    loginPane.setVisible(true); loginPane.setManaged(true);
	    loginStatus.setText("");
	  };
	  Runnable showSignup = () -> {
	    loginPane.setVisible(false); loginPane.setManaged(false);
	    chatPane.setVisible(false); chatPane.setManaged(false);
	    signupPane.setVisible(true); signupPane.setManaged(true);
	    signupStatus.setText("");
	  };
	  Runnable showChat = () -> {
	    loginPane.setVisible(false); loginPane.setManaged(false);
	    signupPane.setVisible(false); signupPane.setManaged(false);
	    chatPane.setVisible(true); chatPane.setManaged(true);
	  };

	  toSignup.setOnAction(e -> showSignup.run());
	  toLogin.setOnAction(e -> showLogin.run());

	  // ---- LOGIN action ----
	  loginBtn.setOnAction(e -> {
	    loginBtn.setDisable(true);
	    loginStatus.setText("Logging in...");
	    new Thread(() -> {
	      var u = loginUser.getText().trim().toLowerCase();  // normalize
	      var p = loginPass.getText();
	      try {
	        token[0] = api.login(u, p);
	        Platform.runLater(() -> { loginStatus.setText("Login OK"); showChat.run(); });
	      } catch (Exception ex) {
	        Platform.runLater(() -> {
	          loginBtn.setDisable(false);
	          loginStatus.setText("Login failed: " + (ex.getMessage() != null ? ex.getMessage() : ex));
	        });
	      }
	    }).start();
	  });

	  // ---- SIGNUP action ----
	  signupBtn.setOnAction(e -> {
	    signupBtn.setDisable(true);
	    signupStatus.setText("Creating account...");
	    new Thread(() -> {
	      var u = suUser.getText().trim().toLowerCase();
	      var n = suName.getText().trim();
	      if (n.isEmpty() && !u.isEmpty()) n = Character.toUpperCase(u.charAt(0)) + u.substring(1);
	      var p = suPass.getText();
	      try {
	        api.register(u, n, p);
	        Platform.runLater(() -> {
	          signupStatus.setText("Account created. Please log in.");
	          signupBtn.setDisable(false);
	          // Option A: switch back to login so user enters password to get JWT
	          showLogin.run();
	          loginUser.setText(u);
	          loginPass.setText(p);
	        });
	      } catch (Exception ex) {
	        Platform.runLater(() -> {
	          signupBtn.setDisable(false);
	          signupStatus.setText("Sign up failed: " + (ex.getMessage() != null ? ex.getMessage() : ex));
	        });
	      }
	    }).start();
	  });

	  // ---- CONNECT ----
	  connectBtn.setOnAction(e -> {
	    connectBtn.setDisable(true);
	    messages.appendText("Connecting...\n");
	    if (token[0] == null || token[0].isBlank()) {
	      messages.appendText("No token yet — log in first.\n");
	      connectBtn.setDisable(false);
	      return;
	    }
	    stomp.connect(
	      WS_URL, token[0], conv.getText().trim(),
	      m -> Platform.runLater(() -> {
	        var ts = (m.sentAt() != null) ? TS.format(m.sentAt()) : TS.format(Instant.now());
	        messages.appendText("[" + ts + "] " + m.sender() + ": " + m.content() + "\n");
	      }),
	      () -> Platform.runLater(() -> messages.appendText("connected\n")),
	      err -> Platform.runLater(() -> {
	        messages.appendText("connect error: " + err.getMessage() + "\n");
	        connectBtn.setDisable(false);
	      })
	    );
	  });

	  // ---- SEND ----
	  sendBtn.setOnAction(e -> {
	    String text = input.getText().trim(); if (text.isEmpty()) return;
	    stomp.send(conv.getText().trim(), text);
	    messages.appendText("sent: " + text + "\n");
	    input.clear();
	  });
	}


  public static void main(String[] args) { launch(args); }
}
