package vogar;

import java.io.File;
import java.io.FileFilter;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * Selects files to be kept from a test run.
 */
public final class RetrievedFilesFilter implements FileFilter {

    @Inject RetrievedFilesFilter() {}

    @Inject @Named("profile") boolean profile;
    @Inject @Named("profileFile") File profileFile;

    @Override public boolean accept(File file) {
        // save Caliper and profiler results; discard java.util.prefs XML
        return !file.getName().equals("prefs.xml")
                && (file.getName().endsWith(".xml")
                        || file.getName().endsWith(".json")
                        || (profile && file.getName().equals(profileFile.getName())));
    }
}
