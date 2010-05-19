/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package vogar.target;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import vogar.Result;
import vogar.TestProperties;

/**
 * Runs an action, in process on the target.
 */
public class TestRunner {

    protected final Properties properties;

    protected final String qualifiedName;
    protected final String classOrPackageName;
    protected final int monitorPort;
    protected final List<Runner> runners;

    protected TestRunner() {
        properties = loadProperties();
        qualifiedName = properties.getProperty(TestProperties.QUALIFIED_NAME);
        classOrPackageName = properties.getProperty(TestProperties.TEST_CLASS_OR_PACKAGE);
        monitorPort = Integer.parseInt(properties.getProperty(TestProperties.MONITOR_PORT));
        runners = Arrays.asList(new JUnitRunner(),
                                new CaliperRunner(),
                                new MainRunner());
    }

    protected static Properties loadProperties() {
        Properties properties = new Properties();
        try {
            InputStream propertiesStream = TestRunner.class.getResourceAsStream(
                    "/" + TestProperties.FILE);
            if (propertiesStream == null) {
                throw new RuntimeException(TestProperties.FILE + " missing!");
            }
            properties.load(propertiesStream);
            return properties;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the class to run the test with based on {@param klass}. For instance, a class
     * that extends junit.framework.TestCase should be run with JUnitSpec.
     *
     * Returns null if no such associated runner exists.
     */
    private Class<?> runnerClass(Class<?> klass) {
        for (Runner runner : runners) {
            if (runner.supports(klass)) {
                return runner.getClass();
            }
        }

        return null;
    }

    public void run(String... args) {
        final TargetMonitor monitor = new TargetMonitor();
        monitor.await(monitorPort);

        PrintStream monitorPrintStream = new PrintStream(System.out) {
            @Override public void print(String str) {
                super.print(str);
                monitor.output(str != null ? str : "null");
            }
        };
        System.setOut(monitorPrintStream);
        System.setErr(monitorPrintStream);

        Set<Class<?>> classes = new ClassFinder().find(classOrPackageName);

        for (Class<?> klass : classes) {
            Class<?> runnerClass = runnerClass(klass);
            if (runnerClass != null) {
                Runner runner;
                try {
                    runner = (Runner) runnerClass.newInstance();
                    runner.init(monitor, qualifiedName, klass);
                } catch (Exception e) {
                    monitor.outcomeStarted(qualifiedName, qualifiedName);
                    e.printStackTrace();
                    monitor.outcomeFinished(Result.ERROR);
                    monitor.close();
                    return;
                }
                runner.run(qualifiedName, klass, args);
            } else {
                monitor.outcomeStarted(klass.getName(), qualifiedName);
                System.out.println("Skipping " + klass.getName() + ": no associated runner class");
                monitor.outcomeFinished(Result.UNSUPPORTED);
            }
        }

        monitor.close();
    }

    public static void main(String[] args) {
        new TestRunner().run(args);
    }
}
