/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.webmonitor.handlers;

import com.fasterxml.jackson.core.JsonGenerator;
import org.apache.flink.runtime.instance.ActorGateway;
import org.apache.flink.runtime.messages.webmonitor.RequestStatusOverview;
import org.apache.flink.runtime.messages.webmonitor.StatusOverview;
import org.apache.flink.runtime.util.EnvironmentInformation;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.FiniteDuration;

import java.io.StringWriter;
import java.util.Map;

import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * Responder that returns the status of the Flink cluster, such as how many
 * TaskManagers are currently connected, and how many jobs are running.
 */
public class ClusterOverviewHandler implements RequestHandler {

	private static final String version = EnvironmentInformation.getVersion();

	private static final String commitID = EnvironmentInformation.getRevisionInformation().commitId;

	private final FiniteDuration timeout;
	
	public ClusterOverviewHandler(FiniteDuration timeout) {
		this.timeout = checkNotNull(timeout);
	}

	@Override
	public String handleRequest(Map<String, String> pathParams, Map<String, String> queryParams, ActorGateway jobManager) throws Exception {
		// we need no parameters, get all requests
		try {
			if (jobManager != null) {
				Future<Object> future = jobManager.ask(RequestStatusOverview.getInstance(), timeout);
				StatusOverview overview = (StatusOverview) Await.result(future, timeout);

				StringWriter writer = new StringWriter();
				JsonGenerator gen = JsonFactory.jacksonFactory.createGenerator(writer);

				gen.writeStartObject();
				gen.writeNumberField("taskmanagers", overview.getNumTaskManagersConnected());
				gen.writeNumberField("slots-total", overview.getNumSlotsTotal());
				gen.writeNumberField("slots-available", overview.getNumSlotsAvailable());
				gen.writeNumberField("jobs-running", overview.getNumJobsRunningOrPending());
				gen.writeNumberField("jobs-finished", overview.getNumJobsFinished());
				gen.writeNumberField("jobs-cancelled", overview.getNumJobsCancelled());
				gen.writeNumberField("jobs-failed", overview.getNumJobsFailed());
				gen.writeStringField("flink-version", version);
				if (!commitID.equals(EnvironmentInformation.UNKNOWN)) {
					gen.writeStringField("flink-commit", commitID);
				}
				gen.writeEndObject();

				gen.close();
				return writer.toString();
			} else {
				throw new Exception("No connection to the leading JobManager.");
			}
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to fetch list of all running jobs: " + e.getMessage(), e);
		}
	}
}
