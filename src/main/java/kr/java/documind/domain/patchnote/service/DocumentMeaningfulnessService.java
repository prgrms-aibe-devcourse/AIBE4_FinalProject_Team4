package kr.java.documind.domain.patchnote.service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DocumentMeaningfulnessService {

    static final double TRIVIAL_DIFF_RATIO_THRESHOLD = 0.02;

    private static final Pattern NUMERIC_CHANGE_PATTERN =
            Pattern.compile(
                    "\\d+(?:[.,]\\d+)?\\s*(?:배|초|회|개|레벨)?\\s*"
                            + "(?:%|퍼센트|증가|감소|변경|조정|강화|너프|상승|하락|향상|저하)");

    private static final List<String> GAME_KEYWORDS =
            List.of(
                    "공격력", "방어력", "체력", "마나", "데미지", "보상", "확률", "레벨", "스킬", "쿨타임", "드랍", "상점",
                    "로그인", "매칭", "캐릭터", "아이템", "퀘스트", "던전", "몬스터", "경험치", "골드", "밸런스", "버프", "너프",
                    "패치");

    public boolean isMeaningful(boolean isNewDocument, String currentText, String previousText) {
        if (isNewDocument || previousText == null || previousText.isBlank()) {
            log.debug("[Meaningfulness] 신규 문서 또는 이전 텍스트 없음 → meaningful");
            return true;
        }

        double diffRatio = computeDiffRatio(currentText, previousText);
        if (diffRatio >= TRIVIAL_DIFF_RATIO_THRESHOLD) {
            log.debug(
                    "[Meaningfulness] diff ratio {}% ≥ 2% → meaningful",
                    String.format("%.1f", diffRatio * 100));
            return true;
        }

        if (hasNumericChange(currentText, previousText)) {
            log.debug("[Meaningfulness] 수치 변경 감지 → meaningful");
            return true;
        }

        if (hasCoreKeywordChange(currentText, previousText)) {
            log.debug("[Meaningfulness] 핵심 키워드 변경 감지 → meaningful");
            return true;
        }

        log.info(
                "[Meaningfulness] 변경 경미 → trivial. diffRatio={}%",
                String.format("%.2f", diffRatio * 100));
        return false;
    }

    double computeDiffRatio(String currentText, String previousText) {
        if (currentText == null || currentText.isBlank()) return 0.0;

        Set<String> currentWords = tokenize(currentText);
        Set<String> previousWords = tokenize(previousText);

        long added = currentWords.stream().filter(w -> !previousWords.contains(w)).count();
        long removed = previousWords.stream().filter(w -> !currentWords.contains(w)).count();

        int maxSize = Math.max(currentWords.size(), previousWords.size());
        if (maxSize == 0) return 0.0;

        return (double) (added + removed) / maxSize;
    }

    boolean hasNumericChange(String currentText, String previousText) {
        Set<String> currentPatterns = extractNumericPatterns(currentText);
        Set<String> previousPatterns = extractNumericPatterns(previousText);
        return !currentPatterns.equals(previousPatterns);
    }

    boolean hasCoreKeywordChange(String currentText, String previousText) {
        Set<String> currentKeywords = extractGameKeywords(currentText);
        Set<String> previousKeywords = extractGameKeywords(previousText);
        return !currentKeywords.equals(previousKeywords);
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Set.of();
        return Arrays.stream(text.split("[\\s.,;:!?()\r\n\\[\\]{}\"']+"))
                .filter(w -> w.length() > 1)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    private Set<String> extractNumericPatterns(String text) {
        if (text == null || text.isBlank()) return Set.of();
        Matcher matcher = NUMERIC_CHANGE_PATTERN.matcher(text);
        Set<String> matches = new HashSet<>();
        while (matcher.find()) {
            matches.add(matcher.group().trim().toLowerCase());
        }
        return matches;
    }

    private Set<String> extractGameKeywords(String text) {
        if (text == null || text.isBlank()) return Set.of();
        String lower = text.toLowerCase();
        return GAME_KEYWORDS.stream().filter(lower::contains).collect(Collectors.toSet());
    }
}
