/*
 * Copyright 2018 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.vertx.openshift.proton.frontend;

public class WorkerUpdate {
  private final String workerId;
  private final long timestamp;
  private final long requestsProcessed;
  private final long processingErrors;

  public WorkerUpdate(String workerId, long timestamp, long requestsProcessed,
                      long processingErrors) {
    this.workerId = workerId;
    this.timestamp = timestamp;
    this.requestsProcessed = requestsProcessed;
    this.processingErrors = processingErrors;
  }

  public String getWorkerId() {
    return workerId;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public long getRequestsProcessed() {
    return requestsProcessed;
  }

  public long getProcessingErrors() {
    return processingErrors;
  }

  @Override
  public String toString() {
    return String.format("WorkerUpdate{workerId=%s, timestamp=%s, requestsProcessed=%s, processingErrors=%s}",
      workerId, timestamp, requestsProcessed, processingErrors);
  }
}
