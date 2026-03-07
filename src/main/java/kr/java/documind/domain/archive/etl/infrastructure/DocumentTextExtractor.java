package kr.java.documind.domain.archive.etl.infrastructure;

import java.nio.file.Path;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

@Component
public class DocumentTextExtractor {

    public List<Document> read(Path tempFilePath) {
        TikaDocumentReader reader = new TikaDocumentReader(new FileSystemResource(tempFilePath));
        return reader.get();
    }
}
