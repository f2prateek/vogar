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
import java.io.FilenameFilter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import vogar.commands.Mkdir;
import vogar.commands.Rm;

/**
 * TODO add description of directory structures for the tag and auto stores
 */
public final class OutcomeStore {
    private static final String FILE_NAME_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssz";

    private static final String TAG_FILENAME = "canonical.xml";

    private final File tagResultsDir;
    private final String tagName;
    private final String compareToTag;
    private final File autoResultsDir;
    private final boolean recordResults;
    private final ExpectationStore expectationStore;
    private final Date date;

    OutcomeStore(File tagDir, String tagName, String compareToTag, File resultsDir,
            boolean recordResults, ExpectationStore expectationStore, Date date) {
        this.tagResultsDir = new File(tagDir, "results");
        this.tagName = tagName;
        this.compareToTag = compareToTag;
        this.autoResultsDir = new File(resultsDir, "auto");
        this.recordResults = recordResults;
        this.expectationStore = expectationStore;
        this.date = date;
    }

    public AnnotatedOutcome read(Outcome outcome) {
        Expectation expectation = expectationStore.get(outcome);

        // read tag result (if applicable)
        Outcome tagOutcome = null;
        XmlReportReader reportReader = new XmlReportReader();
        if (compareToTag != null) {
            String tagOutputFileName = TAG_FILENAME;
            File tagResultsDir = new File(this.tagResultsDir, compareToTag);
            File tagOutcomeResultsDir = new File(tagResultsDir, outcome.getPath());
            File tagFile = new File(tagOutcomeResultsDir, tagOutputFileName);
            if (tagFile.exists()) {
                Collection<Outcome> outcomes =
                        reportReader.readSuiteReport(tagFile);
                // get what should be the only outcome in outcomes
                tagOutcome = outcomes.iterator().next();
            }
        }
        // read automatically recorded results (if they exist)
        File outcomeResultDir = new File(autoResultsDir, outcome.getPath());
        List<Outcome> previousOutcomes = new ArrayList<Outcome>();
        if (outcomeResultDir.exists()) {
            FilenameFilter xmlFilter = new FilenameFilter() {
                public boolean accept(File dir, String fileName) {
                    return fileName.endsWith(".xml");
                }
            };
            List<File> xmlResultFiles = Arrays.asList(outcomeResultDir.listFiles(xmlFilter));
            Collections.sort(xmlResultFiles, Collections.reverseOrder());
            for (File resultXmlFile : xmlResultFiles) {
                Collection<Outcome> outcomes = reportReader.readSuiteReport(resultXmlFile);
                previousOutcomes.add(outcomes.iterator().next());
            }
        }

        return new AnnotatedOutcome(outcome, expectation, previousOutcomes, compareToTag,
                tagOutcome);
    }

    public void write(Outcome outcome, boolean hasChanged) {
        File outcomeResultDir = new File(autoResultsDir, outcome.getPath());

        // record the outcome's result (only if the outcome has changed)
        if (hasChanged && recordResults) {
            // Re-output current results to file(s)
            new Mkdir().mkdirs(outcomeResultDir);
            XmlReportPrinter singleReportPrinter =
                    new XmlReportPrinter(outcomeResultDir, expectationStore, date, true);
            SimpleDateFormat dateFormat = new SimpleDateFormat(FILE_NAME_DATE_FORMAT);
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            dateFormat.setLenient(true);
            String timestamp = dateFormat.format(date);

            String outputFileName = timestamp + ".xml";
            singleReportPrinter.generateReport(outcome, outputFileName);
        }

        // record the outcome's result to a tag (if applicable)
        if (tagName != null) {
            String tagOutputFileName = TAG_FILENAME;
            File tagDir = new File(this.tagResultsDir, tagName);
            File tagOutcomeResultDir = new File(tagDir, outcome.getPath());
            File outcomeTagFile = new File(tagOutcomeResultDir, tagOutputFileName);
            if (outcomeTagFile.exists()) {
                new Rm().file(outcomeTagFile);
            }
            if (!tagOutcomeResultDir.mkdirs() && !tagOutcomeResultDir.exists()) {
                throw new RuntimeException("Failed to create directory " + tagOutcomeResultDir);
            }
            XmlReportPrinter tagSingleReportPrinter =
                    new XmlReportPrinter(tagOutcomeResultDir, expectationStore, date, true);
            tagSingleReportPrinter.generateReport(outcome, tagOutputFileName);
        }
    }
}