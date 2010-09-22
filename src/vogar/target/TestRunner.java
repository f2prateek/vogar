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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import vogar.Result;
import vogar.TestProperties;
import vogar.monitor.SocketTargetMonitor;
import vogar.monitor.TargetMonitor;

/**
 * Runs an action, in process on the target.
 */
public final class TestRunner {

    protected final Properties properties;

    protected final String qualifiedName;
    protected final String qualifiedClassOrPackageName;
    protected final int monitorPort;
    protected final int timeoutSeconds;
    protected final List<Runner> runners;
    protected final String[] args;

    public TestRunner(List<String> argsList) {
        properties = loadProperties();
        qualifiedName = properties.getProperty(TestProperties.QUALIFIED_NAME);
        qualifiedClassOrPackageName = properties.getProperty(TestProperties.TEST_CLASS_OR_PACKAGE);
        timeoutSeconds = Integer.parseInt(properties.getProperty(TestProperties.TIMEOUT));
        runners = Arrays.asList(new JUnitRunner(),
                                new JUnit4Runner(),
                                new CaliperRunner(),
                                new MainRunner());

        int monitorPort = Integer.parseInt(properties.getProperty(TestProperties.MONITOR_PORT));
        for (Iterator<String> i = argsList.iterator(); i.hasNext(); ) {
            if (i.next().equals("--monitorPort")) {
                i.remove();
                monitorPort = Integer.parseInt(i.next());
                i.remove();
            }
        }

        this.monitorPort = monitorPort;
        this.args = argsList.toArray(new String[argsList.size()]);
    }

    private Properties loadProperties() {
        Properties properties = new Properties();
        try {
            properties.load(getPropertiesStream());
            return properties;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Attempt to load the test properties file from both the application and system classloader.
     * This is necessary because sometimes we run tests from the boot classpath.
     */
    private InputStream getPropertiesStream() throws IOException {
        for (Class<?> classToLoadFrom : new Class<?>[] { TestRunner.class, Object.class }) {
            InputStream propertiesStream = classToLoadFrom.getResourceAsStream(
                    "/" + TestProperties.FILE);
            if (propertiesStream != null) {
                return propertiesStream;
            }
        }
        throw new IOException(TestProperties.FILE + " missing!");
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
        final SocketTargetMonitor monitor = new SocketTargetMonitor();
        monitor.await(monitorPort);

        run(monitor, args);

        monitor.close();
    }

    public void run(final TargetMonitor monitor, String... args) {
        PrintStream monitorPrintStream = new PrintStream(System.out) {
            @Override public void print(long l) {
                print(String.valueOf(l));
            }

            @Override public void print(int i) {
                print(String.valueOf(i));
            }

            @Override public void print(float f) {
                print(String.valueOf(f));
            }

            @Override public void print(double d) {
                print(String.valueOf(d));
            }

            @Override public void print(char[] s) {
                print(String.valueOf(s));
            }

            @Override public void print(char c) {
                print(String.valueOf(c));
            }

            @Override public void print(Object obj) {
                print(obj != null ? obj.toString() : "null");
            }

            @Override public void print(String str) {
                monitor.output(str != null ? str : "null");
            }

            @Override public void println() {
                print("\n");
            }

            /**
             * Although println() is documented to be equivalent to print()
             * followed by println(), this isn't the behavior on HotSpot
             * and we must manually override println(String) to ensure that
             * newlines aren't dropped.
             */
            @Override public void println(String s) {
                print(s + "\n");
            }

            @Override public void println(long l) {
                println(String.valueOf(l));
            }

            @Override public void println(int i) {
                println(String.valueOf(i));
            }

            @Override public void println(float f) {
                println(String.valueOf(f));
            }

            @Override public void println(double d) {
                println(String.valueOf(d));
            }

            @Override public void println(char[] s) {
                println(String.valueOf(s));
            }

            @Override public void println(char c) {
                println(String.valueOf(c));
            }

            @Override public void println(Object obj) {
                println(obj != null ? obj.toString() : "null");
            }
        };
        System.setOut(monitorPrintStream);
        System.setErr(monitorPrintStream);

        String classOrPackageName;
        String qualification;

        // Check whether the class or package is qualified and, if so, strip it off and pass it
        // separately to the runners. For instance, may qualify a junit class by appending
        // #method_name, where method_name is the name of a single test of the class to run.
        int hash_position = qualifiedClassOrPackageName.indexOf("#");
        if (hash_position != -1) {
            classOrPackageName = qualifiedClassOrPackageName.substring(0, hash_position);
            qualification = qualifiedClassOrPackageName.substring(hash_position + 1);
        } else {
            classOrPackageName = qualifiedClassOrPackageName;
            qualification = null;
        }

        Set<Class<?>> classes = new ClassFinder().find(classOrPackageName);

        // if there is more than one class in the set, this must be a package. Since we're
        // running everything in the package already, remove any class called AllTests.
        if (classes.size() > 1) {
            Set<Class<?>> toRemove = new HashSet<Class<?>>();
            for (Class<?> klass : classes) {
                if (klass.getName().endsWith(".AllTests")) {
                    toRemove.add(klass);
                }
            }
            classes.removeAll(toRemove);
        }

        for (Class<?> klass : classes) {
            Class<?> runnerClass = runnerClass(klass);
            if (runnerClass != null) {
                Runner runner;
                try {
                    runner = (Runner) runnerClass.newInstance();
                    runner.init(monitor, qualifiedName, qualification, klass);
                } catch (Exception e) {
                    monitor.outcomeStarted(null, qualifiedName, qualifiedName);
                    e.printStackTrace();
                    monitor.outcomeFinished(Result.ERROR);
                    return;
                }
                runner.run(qualifiedName, klass, args, timeoutSeconds);
            } else {
                monitor.outcomeStarted(null, klass.getName(), qualifiedName);
                System.out.println("Skipping " + klass.getName() + ": no associated runner class");
                monitor.outcomeFinished(Result.UNSUPPORTED);
            }
        }
    }

    public static void main(String[] args) {
        TestRunner testRunner = new TestRunner(new ArrayList<String>(Arrays.asList(args)));
        testRunner.run(testRunner.args);
    }
}
