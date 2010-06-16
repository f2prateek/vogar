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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private static Console INSTANCE;

    private boolean color;
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

    public void setColor(boolean color) {
        this.color = color;
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
        System.out.println(yellow("Warning: " + message));
    }

    /**
     * Warns, and also puts a list of strings afterwards.
     */
    public synchronized void warn(String message, List<String> list) {
        newLine();
        System.out.println(yellow("Warning: " + message));
        for (String item : list) {
            System.out.println(yellow(indent + item));
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
            System.out.println(green("OK (" + result + ")"));
        } else if (resultValue == ResultValue.FAIL) {
            System.out.println(red("FAIL (" + result + ")"));
        } else if (resultValue == ResultValue.IGNORE) {
            System.out.println(yellow("SKIP (" + result + ")"));
        }

        currentLine = CurrentLine.NEW;
    }

    public synchronized void summarizeFailures(List<String> failureNames) {
        newLine();
        System.out.println("Failure summary:");
        for (String failureName : failureNames) {
            System.out.println(red(failureName));
        }
    }

    public synchronized void summarizeSkips(List<String> skippedNames) {
        newLine();
        System.out.println("Skip summary:");
        for (String skippedName : skippedNames) {
            System.out.println(yellow(skippedName));
        }
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

    protected String green(String message) {
        return color ? ("\u001b[32;1m" + message + "\u001b[0m") : message;
    }

    protected String red(String message) {
        return color ? ("\u001b[31;1m" + message + "\u001b[0m") : message;
    }

    protected String yellow(String message) {
        return color ? ("\u001b[33;1m" + message + "\u001b[0m") : message;
    }

    private void eraseCurrentLine() {
        System.out.print(color ? "\u001b[2K\r" : "\n");
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
