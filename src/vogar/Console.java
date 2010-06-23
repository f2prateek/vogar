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

import com.google.common.collect.Lists;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Controls, formats and emits output to the command line. This class emits
 * output in two modes:
 * <ul>
 *   <li><strong>Streaming</strong> output prints as it is received, but cannot
 *       support multiple concurrent output streams.
 *   <li><strong>Multiplexing</strong> buffers output until it is complete and
 *       then prints it completely.
 * </ul>
 */
public abstract class Console {

    private static Console NULL_CONSOLE = new Console() {
        @Override public void streamOutput(String outcomeName, String output) {
            throw new IllegalStateException("Call Console.init() first");
        }
    };

    private static Console INSTANCE = NULL_CONSOLE;

    private boolean useColor;
    private boolean verbose;
    protected String indent;
    protected CurrentLine currentLine = CurrentLine.NEW;

    private Console() {}

    public static void init(boolean streaming) {
        INSTANCE = streaming ? new StreamingConsole() : new MultiplexingConsole();
    }

    public static Console getInstance() {
        return INSTANCE;
    }

    public void setIndent(String indent) {
        this.indent = indent;
    }

    public void setUseColor(boolean useColor) {
        this.useColor = useColor;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public synchronized void verbose(String s) {
        newLine();
        System.out.print(s);
        System.out.flush();
        currentLine = CurrentLine.VERBOSE;
    }

    public synchronized void warn(String message) {
        newLine();
        System.out.println(colorString("Warning: " + message, Color.YELLOW));
    }

    /**
     * Warns, and also puts a list of strings afterwards.
     */
    public synchronized void warn(String message, List<String> list) {
        newLine();
        System.out.println(colorString("Warning: " + message, Color.YELLOW));
        for (String item : list) {
            System.out.println(colorString(indent + item, Color.YELLOW));
        }
    }

    public synchronized void info(String s) {
        newLine();
        System.out.println(s);
    }

    public synchronized void info(String message, Throwable throwable) {
        newLine();
        System.out.println(message);
        throwable.printStackTrace(System.out);
    }

    /**
     * Begins streaming output for the named action.
     */
    public void action(String name) {}

    /**
     * Begins streaming output for the named outcome.
     */
    public void outcome(String name) {}

    /**
     * Appends the action output immediately to the stream when streaming is on,
     * or to a buffer when streaming is off. Buffered output will be held and
     * printed only if the outcome is unsuccessful.
     */
    public abstract void streamOutput(String outcomeName, String output);

    /**
     * Writes the action's outcome.
     */
    public synchronized void printResult(String outcomeName, Result result, ResultValue resultValue) {
        if (currentLine == CurrentLine.NAME) {
            System.out.print(" ");
        } else {
            System.out.print("\n" + indent + outcomeName + " ");
        }

        if (resultValue == ResultValue.OK) {
            System.out.println(colorString("OK (" + result + ")", Color.GREEN));
        } else if (resultValue == ResultValue.FAIL) {
            System.out.println(colorString("FAIL (" + result + ")", Color.RED));
        } else if (resultValue == ResultValue.IGNORE) {
            System.out.println(colorString("SKIP (" + result + ")", Color.YELLOW));
        }

        currentLine = CurrentLine.NEW;
    }

    public synchronized void summarizeOutcomes(Set<AnnotatedOutcome> annotatedOutcomesSet) {
        List<AnnotatedOutcome> annotatedOutcomes = AnnotatedOutcome.ORDER_BY_NAME.sortedCopy(annotatedOutcomesSet);

        List<String> failures = Lists.newArrayList();
        List<String> skips = Lists.newArrayList();
        List<String> successes = Lists.newArrayList();

        // figure out whether each outcome is noteworthy, and add a message to the appropriate list
        for (AnnotatedOutcome annotatedOutcome : annotatedOutcomes) {
            if (!annotatedOutcome.isNoteworthy()) {
                continue;
            }

            Color color;
            List<String> list;
            if (annotatedOutcome.getResultValue() == ResultValue.OK) {
                color = Color.GREEN;
                list = successes;
            } else if (annotatedOutcome.getResultValue() == ResultValue.FAIL) {
                color = Color.RED;
                list = failures;
            } else {
                color = Color.YELLOW;
                list = skips;
            }

            String tagMessage = "";
            if (annotatedOutcome.hasTag()) {
                tagMessage = String.format(" [%s at tag %s]",
                        generateSparkLine(Arrays.asList(annotatedOutcome.getTagResultValue())),
                        annotatedOutcome.getTagName());
            }

            Date lastChanged = annotatedOutcome.lastChanged();
            String timestamp;
            if (lastChanged == null) {
                timestamp = colorString("never", Color.YELLOW);
            } else {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd H:mm:ss");
                dateFormat.setLenient(true);
                timestamp = dateFormat.format(lastChanged);
            }

            String brokeThisMessage = "";
            ResultValue resultValue = annotatedOutcome.getResultValue();
            boolean resultValueChanged = resultValue != annotatedOutcome.getMostRecentResultValue();
            boolean resultValueChangedSinceTag =
                    annotatedOutcome.hasTag() && annotatedOutcome.getTagResultValue() != resultValue;
            if (resultValueChanged) {
                if (resultValue == ResultValue.OK) {
                    brokeThisMessage = colorString(" (you probably fixed this)", Color.YELLOW);
                } else {
                    brokeThisMessage = colorString(" (you probably broke this)", Color.YELLOW);
                }
            } else if (resultValueChangedSinceTag) {
                if (resultValue == ResultValue.OK) {
                    brokeThisMessage = colorString(" (fixed since tag)", Color.YELLOW);
                } else {
                    brokeThisMessage = colorString(" (broken since tag)", Color.YELLOW);
                }
            }

            List<ResultValue> previousResultValues = annotatedOutcome.getPreviousResultValues();
            int numResultValuesToShow = Math.min(10, previousResultValues.size());
            List<ResultValue> previousResultValuesToShow = new ArrayList<ResultValue>(
                    previousResultValues.subList(0, numResultValuesToShow));
            Collections.reverse(previousResultValuesToShow);

            if (!previousResultValuesToShow.isEmpty()) {
                list.add(String.format("%s%s%s [last %d: %s] [result last changed: %s]%s",
                            indent,
                            colorString(annotatedOutcome.getOutcome().getName(), color),
                            tagMessage,
                            previousResultValuesToShow.size(),
                            generateSparkLine(previousResultValuesToShow),
                            timestamp,
                            brokeThisMessage));
            } else {
                list.add(String.format("%s%s%s",
                            indent,
                            colorString(annotatedOutcome.getOutcome().getName(), color),
                            tagMessage));
            }
        }

        newLine();
        if (!successes.isEmpty()) {
            System.out.println("Success summary:");
            for (String success : successes) {
                System.out.println(success);
            }
        }
        if (!failures.isEmpty()) {
            System.out.println("Failure summary:");
            for (String failure : failures) {
                System.out.println(failure);
            }
        }
        if (!skips.isEmpty()) {
            System.out.println("Skips summary:");
            for (String skip : skips) {
                System.out.println(skip);
            }
        }
    }

    private String generateSparkLine(List<ResultValue> resultValues) {
        StringBuilder sb = new StringBuilder();
        for (ResultValue resultValue : resultValues) {
            if (resultValue == ResultValue.OK) {
                sb.append(colorString("\u2713", Color.GREEN));
            } else if (resultValue == ResultValue.FAIL) {
                sb.append(colorString("X", Color.RED));
            } else {
                sb.append(colorString("-", Color.YELLOW));
            }
        }
        return sb.toString();
    }

    /**
     * Prints the action output with appropriate indentation.
     */
    protected void printOutput(CharSequence streamedOutput) {
        if (streamedOutput.length() == 0) {
            return;
        }

        String[] lines = messageToLines(streamedOutput.toString());

        if (currentLine != CurrentLine.STREAMED_OUTPUT) {
            newLine();
            System.out.print(indent);
            System.out.print(indent);
        }
        System.out.print(lines[0]);
        currentLine = CurrentLine.STREAMED_OUTPUT;

        for (int i = 1; i < lines.length; i++) {
            newLine();

            if (lines[i].length() > 0) {
                System.out.print(indent);
                System.out.print(indent);
                System.out.print(lines[i]);
                currentLine = CurrentLine.STREAMED_OUTPUT;
            }
        }
    }

    /**
     * Inserts a linebreak if necessary.
     */
    protected void newLine() {
        if (currentLine == CurrentLine.NEW) {
            return;
        } else if (currentLine == CurrentLine.VERBOSE) {
            // --verbose means "leave all the verbose output on the screen".
            if (!verbose) {
                // Otherwise we overwrite verbose output whenever something new arrives.
                eraseCurrentLine();
                currentLine = CurrentLine.NEW;
                return;
            }
        }

        System.out.println();
        currentLine = CurrentLine.NEW;
    }

    /**
     * Status of a currently-in-progress line of output.
     */
    enum CurrentLine {

        /**
         * The line is blank.
         */
        NEW,

        /**
         * The line contains streamed application output. Additional streamed
         * output may be appended without additional line separators or
         * indentation.
         */
        STREAMED_OUTPUT,

        /**
         * The line contains the name of an action or outcome. The outcome's
         * result (such as "OK") can be appended without additional line
         * separators or indentation.
         */
        NAME,

        /**
         * The line contains verbose output, and may be overwritten.
         */
        VERBOSE,
    }

    /**
     * Returns an array containing the lines of the given text.
     */
    private String[] messageToLines(String message) {
        // pass Integer.MAX_VALUE so split doesn't trim trailing empty strings.
        return message.split("\r\n|\r|\n", Integer.MAX_VALUE);
    }

    private enum Color {
        GREEN, RED, YELLOW;

        public int getCode() {
            switch (this) {
                case GREEN:
                    return 32;
                case RED:
                    return 31;
                case YELLOW:
                    return 33;
            }
            throw new IllegalArgumentException(this + " is an invalid color");
        }
    }

    protected String colorString(String message, Color color) {
        return useColor ? ("\u001b[" + color.getCode() + ";1m" + message + "\u001b[0m") : message;
    }

    private void eraseCurrentLine() {
        System.out.print(useColor ? "\u001b[2K\r" : "\n");
        System.out.flush();
    }

    /**
     * This console prints output as it's emitted. It supports at most one
     * action at a time.
     */
    private static class StreamingConsole extends Console {
        private String currentName;

        @Override public synchronized void action(String name) {
            newLine();
            System.out.print("Action " + name);
            System.out.flush();
            currentName = name;
            currentLine = CurrentLine.NAME;
        }

        /**
         * Prints the beginning of the named outcome.
         */
        @Override public synchronized void outcome(String name) {
            // if the outcome and action names are the same, omit the outcome name
            if (name.equals(currentName)) {
                return;
            }

            currentName = name;
            super.newLine();
            System.out.print(indent + name);
            System.out.flush();
            currentLine = CurrentLine.NAME;
        }

        @Override public synchronized void streamOutput(String outcomeName, String output) {
            super.printOutput(output);
        }
    }

    /**
     * This console buffers output, only printing when a result is found. It
     * supports multiple concurrent actions.
     */
    private static class MultiplexingConsole extends Console {
        private final Map<String, StringBuilder> bufferedOutputByOutcome = new HashMap<String, StringBuilder>();

        @Override public synchronized void streamOutput(String outcomeName, String output) {
            StringBuilder buffer = bufferedOutputByOutcome.get(outcomeName);
            if (buffer == null) {
                buffer = new StringBuilder();
                bufferedOutputByOutcome.put(outcomeName, buffer);
            }

            buffer.append(output);
        }

        @Override public synchronized void printResult(String outcomeName, Result result, ResultValue resultValue) {
            newLine();
            System.out.print(indent + outcomeName);
            currentLine = CurrentLine.NAME;

            StringBuilder buffer = bufferedOutputByOutcome.remove(outcomeName);
            if (buffer != null) {
                printOutput(buffer);
            }

            super.printResult(outcomeName, result, resultValue);
        }
    }
}
