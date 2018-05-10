package io.vertx.openshift.auth.service;

import io.vertx.core.json.JsonObject;

public class Greeting extends JsonObject {

  private static final String template = "Hello, %s!";

  public Greeting() {
  }

  public Greeting(long id, String content) {
    put("id", id);
    put("content", String.format(template, content));
  }

  public long getId() {
    return getLong("id", 0L);
  }

  public String getContent() {
    return getString("content");
  }
}
