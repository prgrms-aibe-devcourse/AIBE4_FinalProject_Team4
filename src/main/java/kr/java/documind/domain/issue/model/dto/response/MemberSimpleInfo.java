package kr.java.documind.domain.issue.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import kr.java.documind.domain.member.model.entity.Member;

/**
 * 멤버 간단 정보
 *
 * <p>댓글 작성자, 멘션된 사용자 등 멤버 기본 정보 제공
 */
@Schema(description = "멤버 간단 정보")
public record MemberSimpleInfo(
        @Schema(description = "멤버 ID", example = "123e4567-e89b-12d3-a456-426614174000")
                UUID memberId,
        @Schema(description = "닉네임", example = "김개발") String nickname,
        @Schema(description = "프로필 이미지 경로", example = "/profiles/user123.jpg", nullable = true)
                String profileImageUrl) {

    /**
     * Member 엔티티로부터 MemberSimpleInfo 생성
     *
     * @param member 멤버 엔티티
     * @return MemberSimpleInfo
     */
    public static MemberSimpleInfo from(Member member) {
        if (member == null) {
            return null;
        }

        return new MemberSimpleInfo(member.getId(), member.getNickname(), member.getProfileKey());
    }
}
