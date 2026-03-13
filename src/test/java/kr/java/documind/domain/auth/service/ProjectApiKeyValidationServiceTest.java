package kr.java.documind.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.auth.model.entity.Project;
import kr.java.documind.domain.auth.model.entity.ProjectApiKey;
import kr.java.documind.domain.auth.model.enums.ApiKeyType;
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

        // 정상 흐름 테스트들을 위해 기본적으로 프로젝트를 활성 상태(true)로 설정
        lenient().when(testProject.isActive()).thenReturn(true);
    }

    @Test
    @DisplayName("API Key 발급부터 검증까지 전체 흐름: 유효한 키 → ProjectId 반환")
    void getProjectIdByApiKey_ValidKey_ReturnsProjectId() {
        // Given: 수집(INGEST)용 키 생성 (접두사 dmi_ 주입)
        String generatedRawApiKey = HmacApiKeyUtil.generatePlainKey("dmi_");
        String extractedPrefix = HmacApiKeyUtil.extractPrefix(generatedRawApiKey);
        String last4 = HmacApiKeyUtil.extractLast4(generatedRawApiKey);
        String hashedApiKey = HmacApiKeyUtil.computeHmac(generatedRawApiKey, hmacSecret);

        // 엔티티 생성 시 ApiKeyType.INGEST 명시
        ProjectApiKey apiKey =
                ProjectApiKey.create(
                        testProject, hashedApiKey, extractedPrefix, last4, ApiKeyType.INGEST);

        // 해시 및 타입(INGEST)으로 검색 시 Mocking
        when(projectApiKeyRepository.findByApiKeyHashAndKeyTypeWithProject(
                        hashedApiKey, ApiKeyType.INGEST))
                .thenReturn(Optional.of(apiKey));

        // When: 생성된 수집용 API Key와 INGEST 타입으로 검증 시도
        UUID resultProjectId =
                validationService.getProjectIdByApiKey(generatedRawApiKey, ApiKeyType.INGEST);

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
        UUID resultProjectId = validationService.getProjectIdByApiKey(nullApiKey, ApiKeyType.QUERY);

        // Then
        assertThat(resultProjectId).isNull();
    }

    @Test
    @DisplayName("API Key 검증 실패: 존재하지 않는 API Key 해시 → null 반환")
    void getProjectIdByApiKey_NonExistentHash_ReturnsNull() {
        // Given
        String rawApiKey = HmacApiKeyUtil.generatePlainKey("dmq_");
        String hashedApiKey = HmacApiKeyUtil.computeHmac(rawApiKey, hmacSecret);

        // DB에 해당 해시와 타입 조합이 없을 때
        when(projectApiKeyRepository.findByApiKeyHashAndKeyTypeWithProject(
                        hashedApiKey, ApiKeyType.QUERY))
                .thenReturn(Optional.empty());

        // When
        UUID resultProjectId = validationService.getProjectIdByApiKey(rawApiKey, ApiKeyType.QUERY);

        // Then
        assertThat(resultProjectId).isNull();
    }

    @Test
    @DisplayName("API Key 검증 실패: 변조된 API Key 입력 시 (해시 불일치) → null 반환")
    void getProjectIdByApiKey_HashMismatch_ReturnsNull() {
        // Given
        String originalRawApiKey = HmacApiKeyUtil.generatePlainKey("dmi_");
        String tamperedRawApiKey =
                originalRawApiKey.substring(0, originalRawApiKey.length() - 1) + "X";
        String tamperedHashedApiKey = HmacApiKeyUtil.computeHmac(tamperedRawApiKey, hmacSecret);

        // 변조된 해시값은 DB에 없음
        when(projectApiKeyRepository.findByApiKeyHashAndKeyTypeWithProject(
                        tamperedHashedApiKey, ApiKeyType.INGEST))
                .thenReturn(Optional.empty());

        // When
        UUID resultProjectId =
                validationService.getProjectIdByApiKey(tamperedRawApiKey, ApiKeyType.INGEST);

        // Then
        assertThat(resultProjectId).isNull();
    }

    @Test
    @DisplayName("API Key 검증 실패: 정지된(SUSPENDED) 키 → null 반환")
    void getProjectIdByApiKey_SuspendedKey_ReturnsNull() {
        // Given
        String generatedRawApiKey = HmacApiKeyUtil.generatePlainKey("dmi_");
        String extractedPrefix = HmacApiKeyUtil.extractPrefix(generatedRawApiKey);
        String last4 = HmacApiKeyUtil.extractLast4(generatedRawApiKey);
        String hashedApiKey = HmacApiKeyUtil.computeHmac(generatedRawApiKey, hmacSecret);

        ProjectApiKey apiKey =
                ProjectApiKey.create(
                        testProject, hashedApiKey, extractedPrefix, last4, ApiKeyType.INGEST);
        apiKey.suspend(); // 상태 변경

        when(projectApiKeyRepository.findByApiKeyHashAndKeyTypeWithProject(
                        hashedApiKey, ApiKeyType.INGEST))
                .thenReturn(Optional.of(apiKey));

        // When
        UUID resultProjectId =
                validationService.getProjectIdByApiKey(generatedRawApiKey, ApiKeyType.INGEST);

        // Then
        assertThat(resultProjectId).isNull();
    }

    @Test
    @DisplayName("API Key 검증 실패: 폐기된(REVOKED) 키 → null 반환")
    void getProjectIdByApiKey_RevokedKey_ReturnsNull() {
        // Given
        String generatedRawApiKey = HmacApiKeyUtil.generatePlainKey("dmq_");
        String extractedPrefix = HmacApiKeyUtil.extractPrefix(generatedRawApiKey);
        String last4 = HmacApiKeyUtil.extractLast4(generatedRawApiKey);
        String hashedApiKey = HmacApiKeyUtil.computeHmac(generatedRawApiKey, hmacSecret);

        ProjectApiKey apiKey =
                ProjectApiKey.create(
                        testProject, hashedApiKey, extractedPrefix, last4, ApiKeyType.QUERY);
        apiKey.revoke(); // 상태 변경

        when(projectApiKeyRepository.findByApiKeyHashAndKeyTypeWithProject(
                        hashedApiKey, ApiKeyType.QUERY))
                .thenReturn(Optional.of(apiKey));

        // When
        UUID resultProjectId =
                validationService.getProjectIdByApiKey(generatedRawApiKey, ApiKeyType.QUERY);

        // Then
        assertThat(resultProjectId).isNull();
    }

    @Test
    @DisplayName("API Key 검증 실패: 요구하는 타입(Scope)과 실제 키의 타입이 불일치 → null 반환")
    void getProjectIdByApiKey_TypeMismatch_ReturnsNull() {
        // Given: 조회(QUERY)용으로 발급된 키를 수집(INGEST) API에 사용하려 시도
        String queryRawApiKey = HmacApiKeyUtil.generatePlainKey("dmq_");
        String hashedApiKey = HmacApiKeyUtil.computeHmac(queryRawApiKey, hmacSecret);

        // INGEST를 요구하지만 DB에는 해당 해시의 INGEST 키가 존재하지 않으므로 empty 반환 (1차 방어)
        when(projectApiKeyRepository.findByApiKeyHashAndKeyTypeWithProject(
                        hashedApiKey, ApiKeyType.INGEST))
                .thenReturn(Optional.empty());

        // When: 수집(INGEST) 권한이 필요한 엔드포인트에서 조회용 키를 사용
        UUID resultProjectId =
                validationService.getProjectIdByApiKey(queryRawApiKey, ApiKeyType.INGEST);

        // Then: 권한 불일치로 식별 실패
        assertThat(resultProjectId).isNull();
    }

    @Test
    @DisplayName("API Key 검증 실패: DB 조회 결과와 요구 타입이 불일치하는 논리적 에러 상황 방어 (2차 방어선)")
    void getProjectIdByApiKey_DefensiveTypeMismatch_ReturnsNull() {
        // Given: 시스템적 오류나 쿼리 오작동으로 인해 INGEST 검색 시 QUERY 키 엔티티가 반환되었다고 가정
        String rawApiKey = HmacApiKeyUtil.generatePlainKey("dmq_");
        String extractedPrefix = HmacApiKeyUtil.extractPrefix(rawApiKey);
        String last4 = HmacApiKeyUtil.extractLast4(rawApiKey);
        String hashedApiKey = HmacApiKeyUtil.computeHmac(rawApiKey, hmacSecret);

        // 실제로는 QUERY 타입인 엔티티
        ProjectApiKey queryApiKey =
                ProjectApiKey.create(
                        testProject, hashedApiKey, extractedPrefix, last4, ApiKeyType.QUERY);

        // Mocking: INGEST로 찾았는데 QUERY 엔티티가 반환됨 (비정상 상황)
        when(projectApiKeyRepository.findByApiKeyHashAndKeyTypeWithProject(
                        hashedApiKey, ApiKeyType.INGEST))
                .thenReturn(Optional.of(queryApiKey));

        // When
        UUID resultProjectId = validationService.getProjectIdByApiKey(rawApiKey, ApiKeyType.INGEST);

        // Then: 서비스 레이어의 방어 로직(if (apiKey.getKeyType() != requiredType))에 의해 차단됨
        assertThat(resultProjectId).isNull();
    }

    @Test
    @DisplayName("API Key 검증 실패: 삭제된(Soft Delete) 프로젝트에 대한 접근 → null 반환")
    void getProjectIdByApiKey_DeletedProject_ReturnsNull() {
        // Given
        String generatedRawApiKey = HmacApiKeyUtil.generatePlainKey("dmi_");
        String extractedPrefix = HmacApiKeyUtil.extractPrefix(generatedRawApiKey);
        String last4 = HmacApiKeyUtil.extractLast4(generatedRawApiKey);
        String hashedApiKey = HmacApiKeyUtil.computeHmac(generatedRawApiKey, hmacSecret);

        ProjectApiKey apiKey =
                ProjectApiKey.create(
                        testProject, hashedApiKey, extractedPrefix, last4, ApiKeyType.INGEST);

        // 해당 테스트에 한해 프로젝트가 비활성화(삭제)된 상태라고 가정하여 Mocking 덮어쓰기
        when(testProject.isActive()).thenReturn(false);

        when(projectApiKeyRepository.findByApiKeyHashAndKeyTypeWithProject(
                        hashedApiKey, ApiKeyType.INGEST))
                .thenReturn(Optional.of(apiKey));

        // When
        UUID resultProjectId =
                validationService.getProjectIdByApiKey(generatedRawApiKey, ApiKeyType.INGEST);

        // Then: 프로젝트가 삭제되었으므로 키가 유효하더라도 null을 반환해야 함
        assertThat(resultProjectId).isNull();
    }
}
