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
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import vogar.commands.Mkdir;

/**
 * A representation of a previous invocation of Vogar.
 */
public class Tag {
    final private String ARGS_DIR = "args";

    final private File argsDir;
    final private String tag;
    final private boolean tagOverwrite;

    public Tag(File tagDir, String tag, boolean tagOverwrite) {
        this.tag = tag;
        this.argsDir = new File(tagDir, ARGS_DIR);
        this.tagOverwrite = tagOverwrite;
    }

    public String[] getArgs() throws FileNotFoundException {
        List<String> args = new ArrayList<String>();
        Scanner scanner = new Scanner(new File(argsDir, tag));
        while (scanner.hasNextLine()) {
            args.add(scanner.nextLine());
        }
        scanner.close();
        return args.toArray(new String[args.size()]);
    }

    public void saveArgs(String[] args) throws FileNotFoundException {
        File argsFile = new File(argsDir, tag);
        if (!tagOverwrite && argsFile.exists()) {
            throw new FileNotFoundException("Tag \"" + tag + "\" already exists");
        }
        new Mkdir().mkdirs(argsFile.getParentFile());
        PrintStream stream;
        try {
            stream = new PrintStream(argsFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--tag")) {
                // Don't write out the --tag option or its argument
                i++;
            } else {
                stream.println(args[i]);
            }
        }

        stream.close();
    }
}
