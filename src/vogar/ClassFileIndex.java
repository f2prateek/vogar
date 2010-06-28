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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import vogar.commands.Mkdir;

/**
 * Indexes the locations of commonly used classes to assist in constructing correct Vogar commands.
 */
public final class ClassFileIndex {
    // how many milliseconds before the cache expires and we reindex jars
    private final long cacheExpiry = 86400000; // = one day
    private final  String DELIMITER = "\t";
    private final File classFileIndexFile =
            new File(System.getProperty("user.home"), ".vogar/classfileindex");
    // regular expressions representing things that make sense on the classpath
    private final List<String> JAR_PATTERN_STRINGS = Arrays.asList(
            "classes\\.jar"
    );
    // regular expressions representing failures probably due to things missing on the classpath
    private final List<String> FAILURE_PATTERN_STRINGS = Arrays.asList(
            ".*package (.*) does not exist.*",
            ".*import (.*);.*",
            ".*ClassNotFoundException: (\\S*).*",
            ".*NoClassDefFoundError: Could not initialize class (\\S*).*"
    );
    private final List<Pattern> JAR_PATTERNS = new ArrayList<Pattern>();
    {
        for (String patternString : JAR_PATTERN_STRINGS) {
            this.JAR_PATTERNS.add(Pattern.compile(patternString));
        }
    }
    private final List<Pattern> FAILURE_PATTERNS = new ArrayList<Pattern>();
    {
        for (String patternString : FAILURE_PATTERN_STRINGS) {
            // DOTALL flag allows proper handling of multiline strings
            this.FAILURE_PATTERNS.add(Pattern.compile(patternString, Pattern.DOTALL));
        }
    }
    private final Map<String, Set<File>> classFileMap = new HashMap<String, Set<File>>();
    private final List<File> jarSearchDirs;

    public ClassFileIndex(List<File> jarSearchDirs) {
        this.jarSearchDirs = jarSearchDirs;
    }

    public Set<File> suggestClasspaths(String testOutput) {
        Set<File> suggestedClasspaths = new HashSet<File>();

        for (Pattern pattern : FAILURE_PATTERNS) {
            Matcher matcher = pattern.matcher(testOutput);
            if (!matcher.matches()) {
                continue;
            }

            for (int i = 1; i <= matcher.groupCount(); i++) {
                String missingPackageOrClass = matcher.group(i);
                Set<File> containingJars = classFileMap.get(missingPackageOrClass);
                if (containingJars != null) {
                    suggestedClasspaths.addAll(containingJars);
                }
            }
        }

        return suggestedClasspaths;
    }

    /**
     * Search through the jar search directories to find .jars to index.
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
        for (File jarSearchDir : jarSearchDirs) {
            if (!jarSearchDir.exists()) {
                Console.getInstance().warn("directory \"" + jarSearchDir + "\" in jar paths"
                        + " doesn't exist");
                continue;
            }

            // traverse the jar directory, looking for files called ending in .jar
            Console.getInstance().verbose("looking in " + jarSearchDir + " for .jar files");

            Set<File> jarFiles = new HashSet<File>();
            getJarFiles(jarFiles, jarSearchDir);
            for (File file : jarFiles) {
                indexJarFile(file);
            }
        }

        // save for use on subsequent runs
        writeIndexCache();
    }

    private void indexJarFile(File file) {
        try {
            JarFile jarFile = new JarFile(file);
            for (Enumeration<JarEntry> e = jarFile.entries(); e.hasMoreElements(); ) {
                JarEntry jarEntry = e.nextElement();

                // change paths into classes/packages, strip trailing period, and strip
                // trailing .class extension
                String classPath = jarEntry.getName()
                        .replaceAll("/", ".")
                        .replaceFirst("\\.$", "")
                        .replaceFirst("\\.class$", "");
                if (classFileMap.containsKey(classPath)) {
                    classFileMap.get(classPath).add(file);
                } else {
                    Set<File> classPathJars = new HashSet<File>();
                    classPathJars.add(file);
                    classFileMap.put(classPath, classPathJars);
                }
            }
        } catch (IOException e) {
            Console.getInstance().warn("failed to read " + file + ": " + e.getMessage());
        }
    }

    private void getJarFiles(Set<File> jarFiles, File dir) {
        List<File> files = Arrays.asList(dir.listFiles());
        for (File file : files) {
            if (file.isDirectory()) {
                getJarFiles(jarFiles, file);
                continue;
            }

            for (Pattern pattern : JAR_PATTERNS) {
                Matcher matcher = pattern.matcher(file.getName());
                if (matcher.matches()) {
                    jarFiles.add(file);
                }
            }
        }
    }

    private void writeIndexCache() {
        Console.getInstance().verbose("writing index cache");

        BufferedWriter indexCacheWriter;
        new Mkdir().mkdirs(classFileIndexFile.getParentFile());
        try {
            indexCacheWriter = new BufferedWriter(new FileWriter(classFileIndexFile));
            for (Map.Entry<String, Set<File>> entry : classFileMap.entrySet()) {
                indexCacheWriter.write(entry.getKey() + DELIMITER
                        + Strings.join(entry.getValue(), DELIMITER));
                indexCacheWriter.newLine();
            }
            indexCacheWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void readIndexCache() {
        Console.getInstance().verbose("reading class file index cache");

        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(classFileIndexFile));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Each line is a mapping of a class, package or file to the .jar files that
                // contain its definition within VOGAR_JAR_PATH. Each component is separated
                // by a delimiter.
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
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
