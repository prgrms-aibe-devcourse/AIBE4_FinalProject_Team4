package kr.java.documind.domain.patchnote.util;

import java.util.List;
import kr.java.documind.domain.patchnote.config.TokenRagProperties;
import kr.java.documind.domain.patchnote.model.dto.TokenEstimation;
import kr.java.documind.domain.patchnote.model.entity.PendingItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TokenEstimator {

    private final TokenRagProperties properties;

    /** pending_item summary 기반 토큰 사전 추정 한국어 기준 보수적 추정: 약 3자 = 1토큰 */
    public TokenEstimation estimate(List<PendingItem> items) {
        int summaryTokens = items.stream().mapToInt(item -> item.getSummary().length() / 3).sum();

        int estimated = summaryTokens + properties.promptOverhead();

        return new TokenEstimation(
                estimated,
                properties.tokenLimit(),
                estimated > properties.tokenLimit(),
                items.size());
    }
}
