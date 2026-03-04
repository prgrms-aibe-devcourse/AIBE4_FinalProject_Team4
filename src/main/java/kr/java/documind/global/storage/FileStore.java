package kr.java.documind.global.storage;

import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStore {

    String save(MultipartFile file) throws IOException;

    Resource load(String storedKey);

    void delete(String storedKey);

    String getAccessUrl(String storedKey);

    default void registerRollback(String storedKey) {}
}
