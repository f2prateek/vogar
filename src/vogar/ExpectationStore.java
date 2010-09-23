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

import com.google.caliper.internal.gson.stream.JsonReader;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A database of expected outcomes. Entries in this database come in two forms.
 * <ul>
 *   <li>Outcome expectations name an outcome (or its prefix, such as
 *       "java.util"), its expected result, and an optional pattern to match
 *       the expected output.
 *   <li>Failure expectations include a pattern that may match the output of any
 *       outcome. These expectations are useful for hiding failures caused by
 *       cross-cutting features that aren't supported.
 * </ul>
 *
 * <p>If an outcome matches both an outcome expectation and a failure
 * expectation, the outcome expectation will be returned.
 */
final class ExpectationStore {
    private final Map<String, Expectation> outcomes = new LinkedHashMap<String, Expectation>();
    private final Map<String, Expectation> failures = new LinkedHashMap<String, Expectation>();

    private ExpectationStore() {}

    /**
     * Finds the expected result for the specified action or outcome name. This
     * returns a value for all names, even if no explicit expectation was set.
     */
    public Expectation get(String name) {
        Expectation byName = getByNameOrPackage(name);
        return byName != null ? byName : Expectation.SUCCESS;
    }

    /**
     * Finds the expected result for the specified outcome after it has
     * completed. Unlike {@code get()}, this also takes into account the
     * outcome's output.
     *
     * <p>For outcomes that have both a name match and an output match,
     * exact name matches are preferred, then output matches, then inexact
     * name matches.
     */
    public Expectation get(Outcome outcome) {
        Expectation exactNameMatch = outcomes.get(outcome.getName());
        if (exactNameMatch != null) {
            return exactNameMatch;
        }

        for (Map.Entry<String, Expectation> entry : failures.entrySet()) {
            if (entry.getValue().matches(outcome)) {
                return entry.getValue();
            }
        }

        Expectation byName = getByNameOrPackage(outcome.getName());
        return byName != null ? byName : Expectation.SUCCESS;
    }

    private Expectation getByNameOrPackage(String name) {
        while (true) {
            Expectation expectation = outcomes.get(name);
            if (expectation != null) {
                return expectation;
            }

            int dotOrHash = Math.max(name.lastIndexOf('.'), name.lastIndexOf('#'));
            if (dotOrHash == -1) {
                return null;
            }

            name = name.substring(0, dotOrHash);
        }
    }

    public static ExpectationStore parse(Set<File> expectationFiles) throws IOException {
        ExpectationStore result = new ExpectationStore();
        for (File f : expectationFiles) {
            if (f.exists()) {
                result.parse(f);
            }
        }
        return result;
    }

    public void parse(File expectationsFile) throws IOException {
        Console.getInstance().verbose("loading expectations file " + expectationsFile);

        int count = 0;
        JsonReader reader = null;
        try {
            reader = new JsonReader(new FileReader(expectationsFile));
            reader.setLenient(true);
            reader.beginArray();
            while (reader.hasNext()) {
                readExpectation(reader);
                count++;
            }
            reader.endArray();

            Console.getInstance().verbose("loaded " + count + " expectations from " + expectationsFile);
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    private void readExpectation(JsonReader reader) throws IOException {
        boolean isFailure = false;
        Result result = Result.SUCCESS;
        Pattern pattern = Expectation.MATCH_ALL_PATTERN;
        Set<String> names = new LinkedHashSet<String>();
        Set<String> tags = new LinkedHashSet<String>();
        String description = "";
        long buganizerBug = -1;

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("result")) {
                result = Result.valueOf(reader.nextString());
            } else if (name.equals("name")) {
                names.add(reader.nextString());
            } else if (name.equals("names")) {
                readStrings(reader, names);
            } else if (name.equals("failure")) {
                isFailure = true;
                names.add(reader.nextString());
            } else if (name.equals("pattern")) {
                pattern = Pattern.compile(reader.nextString(), Pattern.MULTILINE | Pattern.DOTALL);
            } else if (name.equals("substring")) {
                pattern = Pattern.compile(Pattern.quote(reader.nextString()), Pattern.MULTILINE | Pattern.DOTALL);
            } else if (name.equals("tags")) {
                readStrings(reader, tags);
            } else if (name.equals("description")) {
                Iterable<String> split = Splitter.on("\n").omitEmptyStrings().trimResults().split(reader.nextString());
                description = Joiner.on("\n").join(split);
            } else if (name.equals("bug")) {
                buganizerBug = reader.nextLong();
            } else {
                Console.getInstance().warn("Unhandled name in expectations file: " + name);
                reader.skipValue();
            }
        }
        reader.endObject();

        if (names.isEmpty()) {
            throw new IllegalArgumentException("Missing 'name' or 'failure' key in " + reader);
        }

        Expectation expectation = new Expectation(result, pattern, tags, description, buganizerBug);
        Map<String, Expectation> map = isFailure ? failures : outcomes;
        for (String name : names) {
            if (map.put(name, expectation) != null) {
                throw new IllegalArgumentException("Duplicate expectations for " + name);
            }
        }
    }

    private void readStrings(JsonReader reader, Set<String> output) throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            output.add(reader.nextString());
        }
        reader.endArray();
    }
}
