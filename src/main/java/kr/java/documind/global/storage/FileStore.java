package kr.java.documind.global.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStore {

    String save(MultipartFile file);

    Resource load(String storedKey);

    String getAccessUrl(String storedKey);

    void deleteOnCommit(String storedKey);

    void deleteOnRollback(String storedKey);
}
