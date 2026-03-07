package kr.java.documind.domain.logcollector.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// TODO: [godqhrenf] Rate Limit 테스트용 컨트롤러. 로그 수집 API 컨트롤러 작성 후 삭제 예정
@RestController
public class DummyRateLimitController {

    /** Rate Limit 필터가 정상 작동하는지 확인하기 위한 임시 엔드포인트 */
    @GetMapping("/api/logs/test-rate-limit")
    public ResponseEntity<String> testRateLimit() {
        return ResponseEntity.ok("Rate Limit Passed! Token is consumed.");
    }
}
