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

package vogar;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import vogar.commands.Mkdir;
import vogar.tasks.BuildActionTask;
import vogar.tasks.CleanupActionTask;
import vogar.tasks.RunActionTask;
import vogar.tasks.Task;
import vogar.tasks.TaskQueue;
import vogar.util.TimeUtilities;

/**
 * Compiles, installs, runs and reports on actions.
 */
public final class Driver {

    @Inject Console console;
    @Inject Mkdir mkdir;
    @Inject @Named("localTemp") File localTemp;
    @Inject ExpectationStore expectationStore;
    @Inject Mode mode;
    @Inject XmlReportPrinter reportPrinter;
    @Inject @Named("firstMonitorPort")
    public int firstMonitorPort;
    @Inject @Named("smallTimeoutSeconds")
    public int smallTimeoutSeconds;
    @Inject @Named("largeTimeoutSeconds") int largeTimeoutSeconds;
    @Inject JarSuggestions jarSuggestions;
    @Inject ClassFileIndex classFileIndex;
    @Inject @Named("numRunners")
    public int numRunnerThreads;
    @Inject @Named("benchmark")
    public boolean benchmark;
    @Inject OutcomeStore outcomeStore;
    @Inject TaskQueue taskQueue;
    private int successes = 0;
    private int failures = 0;
    private int skipped = 0;

    private final Map<String, Action> actions = Collections.synchronizedMap(
            new LinkedHashMap<String, Action>());
    private final Map<String, Outcome> outcomes = Collections.synchronizedMap(
            new LinkedHashMap<String, Outcome>());
    public boolean recordResults = true;

    /**
     * Builds and executes the actions in the given files.
     */
    public boolean buildAndRun(Collection<File> files, Collection<String> classes) {
        if (!actions.isEmpty()) {
            throw new IllegalStateException("Drivers are not reusable");
        }

        mkdir.mkdirs(localTemp);

        filesToActions(files);
        classesToActions(classes);

        if (actions.isEmpty()) {
            console.info("Nothing to do.");
            return false;
        }

        console.info("Actions: " + actions.size());
        final long t0 = System.currentTimeMillis();

        // install vogar and do other preparation for the target
        mode.environment.installTasks(taskQueue);
        mode.installTasks(taskQueue);

        // TODO: this is a hack because we're using tasks as predicates
        Task installedRunner = Task.uponSuccessOf(taskQueue.getTasks());
        taskQueue.enqueue(installedRunner);

        for (Action action : actions.values()) {
            Outcome outcome = outcomes.get(action.getName());
            if (outcome != null) {
                addEarlyResult(outcome);
            } else if (expectationStore.get(action.getName()).getResult() == Result.UNSUPPORTED) {
                addEarlyResult(new Outcome(action.getName(), Result.UNSUPPORTED,
                    "Unsupported according to expectations file"));
            } else {
                String actionName = action.getName();
                Expectation expectation = expectationStore.get(actionName);
                int timeoutSeconds = expectation.getTags().contains("large")
                        ? largeTimeoutSeconds
                        : smallTimeoutSeconds;

                File jar = mode.environment.hostJar(action);
                Task buildActionTask = new BuildActionTask(action, mode, this, jar);
                Task installActionTask = mode.installActionTask(
                        taskQueue, buildActionTask, action, jar);
                RunActionTask runActionTask = new RunActionTask(action, console, mode,
                        timeoutSeconds, this, installedRunner, installActionTask);
                taskQueue.enqueue(buildActionTask);
                taskQueue.enqueue(runActionTask);
                taskQueue.enqueue(new CleanupActionTask(action, mode, runActionTask));
            }
        }

        taskQueue.runTasks();

        List<Task> blockedTasks = taskQueue.getBlockedTasks();
        for (Task task : blockedTasks) {
            console.info("Failed to execute " + task);
        }

        if (reportPrinter.isReady()) {
            console.info("Printing XML Reports... ");
            int numFiles = reportPrinter.generateReports(outcomes.values());
            console.info(numFiles + " XML files written.");
        }

        mode.shutdown();
        long t1 = System.currentTimeMillis();

        Map<String, AnnotatedOutcome> annotatedOutcomes = outcomeStore.read(this.outcomes);
        if (recordResults) {
            outcomeStore.write(outcomes);
        }

        console.summarizeOutcomes(annotatedOutcomes.values());

        List<String> jarStringList = jarSuggestions.getStringList();
        if (!jarStringList.isEmpty()) {
            console.warn(
                    "consider adding the following to the classpath:",
                    jarStringList);
        }

        if (failures > 0 || skipped > 0) {
            console.info(String.format(
                    "Outcomes: %s. Passed: %d, Failed: %d, Skipped: %d. Took %s.",
                    (successes + failures + skipped), successes, failures, skipped,
                    TimeUtilities.msToString(t1 - t0)));
        } else {
            console.info(String.format("Outcomes: %s. All successful. Took %s.",
                    successes, TimeUtilities.msToString(t1 - t0)));
        }
        return failures == 0;
    }

    private void classesToActions(Collection<String> classNames) {
        for (String className : classNames) {
            Action action = new Action(className, className, null, null, null);
            actions.put(action.getName(), action);
        }
    }

    private void filesToActions(Collection<File> files) {
        for (File file : files) {
            new ActionFinder(console, actions, outcomes).findActions(file);
        }
    }

    public synchronized void addEarlyResult(Outcome earlyFailure) {
        if (earlyFailure.getResult() == Result.UNSUPPORTED) {
            console.verbose("skipped " + earlyFailure.getName());
            skipped++;

        } else {
            for (String line : earlyFailure.getOutputLines()) {
                console.streamOutput(earlyFailure.getName(), line + "\n");
            }
            recordOutcome(earlyFailure);
        }
    }

    public synchronized void recordOutcome(Outcome outcome) {
        outcomes.put(outcome.getName(), outcome);
        Expectation expectation = expectationStore.get(outcome);
        ResultValue resultValue = outcome.getResultValue(expectation);

        if (resultValue == ResultValue.OK) {
            successes++;
        } else if (resultValue == ResultValue.FAIL) {
            failures++;
        } else { // ResultValue.IGNORE
            skipped++;
        }

        Result result = outcome.getResult();
        console.outcome(outcome.getName());
        console.printResult(outcome.getName(), result, resultValue, expectation);

        JarSuggestions singleOutcomeJarSuggestions = new JarSuggestions();
        singleOutcomeJarSuggestions.addSuggestionsFromOutcome(outcome, classFileIndex,
                mode.getClasspath());
        List<String> jarStringList = singleOutcomeJarSuggestions.getStringList();
        if (!jarStringList.isEmpty()) {
            console.warn(
                    "may have failed because some of these jars are missing from the classpath:",
                    jarStringList);
        }
        jarSuggestions.addSuggestions(singleOutcomeJarSuggestions);
    }
}
