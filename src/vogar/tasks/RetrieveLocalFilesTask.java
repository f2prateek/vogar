/*
 * Copyright (C) 2011 The Android Open Source Project
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

package vogar.tasks;

import java.io.File;
import java.io.FileFilter;
import vogar.Result;
import vogar.Run;
import vogar.commands.Command;

public final class RetrieveLocalFilesTask extends Task {
    private final Run run;
    private final File destination;
    private final File source;
    private final FileFilter filenameFilter;

    public RetrieveLocalFilesTask(Run run, File destination, File source, FileFilter filenameFilter) {
        super("retrieve files from " + source);
        this.run = run;
        this.destination = destination;
        this.source = source;
        this.filenameFilter = filenameFilter;
    }

    @Override protected Result execute() throws Exception {
        retrieveFiles(destination, source);
        return Result.SUCCESS;
    }

    /**
     * Recursively scans {@code dir} for files to grab.
     */
    private void retrieveFiles(File destination, File source) {
        for (File file : source.listFiles(filenameFilter)) {
            run.log.info("Moving " + file + " to " + destination);
            run.mkdir.mkdirs(destination);
            new Command(run.log, "cp", file.getPath(), destination.getPath()).execute();
        }

        FileFilter directoryFilter = new FileFilter() {
            public boolean accept(File file) {
                return file.isDirectory();
            }
        };

        for (File subDir : source.listFiles(directoryFilter)) {
            retrieveFiles(new File(destination, subDir.getName()), subDir);
        }
    }
}
