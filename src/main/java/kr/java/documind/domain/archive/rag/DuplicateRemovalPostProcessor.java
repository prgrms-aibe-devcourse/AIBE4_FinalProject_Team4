package kr.java.documind.domain.archive.rag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;

@Slf4j
public class DuplicateRemovalPostProcessor implements DocumentPostProcessor {

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        Set<String> seenIds = new LinkedHashSet<>();
        List<Document> idDeduplicated = new ArrayList<>();

        for (Document doc : documents) {
            if (seenIds.add(doc.getId())) {
                idDeduplicated.add(doc);
            }
        }

        List<Document> deduplicated = removeOverlappingChunks(idDeduplicated);

        int removed = documents.size() - deduplicated.size();
        if (removed > 0) {
            log.info("[PostProcessor] 중복 청크 {} 건 제거 ({} → {})",
                    removed, documents.size(), deduplicated.size());
        }

        return deduplicated;
    }

    private List<Document> removeOverlappingChunks(List<Document> documents) {
        boolean[] removed = new boolean[documents.size()];

        for (int i = 0; i < documents.size(); i++) {
            if (removed[i]) continue;
            for (int j = i + 1; j < documents.size(); j++) {
                if (removed[j]) continue;
                if (!sameSource(documents.get(i), documents.get(j))) continue;

                String textA = documents.get(i).getText();
                String textB = documents.get(j).getText();
                if (textA == null || textB == null) continue;

                if (textA.contains(textB)) {
                    removed[j] = true;
                } else if (textB.contains(textA)) {
                    removed[i] = true;
                    break;
                }
            }
        }

        List<Document> result = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            if (!removed[i]) {
                result.add(documents.get(i));
            }
        }
        return result;
    }

    private boolean sameSource(Document a, Document b) {
        Object sourceA = a.getMetadata().get("source_id");
        Object sourceB = b.getMetadata().get("source_id");
        if (sourceA == null || sourceB == null) return false;
        return String.valueOf(sourceA).equals(String.valueOf(sourceB));
    }
}
