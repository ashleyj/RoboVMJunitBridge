/*
 * Copyright (C) 2014 Trillian Mobile AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.robovm.devicebridge.internal.listener;

import com.google.gson.GsonBuilder;
import org.apache.maven.surefire.report.RunListener;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.robovm.apple.foundation.Foundation;
import org.robovm.devicebridge.ResultObject;
import org.robovm.devicebridge.internal.adapters.AtomicIntegerTypeAdapter;
import org.robovm.devicebridge.internal.adapters.DescriptionTypeAdapter;
import org.robovm.devicebridge.internal.adapters.ThrowableTypeAdapter;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.robovm.devicebridge.ResultObject.*;

/**
 * JUnit RunListener which sends results via a socket to a listening instance
 * (eg. surefire provider)
 */
public class RoboTestListener extends org.junit.runner.notification.RunListener {

    private final RunListener reporter;
    private Socket hostSocket;

    private static ArrayList<String> failedTests = new ArrayList<String>();

    public RoboTestListener(RunListener reporter, String host, String port) throws IOException {
        this.reporter = reporter;
        hostSocket = new Socket(host, Integer.parseInt(port));
    }

    @Override
    public void testIgnored(Description description) throws Exception {
        sendToHost(TEST_IGNORED, createDescriptionResult(description, TEST_IGNORED));
    }

    @Override
    public void testRunStarted(Description description) throws Exception {
        sendToHost(TEST_RUN_STARTED, createDescriptionResult(description, TEST_RUN_STARTED));
    }

    @Override
    public void testRunFinished(Result result) throws Exception {
        sendToHost(TEST_RUN_FINISHED, createResultResult(result, TEST_RUN_FINISHED));
    }

    @Override
    public void testStarted(Description description) throws Exception {
        sendToHost(TEST_STARTED, createDescriptionResult(description, TEST_STARTED));
    }

    @Override
    public void testFinished(Description description) throws Exception {
        for (String failedTest : failedTests) {
            if (description.getDisplayName().equals(failedTest)) {
                return;
            }
        }
        sendToHost(TEST_FINISHED, createDescriptionResult(description, TEST_FINISHED));
    }

    @Override
    public void testFailure(Failure failure) throws Exception {
        failedTests.add(failure.getDescription().getDisplayName());
        sendToHost(TEST_FAILURE, createFailureResult(failure, TEST_FAILURE));
    }

    private ResultObject createFailureResult(Failure failure, int type) {
        ResultObject resultObject = new ResultObject();
        resultObject.setFailure(failure);
        resultObject.setResultType(type);
        return resultObject;
    }

    private ResultObject createDescriptionResult(Description description, int type) {
        ResultObject resultObject = new ResultObject();
        resultObject.setDescription(description);
        resultObject.setResultType(type);
        return resultObject;
    }

    private ResultObject createResultResult(Result result, int type) {
        ResultObject resultObject = new ResultObject();
        resultObject.setResult(result);
        resultObject.setResultType(type);
        return resultObject;
    }

    public void sendToHost(int type, ResultObject message) {

        if (type == TEST_RUN_FINISHED) {
            try {
                transmit(message);
                hostSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            try {
                transmit(message);
            } catch (Exception e) {
                Foundation.log("Can't send result " + type + " - " + e.getMessage());
                for (StackTraceElement stackTraceElement : e.getStackTrace()) {
                    Foundation.log("\t" + stackTraceElement.toString());
                }
                ;
                e.printStackTrace();
            }
        }
    }

    private void transmit(ResultObject message) throws IOException, InterruptedException {

        if (hostSocket == null) {
            throw new RuntimeException("Connection to host died");
        } else {
            Foundation.log("Transmitting to host");
            // if (hostSocket.isConnected()) {
            // Foundation.log("socket connected");
            // } else {
            // Foundation.log("socket not connected");
            // }

            PrintWriter writer;

            String transmitMessage = new GsonBuilder()
                    .registerTypeAdapter(Description.class, new DescriptionTypeAdapter())
                    .registerTypeAdapter(AtomicInteger.class, new AtomicIntegerTypeAdapter())
                    .registerTypeAdapter(Failure.class, new DescriptionTypeAdapter.FailureTypeAdapter())
                    .registerTypeAdapter(Throwable.class, new ThrowableTypeAdapter())
                    .create().toJson(message);

            Foundation.log("GSON created");
            Foundation.log("Sending : " + transmitMessage);
            writer = new PrintWriter(hostSocket.getOutputStream(), true);
            writer.println(transmitMessage);
            writer.flush();
            /* Give server time to process response */
            Thread.sleep(2000);

        }
    }

}
