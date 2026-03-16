package kr.java.documind.domain.archive.document.infrastructure;

import kr.java.documind.global.storage.FileStore;
import kr.java.documind.global.storage.FileStoreResult;
import kr.java.documind.global.util.FileUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class DocumentFileStorage {

    private final FileStore fileStore;

    public StoredDocumentFile store(MultipartFile file) {
        FileStoreResult storeResult = fileStore.save(file);
        fileStore.deleteOnRollback(storeResult.storedKey());

        return new StoredDocumentFile(
                StringUtils.stripFilenameExtension(file.getOriginalFilename()),
                storeResult.extension(),
                file.getSize(),
                storeResult.storedKey());
    }

    public Resource load(String storedKey) {
        return fileStore.load(storedKey);
    }

    public StoredDocumentFile replace(String oldStoredKey, MultipartFile file) {
        StoredDocumentFile storedFile = store(file);
        fileStore.deleteOnCommit(oldStoredKey);
        return storedFile;
    }

    public void deleteOnCommit(String storedKey) {
        fileStore.deleteOnCommit(storedKey);
    }

    public String computeHash(MultipartFile file) {
        return FileUtil.computeSha256(file);
    }

    public record StoredDocumentFile(
            String displayName, String extension, long size, String storedKey) {}
}
