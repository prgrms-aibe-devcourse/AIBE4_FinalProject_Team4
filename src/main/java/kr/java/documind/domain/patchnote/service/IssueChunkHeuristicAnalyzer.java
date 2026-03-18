package kr.java.documind.domain.patchnote.service;

import java.util.List;
import java.util.regex.Pattern;
import kr.java.documind.domain.patchnote.model.dto.IssueChunkAnalysis;
import kr.java.documind.domain.patchnote.model.dto.IssueChunkingSource;
import kr.java.documind.domain.patchnote.model.dto.IssueCommentChunkSource;
import org.springframework.stereotype.Component;

@Component
public class IssueChunkHeuristicAnalyzer {

    private static final int MIN_COMMENT_LENGTH = 15;

    private static final List<Pattern> NUMERIC_CHANGE_PATTERNS =
            List.of(
                    Pattern.compile(
                            "\\b\\d+([.,]\\d+)?\\s*(%|배|x|×|초|분|회|개|건)?\\s*(증가|감소|변경|수정|조정|상향|하향|향상|개선|저하)"),
                    Pattern.compile(
                            "\\b\\d+([.,]\\d+)?\\s*(%|배|x|×|초|분|회|개|건)?\\s*(->|→)\\s*\\d+([.,]\\d+)?\\s*(%|배|x|×|초|분|회|개|건)?"),
                    Pattern.compile(
                            "\\b\\d+([.,]\\d+)?\\s*(%|배|x|×|초|분|회|개|건)?\\s*에서\\s*\\d+([.,]\\d+)?\\s*(%|배|x|×|초|분|회|개|건)?\\s*로"),
                    Pattern.compile("[+-]\\d+([.,]\\d+)?\\s*%"));

    private static final List<String> PLAYER_IMPACT_KEYWORDS =
            List.of(
                    "접속 불가", "진행 불가", "획득", "지급", "보상", "데미지", "피해량", "쿨타임", "경험치", "드랍", "드롭",
                    "매칭 실패", "구매 실패", "결제 실패", "크래시", "충돌", "로그인", "전투", "스킬", "아이템", "퀘스트", "던전",
                    "상점");

    public IssueChunkAnalysis analyze(IssueChunkingSource source) {
        boolean hasResolution = hasText(source.resolutionNote());
        String analysisText = collectAnalysisText(source);

        return new IssueChunkAnalysis(
                hasResolution,
                detectNumericChange(analysisText),
                detectAffectsPlayer(analysisText));
    }

    public boolean hasMeaningfulComment(IssueCommentChunkSource comment) {
        return comment != null
                && hasText(comment.content())
                && comment.content().trim().length() >= MIN_COMMENT_LENGTH;
    }

    private boolean detectNumericChange(String text) {
        for (Pattern pattern : NUMERIC_CHANGE_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean detectAffectsPlayer(String text) {
        return PLAYER_IMPACT_KEYWORDS.stream().anyMatch(text::contains);
    }

    private String collectAnalysisText(IssueChunkingSource source) {
        StringBuilder sb = new StringBuilder();

        appendIfHasText(sb, source.title());
        appendIfHasText(sb, source.description());
        appendIfHasText(sb, source.resolutionNote());

        for (IssueCommentChunkSource comment : source.comments()) {
            appendIfHasText(sb, comment.content());
        }

        return sb.toString();
    }

    private void appendIfHasText(StringBuilder sb, String value) {
        if (hasText(value)) {
            sb.append(value.trim()).append(' ');
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
