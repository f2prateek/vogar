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
import java.io.FileFilter;
import javax.inject.Inject;
import vogar.commands.Command;
import vogar.commands.Mkdir;

final class EnvironmentHost extends Environment {

    @Inject RetrievedFilesFilter retrievedFiles;

    @Inject EnvironmentHost() {}

    @Override public void prepare() {}

    @Override public void prepareUserDir(Action action) {
        File actionUserDir = actionUserDir(action);

        // if the user dir exists, cp would copy the files to the wrong place
        if (actionUserDir.exists()) {
            throw new IllegalStateException();
        }

        File resourcesDirectory = action.getResourcesDirectory();
        if (resourcesDirectory != null) {
            new Mkdir().mkdirs(actionUserDir.getParentFile());
            new Command("cp", "-r", resourcesDirectory.toString(),
                    actionUserDir.toString()).execute();
        } else {
            new Mkdir().mkdirs(actionUserDir);
        }

        action.setUserDir(actionUserDir);
    }

    /**
     * Recursively scans {@code dir} for xml files to grab.
     */
    private void retrieveFiles(File destination, File source, FileFilter filenameFilter) {
        for (File file : source.listFiles(filenameFilter)) {
            Console.getInstance().info("Moving " + file + " to " + destination);
            new Mkdir().mkdirs(destination);
            new Command("cp", file.getPath(), destination.getPath()).execute();
        }

        FileFilter directoryFilter = new FileFilter() {
            public boolean accept(File file) {
                return file.isDirectory();
            }
        };

        for (File subDir : source.listFiles(directoryFilter)) {
            retrieveFiles(new File(destination, subDir.getName()), subDir, filenameFilter);
        }
    }

    @Override public void cleanup(Action action) {
        retrieveFiles(new File("./vogar-results"), action.getUserDir(), retrievedFiles);
        super.cleanup(action);
    }
}
