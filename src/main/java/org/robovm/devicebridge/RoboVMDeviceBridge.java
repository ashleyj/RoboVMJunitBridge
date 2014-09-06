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

package org.robovm.devicebridge;

import com.google.gson.GsonBuilder;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.robovm.compiler.config.Config;
import org.robovm.compiler.target.LaunchParameters;
import org.robovm.devicebridge.internal.adapters.AtomicIntegerTypeAdapter;
import org.robovm.devicebridge.internal.adapters.DescriptionTypeAdapter;
import org.robovm.devicebridge.internal.Logger;
import rx.Observable;
import rx.Subscriber;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bridge between device and client (IDE, gradle, maven...)
 */
public class RoboVMDeviceBridge {

    private ServerSocket serverSocket;

    public RoboVMDeviceBridge() {
    }

    /**
     * Create server side listener
     * 
     * @param port
     *            listening port
     * @return
     */
    public Observable<ResultObject> startServer(final int port) {
        return Observable.create(new Observable.OnSubscribe<ResultObject>() {
            @Override
            public void call(Subscriber<? super ResultObject> subscriber) {
                try {
                    Logger.log("Starting server listener");
                    serverSocket = new ServerSocket(port);
                    Socket socket = serverSocket.accept();
                    String line;
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(socket.getInputStream()));

                    while ((line = reader.readLine()) != null) {
                        Logger.log("Read from socket " + line);
                        ResultObject resultObject = jsonToResultObject(line);
                        subscriber.onNext(resultObject);
                    }
                    subscriber.onCompleted();
                } catch (Exception e) {
                    Logger.log("Error sending result " + e.getMessage());
                    subscriber.onError(e);
                }
            }
        });
    }

    private ResultObject jsonToResultObject(String jsonString) {
        ResultObject resultObject = new GsonBuilder()
                .registerTypeAdapter(Description.class, new DescriptionTypeAdapter())
                .registerTypeAdapter(AtomicInteger.class, new AtomicIntegerTypeAdapter())
                .registerTypeAdapter(Failure.class, new DescriptionTypeAdapter.FailureTypeAdapter())
                .create()
                .fromJson(jsonString, ResultObject.class);

        return resultObject;
    }

    /**
     * Compile configuration and execute (on device or simulator)
     * 
     * @param configBuilder
     *            appropriate configuration for execution
     * @throws IOException
     */
    public void compileAndRun(Config.Builder configBuilder) throws IOException {

        if (configBuilder == null) {
            throw new IllegalArgumentException("RoboVM configuration cannot be null");
        }

        Logger.log("Building Runner");
        new org.robovm.compilerhelper.Compiler()
                .withConfiguration(configBuilder)
                .compile();

        try {
            Config config = configBuilder.build();

            Logger.log("Launching Simulator");
            LaunchParameters launchParameters = config.getTarget().createLaunchParameters();
            config.getTarget().launch(launchParameters).waitFor();
        } catch (InterruptedException e) {
            if (serverSocket != null) {
                serverSocket.close();
            }
            e.printStackTrace();
        }
    }

}
