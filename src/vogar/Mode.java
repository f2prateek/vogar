/*
 * Copyright (C) 2010 The Android Open Source Project
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

package vogar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Named;
import vogar.commands.Command;
import vogar.commands.CommandFailedException;
import vogar.commands.Mkdir;

/**
 * A Mode for running actions. Examples including running in a virtual machine
 * either on the host or a device or within a specific context such as within an
 * Activity.
 */
public abstract class Mode {

    private static final Pattern JAVA_SOURCE_PATTERN = Pattern.compile("\\/(\\w)+\\.java$");

    @Inject protected Environment environment;
    @Inject Log log;
    @Inject Mkdir mkdir;
    @Inject @Named("buildClasspath") Classpath buildClasspath;
    @Inject @Named("sourcepath") List<File> sourcepath;
    @Inject @Named("debugPort") Integer debugPort;
    @Inject @Named("javacArgs") List<String> javacArgs;
    @Inject @Named("javaHome") File javaHome;
    @Inject @Named("firstMonitorPort") int firstMonitorPort;
    @Inject @Named("smallTimeoutSeconds") int timeoutSeconds;
    @Inject @Named("useBootClasspath") boolean useBootClasspath;
    @Inject @Named("profile") boolean profile;
    @Inject @Named("profileDepth") int profileDepth;
    @Inject @Named("profileInterval") int profileInterval;
    @Inject @Named("profileFile") File profileFile;
    @Inject @Named("profileThreadGroup") boolean profileThreadGroup;

    /**
     * User classes that need to be included in the classpath for both
     * compilation and execution. Also includes dependencies of all active
     * runners.
     */
    @Inject protected Classpath classpath = new Classpath();

    /**
     * Returns a path for a Java tool such as java, javac, jar where
     * the Java home is used if present, otherwise assumes it will
     * come from the path.
     */
    String javaPath (String tool) {
        return (javaHome == null)
            ? tool
            : new File(new File(javaHome, "bin"), tool).getPath();
    }

    /**
     * Initializes the temporary directories and harness necessary to run
     * actions.
     */
    protected void prepare() {
        environment.prepare();
        classpath.addAll(vogarJar());
        installRunner();
    }

    /**
     * Returns the .jar file containing Vogar.
     */
    private File vogarJar() {
        URL jarUrl = Vogar.class.getResource("/vogar/Vogar.class");
        if (jarUrl == null) {
            // should we add an option for IDE users, to use a user-specified vogar.jar?
            throw new IllegalStateException("Vogar cannot find its own .jar");
        }

        /*
         * Parse a URI like jar:file:/Users/jessewilson/vogar/vogar.jar!/vogar/Vogar.class
         * to yield a .jar file like /Users/jessewilson/vogar/vogar.jar.
         */
        String url = jarUrl.toString();
        int bang = url.indexOf("!");
        String JAR_URI_PREFIX = "jar:file:";
        if (url.startsWith(JAR_URI_PREFIX) && bang != -1) {
            return new File(url.substring(JAR_URI_PREFIX.length(), bang));
        } else {
            throw new IllegalStateException("Vogar cannot find the .jar file in " + jarUrl);
        }
    }

    /**
     * Compiles classes for the given action and makes them ready for execution.
     *
     * @return null if the compilation succeeded, or an outcome describing the
     *      failure otherwise.
     */
    public Outcome buildAndInstall(Action action) {
        log.verbose("build " + action.getName());
        environment.prepareUserDir(action);

        try {
            File jar = compile(action);
            postCompile(action, jar);
        } catch (CommandFailedException e) {
            return new Outcome(action.getName(),
                    Result.COMPILE_FAILED, e.getOutputLines());
        } catch (IOException e) {
            return new Outcome(action.getName(), Result.ERROR, e);
        }
        return null;
    }

