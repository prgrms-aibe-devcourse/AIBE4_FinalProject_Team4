package kr.java.documind.domain.patchnote.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 패치노트 초안 SSE 스트리밍 요청 DTO.
 *
 * @param majorVersion     대상 버전 (major)
 * @param minorVersion     대상 버전 (minor)
 * @param patchVersion     대상 버전 (patch)
 * @param modelAlias       사용할 LLM 모델 alias (null → 기본 모델)
 * @param additionalPrompt LLM에 전달할 추가 지침 (null 또는 빈 값이면 무시)
 * @param overwrite        동일 버전이 존재해도 스트리밍을 시작할지 여부
 *                         (true → 버전 중복 체크 스킵, 저장 시 기존 버전 soft delete)
 */
@Schema(description = "패치노트 초안 생성 요청")
public record DraftStreamRequest(
        @Schema(description = "major 버전", example = "1") int majorVersion,
        @Schema(description = "minor 버전", example = "2") int minorVersion,
        @Schema(description = "patch 버전", example = "0") int patchVersion,
        @Schema(description = "LLM 모델 alias (null → 기본 모델)", example = "gpt-4o") String modelAlias,
        @Schema(description = "LLM 추가 지침 (null → 기본 지침만 사용)", example = "결제 관련 이슈를 강조해 주세요.")
                String additionalPrompt,
        @Schema(description = "동일 버전 존재 시 덮어쓰기 여부 (true → 버전 중복 체크 스킵)", example = "false")
                boolean overwrite) {}
