package io.vertx.openshift.proton.frontend;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Martin Spisiak (mspisiak@redhat.com) on 27/07/18.
 */
public class Data {
  private final Queue<String> requestIds;
  private final Map<String, Response> responses;
  private final Map<String, WorkerUpdate> workers;

  public Data() {
    this.requestIds = new ConcurrentLinkedQueue<>();
    this.responses = new ConcurrentHashMap<>();
    this.workers = new ConcurrentHashMap<>();
  }

  public Queue<String> getRequestIds() {
    return requestIds;
  }

  public Map<String, Response> getResponses() {
    return responses;
  }

  public Map<String, WorkerUpdate> getWorkers() {
    return workers;
  }

  @Override
  public String toString() {
    return String.format("Data{requestIds=%s, responses=%s, workers=%s}",
      requestIds, responses, workers);
  }
}
