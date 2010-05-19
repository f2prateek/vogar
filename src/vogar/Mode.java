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
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import vogar.commands.Command;
import vogar.commands.CommandFailedException;
import vogar.commands.Mkdir;

/**
 * A Mode for running actions. Examples including running in a virtual machine
 * either on the host or a device or within a specific context such as within an
 * Activity.
 */
abstract class Mode {

    private static final Pattern JAVA_SOURCE_PATTERN = Pattern.compile("\\/(\\w)+\\.java$");

    protected final Environment environment;

    protected static class Options {
        protected final Classpath buildClasspath;
        protected final List<File> sourcepath;
        protected final List<String> javacArgs;
        protected final File javaHome;
        protected final int monitorPort;
        protected final int timeoutSeconds;
        protected final boolean useBootClasspath;
        protected final Classpath classpath;

        Options(Classpath buildClasspath,
                List<File> sourcepath,
                List<String> javacArgs,
                File javaHome,
                int monitorPort,
                int timeoutSeconds,
                boolean useBootClasspath,
                Classpath classpath) {
            this.buildClasspath = buildClasspath;
            this.sourcepath = sourcepath;
            this.javacArgs = javacArgs;
            this.javaHome = javaHome;
            this.monitorPort = monitorPort;
            this.timeoutSeconds = timeoutSeconds;
            this.useBootClasspath = useBootClasspath;
            this.classpath = classpath;
        }
    }

    final Options modeOptions;

    /**
     * User classes that need to be included in the classpath for both
     * compilation and execution. Also includes dependencies of all active
     * runners.
     */
    protected final Classpath classpath = new Classpath();

    Mode(Environment environment, Options modeOptions) {
        this.environment = environment;
        this.modeOptions = modeOptions;
        this.classpath.addAll(modeOptions.classpath);
    }

    /**
     * Returns a path for a Java tool such as java, javac, jar where
     * the Java home is used if present, otherwise assumes it will
     * come from the path.
     */
    String javaPath (String tool) {
        return (modeOptions.javaHome == null)
            ? tool
            : new File(new File(modeOptions.javaHome, "bin"), tool).getPath();
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
        Console.getInstance().verbose("build " + action.getName());

        try {
            File jar = compile(action);
            postCompile(action, jar);
        } catch (CommandFailedException e) {
            return new Outcome(action.getName(), action.getName(),
                    Result.COMPILE_FAILED, e.getOutputLines());
        } catch (IOException e) {
            return new Outcome(action.getName(), Result.ERROR, e);
        }
        environment.prepareUserDir(action);
        return null;
    }

    /**
     * Returns the .jar file containing the action's compiled classes.
     *
     * @throws CommandFailedException if javac fails
     */
    private File compile(Action action) throws IOException {
        File classesDir = environment.file(action, "classes");
        new Mkdir().mkdirs(classesDir);
        FileOutputStream propertiesOut = new FileOutputStream(
                new File(classesDir, TestProperties.FILE));
        Properties properties = new Properties();
        fillInProperties(properties, action);
        properties.store(propertiesOut, "generated by " + Mode.class.getName());
        propertiesOut.close();

        Set<File> sourceFiles = new HashSet<File>();
        File javaFile = action.getJavaFile();
        Javac javac = new Javac(javaPath("javac"));
        if (javaFile != null) {
            if (!JAVA_SOURCE_PATTERN.matcher(javaFile.toString()).find()) {
                throw new CommandFailedException(Collections.<String>emptyList(),
                        Collections.singletonList("Cannot compile: " + javaFile));
            }
            sourceFiles.add(javaFile);
            Classpath sourceDirs = Classpath.of(action.getSourcePath());
            sourceDirs.addAll(modeOptions.sourcepath);
            javac.sourcepath(sourceDirs.getElements());
        }
        if (!sourceFiles.isEmpty()) {
            if (!modeOptions.buildClasspath.isEmpty()) {
                javac.bootClasspath(modeOptions.buildClasspath);
            }
            javac.classpath(classpath)
                    .destination(classesDir)
                    .extra(modeOptions.javacArgs)
                    .compile(sourceFiles);
        }

        File jar = environment.hostJar(action);
        new Command(javaPath("jar"), "cvfM", jar.getPath(),
                "-C", classesDir.getPath(), "./").execute();
        return jar;
    }

    /**
     * Fill in properties for running in this mode
     */
    protected void fillInProperties(Properties properties, Action action) {
        properties.setProperty(TestProperties.TEST_CLASS_OR_PACKAGE, action.getTargetClass());
        properties.setProperty(TestProperties.QUALIFIED_NAME, action.getName());
        properties.setProperty(TestProperties.MONITOR_PORT, Integer.toString(modeOptions.monitorPort));
        properties.setProperty(TestProperties.TIMEOUT, Integer.toString(modeOptions.timeoutSeconds));
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
     */
    protected abstract Command createActionCommand(Action action);

    /**
     * Deletes files and releases any resources required for the execution of
     * the given action.
     */
    void cleanup(Action action) {
        environment.cleanup(action);
    }

    /**
     * Cleans up after all actions have completed.
     */
    void shutdown() {
        environment.shutdown();
    }
}
