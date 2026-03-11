package kr.java.documind.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.auth.model.entity.Project;
import kr.java.documind.domain.auth.model.entity.ProjectApiKey;
import kr.java.documind.domain.auth.model.repository.ProjectApiKeyRepository;
import kr.java.documind.global.util.HmacApiKeyUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectApiKeyValidationService 단위 테스트")
class ProjectApiKeyValidationServiceTest {

    @Mock private ProjectApiKeyRepository projectApiKeyRepository;
    @Mock private Project testProject;

    @InjectMocks private ProjectApiKeyValidationService validationService;

    private final String hmacSecret = "test-secret";
    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(validationService, "hmacSecret", hmacSecret);
        lenient().when(testProject.getId()).thenReturn(projectId);
    }

    @Test
    @DisplayName("API Key 발급부터 검증까지 전체 흐름: 유효한 키 → ProjectId 반환")
    void getProjectIdByApiKey_ValidKey_ReturnsProjectId() {
        // Given: HmacApiKeyUtil을 사용하여 API Key 생성
        String generatedRawApiKey = HmacApiKeyUtil.generatePlainKey();
        String extractedPrefix = HmacApiKeyUtil.extractPrefix(generatedRawApiKey);
        String last4 = HmacApiKeyUtil.extractLast4(generatedRawApiKey);
        String hashedApiKey = HmacApiKeyUtil.computeHmac(generatedRawApiKey, hmacSecret);

        ProjectApiKey apiKey =
                ProjectApiKey.create(testProject, hashedApiKey, extractedPrefix, last4);

        // 해시 검색으로 Mocking
        when(projectApiKeyRepository.findByApiKeyHash(hashedApiKey))
                .thenReturn(Optional.of(apiKey));

        // When: 생성된 API Key로 검증 시도
        UUID resultProjectId = validationService.getProjectIdByApiKey(generatedRawApiKey);

        // Then: 검증 성공 및 ProjectId 반환
        assertThat(resultProjectId).isNotNull();
        assertThat(resultProjectId).isEqualTo(projectId);
    }

    @Test
    @DisplayName("API Key 검증 실패: API Key가 null인 경우 → null 반환")
    void getProjectIdByApiKey_NullApiKey_ReturnsNull() {
        // Given
        String nullApiKey = null;

        // When
        UUID resultProjectId = validationService.getProjectIdByApiKey(nullApiKey);

        // Then
        assertThat(resultProjectId).isNull();
    }

    @Test
    @DisplayName("API Key 검증 실패: 존재하지 않는 API Key → null 반환")
    void getProjectIdByApiKey_NonExistentHash_ReturnsNull() {
        // Given
        String rawApiKey = HmacApiKeyUtil.generatePlainKey();
        String hashedApiKey = HmacApiKeyUtil.computeHmac(rawApiKey, hmacSecret);

        // DB에 해당 해시값이 없을 때 (Optional.empty 반환)
        when(projectApiKeyRepository.findByApiKeyHash(hashedApiKey)).thenReturn(Optional.empty());

        // When
        UUID resultProjectId = validationService.getProjectIdByApiKey(rawApiKey);

        // Then
        assertThat(resultProjectId).isNull();
    }

    @Test
    @DisplayName("API Key 검증 실패: 변조된 API Key 입력 시 (해시 불일치) → null 반환")
    void getProjectIdByApiKey_HashMismatch_ReturnsNull() {
        // Given: 정상적인 키가 발급되었으나
        String originalRawApiKey = HmacApiKeyUtil.generatePlainKey();

        // 클라이언트가 끝자리를 'X'로 변조하여 요청했다고 가정
        String tamperedRawApiKey =
                originalRawApiKey.substring(0, originalRawApiKey.length() - 1) + "X";
        String tamperedHashedApiKey = HmacApiKeyUtil.computeHmac(tamperedRawApiKey, hmacSecret);

        // 변조된 키로 만든 해시값은 DB에 존재하지 않음
        when(projectApiKeyRepository.findByApiKeyHash(tamperedHashedApiKey))
                .thenReturn(Optional.empty());

        // When: 변조된 키로 검증 시도
        UUID resultProjectId = validationService.getProjectIdByApiKey(tamperedRawApiKey);

        // Then: 식별 실패로 null 반환
        assertThat(resultProjectId).isNull();
    }

    @Test
    @DisplayName("API Key 검증 실패: 정지된(SUSPENDED) 키 → null 반환")
    void getProjectIdByApiKey_SuspendedKey_ReturnsNull() {
        // Given
        String generatedRawApiKey = HmacApiKeyUtil.generatePlainKey();
        String extractedPrefix = HmacApiKeyUtil.extractPrefix(generatedRawApiKey);
        String last4 = HmacApiKeyUtil.extractLast4(generatedRawApiKey);
        String hashedApiKey = HmacApiKeyUtil.computeHmac(generatedRawApiKey, hmacSecret);

        ProjectApiKey apiKey =
                ProjectApiKey.create(testProject, hashedApiKey, extractedPrefix, last4);
        apiKey.suspend(); // 상태 변경

        when(projectApiKeyRepository.findByApiKeyHash(hashedApiKey))
                .thenReturn(Optional.of(apiKey));

        // When
        UUID resultProjectId = validationService.getProjectIdByApiKey(generatedRawApiKey);

        // Then
        assertThat(resultProjectId).isNull();
    }

    @Test
    @DisplayName("API Key 검증 실패: 폐기된(REVOKED) 키 → null 반환")
    void getProjectIdByApiKey_RevokedKey_ReturnsNull() {
        // Given
        String generatedRawApiKey = HmacApiKeyUtil.generatePlainKey();
        String extractedPrefix = HmacApiKeyUtil.extractPrefix(generatedRawApiKey);
        String last4 = HmacApiKeyUtil.extractLast4(generatedRawApiKey);
        String hashedApiKey = HmacApiKeyUtil.computeHmac(generatedRawApiKey, hmacSecret);

        ProjectApiKey apiKey =
                ProjectApiKey.create(testProject, hashedApiKey, extractedPrefix, last4);
        apiKey.revoke(); // 상태 변경

        when(projectApiKeyRepository.findByApiKeyHash(hashedApiKey))
                .thenReturn(Optional.of(apiKey));

        // When
        UUID resultProjectId = validationService.getProjectIdByApiKey(generatedRawApiKey);

        // Then
        assertThat(resultProjectId).isNull();
    }
}