    /**
     * Returns the .jar file containing the action's compiled classes.
     *
     * @throws CommandFailedException if javac fails
     */
    private File compile(Action action) throws IOException {
        File classesDir = environment.file(action, "classes");
        mkdir.mkdirs(classesDir);
        createJarMetadataFiles(action, classesDir);

        Set<File> sourceFiles = new HashSet<File>();
        File javaFile = action.getJavaFile();
        Javac javac = new Javac(log, javaPath("javac"));
        if (debugPort != null) {
            javac.debug();
        }
        if (javaFile != null) {
            if (!JAVA_SOURCE_PATTERN.matcher(javaFile.toString()).find()) {
                throw new CommandFailedException(Collections.<String>emptyList(),
                        Collections.singletonList("Cannot compile: " + javaFile));
            }
            sourceFiles.add(javaFile);
            Classpath sourceDirs = Classpath.of(action.getSourcePath());
            sourceDirs.addAll(sourcepath);
            javac.sourcepath(sourceDirs.getElements());
        }
        if (!sourceFiles.isEmpty()) {
            if (!buildClasspath.isEmpty()) {
                javac.bootClasspath(buildClasspath);
            }
            javac.classpath(classpath)
                    .destination(classesDir)
                    .extra(javacArgs)
                    .compile(sourceFiles);
        }

        File jar = environment.hostJar(action);
        new Command(log, javaPath("jar"), "cvfM", jar.getPath(),
                "-C", classesDir.getPath(), "./").execute();
        return jar;
    }

    /**
     * Writes files to {@code classesDir} to be included in the .jar file for
     * {@code action}.
     */
    protected void createJarMetadataFiles(Action action, File classesDir) throws IOException {
        OutputStream propertiesOut
                = new FileOutputStream(new File(classesDir, TestProperties.FILE));
        Properties properties = new Properties();
        fillInProperties(properties, action);
        properties.store(propertiesOut, "generated by " + Mode.class.getName());
        propertiesOut.close();
    }

    /**
     * Fill in properties for running in this mode
     */
    protected void fillInProperties(Properties properties, Action action) {
        properties.setProperty(TestProperties.TEST_CLASS_OR_PACKAGE, action.getTargetClass());
        properties.setProperty(TestProperties.QUALIFIED_NAME, action.getName());
        properties.setProperty(TestProperties.MONITOR_PORT, Integer.toString(firstMonitorPort));
        properties.setProperty(TestProperties.TIMEOUT, Integer.toString(timeoutSeconds));
        properties.setProperty(TestProperties.PROFILE, Boolean.toString(profile));
        properties.setProperty(TestProperties.PROFILE_DEPTH, Integer.toString(profileDepth));
        properties.setProperty(TestProperties.PROFILE_INTERVAL, Integer.toString(profileInterval));
        properties.setProperty(TestProperties.PROFILE_FILE, profileFile.getName());
        properties.setProperty(TestProperties.PROFILE_THREAD_GROUP,
                               Boolean.toString(profileThreadGroup));
    }

    /**
     * Hook method called after runner compilation.
     */
    protected void installRunner() {}

    /**
     * Hook method called after action compilation.
     */
    protected void postCompile(Action action, File jar) {}

    /**
     * Create the command that executes the action.
     *
     * @param skipPast the last outcome to skip, or null to run all outcomes.
     * @param monitorPort the port to accept connections on, or -1 for the
     */
    public abstract Command createActionCommand(Action action, String skipPast, int monitorPort);

    /**
     * Deletes files and releases any resources required for the execution of
     * the given action.
     */
    public void cleanup(Action action) {
        environment.cleanup(action);
    }

    /**
     * Cleans up after all actions have completed.
     */
    void shutdown() {
        environment.shutdown();
    }

    public Classpath getClasspath() {
        return classpath;
    }

    /**
     * Returns true if this mode requires a socket connection for reading test
     * results. Otherwise all communication happens over the output stream of
     * the forked process.
     */
    public boolean useSocketMonitor() {
        return false;
    }
}
