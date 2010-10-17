package vogar;

import java.io.File;
import java.io.FileFilter;
import javax.inject.Inject;

/**
 * Selects files to be kept from a test run.
 */
public final class RetrievedFilesFilter implements FileFilter {
    
    @Inject RetrievedFilesFilter() {}

    @Override public boolean accept(File file) {
        // save Caliper results; discard java.util.prefs XML
        return !file.getName().equals("prefs.xml")
                && (file.getName().endsWith(".xml") || file.getName().endsWith(".json"));
    }
}
