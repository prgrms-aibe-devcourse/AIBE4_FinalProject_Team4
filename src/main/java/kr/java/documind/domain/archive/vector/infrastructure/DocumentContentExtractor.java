package kr.java.documind.domain.archive.vector.infrastructure;

import java.nio.file.Path;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

@Component
public class DocumentContentExtractor {

    public List<Document> extract(Path tempFilePath) {
        if (isPdf(tempFilePath)) {
            return new PdfDocumentReader(tempFilePath).get();
        }
        return new TikaDocumentReader(new FileSystemResource(tempFilePath)).get();
    }

    private boolean isPdf(Path path) {
        return path.toString().toLowerCase().endsWith(".pdf");
    }
}
