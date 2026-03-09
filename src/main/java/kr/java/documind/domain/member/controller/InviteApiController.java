package kr.java.documind.domain.member.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.java.documind.domain.member.model.dto.InviteSendRequest;
import kr.java.documind.domain.member.model.dto.ProjectRequestContext;
import kr.java.documind.domain.member.service.InvitationService;
import kr.java.documind.global.annotation.CurrentProject;
import kr.java.documind.global.annotation.RequireProjectManager;
import kr.java.documind.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Invitation", description = "프로젝트 초대 API")
@RestController
@RequestMapping("/api/projects/{publicId}/invitations")
@RequiredArgsConstructor
public class InviteApiController {

    private final InvitationService invitationService;

    @Operation(
            summary = "멤버 초대",
            description =
                    "이메일로 프로젝트 초대 링크를 발송합니다. "
                            + "동일 이메일의 기존 PENDING 초대는 자동으로 철회됩니다. "
                            + "이메일 발송은 비동기로 처리되며, API는 즉시 성공을 반환합니다. "
                            + "MANAGER 권한이 필요합니다.")
    @RequireProjectManager
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> sendInvitation(
            @CurrentProject ProjectRequestContext ctx,
            @Valid @RequestBody InviteSendRequest request) {

        invitationService.sendInvitation(ctx.publicId(), ctx.actorMemberId(), request);
        return ResponseEntity.ok(ApiResponse.success("초대를 발송했습니다. 메일은 비동기로 처리됩니다."));
    }
}
