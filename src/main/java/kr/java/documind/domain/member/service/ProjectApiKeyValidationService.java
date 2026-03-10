package kr.java.documind.domain.member.service;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ProjectApiKeyValidationService {

    // 테스트를 위한 약속된 더미 데이터 세팅
    public static final String VALID_TEST_API_KEY = "test-api-key-1234";
    public static final UUID DUMMY_PROJECT_ID =
            UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    /**
     * [임시] API Key 검증 로직 (해싱 및 DB 연동 구현 전까지 사용)
     * TODO: [godqhrenf] Api-Key 검증 후 projectId 반환 로직 구현
     */
    public UUID getProjectIdByApiKey(String rawApiKey) {
        log.info("요청된 API Key (테스트 모드): {}", rawApiKey);

        // 1. 약속된 테스트용 API Key가 들어온 경우 -> 고정된 더미 ProjectId 반환
        if (VALID_TEST_API_KEY.equals(rawApiKey)) {
            log.info("API Key 검증 성공! 테스트 ProjectId 반환: {}", DUMMY_PROJECT_ID);
            return DUMMY_PROJECT_ID;
        }

        // 2. 그 외의 키가 들어온 경우 -> null 반환 (Filter에서 401 에러로 튕겨냄)
        log.warn("유효하지 않은 API Key 접근 시도: {}", rawApiKey);
        return null;
    }
}
