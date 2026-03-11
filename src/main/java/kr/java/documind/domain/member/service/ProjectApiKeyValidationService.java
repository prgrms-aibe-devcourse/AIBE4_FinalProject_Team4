package kr.java.documind.domain.member.service;

import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.auth.model.entity.ProjectApiKey;
import kr.java.documind.domain.auth.model.repository.ProjectApiKeyRepository;
import kr.java.documind.domain.member.model.enums.ApiKeyStatus;
import kr.java.documind.global.util.HmacApiKeyUtil;
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
        if (rawApiKey == null || rawApiKey.isBlank()) {
            log.warn("API Key가 제공되지 않았습니다.");
            return null;
        }

        String requestHashedKey = HmacApiKeyUtil.computeHmac(rawApiKey, hmacSecret);
        Optional<ProjectApiKey> apiKeyOptional =
                projectApiKeyRepository.findByApiKeyHash(requestHashedKey);

        if (apiKeyOptional.isEmpty()) {
            log.warn("유효하지 않은 API Key 접근 시도: {}", HmacApiKeyUtil.maskApiKey(rawApiKey));
            return null;
        }

        ProjectApiKey apiKey = apiKeyOptional.get();

        if (apiKey.getApiKeyStatus() != ApiKeyStatus.ACTIVE) {
            log.warn(
                    "비활성 API Key입니다 (상태: {}): {}",
                    apiKey.getApiKeyStatus(),
                    HmacApiKeyUtil.maskApiKey(rawApiKey));
            return null;
        }

        log.info("API Key 검증 성공! Project PublicId: {}", apiKey.getProject().getPublicId());
        return apiKey.getProject().getId();
    }
}
