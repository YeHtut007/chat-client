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

}
