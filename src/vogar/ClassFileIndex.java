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
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import vogar.commands.Command;
import vogar.commands.Mkdir;

/**
 * Indexes the locations of commonly used classes to assist in constructing correct Vogar commands.
 */
public class ClassFileIndex {
    // how many milliseconds before the cache expires and we reindex jars
    final private long cacheExpiry = 86400000; // = one day
    final private String DELIMITER = "\t";
    final private File classFileIndexFile =
            new File(System.getProperty("user.home"), ".vogar/classfileindex");
    // regular expressions representing things that make sense on the classpath
    final private List<String> JAR_PATTERN_STRINGS = Arrays.asList(
            "classes\\.jar"
    );
    // regular expressions representing failures probably due to things missing on the classpath
    final private List<String> FAILURE_PATTERN_STRINGS = Arrays.asList(
            ".*package (.*) does not exist.*",
            ".*import (.*);.*",
            ".*ClassNotFoundException: (\\S*).*"
    );
    private List<Pattern> jarPatterns;
    private List<Pattern> failurePatterns;
    private Map<String, Set<File>> classFileMap;

    public ClassFileIndex() {
        this.classFileMap = new HashMap<String, Set<File>>();
        this.failurePatterns = new ArrayList<Pattern>();
        this.jarPatterns = new ArrayList<Pattern>();
        for (String patternString : FAILURE_PATTERN_STRINGS) {
            // DOTALL flag allows proper handling of multiline strings
            this.failurePatterns.add(Pattern.compile(patternString, Pattern.DOTALL));
        }
        for (String patternString : JAR_PATTERN_STRINGS) {
            this.jarPatterns.add(Pattern.compile(patternString));
        }
    }

    public Set<File> suggestClasspaths(List<String> testOutput) {
        Set<File> suggestedClasspaths = new HashSet<File>();

        for (String line : testOutput) {
            for (Pattern pattern : failurePatterns) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.matches()) {
                    for (int i = 1; i <= matcher.groupCount(); i++) {
                        String missingPackageOrClass = matcher.group(i);
                        if (classFileMap.containsKey(missingPackageOrClass)) {
                            suggestedClasspaths.addAll(classFileMap.get(missingPackageOrClass));
                        }
                    }
                }
            }
        }
        return suggestedClasspaths;
    }

    /**
     * Search through the VOGAR_JAR_PATH to find .jars to index.
     *
     * If this has already been done, instead just use the cached version in .vogar
     */
    public void createIndex() {
        if (!classFileMap.isEmpty()) {
            return;
        }

        if (classFileIndexFile.exists()) {
            long lastModified = classFileIndexFile.lastModified();
            long curTime = new Date().getTime();
            boolean cacheExpired = lastModified < curTime - cacheExpiry;
            if (cacheExpired) {
                Console.getInstance().verbose("class file index expired, rebuilding");
            } else {
                readIndexCache();
                return;
            }
        }

        Console.getInstance().verbose("building class file index");

        // Create index
        String jarPath = System.getenv("VOGAR_JAR_PATH");
        if (jarPath == null) {
            Console.getInstance().warn("VOGAR_JAR_PATH environment variable is not set, "
                    + "can't create class file index");
            return;
        }
        String[] jarDirs = jarPath.split(":");
        for (String jarDir : jarDirs) {
            if (jarDir.equals("")) {
                // protect against trailing or leading colons
                continue;
            }
            File jarDirFile = new File(jarDir);

            if (!jarDirFile.exists()) {
                Console.getInstance().warn("directory \"" + jarDirFile + "\" on VOGAR_JAR_PATH"
                        + " doesn't exist");
                continue;
            }

            // traverse the jar directory, looking for files called ending in .jar
            Console.getInstance().verbose("looking in " + jarDirFile + " for .jar files");

            Set<File> jarFiles = new HashSet<File>();
            getJarFiles(jarFiles, jarDirFile);
            for (File jarFile : jarFiles) {
                List<String> rawResults = new Command("jar", "tvf", jarFile.getPath()).execute();
                for (String resultLine : rawResults) {
                    // the filename is the last entry in the line - convert it to a class/package
                    // name
                    String[] splitLine = resultLine.split(" ");
                    String classPath = splitLine[splitLine.length - 1]
                            // change paths into classes/packages
                            .replaceAll("/", ".")
                            // strip trailing period
                            .replaceFirst("\\.$", "")
                            // strip trailing .class extension
                            .replaceFirst("\\.class$", "");
                    if (classFileMap.containsKey(classPath)) {
                        classFileMap.get(classPath).add(jarFile);
                    } else {
                        Set<File> classPathJars = new HashSet<File>();
                        classPathJars.add(jarFile);
                        classFileMap.put(classPath, classPathJars);
                    }
                }
            }
        }

        // save for use on subsequent runs
        writeIndexCache();
    }

    private void getJarFiles(Set<File> jarFiles, File dir) {
        List<File> files = Arrays.asList(dir.listFiles());
        for (File file : files) {
            if (file.isDirectory()) {
                getJarFiles(jarFiles, file);
            } else {
                for (Pattern pattern : jarPatterns) {
                    Matcher matcher = pattern.matcher(file.getName());
                    if (matcher.matches()) {
                        jarFiles.add(file);
                    }
                }
            }
        }
    }

    private void writeIndexCache() {
        Console.getInstance().verbose("writing index cache");

        PrintStream indexCacheWriter;
        new Mkdir().mkdirs(classFileIndexFile.getParentFile());
        try {
            indexCacheWriter = new PrintStream(classFileIndexFile);
        } catch (FileNotFoundException e) {
            Console.getInstance().warn("cannot write to file " + classFileIndexFile + ": ");
            Console.getInstance().warn(e.getMessage());
            return;
        }

        for (Map.Entry<String, Set<File>> entry : classFileMap.entrySet()) {
            indexCacheWriter.println(entry.getKey() + DELIMITER
                    + Strings.join(entry.getValue(), DELIMITER));
        }
        indexCacheWriter.close();
    }

    private void readIndexCache() {
        Console.getInstance().verbose("reading class file index cache");

        Scanner scanner;
        try {
            scanner = new Scanner(classFileIndexFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        while (scanner.hasNextLine()) {
            // Each line is a mapping of a class, package or file to the .jar files that
            // contain its definition within VOGAR_JAR_PATH. Each component is separated by a
            // tab (\t) character.
            String line = scanner.nextLine();
            String[] parts = line.split(DELIMITER);
            if (parts.length < 2) {
                throw new RuntimeException("classfileindex contains invalid line: " + line);
            }
            String resource = parts[0];
            Set<File> jarFiles = new HashSet<File>();
            for (int i = 1; i < parts.length; i++) {
                jarFiles.add(new File(parts[i]));
            }
            classFileMap.put(resource, jarFiles);
        }
    }
}
