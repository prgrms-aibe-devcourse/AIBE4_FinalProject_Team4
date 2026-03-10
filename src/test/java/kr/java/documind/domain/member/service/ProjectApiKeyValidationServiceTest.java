package kr.java.documind.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
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
        // Given: HmacApiKeyUtil을 사용하여 API Key 생성 및 저장 준비
        String generatedRawApiKey = HmacApiKeyUtil.generatePlainKey();
        String extractedPrefix = HmacApiKeyUtil.extractPrefix(generatedRawApiKey);
        String last4 = HmacApiKeyUtil.extractLast4(generatedRawApiKey);
        String hashedApiKey = HmacApiKeyUtil.computeHmac(generatedRawApiKey, hmacSecret);

        ProjectApiKey apiKey = ProjectApiKey.create(testProject, hashedApiKey, extractedPrefix, last4);

        when(projectApiKeyRepository.findByKeyPrefix(extractedPrefix)).thenReturn(Optional.of(apiKey));

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
    @DisplayName("API Key 검증 실패: 존재하지 않는 Prefix → null 반환")
    void getProjectIdByApiKey_NonExistentPrefix_ReturnsNull() {
        // Given
        String rawApiKey = "docu_nonexistentkey";
        when(projectApiKeyRepository.findByKeyPrefix(anyString())).thenReturn(Optional.empty());

        // When
        UUID resultProjectId = validationService.getProjectIdByApiKey(rawApiKey);

        // Then
        assertThat(resultProjectId).isNull();
    }

    @Test
    @DisplayName("API Key 검증 실패: 해시 값이 일치하지 않는 경우 → null 반환")
    void getProjectIdByApiKey_HashMismatch_ReturnsNull() {
        // Given
        String rawApiKey = "docu_1234567890abcdef";
        String prefix = HmacApiKeyUtil.extractPrefix(rawApiKey);
        String last4 = HmacApiKeyUtil.extractLast4(rawApiKey);
        // DB에는 다른 키의 해시가 저장되어 있다고 가정
        String wrongHashedApiKey = HmacApiKeyUtil.computeHmac("wrong-api-key", hmacSecret);
        ProjectApiKey apiKey = ProjectApiKey.create(testProject, wrongHashedApiKey, prefix, last4);

        when(projectApiKeyRepository.findByKeyPrefix(prefix)).thenReturn(Optional.of(apiKey));

        // When
        UUID resultProjectId = validationService.getProjectIdByApiKey(rawApiKey);

        // Then
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

        ProjectApiKey apiKey = ProjectApiKey.create(testProject, hashedApiKey, extractedPrefix, last4);
        apiKey.suspend();

        when(projectApiKeyRepository.findByKeyPrefix(extractedPrefix)).thenReturn(Optional.of(apiKey));

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

        ProjectApiKey apiKey = ProjectApiKey.create(testProject, hashedApiKey, extractedPrefix, last4);
        apiKey.revoke();

        when(projectApiKeyRepository.findByKeyPrefix(extractedPrefix)).thenReturn(Optional.of(apiKey));

        // When
        UUID resultProjectId = validationService.getProjectIdByApiKey(generatedRawApiKey);

        // Then
        assertThat(resultProjectId).isNull();
    }
}
