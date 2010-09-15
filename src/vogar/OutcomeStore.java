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

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TimeZone;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import vogar.commands.Mkdir;
import vogar.commands.Rm;
import vogar.util.Strings;

/**
 * TODO add description of directory structures for the tag and auto stores
 */
public final class OutcomeStore {
    private static final String FILE_NAME_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssz";
    private static final String TAG_FILENAME = "canonical.xml";
    private static final FilenameFilter XML_FILTER = new FilenameFilter() {
        public boolean accept(File dir, String fileName) {
            return fileName.endsWith(".xml");
        }
    };

    @Inject @Named("tagDir") File tagDir;
    @Inject @Named("tagName") String tagName;
    @Inject @Named("compareToTag") String compareToTag;
    @Inject @Named("resultsDir") File resultsDir;
    @Inject @Named("recordResults") boolean recordResults;
    @Inject Provider<XmlReportPrinter> xmlReportPrinterProvider;
    @Inject ExpectationStore expectationStore;
    @Inject Date date;

    private File tagResultsDir() {
        return new File(tagDir, "results");
    }

    private File autoResultsDir() {
        return new File(resultsDir, "auto");
    }

    public AnnotatedOutcome read(Outcome outcome) {
        Expectation expectation = expectationStore.get(outcome);

        // read tag result (if applicable)
        Outcome tagOutcome = null;
        XmlReportReader reportReader = new XmlReportReader();
        if (compareToTag != null) {
            String tagOutputFileName = TAG_FILENAME;
            File tagResultsDir = new File(tagResultsDir(), compareToTag);
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
        File outcomeResultDir = new File(autoResultsDir(), outcome.getPath());
        SortedMap<Long, Outcome> previousOutcomes = Maps.newTreeMap();
        boolean hasMetadata = false;
        if (outcomeResultDir.exists()) {
            List<File> xmlResultFiles = getXmlFiles(outcomeResultDir);
            Map<String, Outcome> outcomesByFileName = Maps.newHashMap();

            for (File resultXmlFile : xmlResultFiles) {
                Collection<Outcome> outcomes = reportReader.readSuiteReport(resultXmlFile);
                outcomesByFileName.put(resultXmlFile.getName(), outcomes.iterator().next());
            }

            File metadataFile = new File(outcomeResultDir, ".meta");
            if (metadataFile.exists()) {
                try {
                    List<String> lines = Strings.readFileLines(metadataFile);
                    for (String line : lines) {
                        Splitter commas = Splitter.on(",");
                        Iterator<String> lineParts = commas.split(line).iterator();
                        long time = Long.valueOf(lineParts.next());
                        Outcome previousOutcome = outcomesByFileName.get(lineParts.next());
                        if (previousOutcome != null) {
                            previousOutcomes.put(time, previousOutcome);
                        } else {
                            Console.getInstance().warn("No outcome: " + lineParts.next());
                        }
                    }
                    hasMetadata = true;
                } catch (Exception e) {
                    Console.getInstance().info("failed to read outcome metadata", e);
                }
            } else {
                for (Outcome previousOutcome : outcomesByFileName.values()) {
                    previousOutcomes.put(previousOutcome.getDate().getTime(), previousOutcome);
                }
            }
        }

        return new AnnotatedOutcome(outcome, expectation, previousOutcomes, compareToTag,
                tagOutcome, hasMetadata);
    }

    private List<File> getXmlFiles(File outcomeResultDir) {
        return Arrays.asList(outcomeResultDir.listFiles(XML_FILTER));
    }

    public void write(Outcome outcome, boolean hasChanged) {
        File outcomeResultDir = new File(autoResultsDir(), outcome.getPath());

        if (recordResults) {
            new Mkdir().mkdirs(outcomeResultDir);

            // record the outcome's result (only if the outcome has changed)
            SimpleDateFormat dateFormat = new SimpleDateFormat(FILE_NAME_DATE_FORMAT);
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            dateFormat.setLenient(true);
            String timestamp = dateFormat.format(date);
            String outputFileName = timestamp + ".xml";
            if (hasChanged) {
                XmlReportPrinter singleReportPrinter = xmlReportPrinterProvider.get();
                singleReportPrinter.setOutput(outcomeResultDir, true);
                singleReportPrinter.generateReport(outcome, outputFileName);
            }

            try {
                File metadataFile = new File(outcomeResultDir, ".meta");
                PrintStream metadataPrintStream =
                        new PrintStream(new FileOutputStream(metadataFile, true));
                String fileNameContainingOutcome;
                if (hasChanged) {
                    fileNameContainingOutcome = outputFileName;
                } else {
                    List<File> xmlFiles = getXmlFiles(outcomeResultDir);
                    if (xmlFiles.isEmpty()) {
                        throw new RuntimeException("expected at least one outcome in "
                                + outcomeResultDir);
                    }
                    Collections.sort(xmlFiles, Collections.reverseOrder());
                    fileNameContainingOutcome = xmlFiles.get(0).getName();
                }
                Joiner commaJoiner = Joiner.on(",");
                metadataPrintStream.println(commaJoiner.join(
                        String.valueOf(outcome.getDate().getTime()),
                        fileNameContainingOutcome));
                metadataPrintStream.close();
            } catch (Exception e) {
                Console.getInstance().info("failed to write outcome metadata", e);
            }
        }

        // record the outcome's result to a tag (if applicable)
        if (tagName != null) {
            String tagOutputFileName = TAG_FILENAME;
            File tagDir = new File(tagResultsDir(), tagName);
            File tagOutcomeResultDir = new File(tagDir, outcome.getPath());
            File outcomeTagFile = new File(tagOutcomeResultDir, tagOutputFileName);
            if (outcomeTagFile.exists()) {
                new Rm().file(outcomeTagFile);
            }
            if (!tagOutcomeResultDir.mkdirs() && !tagOutcomeResultDir.exists()) {
                throw new RuntimeException("Failed to create directory " + tagOutcomeResultDir);
            }
            XmlReportPrinter tagSingleReportPrinter = xmlReportPrinterProvider.get();
            tagSingleReportPrinter.setOutput(tagOutcomeResultDir, true);
            tagSingleReportPrinter.generateReport(outcome, tagOutputFileName);
        }
    }
}
