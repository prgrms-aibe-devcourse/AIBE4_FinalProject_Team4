package kr.java.documind.global.storage;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.Resource;

public interface FileStore {

    String save(InputStream inputStream, String originalFilename) throws IOException;

    Resource load(String storedKey);

    void delete(String storedKey);

    String detectContentType(String storedKey);
}
