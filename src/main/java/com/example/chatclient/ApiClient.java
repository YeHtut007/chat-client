package com.example.chatclient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;


public class ApiClient {
  private final String baseUrl;
  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper mapper = new ObjectMapper();
  private String lastToken;
  private static final DateTimeFormatter TS =
		    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
		                     .withZone(ZoneId.systemDefault());


  public ApiClient(String baseUrl) { this.baseUrl = baseUrl; }

  public String login(String username, String password) throws Exception {
	  var body = mapper.writeValueAsString(Map.of("username", username, "password", password));
	  var req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/auth/login"))
	      .header("Content-Type", "application/json")
	      .POST(HttpRequest.BodyPublishers.ofString(body))
	      .build();

	  var res = http.send(req, HttpResponse.BodyHandlers.ofString());

	  if (res.statusCode() != 200) {
	    throw new RuntimeException("HTTP " + res.statusCode() + " body=" + res.body());
	  }

	  JsonNode node = mapper.readTree(res.body());
	  JsonNode tokenNode = node.path("token");
	  if (tokenNode.isMissingNode() || tokenNode.isNull()) {
	    throw new RuntimeException("No 'token' in response: " + res.body());
	  }

	  this.lastToken = tokenNode.asText();
	  return this.lastToken;
	}
  
//--- Friends & DM ---

public java.util.List<java.util.Map<String,Object>> searchUsers(String q) throws Exception {
 var url = baseUrl + "/api/users/search?q=" + java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8);
 var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
     .header("Authorization", "Bearer " + lastToken)
     .GET().build();
 var res = http.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
 if (res.statusCode()!=200) throw new RuntimeException("search failed: " + res.body());
 var arr = mapper.readTree(res.body());
 var out = new java.util.ArrayList<java.util.Map<String,Object>>();
 for (var n : arr) {
   out.add(java.util.Map.of(
       "id", n.get("id").asText(),
       "username", n.get("username").asText(),
       "displayName", n.get("displayName").asText()
   ));
 }
 return out;
}

public String sendFriendRequest(String toUsername) throws Exception {
 var body = mapper.writeValueAsString(java.util.Map.of("toUsername", toUsername));
 var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(baseUrl + "/api/friends/requests"))
     .header("Authorization", "Bearer " + lastToken)
     .header("Content-Type", "application/json")
     .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build();
 var res = http.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
 if (res.statusCode()!=200) throw new RuntimeException("request failed: " + res.body());
 return mapper.readTree(res.body()).get("id").asText();
}

public java.util.List<java.util.Map<String,Object>> incomingRequests() throws Exception {
 var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(baseUrl + "/api/friends/requests/incoming"))
     .header("Authorization", "Bearer " + lastToken).GET().build();
 var res = http.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
 if (res.statusCode()!=200) throw new RuntimeException("incoming failed: " + res.body());
 var arr = mapper.readTree(res.body());
 var out = new java.util.ArrayList<java.util.Map<String,Object>>();
 for (var n: arr) out.add(java.util.Map.of(
     "id", n.get("id").asText(),
     "from", n.get("from").get("username").asText(),
     "at", n.get("createdAt").asText()
 ));
 return out;
}

public void acceptRequest(String requestId) throws Exception {
 var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(baseUrl + "/api/friends/requests/"+requestId+"/accept"))
     .header("Authorization", "Bearer " + lastToken)
     .POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build();
 var res = http.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
 if (res.statusCode()!=200) throw new RuntimeException("accept failed: " + res.body());
}

public java.util.List<java.util.Map<String,Object>> listFriends() throws Exception {
 var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(baseUrl + "/api/friends"))
     .header("Authorization", "Bearer " + lastToken).GET().build();
 var res = http.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
 if (res.statusCode()!=200) throw new RuntimeException("friends failed: " + res.body());
 var arr = mapper.readTree(res.body());
 var out = new java.util.ArrayList<java.util.Map<String,Object>>();
 for (var n : arr) out.add(java.util.Map.of(
     "username", n.get("username").asText(),
     "displayName", n.get("displayName").asText()
 ));
 return out;
}

