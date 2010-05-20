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
    private static final String TEST_ROOT = "/test/";

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

        try {
            Action action = fileToAction(file);
            actions.put(action.getName(), action);
        } catch (IllegalArgumentException e) {
            String actionName = file.getPath();
            Action action = new Action(actionName, null, null, null, file);
            actions.put(actionName, action);
            outcomes.put(actionName, new Outcome(actionName, Result.UNSUPPORTED, e));
        }
    }

    private boolean matches(File file) {
        return !file.getName().startsWith(".") && file.getName().endsWith(".java");
    }

    /**
     * Returns an action for the given .java file.
     */
    private Action fileToAction(File javaFile) {
        // We can get the unqualified class name from the path.
        // It's the last element minus the trailing ".java".
        String filename = javaFile.getName();
        String simpleName = filename.substring(0, filename.length() - ".java".length());

        // For the package, the only foolproof way is to look for the package
        // declaration inside the file.
        try {
            String content = Strings.readFile(javaFile);
            Pattern packagePattern = Pattern.compile(PACKAGE_PATTERN);
            Matcher packageMatcher = packagePattern.matcher(content);
            if (packageMatcher.find()) {
                String packageName = packageMatcher.group(1);
                String className = packageName + "." + simpleName;
                return new Action(className, className, null, getSourcePath(javaFile, className), javaFile);
            }

            if (!Pattern.compile(TYPE_DECLARATION_PATTERN).matcher(content).find()) {
                throw new IllegalArgumentException("Malformed .java file: " + javaFile);
            }

            String path = javaFile.getAbsolutePath();
            int indexOfTest = path.indexOf(TEST_ROOT);
            if (indexOfTest != -1) {
                path = path.substring(indexOfTest + TEST_ROOT.length(), path.length() - ".java".length());
            } else {
                path = path.substring(1);
            }
            String actionName = path.replace(File.separatorChar, '.');
            return new Action(actionName, simpleName, null, javaFile.getParentFile(), javaFile);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Returns the source path of {@code file}.
     */
    private File getSourcePath(File file, String className) {
        String path = file.getPath();
        String relativePath = className.replace('.', File.separatorChar) + ".java";
        if (!path.endsWith(relativePath)) {
            throw new IllegalArgumentException("Expected a file ending in " + relativePath + " but found " + path);
        }
        return new File(path.substring(0, path.length() - relativePath.length()));
    }
}
