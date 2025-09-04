package com.example.chatclient;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.ListCell;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ChatApp extends Application {

  // Config
  private static final String API_BASE = System.getProperty("apiBase", "https://chat-server-wot9.onrender.com");
  private static final String WS_URL   = System.getProperty("wsUrl",   "wss://chat-server-wot9.onrender.com/ws-native");
  private static final DateTimeFormatter TS =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

  // Services
  private final ApiClient api = new ApiClient(API_BASE);
  private final StompService stomp = new StompService();
  private String currentUser;

  // Small view models
  private static class UserItem {
    final String username, displayName;
    UserItem(String u, String d){ this.username=u; this.displayName=d; }
    @Override public String toString(){ return username + " (" + displayName + ")"; }
  }
  private static class IncomingReq {
    final String id, from, at;
    IncomingReq(String id, String from, String at){ this.id=id; this.from=from; this.at=at; }
    @Override public String toString(){ return "from " + from + " · " + at; }
  }
  private static class FriendItem {
    final String username, displayName;
    FriendItem(String u, String d){ this.username=u; this.displayName=d; }
    @Override public String toString(){ return username + " (" + displayName + ")"; }
  }

  @Override public void start(Stage stage) {
    stage.setTitle("JavaFX Chat");
    final String[] token = new String[1];

    // ===== Messages list =====
    var items = javafx.collections.FXCollections.<ChatMessage>observableArrayList();
    var listView = new ListView<>(items);
    listView.setFocusTraversable(false);
    listView.setPrefHeight(360);

    listView.setCellFactory(v -> new ListCell<>() {
      @Override protected void updateItem(ChatMessage m, boolean empty) {
        super.updateItem(m, empty);
        if (empty || m == null) { setText(null); setGraphic(null); return; }

        var time = TS.format(m.sentAt() != null ? m.sentAt() : Instant.now());

        var header = new Label((m.sender() != null ? m.sender() : "system") + "  ·  " + time);
        header.getStyleClass().add("msg-header");

        var body = new Label(m.content());
        body.setWrapText(true);
        body.getStyleClass().add("msg-body");

        var bubble = new VBox(2, header, body);
        boolean mine = currentUser != null && m.sender() != null && m.sender().equalsIgnoreCase(currentUser);

        bubble.getStyleClass().add(mine ? "bubble-me" : "bubble-other");
        bubble.setMaxWidth(460);

        var row = new HBox(bubble);
        row.setFillHeight(true);
        row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 8, 2, 8));

        setGraphic(row);
        setText(null);
      }
    });

    items.addListener((javafx.collections.ListChangeListener<ChatMessage>) c -> {
      while (c.next()) if (c.wasAdded()) listView.scrollTo(items.size() - 1);
    });

    // ===== LOGIN PANE =====
    var loginUser = new TextField("alice"); loginUser.setPromptText("username");
    var loginPass = new PasswordField();    loginPass.setPromptText("password");
    var loginBtn  = new Button("Login");
    var toSignup  = new Hyperlink("No account? Sign up");
    var loginStatus = new Label();
    var loginPane = new VBox(8, new Label("Log in"), loginUser, loginPass, loginBtn, toSignup, loginStatus);
    loginPane.setPadding(new Insets(12));

    // ===== SIGNUP PANE =====
    var suUser  = new TextField(); suUser.setPromptText("username");
    var suName  = new TextField(); suName.setPromptText("display name");
    var suPass  = new PasswordField(); suPass.setPromptText("password");
    var signupBtn = new Button("Create account");
    var toLogin   = new Hyperlink("Have an account? Log in");
    var signupStatus = new Label();
    var signupPane = new VBox(8, new Label("Sign up"), suUser, suName, suPass, signupBtn, toLogin, signupStatus);
    signupPane.setPadding(new Insets(12));
    signupPane.setVisible(false); signupPane.setManaged(false);

    // ===== CHAT CORE =====
    var conv = new TextField("11111111-1111-1111-1111-111111111111");
    var connectBtn = new Button("Connect");
    var input = new TextField(); input.setPromptText("message");
    var sendBtn = new Button("Send"); sendBtn.setDisable(true);
    var status = new Label("offline");

    // Enter-to-send (keeps default buttons out of the way)
    input.setOnAction(e -> sendBtn.fire());

    // (B) style classes
    connectBtn.getStyleClass().add("btn-primary");
    sendBtn.getStyleClass().add("btn-primary");

    var chatCore = new VBox(8,
        new HBox(8, new Label("Conversation:"), conv, connectBtn, status),
        listView,
        new HBox(8, input, sendBtn)
    );
    chatCore.setPadding(new Insets(12));
    chatCore.getStyleClass().add("section"); // (C)

    // ===== HOISTED buttons/controls (so cross-tabs can call them) =====
    final Button refreshFriends = new Button("Refresh");
    refreshFriends.getStyleClass().add("btn-ghost"); // (B)

    final ChoiceBox<String> reqMode = new ChoiceBox<>(javafx.collections.FXCollections.observableArrayList("Incoming","Outgoing"));
    reqMode.setValue("Incoming");
    final Button refreshReq = new Button("Refresh");
    refreshReq.getStyleClass().add("btn-ghost"); // (B)

    // ===== REQUESTS TAB =====
    var reqIncoming = new ListView<IncomingReq>();
    reqIncoming.setPlaceholder(new Label("No incoming requests")); // helpful placeholder
    reqIncoming.setCellFactory(lv -> new ListCell<>() {
      private final Label label = new Label();
      private final Button acceptBtn = new Button("Accept");
      private final Button declineBtn = new Button("Decline");
      { acceptBtn.getStyleClass().add("btn-primary"); declineBtn.getStyleClass().add("btn-danger"); }
      private final Region spacer = new Region();
      private final HBox box = new HBox(8, label, spacer, acceptBtn, declineBtn);
      { HBox.setHgrow(spacer, Priority.ALWAYS); }
      @Override protected void updateItem(IncomingReq it, boolean empty) {
        super.updateItem(it, empty);
        if (empty || it == null) { setGraphic(null); setText(null); return; }
        label.setText(it.toString());
        acceptBtn.setDisable(false); declineBtn.setDisable(false);

        acceptBtn.setOnAction(ev -> {
          acceptBtn.setDisable(true); declineBtn.setDisable(true);
          new Thread(() -> {
            try {
              api.acceptRequest(it.id);
              Platform.runLater(() -> {
                getListView().getItems().remove(it);
                status.setText("request accepted");
                refreshFriends.fire(); // show friend immediately
              });
            } catch (Exception ex) {
              Platform.runLater(() -> {
                status.setText("accept failed: " + ex.getMessage());
                acceptBtn.setDisable(false); declineBtn.setDisable(false);
              });
            }
          }).start();
        });

        declineBtn.setOnAction(ev -> {
          acceptBtn.setDisable(true); declineBtn.setDisable(true);
          new Thread(() -> {
            try {
              api.declineRequest(it.id);
              Platform.runLater(() -> {
                getListView().getItems().remove(it);
                status.setText("request declined");
              });
            } catch (Exception ex) {
              Platform.runLater(() -> {
                status.setText("decline failed: " + ex.getMessage());
                acceptBtn.setDisable(false); declineBtn.setDisable(false);
              });
            }
          }).start();
        });
        setGraphic(box); setText(null);
      }
    });

    var reqOutgoing = new ListView<String>();
    reqOutgoing.setPlaceholder(new Label("No outgoing requests"));

    Runnable loadIncoming = () -> new Thread(() -> {
      try {
        var list = api.incomingRequests();
        Platform.runLater(() -> {
          reqIncoming.getItems().clear();
          for (var r : list) {
            reqIncoming.getItems().add(new IncomingReq(
                String.valueOf(r.get("id")),
                String.valueOf(r.get("from")),
                String.valueOf(r.get("at"))
            ));
          }
        });
      } catch (Exception ex) {
        Platform.runLater(() -> status.setText("load incoming failed: " + ex.getMessage()));
      }
    }).start();

    Runnable loadOutgoing = () -> new Thread(() -> {
      try {
        var list = api.outgoingRequests();
        Platform.runLater(() -> {
          reqOutgoing.getItems().clear();
          for (var r : list) {
            reqOutgoing.getItems().add("to " + r.get("to") + " · " + r.get("at"));
          }
        });
      } catch (Exception ex) {
        Platform.runLater(() -> status.setText("load outgoing failed: " + ex.getMessage()));
      }
    }).start();

    refreshReq.setOnAction(e -> {
      if ("Incoming".equals(reqMode.getValue())) loadIncoming.run();
      else loadOutgoing.run();
    });

    var requestsCenter = new StackPane(reqIncoming, reqOutgoing);
    reqOutgoing.setVisible(false); reqOutgoing.setManaged(false);

    reqMode.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
      boolean incoming = "Incoming".equals(n);
      reqIncoming.setVisible(incoming); reqIncoming.setManaged(incoming);
      reqOutgoing.setVisible(!incoming); reqOutgoing.setManaged(!incoming);
      refreshReq.fire(); // auto reload
    });

    var requestsPane = new VBox(8, new HBox(8, new Label("View:"), reqMode, refreshReq), requestsCenter);
    requestsPane.setPadding(new Insets(12));
    requestsPane.getStyleClass().add("section"); // (C)

    // ===== FRIENDS TAB =====
    var friendsList = new ListView<FriendItem>();
    friendsList.setPlaceholder(new Label("No friends yet"));
    friendsList.setCellFactory(lv -> new ListCell<>() {
      private final Label label = new Label();
      private final Button chatBtn = new Button("Open chat");
      { chatBtn.getStyleClass().add("btn-primary"); }
      private final Region spacer = new Region();
      private final HBox box = new HBox(8, label, spacer, chatBtn);
      { HBox.setHgrow(spacer, Priority.ALWAYS); }
      @Override protected void updateItem(FriendItem it, boolean empty) {
        super.updateItem(it, empty);
        if (empty || it == null) { setGraphic(null); setText(null); return; }
        label.setText(it.toString());
        chatBtn.setOnAction(ev -> {
          chatBtn.setDisable(true);
          new Thread(() -> {
            try {
              var convId = api.openDm(it.username);
              Platform.runLater(() -> {
                chatBtn.setDisable(false);
                conv.setText(convId);
                connectBtn.fire();
              });
            } catch (Exception ex) {
              Platform.runLater(() -> {
                chatBtn.setDisable(false);
                status.setText("open DM failed: " + ex.getMessage());
              });
            }
          }).start();
        });
        setGraphic(box); setText(null);
      }
    });

    var friendsPane = new VBox(8, new HBox(8, refreshFriends), friendsList);
    friendsPane.setPadding(new Insets(12));
    friendsPane.getStyleClass().add("section"); // (C)

    refreshFriends.setOnAction(e -> new Thread(() -> {
      try {
        var list = api.listFriends();
        Platform.runLater(() -> {
          friendsList.getItems().clear();
          for (var f : list) {
            friendsList.getItems().add(new FriendItem(
                String.valueOf(f.get("username")),
                String.valueOf(f.get("displayName"))
            ));
          }
        });
      } catch (Exception ex) {
        Platform.runLater(() -> status.setText("friends failed: " + ex.getMessage()));
      }
    }).start());

    // ===== TABS =====
    var tabs = new TabPane();
    tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    tabs.getTabs().addAll(
        new Tab("Chat", chatCore),
        new Tab("Requests", requestsPane),
        new Tab("Friends", friendsPane)
    );

    // ===== SEARCH TAB (built after Requests so we can reference reqMode/refreshReq) =====
    var searchBox = new TextField(); searchBox.setPromptText("Search username");
    var searchBtn = new Button("Search"); searchBtn.getStyleClass().add("btn-ghost"); // (B)
    var searchResults = new ListView<UserItem>();
    searchResults.setPlaceholder(new Label("No users found")); // <— placeholder
    searchResults.setCellFactory(lv -> new ListCell<>() {
      private final Label label = new Label();
      private final Button addBtn = new Button("Add friend");
      { addBtn.getStyleClass().add("btn-primary"); }
      private final Region spacer = new Region();
      private final HBox box = new HBox(8, label, spacer, addBtn);
      { HBox.setHgrow(spacer, Priority.ALWAYS); }
      @Override protected void updateItem(UserItem it, boolean empty) {
        super.updateItem(it, empty);
        if (empty || it == null) { setGraphic(null); setText(null); return; }
        label.setText(it.toString());
        addBtn.setDisable(false);
        addBtn.setText("Add friend");
        addBtn.setOnAction(ev -> {
          addBtn.setDisable(true);
          addBtn.setText("Sending...");
          new Thread(() -> {
            try {
              api.sendFriendRequest(it.username);
              Platform.runLater(() -> {
                addBtn.setText("Sent ✓");
                status.setText("friend request sent to " + it.username);
                // Show it under Outgoing for immediate feedback
                reqMode.setValue("Outgoing");
                refreshReq.fire();
              });
            } catch (Exception ex) {
              Platform.runLater(() -> {
                status.setText("request failed: " + ex.getMessage());
                addBtn.setDisable(false);
                addBtn.setText("Add friend");
              });
            }
          }).start();
        });
        setGraphic(box); setText(null);
      }
    });

    // ENTER triggers search (no global default button needed)
    searchBox.setOnAction(e -> searchBtn.fire());

    var searchPane = new VBox(8, new HBox(8, searchBox, searchBtn), searchResults);
    searchPane.setPadding(new Insets(12));
    searchPane.getStyleClass().add("section"); // (C)

    // Search action with loading feedback
    searchBtn.setOnAction(e -> new Thread(() -> {
      String q = searchBox.getText() == null ? "" : searchBox.getText().trim();
      Platform.runLater(() -> {
        status.setText(q.isEmpty() ? "Enter a username to search" : "Searching...");
        searchBtn.setDisable(true);
      });
      if (q.isEmpty()) {
        Platform.runLater(() -> searchBtn.setDisable(false));
        return;
      }
      try {
        var results = api.searchUsers(q);
        Platform.runLater(() -> {
          searchResults.getItems().clear();
          for (var u : results) {
            searchResults.getItems().add(new UserItem(
                String.valueOf(u.get("username")),
                String.valueOf(u.get("displayName"))
            ));
          }
          status.setText(results.isEmpty() ? "No users found" : "");
          searchBtn.setDisable(false);
        });
      } catch (Exception ex) {
        Platform.runLater(() -> {
          status.setText("search failed: " + ex.getMessage());
          searchBtn.setDisable(false);
        });
      }
    }).start());

    tabs.getTabs().add(1, new Tab("Search", searchPane)); // insert Search between Chat and Requests

    // ===== Premium App Bar (A) =====
    Label appTitle = new Label("ChitChat");
    appTitle.getStyleClass().addAll("app-title");

    Region appSpacer = new Region();
    appSpacer.getStyleClass().add("app-spacer");
    HBox.setHgrow(appSpacer, Priority.ALWAYS);

    Label headerUser = new Label("@guest");
    headerUser.getStyleClass().add("app-user");

    HBox appBar = new HBox(12, appTitle, appSpacer, headerUser);
    appBar.getStyleClass().add("app-bar");

    BorderPane premiumShell = new BorderPane(tabs);
    premiumShell.setTop(appBar);

    var chatPane = new StackPane(premiumShell);
    chatPane.setVisible(false); chatPane.setManaged(false);

    // ===== Pane toggles =====
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

    // ===== LOGIN =====
    loginBtn.setOnAction(e -> {
      loginBtn.setDisable(true);
      loginStatus.setText("Logging in...");
      new Thread(() -> {
        var u = loginUser.getText().trim().toLowerCase();
        var p = loginPass.getText();
        try {
          token[0] = api.login(u, p);
          currentUser = u;
          Platform.runLater(() -> {
            loginStatus.setText("Login OK");
            headerUser.setText("@" + currentUser);
            showChat.run();
            // Auto refresh lists on login
            refreshFriends.fire();
            refreshReq.fire();
          });
        } catch (Exception ex) {
          Platform.runLater(() -> {
            loginBtn.setDisable(false);
            loginStatus.setText("Login failed: " + (ex.getMessage() != null ? ex.getMessage() : ex));
          });
        }
      }).start();
    });

    // ===== SIGNUP =====
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

    // ===== CONNECT =====
    connectBtn.setOnAction(e -> {
      connectBtn.setDisable(true);
      status.setText("connecting...");

      if (token[0] == null || token[0].isBlank()) {
        status.setText("no token — log in first");
        connectBtn.setDisable(false);
        return;
      }

      final var conversationId = conv.getText().trim();

      stomp.connect(
          WS_URL,
          token[0],
          conversationId,

          m -> Platform.runLater(() -> items.add(m)),

          () -> Platform.runLater(() -> {
            status.setText("connected");
            items.add(new ChatMessage(
                null,
                java.util.UUID.fromString(conversationId),
                "system",
                "connected",
                java.time.Instant.now()
            ));
            sendBtn.setDisable(false);
          }),

          err -> Platform.runLater(() -> {
            status.setText("error");
            items.add(new ChatMessage(
                null,
                java.util.UUID.fromString(conversationId),
                "system",
                "connect error: " + err.getMessage(),
                java.time.Instant.now()
            ));
            connectBtn.setDisable(false);
            sendBtn.setDisable(true);
          })
      );
    });

    // ===== SEND =====
    sendBtn.setOnAction(e -> {
      String text = input.getText().trim(); if (text.isEmpty()) return;
      stomp.send(conv.getText().trim(), text);
      items.add(new ChatMessage(null, java.util.UUID.fromString(conv.getText().trim()),
          "system", "sent: " + text, Instant.now()));
      input.clear();
    });

    // ===== Root + Scene (D) =====
    var root = new StackPane(chatPane, signupPane, loginPane);
    var scene = new Scene(root, 720, 620);
    var cssUrl = getClass().getResource("/chat.css");
    if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());

    stage.setScene(scene);
    stage.show();
  }

  public static void main(String[] args) { launch(args); }
}
