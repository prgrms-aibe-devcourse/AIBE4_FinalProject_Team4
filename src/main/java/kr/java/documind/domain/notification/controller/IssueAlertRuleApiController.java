package kr.java.documind.domain.notification.controller;

import java.util.List;
import kr.java.documind.domain.auth.model.dto.ProjectRequestContext;
import kr.java.documind.domain.notification.model.dto.request.IssueAlertRuleUpdateRequest;
import kr.java.documind.domain.notification.model.dto.response.IssueAlertRuleResponse;
import kr.java.documind.domain.notification.model.enums.IssueAlertRuleKey;
import kr.java.documind.domain.notification.service.IssueAlertRuleService;
import kr.java.documind.domain.notification.service.NotificationCommandService;
import kr.java.documind.domain.notification.service.NotificationQueryService;
import kr.java.documind.global.annotation.CurrentProject;
import kr.java.documind.global.response.ApiResponse;
import kr.java.documind.global.security.jwt.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{publicId}/issue-alert-rules")
@RequiredArgsConstructor
public class IssueAlertRuleApiController {

    private final NotificationQueryService queryService;
    private final NotificationCommandService commandService;
    private final IssueAlertRuleService alertRuleServcie;

    @GetMapping
    public ResponseEntity<ApiResponse<List<IssueAlertRuleResponse>>> getRules(
            @AuthenticationPrincipal CustomUserDetails auth,
            @CurrentProject ProjectRequestContext ctx) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        alertRuleServcie.getRules(ctx.projectId(), auth.getMemberId())));
    }

    @PatchMapping("/{ruleKey}")
    public ResponseEntity<ApiResponse<Void>> updateRule(
            @PathVariable IssueAlertRuleKey ruleKey,
            @AuthenticationPrincipal CustomUserDetails auth,
            @CurrentProject ProjectRequestContext ctx,
            @RequestBody IssueAlertRuleUpdateRequest request) {
        alertRuleServcie.updateRule(ctx.projectId(), auth.getMemberId(), ruleKey, request.active());
        return ResponseEntity.ok(ApiResponse.success());
    }
}
