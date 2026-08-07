package az.catalog.modules.media;

import java.io.InputStream;

/**
 * Storage backend for uploaded files. Only {@link LocalFileStorage} ships in v1;
 * an S3 implementation drops in behind the same three methods.
 */
public interface FileStorage {

    /**
     * @param folder     logical grouping ("products", "pages", …); already sanitised
     * @param storedName randomised file name including its extension
     * @return relative path recorded on the media row
     */
    String store(String folder, String storedName, InputStream content, long size);

    void delete(String storagePath);

    String publicUrl(String storagePath);
}
