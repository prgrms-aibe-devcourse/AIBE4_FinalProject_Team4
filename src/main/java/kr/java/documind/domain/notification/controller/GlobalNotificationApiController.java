package kr.java.documind.domain.notification.controller;

import kr.java.documind.domain.notification.infrastructure.NotificationSseManager;
import kr.java.documind.domain.notification.model.dto.response.NotificationCursorPageResponse;
import kr.java.documind.domain.notification.service.NotificationCommandService;
import kr.java.documind.domain.notification.service.NotificationQueryService;
import kr.java.documind.global.response.ApiResponse;
import kr.java.documind.global.security.jwt.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class GlobalNotificationApiController {

    private final NotificationQueryService queryService;
    private final NotificationCommandService commandService;
    private final NotificationSseManager sseManager;

    /** 1. SSE 스트림 연결 (전역) */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal CustomUserDetails auth) {
        return sseManager.register(auth.getMemberId());
    }

    /** 2. 전역 알림 목록 조회 (Dropdown용) */
    @GetMapping
    public ResponseEntity<ApiResponse<NotificationCursorPageResponse>> listGlobal(
            @AuthenticationPrincipal CustomUserDetails auth,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long cursorId) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        queryService.getNotifications(
                                null, auth.getMemberId(), cursorId, null, size)));
    }

    /** 3. 전역 미읽음 카운트 (GNB 배지용) */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> unreadCountGlobal(
            @AuthenticationPrincipal CustomUserDetails auth) {
        return ResponseEntity.ok(
                ApiResponse.success(queryService.getUnreadCount(null, auth.getMemberId())));
    }

    /** * 4. 전역 모든 알림 읽음 처리 (Dropdown 내 버튼) 사용자가 속한 모든 프로젝트의 알림을 한꺼번에 읽음 처리합니다. */
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> readAllGlobal(
            @AuthenticationPrincipal CustomUserDetails auth) {
        commandService.markAllRead(auth.getMemberId(), null); // projectId를 null로 넘겨 전역 처리
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** * 5. 개별 알림 읽음 처리 (Dropdown 또는 클릭 시) 알림 ID만으로 처리하며, 내부에서 본인 확인 후 처리합니다. */
    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> readGlobal(
            @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails auth) {
        commandService.markRead(id, auth.getMemberId(), null);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
