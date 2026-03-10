package kr.java.documind.domain.member.service;

import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.auth.model.entity.ProjectApiKey;
import kr.java.documind.domain.auth.model.repository.ProjectApiKeyRepository;
import kr.java.documind.global.util.HmacApiKeyUtil;
import kr.java.documind.domain.member.model.enums.ApiKeyStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectApiKeyValidationService {

    private final ProjectApiKeyRepository projectApiKeyRepository;

    @Value("${api-key.hmac-secret}")
    private String hmacSecret;

    /**
     * API Key를 검증하고, 유효한 경우 연결된 ProjectId를 반환합니다.
     *
     * @param rawApiKey 사용자가 제공한 API Key (plain text)
     * @return 유효한 경우 ProjectId, 그렇지 않으면 null
     */
    @Transactional(readOnly = true)
    public UUID getProjectIdByApiKey(String rawApiKey) {
        if (rawApiKey == null) {
            log.warn("API Key가 제공되지 않았습니다.");
            return null;
        }

        String prefix = HmacApiKeyUtil.extractPrefix(rawApiKey);
        Optional<ProjectApiKey> apiKeyOptional = projectApiKeyRepository.findByKeyPrefix(prefix);

        if (apiKeyOptional.isEmpty()) {
            log.warn("제공된 API Key prefix와 일치하는 키가 없습니다: {}", HmacApiKeyUtil.maskApiKey(rawApiKey));
            return null;
        }

        ProjectApiKey apiKey = apiKeyOptional.get();

        if (apiKey.getApiKeyStatus() != ApiKeyStatus.ACTIVE) {
            log.warn("비활성 API Key입니다 (상태: {}): {}", apiKey.getApiKeyStatus(), HmacApiKeyUtil.maskApiKey(rawApiKey));
            return null;
        }

        String requestHashedKey = HmacApiKeyUtil.computeHmac(rawApiKey, hmacSecret);

        // DB에 저장된 해시값과 비교할 때 타이밍 공격에 안전한 방식으로 비교
        if (HmacApiKeyUtil.constantTimeEquals(apiKey.getApiKeyHash(), requestHashedKey)) {
            log.info("API Key 검증 성공! ProjectId: {}", apiKey.getProject().getPublicId());
            return apiKey.getProject().getId();
        } else {
            log.warn("API Key 해시 값이 일치하지 않습니다: {}", HmacApiKeyUtil.maskApiKey(rawApiKey));
            return null;
        }
    }
}