public String openDm(String peerUsername) throws Exception {
 var body = mapper.writeValueAsString(java.util.Map.of("username", peerUsername));
 var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(baseUrl + "/api/dm/open"))
     .header("Authorization", "Bearer " + lastToken)
     .header("Content-Type", "application/json")
     .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build();
 var res = http.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
 if (res.statusCode()!=200) throw new RuntimeException("open dm failed: " + res.body());
 return mapper.readTree(res.body()).get("conversationId").asText();
}



  public void register(String username, String displayName, String password) throws Exception {
    var body = mapper.writeValueAsString(Map.of(
        "username", username, "displayName", displayName, "password", password));
    var req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/auth/register"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
    var res = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() != 200 && res.statusCode() != 201) {
      throw new RuntimeException("Register failed: " + res.statusCode() + " " + res.body());
    }
  }

  public List<String> loadHistory(String conversationId) throws Exception {
    var url = baseUrl + "/api/conversations/" + conversationId + "/messages";
    var req = HttpRequest.newBuilder(URI.create(url))
        .header("Authorization", "Bearer " + lastToken)
        .GET().build();
    var res = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() != 200) throw new RuntimeException("History failed: " + res.statusCode() + " " + res.body());
    var arr = mapper.readTree(res.body());
    var lines = new ArrayList<String>();
    for (var n : arr) {
    	  var sender = n.get("sender").asText();
    	  var content = n.get("content").asText();
    	  var tsIso = n.get("sentAt").asText();   // e.g. 2025-08-23T06:30:58.961657Z
    	  var ts = Instant.parse(tsIso);
    	  lines.add("[" + TS.format(ts) + "] " + sender + ": " + content);
    	}
    return lines;
  }
  
  public List<String> loadHistoryAfter(String conversationId, long afterEpochMs) throws Exception {
	  var url = baseUrl + "/api/conversations/" + conversationId + "/messages?afterEpochMs=" + afterEpochMs;
	  var req = HttpRequest.newBuilder(URI.create(url))
	      .header("Authorization", "Bearer " + lastToken)
	      .GET().build();
	  var res = http.send(req, HttpResponse.BodyHandlers.ofString());
	  if (res.statusCode() != 200) throw new RuntimeException("History(since) failed: " + res.statusCode());
	  var arr = mapper.readTree(res.body());
	  var lines = new ArrayList<String>();
	  for (var n : arr) {
	    var sender = n.get("sender").asText();
	    var content = n.get("content").asText();
	    var tsIso = n.get("sentAt").asText();
	    lines.add("[" + TS.format(Instant.parse(tsIso)) + "] " + sender + ": " + content);
	  }
	  return lines;
	}
  public void declineRequest(String requestId) throws Exception {
	  var req = java.net.http.HttpRequest.newBuilder(
	      java.net.URI.create(baseUrl + "/api/friends/requests/" + requestId + "/decline"))
	      .header("Authorization", "Bearer " + lastToken)
	      .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
	      .build();
	  var res = http.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
	  if (res.statusCode()!=200) throw new RuntimeException("decline failed: " + res.body());
	}
  
  public java.util.List<java.util.Map<String,Object>> outgoingRequests() throws Exception {
	  var req = java.net.http.HttpRequest.newBuilder(
	      java.net.URI.create(baseUrl + "/api/friends/requests/outgoing"))
	      .header("Authorization", "Bearer " + lastToken).GET().build();
	  var res = http.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
	  if (res.statusCode()!=200) throw new RuntimeException("outgoing failed: " + res.body());
	  var arr = mapper.readTree(res.body());
	  var out = new java.util.ArrayList<java.util.Map<String,Object>>();
	  for (var n: arr) out.add(java.util.Map.of(
	      "id", n.get("id").asText(),
	      "to", n.get("to").get("username").asText(),
	      "at", n.get("createdAt").asText()
	  ));
	  return out;
	}



}
