package kr.java.documind.domain.patchnote.controller;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public interface PatchNoteDraftSwaggerDocs {

    // POST /drafts/stream (SSE 스트리밍 초안 생성)

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "SSE 스트림 시작 성공 — 이벤트 시퀀스: progress → sources → token(N회) → done",
                content =
                        @Content(
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        event: progress
                                                        data: {"step":"BUILDING_CONTEXT"}

                                                        event: sources
                                                        data: {"refs":["ISSUE-245","DOC-1024"]}

                                                        event: progress
                                                        data: {"step":"GENERATING"}

                                                        event: token
                                                        data: {"content":"## 신규\\n"}

                                                        event: done
                                                        data: {"cleanedContent":"## 신규\\n- 새 스킬이 추가되었습니다.","sourceRefs":["ISSUE-245"]}
                                                        """))),
        @ApiResponse(
                responseCode = "200",
                description = "오류 발생 시 — error 이벤트 후 스트림 종료",
                content =
                        @Content(
                                examples = {
                                    @ExampleObject(
                                            name = "DuplicateVersion",
                                            summary = "버전 중복",
                                            value =
                                                    """
                                                    event: error
                                                    data: {"message":"이미 존재하는 버전입니다. v1.2.0"}
                                                    """),
                                    @ExampleObject(
                                            name = "NoItems",
                                            summary = "대기 중 항목 없음",
                                            value =
                                                    """
                                                    event: error
                                                    data: {"message":"패치노트에 포함할 대기 중인 항목이 없습니다."}
                                                    """)
                                })),
        @ApiResponse(
                responseCode = "403",
                description = "프로젝트 멤버 아님",
                content =
                        @Content(
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {"success":false,"error":{"message":"프로젝트 멤버만 접근할 수 있습니다."}}
                                                        """)))
    })
    @interface StreamDraftDocs {}
}
