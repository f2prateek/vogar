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

import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Config {
    public String[] readFile(File configFile) {
        if (!configFile.exists()) {
            return new String[0];
        }

        String[] configArgs;
        List<String> configFileLines;
        try {
            configFileLines = Strings.readFileLines(configFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        ArrayList<String> argsList = Lists.newArrayList();
        for (String rawLine : configFileLines) {
            // strip leading and trailing spaces
            String line = rawLine.replaceFirst("\\s*$", "").replaceFirst("^\\s*", "");

            // allow comments and blank lines
            if (line.startsWith("#") || line.isEmpty()) {
                continue;
            }
            int space = line.indexOf(' ');
            if (space == -1) {
                argsList.add(line);
            } else {
                argsList.add(line.substring(0, space));
                argsList.add(line.substring(space+1));
            }
        }
        configArgs = new String[argsList.size()];

        argsList.toArray(configArgs);
        return configArgs;
    }
}
