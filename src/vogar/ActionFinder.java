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
import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles finding actions to perform, given files and classes.
 */
public final class ActionFinder {
    private static final String PACKAGE_PATTERN = "(?m)^\\s*package\\s+(\\S+)\\s*;";

    private static final String TYPE_DECLARATION_PATTERN
            = "(?m)\\b(?:public|private\\s+)?(?:final\\s+)?(?:interface|class|enum)\\b";

    private final Map<String, Action> actions;
    private final Map<String, Outcome> outcomes;

    public ActionFinder(Map<String, Action> actions, Map<String, Outcome> outcomes) {
        this.actions = actions;
        this.outcomes = outcomes;
    }

    public void findActions(File file) {
        findActionsRecursive(file, 0);
    }

    private void findActionsRecursive(File file, int depth) {
        if (file.isDirectory()) {
            int size = actions.size();
            for (File child : file.listFiles()) {
                findActionsRecursive(child, depth + 1);
            }
            if (depth < 3) {
                Console.getInstance().verbose("Found " + (actions.size() - size) + " actions in " + file);
            }
            return;
        }

        // Don't try to treat this file as a class unless it resembles a .java file
        if (!matches(file)) {
            return;
        }

        String className = fileToClass(file);
        String fullPath = file.getPath();
        String shortPath = className.replace('.', File.separatorChar) + ".java";
        File sourcePath = null;
        if (fullPath.endsWith(shortPath)) {
            sourcePath = new File(fullPath.substring(0, fullPath.length() - shortPath.length()));
        } else {
            String fail = "Class " + className + " expected to be in file ending in " + shortPath
                    + " but instead found in " + fullPath;
            outcomes.put(className, new Outcome(className, Result.UNSUPPORTED, fail));
        }
        actions.put(className, new Action(className, className, null, sourcePath, file));
    }

    private boolean matches(File file) {
        return !file.getName().startsWith(".") && file.getName().endsWith(".java");
    }

    private String fileToClass(File javaFile) {
        // We can get the unqualified class name from the path.
        // It's the last element minus the trailing ".java".
        String filename = javaFile.getName();
        String className = filename.substring(0, filename.length() - 5);

        // For the package, the only foolproof way is to look for the package
        // declaration inside the file.
        try {
            String content = Strings.readFile(javaFile);
            Pattern packagePattern = Pattern.compile(PACKAGE_PATTERN);
            Matcher packageMatcher = packagePattern.matcher(content);
            if (!packageMatcher.find()) {
                // if it doesn't have a package, make sure there's at least a
                // type declaration otherwise we're probably reading the wrong
                // kind of file.
                if (Pattern.compile(TYPE_DECLARATION_PATTERN).matcher(content).find()) {
                    return className;
                }
                throw new IllegalArgumentException("No package declaration found in " + javaFile);
            }
            String packageName = packageMatcher.group(1);
            return packageName + "." + className;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
