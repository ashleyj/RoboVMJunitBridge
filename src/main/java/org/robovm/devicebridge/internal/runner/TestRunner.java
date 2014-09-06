package org.robovm.devicebridge.internal.runner;

import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.robovm.apple.foundation.Foundation;
import org.robovm.apple.foundation.NSBundle;
import org.robovm.devicebridge.internal.listener.RoboTestListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Main TestRunner class run on the device/simulator
 */
public class TestRunner {

    private static String classList = "classLoader.txt";

    public static void main(String[] args) throws IOException, InterruptedException {

        BufferedReader reader = new BufferedReader(new FileReader(new File(getSharedResource(classList))));
        final RoboTestListener listener = new RoboTestListener(null, "127.0.0.1", "8889");

        log("Reading File");
        JUnitCore jUnitCore = new JUnitCore();
        /* provide a means to call back to server */
        jUnitCore.addListener(listener);

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    Foundation.log("TestRunner threw exception " + e.getMessage());
                    for (StackTraceElement stackTraceElement : e.getStackTrace()) {
                        Foundation.log("\t" + stackTraceElement.toString());
                    }
                    ;
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
        });

        log("Got here");

        String classLine;
        while ((classLine = reader.readLine()) != null) {
            if (classLine.contains("#")) {
                log("Running method");
                String classMethod[] = classLine.split("#(?=[^\\.]+$)");
                runMethodOnly(jUnitCore, classMethod[0], classMethod[1]);
            } else {
                log("Running whole class " + classLine);
                runClass(jUnitCore, classLine);
                log("done");
            }
        }
    }

    public static String getSharedResource(String fileName) {
        String[] fileParts = fileName.split("\\.(?=[^\\.]+$)");
        return NSBundle.getMainBundle().findResourcePathInSubPath(fileParts[0], "." + fileParts[1], "test");
    }

    /**
     * Run a single method test
     * 
     * @param jUnitCore
     * @param className
     * @param method
     */
    private static void runMethodOnly(JUnitCore jUnitCore, String className, String method) {
        try {
            jUnitCore.run(Request.method(Class.forName(className), method));
        } catch (ClassNotFoundException e) {
            log("Class not found: " + className);
            e.printStackTrace();
        }
    }

    /**
     * Run class methods in the specified class
     * 
     * @param jUnitCore
     * @param className
     */
    private static void runClass(JUnitCore jUnitCore, String className) {
        try {
            jUnitCore.run(Class.forName(className));
        } catch (ClassNotFoundException e) {
            log("Class not found: " + className);
            e.printStackTrace();
        }
    }

    private static void log(String logLine) {
        System.err.println(logLine);
    }
}