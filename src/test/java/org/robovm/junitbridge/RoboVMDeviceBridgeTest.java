package org.robovm.junitbridge;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.robovm.compiler.Version;
import org.robovm.compiler.config.Config;
import org.robovm.compiler.config.Resource;
import org.robovm.compilerhelper.RoboVMResolver;
import org.robovm.devicebridge.ResultObject;
import org.robovm.devicebridge.RoboVMDeviceBridge;
import org.robovm.devicebridge.internal.runner.TestRunner;
import rx.observables.BlockingObservable;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertTrue;
import static org.powermock.api.mockito.PowerMockito.mock;

public class RoboVMDeviceBridgeTest {

    @Test
    public void testSuccessfulJunitRun() throws IllegalAccessException {

        final RoboVMDeviceBridge roboVMDeviceBridge = new RoboVMDeviceBridge();
        mock(TestRunner.class);

        /* start simulator */
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Config.Builder config = createConfig(TestRunner.class.getCanonicalName());
                    config.addResource(new Resource(new File(getClass().getResource("/test/classLoader.txt").getFile())));
                    roboVMDeviceBridge.compileAndRun(config);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        BlockingObservable<ResultObject> blockingObservable = BlockingObservable.from(roboVMDeviceBridge
                .startServer(8889));

        int successfulTests = 0;
        int failedTests = 0;
        for (ResultObject resultObject : blockingObservable.toIterable()) {
            switch (resultObject.getResultType()) {

            case ResultObject.TEST_FINISHED:
                successfulTests++;
                break;

            case ResultObject.TEST_FAILURE:
                failedTests++;
                break;

            }
        }
        ;

        assertTrue("Successful tests: " + successfulTests, successfulTests == 2);
        assertTrue("Failed tests: " + failedTests, failedTests == 1);
    }

    private Config.Builder createConfig(String clazz) throws IOException, ClassNotFoundException {
        Config.Builder config = new Config.Builder();
        RoboVMResolver resolver = new RoboVMResolver();

        config.read(new File(getClass().getResource("/config.xml").getFile()));

        /* include test classes */
        System.out.println("Including: " + getClass().getResource("/").getFile());
        config.addClasspathEntry(new File(getClass().getResource("/").getFile()));

        /* include non-test classes */
        config.addClasspathEntry(new File(getClass().getResource("/").getFile() + "../classes"));
        System.err.println("Launching class: " + clazz);
        config.mainClass(clazz);

        /* add classpath entries */
        config.addClasspathEntry(resolver.resolveArtifact("org.robovm:robovm-cocoatouch:" + Version.getVersion()));
        config.addClasspathEntry(resolver.resolveArtifact("org.robovm:robovm-objc:" + Version.getVersion()));
        config.addClasspathEntry(resolver.resolveArtifact("junit:junit:4.4"));
        config.addClasspathEntry(resolver.resolveArtifact("com.google.code.gson:gson:2.2.4"));
        config.addClasspathEntry(resolver.resolveArtifact("biz.source_code:base64coder:2010-12-19"));

        config.addForceLinkClass(clazz);
        config.addForceLinkClass(RunnerClass.class.getCanonicalName());
        return config;
    }

}
