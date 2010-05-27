package vogar;

import java.io.File;

/**
 * Interacts with a file system on behalf of a cache.
 */
public interface CacheFileInterface {

    public boolean existsInCache(String key);
    
    public void copyToCache(File source, String key);

    public void copyFromCache(String key, File destination);

    public void prepareDestination(File dir);
}
