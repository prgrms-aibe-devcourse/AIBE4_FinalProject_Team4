package kr.java.documind.domain.notification.controller;

import kr.java.documind.domain.notification.infrastructure.NotificationSseManager;
import kr.java.documind.domain.notification.model.dto.response.NotificationResponse;
import kr.java.documind.domain.notification.service.NotificationCommandService;
import kr.java.documind.domain.notification.service.NotificationQueryService;
import kr.java.documind.global.response.ApiResponse;
import kr.java.documind.global.security.jwt.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationApiController {

    private final NotificationSseManager sseManager;
    private final NotificationQueryService queryService;
    private final NotificationCommandService commandService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal CustomUserDetails auth) {
        return sseManager.register(auth.getMemberId());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> list(
            @AuthenticationPrincipal CustomUserDetails auth,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.success(queryService.getNotifications(auth.getMemberId(), pageable)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> unreadCount(
            @AuthenticationPrincipal CustomUserDetails auth) {
        return ResponseEntity.ok(
                ApiResponse.success(queryService.getUnreadCount(auth.getMemberId())));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> readAll(
            @AuthenticationPrincipal CustomUserDetails auth) {
        commandService.markAllRead(auth.getMemberId());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> read(
            @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails auth) {
        commandService.markRead(id, auth.getMemberId());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PatchMapping("/{id}/ignore")
    public ResponseEntity<ApiResponse<Void>> ignore(
            @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails auth) {
        commandService.markIgnored(id, auth.getMemberId());
        return ResponseEntity.ok(ApiResponse.success());
    }
}
