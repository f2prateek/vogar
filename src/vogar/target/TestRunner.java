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

import java.io.File;
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
import java.util.concurrent.atomic.AtomicReference;

import vogar.Result;
import vogar.TestProperties;
import vogar.monitor.TargetMonitor;

/**
 * Runs an action, in process on the target.
 */
public final class TestRunner {

    protected final Properties properties;

    protected final String qualifiedName;
    protected final String qualifiedClassOrPackageName;

    /** the monitor port if a monitor is expected, or null for no monitor */
    protected final Integer monitorPort;

    /** use an atomic reference so the runner can null it out when it is encountered. */
    protected final AtomicReference<String> skipPastReference;
    protected final int timeoutSeconds;
    protected final List<Runner> runners;
    private final boolean profile;
    private final int profileDepth;
    private final int profileInterval;
    private final File profileFile;
    private final boolean profileThreadGroup;
    protected final String[] args;
    private boolean useSocketMonitor;

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
        String skipPast = null;
        boolean profile = Boolean.parseBoolean(properties.getProperty(TestProperties.PROFILE));
        int profileDepth = Integer.parseInt(properties.getProperty(TestProperties.PROFILE_DEPTH));
        int profileInterval
                = Integer.parseInt(properties.getProperty(TestProperties.PROFILE_INTERVAL));
        File profileFile = new File(properties.getProperty(TestProperties.PROFILE_FILE));
        boolean profileThreadGroup
                = Boolean.parseBoolean(properties.getProperty(TestProperties.PROFILE_THREAD_GROUP));

        for (Iterator<String> i = argsList.iterator(); i.hasNext(); ) {
            String arg = i.next();
            if (arg.equals("--monitorPort")) {
                i.remove();
                monitorPort = Integer.parseInt(i.next());
                i.remove();
            }
            if (arg.equals("--skipPast")) {
                i.remove();
                skipPast = i.next();
                i.remove();
            }
        }

        this.monitorPort = monitorPort;
        this.skipPastReference = new AtomicReference<String>(skipPast);
        this.profile = profile;
        this.profileDepth = profileDepth;
        this.profileInterval = profileInterval;
        this.profileFile = profileFile;
        this.profileThreadGroup = profileThreadGroup;
        this.args = argsList.toArray(new String[argsList.size()]);
    }

    private Properties loadProperties() {
        try {
            InputStream in = getPropertiesStream();
            Properties properties = new Properties();
            properties.load(in);
            in.close();
            return properties;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Configure this test runner to await an incoming socket connection when
     * writing test results. Otherwise all communication happens over
     * System.out.
     */
    public void useSocketMonitor() {
        this.useSocketMonitor = true;
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

    public void run() throws IOException {
        TargetMonitor monitor = useSocketMonitor
                ? TargetMonitor.await(monitorPort)
                : TargetMonitor.forPrintStream(System.out);
        try {
            run(monitor);
        } finally {
            monitor.close();
        }
    }

    public void run(final TargetMonitor monitor) {
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

        TestEnvironment testEnvironment = new TestEnvironment();
        testEnvironment.reset();

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

        Profiler profiler = profile ? Profiler.getInstance() : null;
        if (profiler != null) {
            profiler.setup(profileThreadGroup, profileDepth, profileInterval);
        }
        for (Class<?> klass : classes) {
            Class<?> runnerClass = runnerClass(klass);
            if (runnerClass == null) {
                monitor.outcomeStarted(null, klass.getName(), qualifiedName);
                System.out.println("Skipping " + klass.getName()
                        + ": no associated runner class");
                monitor.outcomeFinished(Result.UNSUPPORTED);
                continue;
            }

            Runner runner;
            try {
                runner = (Runner) runnerClass.newInstance();
                runner.init(monitor, qualifiedName, qualification, klass, skipPastReference,
                        testEnvironment, timeoutSeconds, profile);
            } catch (Exception e) {
                monitor.outcomeStarted(null, qualifiedName, qualifiedName);
                e.printStackTrace();
                monitor.outcomeFinished(Result.ERROR);
                return;
            }
            boolean completedNormally = runner.run(qualifiedName, profiler, args);
            if (!completedNormally) {
                return; // let the caller start another process
            }
        }
        if (profiler != null) {
            profiler.shutdown(profileFile);
        }

        monitor.completedNormally(true);
    }

    public static void main(String[] args) throws IOException {
        new TestRunner(new ArrayList<String>(Arrays.asList(args))).run();
    }
}
