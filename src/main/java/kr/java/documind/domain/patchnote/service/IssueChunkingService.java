package kr.java.documind.domain.patchnote.service;

import java.util.ArrayList;
import java.util.List;

import kr.java.documind.domain.patchnote.model.dto.IssueChunkAnalysis;
import kr.java.documind.domain.patchnote.model.dto.IssueChunkDraft;
import kr.java.documind.domain.patchnote.model.dto.IssueChunkingSource;
import kr.java.documind.domain.patchnote.model.dto.IssueCommentChunkSource;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

@Service
public class IssueChunkingService {

    private static final int RESOLUTION_MERGE_THRESHOLD = 500;

    private final IssueChunkHeuristicAnalyzer heuristicAnalyzer;
    private final IssueChunkDocumentBuilder documentBuilder;

    public IssueChunkingService(
        IssueChunkHeuristicAnalyzer heuristicAnalyzer,
        IssueChunkDocumentBuilder documentBuilder) {
        this.heuristicAnalyzer = heuristicAnalyzer;
        this.documentBuilder = documentBuilder;
    }

    public List<Document> buildChunks(IssueChunkingSource source) {
        IssueChunkAnalysis analysis = heuristicAnalyzer.analyze(source);

        List<IssueChunkDraft> drafts = new ArrayList<>();
        drafts.addAll(buildPrimaryDrafts(source, analysis));
        appendCommentDrafts(source, drafts);

        return toDocuments(source, drafts, analysis);
    }

    private List<IssueChunkDraft> buildPrimaryDrafts(
        IssueChunkingSource source,
        IssueChunkAnalysis analysis) {

        List<IssueChunkDraft> drafts = new ArrayList<>();
        drafts.add(IssueChunkDraft.background());

        if (analysis.hasResolution()) {
            drafts.add(IssueChunkDraft.resolution());

            if (source.resolutionNote().trim().length() <= RESOLUTION_MERGE_THRESHOLD) {
                drafts.add(IssueChunkDraft.merged());
            }
        }

        return drafts;
    }

    private void appendCommentDrafts(
        IssueChunkingSource source,
        List<IssueChunkDraft> drafts) {

        int commentIndex = 0;
        for (IssueCommentChunkSource comment : source.comments()) {
            if (!heuristicAnalyzer.hasMeaningfulComment(comment)) {
                continue;
            }

            drafts.add(IssueChunkDraft.comment(comment.commentId(), commentIndex++));
        }
    }

    private List<Document> toDocuments(
        IssueChunkingSource source,
        List<IssueChunkDraft> drafts,
        IssueChunkAnalysis analysis) {

        List<Document> documents = new ArrayList<>();
        int totalChunks = drafts.size();

        for (int i = 0; i < drafts.size(); i++) {
            documents.add(
                documentBuilder.build(
                    source,
                    drafts.get(i),
                    i,
                    totalChunks,
                    analysis));
        }

        return documents;
    }
}
