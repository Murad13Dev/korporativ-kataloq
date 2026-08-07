package az.catalog.modules.media;

import az.catalog.common.config.AppProperties;
import az.catalog.common.exception.ApiException;
import az.catalog.common.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
public class LocalFileStorage implements FileStorage {

    private final AppProperties properties;
    private Path root;

    @PostConstruct
    void init() throws IOException {
        root = Paths.get(properties.getStorage().getLocal().getRoot()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("Local media storage root: {}", root);
    }

    @Override
    public String store(String folder, String storedName, InputStream content, long size) {
        Path directory = resolveInsideRoot(folder);
        try {
            Files.createDirectories(directory);
            Path target = directory.resolve(storedName);
            // Reject a name that would escape the folder even after normalisation.
            if (!target.normalize().startsWith(root)) {
                throw new ApiException(ErrorCode.FILE_STORAGE_ERROR, "Yanlış fayl yolu");
            }
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
            return folder + "/" + storedName;
        } catch (IOException ex) {
            log.error("Failed to store {} in {}", storedName, directory, ex);
            throw new ApiException(ErrorCode.FILE_STORAGE_ERROR);
        }
    }

    @Override
    public void delete(String storagePath) {
        try {
            Path target = root.resolve(storagePath).normalize();
            if (!target.startsWith(root)) {
                log.warn("Refusing to delete path outside storage root: {}", storagePath);
                return;
            }
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            // A file that cannot be removed is a housekeeping problem, not a request failure.
            log.error("Failed to delete {}", storagePath, ex);
        }
    }

    @Override
    public String publicUrl(String storagePath) {
        String base = properties.getStorage().getLocal().getPublicBaseUrl();
        return base.endsWith("/") ? base + storagePath : base + "/" + storagePath;
    }

    /** Guards against {@code ../} traversal in the folder component. */
    private Path resolveInsideRoot(String folder) {
        Path resolved = root.resolve(folder).normalize();
        if (!resolved.startsWith(root)) {
            throw new ApiException(ErrorCode.FILE_STORAGE_ERROR, "Yanlış qovluq adı");
        }
        return resolved;
    }
}
