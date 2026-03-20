package kr.java.documind.domain.notification.controller;

import kr.java.documind.domain.auth.model.dto.ProjectRequestContext;
import kr.java.documind.domain.notification.model.dto.response.NotificationCursorPageResponse;
import kr.java.documind.domain.notification.service.NotificationCommandService;
import kr.java.documind.domain.notification.service.NotificationQueryService;
import kr.java.documind.global.annotation.CurrentProject;
import kr.java.documind.global.enums.SourceType;
import kr.java.documind.global.response.ApiResponse;
import kr.java.documind.global.security.jwt.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{publicId}/notifications")
@RequiredArgsConstructor
public class ProjectNotificationApiController {

    private final NotificationQueryService queryService;
    private final NotificationCommandService commandService;

    /** 특정 프로젝트 알림 이력 조회 (History Page) */
    @GetMapping
    public ResponseEntity<ApiResponse<NotificationCursorPageResponse>> listByProject(
            @AuthenticationPrincipal CustomUserDetails auth,
            @CurrentProject ProjectRequestContext ctx, // 보안 검증된 프로젝트 컨텍스트
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(required = false) SourceType sourceType) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        queryService.getNotifications(
                                ctx.projectId(), auth.getMemberId(), cursorId, sourceType, size)));
    }

    /** 특정 프로젝트 내 알림 무시하기 (History Page 내 액션) */
    @PatchMapping("/{id}/ignore")
    public ResponseEntity<ApiResponse<Void>> ignore(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails auth,
            @CurrentProject ProjectRequestContext ctx) {
        commandService.markIgnored(id, auth.getMemberId(), ctx.projectId());
        return ResponseEntity.ok(ApiResponse.success());
    }
}
