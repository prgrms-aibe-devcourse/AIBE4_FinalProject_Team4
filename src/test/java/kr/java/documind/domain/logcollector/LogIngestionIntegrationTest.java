// package kr.java.documind.domain.logcollector;
//
// import com.fasterxml.jackson.databind.ObjectMapper;
// import kr.java.documind.domain.logcollector.model.dto.RawLogRequest;
// import kr.java.documind.domain.member.service.ProjectApiKeyValidationService;
// import org.junit.jupiter.api.AfterEach;
// import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
// import org.springframework.boot.test.context.SpringBootTest;
// import org.springframework.data.redis.connection.stream.MapRecord;
// import org.springframework.data.redis.connection.stream.StreamOffset;
// import org.springframework.data.redis.connection.stream.StreamReadOptions;
// import org.springframework.data.redis.core.StringRedisTemplate;
// import org.springframework.http.MediaType;
// import org.springframework.test.web.servlet.MockMvc;
//
// import java.util.List;
// import java.util.Map;
//
// import static org.assertj.core.api.Assertions.assertThat;
// import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
// import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
// @SpringBootTest
// @AutoConfigureMockMvc
// class LogIngestionIntegrationTest {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    @Autowired
//    private StringRedisTemplate redisTemplate;
//
//    private static final String STREAM_KEY = "log:stream:ingest";
//
//    @AfterEach
//    void tearDown() {
//        // 테스트 격리를 위해 매 테스트 종료 후 Redis Stream 데이터를 깔끔하게 지워줍니다.
//        redisTemplate.delete(STREAM_KEY);
//    }
//
//    @Test
//    @DisplayName("성공: 유효한 API Key와 로그 페이로드 전송 시 202 응답 및 Redis 적재 완료")
//    void ingestLog_Success() throws Exception {
//        // Given: "무조건 수용" 정책에 맞게 일부 필드만 채운 클라이언트 요청 데이터 생성
//        RawLogRequest rawRequest = new RawLogRequest(
//            "session-123", "user-456", "ERROR", "combat_event",
//            "2023-10-25T10:00:00Z", "trace-789", "span-001", "Some raw error text",
//            Map.of("service.name", "game-server-alpha"),
//            Map.of("player_level", 99, "weapon", "sword")
//        );
//        String jsonPayload = objectMapper.writeValueAsString(rawRequest);
//
//        // When: 더미 API Key를 헤더에 담아 POST 요청 전송
//        mockMvc.perform(post("/api/logs")
//                .header("Api-Key", ProjectApiKeyValidationService.VALID_TEST_API_KEY) //
// "test-api-key-1234"
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(jsonPayload))
//            // Then 1: API 응답 상태 코드가 202 Accepted 인지 확인
//            .andExpect(status().isAccepted());
//
//        // Then 2: Redis Stream에 데이터가 정상적으로 들어갔는지 딥(Deep) 검증
//        List<MapRecord<String, Object, Object>> streamRecords =
//            redisTemplate.opsForStream().read(StreamReadOptions.empty(),
// StreamOffset.fromStart(STREAM_KEY));
//
//        assertThat(streamRecords).isNotNull();
//        assertThat(streamRecords).hasSize(1); // 큐에 정확히 1개의 로그가 쌓여야 함
//
//        // 저장된 JSON(EnrichedLogMessage) 꺼내기
//        String savedEnrichedPayload = (String) streamRecords.get(0).getValue().get("payload");
//
//        // 🌟 핵심 검증: 서버가 조립한 데이터(봉투)가 잘 씌워졌는지 확인
//        // 1. 더미 ProjectId가 주입되었는가?
//
// assertThat(savedEnrichedPayload).contains(ProjectApiKeyValidationService.DUMMY_PROJECT_ID.toString());
//        // 2. 클라이언트가 보낸 원본 속성(attributes)이 유지되었는가?
//        assertThat(savedEnrichedPayload).contains("game-server-alpha");
//        assertThat(savedEnrichedPayload).contains("weapon");
//
//        // Redis에서 최신 로그 1개 읽어오기
//        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
//            .read(StreamReadOptions.empty(), StreamOffset.fromStart("log:stream:ingest"));
//
//        records.forEach(record -> {
//            System.out.println("메시지 ID: " + record.getId());
//            System.out.println("데이터: " + record.getValue().get("payload"));
//        });
//    }
//
//    @Test
//    @DisplayName("실패: 유효하지 않은 API Key 전송 시 401 응답 및 Redis 적재 차단")
//    void ingestLog_Fail_InvalidApiKey() throws Exception {
//        // Given
//        RawLogRequest rawRequest = new RawLogRequest(
//            null, null, "INFO", null, null, null, null, null, null, null
//        );
//        String jsonPayload = objectMapper.writeValueAsString(rawRequest);
//
//        // When: "틀린" API Key 전송
//        mockMvc.perform(post("/api/logs")
//                .header("Api-Key", "invalid-hacker-key-9999")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(jsonPayload))
//            // Then 1: 필터에서 막혀 401 Unauthorized 에러가 떨어져야 함
//            .andExpect(status().isUnauthorized());
//
//        // Then 2: 컨트롤러에 도달하지 못했으므로 Redis에는 아무것도 적재되지 않아야 함
//        Long streamSize = redisTemplate.opsForStream().size(STREAM_KEY);
//        assertThat(streamSize).isZero();
//    }
// }
