package kr.java.documind.domain.auth.service;

import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.auth.model.entity.ProjectApiKey;
import kr.java.documind.domain.auth.model.enums.ApiKeyType;
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

    @Value("${app.api-key.hmac-secret}")
    private String hmacSecret;

    /**
     * API Key를 검증하고, 유효한 경우 연결된 ProjectId를 반환합니다.
     *
     * @param rawApiKey 사용자가 제공한 API Key (plain text)
     * @param requiredType 이 엔드포인트에서 요구하는 API 키 타입 (INGEST 또는 QUERY)
     * @return 유효한 경우 ProjectId, 그렇지 않으면 null
     */
    @Transactional(readOnly = true)
    public UUID getProjectIdByApiKey(String rawApiKey, ApiKeyType requiredType) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            log.warn("API Key가 제공되지 않았습니다.");
            return null;
        }

        if (requiredType == null) {
            log.warn("API Key 타입이 지정되지 않았습니다.");
            return null;
        }

        String requestHashedKey = HmacApiKeyUtil.computeHmac(rawApiKey, hmacSecret);

        // 해시값과 함께 키 타입으로 조회
        Optional<ProjectApiKey> apiKeyOptional =
                projectApiKeyRepository.findByApiKeyHashAndKeyType(requestHashedKey, requiredType);

        if (apiKeyOptional.isEmpty()) {
            // 타입이 맞지 않거나, 키 자체가 존재하지 않는 경우
            log.warn(
                    "유효하지 않거나 권한이 없는 API Key 접근 시도 (요구 타입: {}): {}",
                    requiredType,
                    HmacApiKeyUtil.maskApiKey(rawApiKey));
            return null;
        }

        ProjectApiKey apiKey = apiKeyOptional.get();

        // 키 타입 일치 여부 재확인 (로직 강화)
        if (apiKey.getKeyType() != requiredType) {
            log.error(
                    "DB 조회 로직 오류: API Key 타입 불일치. keyId={}, dbType={}, requiredType={}",
                    apiKey.getId(),
                    apiKey.getKeyType(),
                    requiredType);
            return null;
        }

        if (apiKey.getApiKeyStatus() != ApiKeyStatus.ACTIVE) {
            log.warn(
                    "비활성 API Key입니다 (상태: {}): {}",
                    apiKey.getApiKeyStatus(),
                    HmacApiKeyUtil.maskApiKey(rawApiKey));
            return null;
        }

        log.info("API Key 검증 성공 (타입: {}): {}", requiredType, HmacApiKeyUtil.maskApiKey(rawApiKey));
        return apiKey.getProject().getId();
    }
}
