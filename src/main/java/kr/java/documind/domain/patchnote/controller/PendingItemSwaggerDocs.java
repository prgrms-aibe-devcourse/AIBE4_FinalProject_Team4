package kr.java.documind.domain.patchnote.controller;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** PendingItemApiController Swagger 응답 명세 인터페이스. */
public interface PendingItemSwaggerDocs {

    // GET /pending-items (피드 목록 조회)

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "피드 조회 성공",
                content =
                        @Content(
                                schema =
                                        @Schema(
                                                implementation =
                                                        kr.java.documind.global.response.ApiResponse
                                                                .class),
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {
                                                          "success": true,
                                                          "data": [
                                                            {
                                                              "id": 42,
                                                              "sourceId": 7,
                                                              "sourceType": "DOCUMENT",
                                                              "title": "몬스터 밸런스 패치 상세",
                                                              "summary": "몬스터 체력 공격력 수치가 조정되었습니다.",
                                                              "patchType": "CHANGE",
                                                              "status": "PENDING",
                                                              "sourceDeleted": false,
                                                              "sourceCreatedAt": "2025-06-01T09:00:00Z"
                                                            }
                                                          ]
                                                        }
                                                        """))),
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
    @interface GetFeedDocs {}

    // GET /pending-items/{itemId} (단건 상세 조회)

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "상세 조회 성공",
                content =
                        @Content(
                                schema =
                                        @Schema(
                                                implementation =
                                                        kr.java.documind.global.response.ApiResponse
                                                                .class),
                                examples = {
                                    @ExampleObject(
                                            name = "sourceActive",
                                            summary = "원본 소스 존재 (링크 활성)",
                                            value =
                                                    """
                                                    {
                                                      "success": true,
                                                      "data": {
                                                        "id": 42,
                                                        "sourceId": 7,
                                                        "sourceType": "DOCUMENT",
                                                        "title": "몬스터 밸런스 패치 상세",
                                                        "summary": "몬스터 체력 공격력 수치가 조정되었습니다.",
                                                        "patchType": "CHANGE",
                                                        "status": "PENDING",
                                                        "sourceDeleted": false,
                                                        "sourceCreatedAt": "2025-06-01T09:00:00Z",
                                                        "sourceLink": "/projects/abc123/documents/7"
                                                      }
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "sourceDeleted",
                                            summary = "원본 소스 삭제됨 (링크 비활성)",
                                            value =
                                                    """
                                                    {
                                                      "success": true,
                                                      "data": {
                                                        "id": 43,
                                                        "sourceId": 8,
                                                        "sourceType": "ISSUE",
                                                        "title": "결제 오류 패치",
                                                        "summary": "결제 처리 중 발생하는 오류가 수정되었습니다.",
                                                        "patchType": "FIX",
                                                        "status": "PENDING",
                                                        "sourceDeleted": true,
                                                        "sourceCreatedAt": "2025-05-20T14:00:00Z",
                                                        "sourceLink": null
                                                      }
                                                    }
                                                    """)
                                })),
        @ApiResponse(
                responseCode = "404",
                description = "항목을 찾을 수 없음",
                content =
                        @Content(
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {"success":false,"error":{"message":"Pending Item을 찾을 수 없습니다. id: 42"}}
                                                        """)))
    })
    @interface GetItemDetailDocs {}

    // PATCH /pending-items/{itemId}/exclude (제외)

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "제외 처리 성공",
                content =
                        @Content(
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {"success":true,"message":"패치노트 피드에서 제외되었습니다."}
                                                        """))),
        @ApiResponse(
                responseCode = "400",
                description = "PENDING 상태가 아닌 항목",
                content =
                        @Content(
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {"success":false,"error":{"message":"PENDING 상태의 항목만 제외할 수 있습니다. 현재 상태: EXCLUDED"}}
                                                        """))),
        @ApiResponse(
                responseCode = "404",
                description = "항목을 찾을 수 없음",
                content =
                        @Content(
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {"success":false,"error":{"message":"Pending Item을 찾을 수 없습니다. id: 42"}}
                                                        """)))
    })
    @interface ExcludeItemDocs {}

    // PATCH /pending-items/{itemId}/restore (복원)

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "복원 성공",
                content =
                        @Content(
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {"success":true,"message":"패치노트 피드로 복원되었습니다."}
                                                        """))),
        @ApiResponse(
                responseCode = "400",
                description = "EXCLUDED 상태가 아닌 항목",
                content =
                        @Content(
                                examples = {
                                    @ExampleObject(
                                            name = "NotExcluded",
                                            summary = "EXCLUDED 상태 아님",
                                            value =
                                                    """
                                                    {"success":false,"error":{"message":"EXCLUDED 상태의 항목만 복원할 수 있습니다. 현재 상태: PENDING"}}
                                                    """),
                                    @ExampleObject(
                                            name = "AlreadyCompleted",
                                            summary = "COMPLETED 항목 복원 시도",
                                            value =
                                                    """
                                                    {"success":false,"error":{"message":"EXCLUDED 상태의 항목만 복원할 수 있습니다. 현재 상태: COMPLETED"}}
                                                    """)
                                })),
        @ApiResponse(
                responseCode = "404",
                description = "항목을 찾을 수 없음",
                content =
                        @Content(
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {"success":false,"error":{"message":"Pending Item을 찾을 수 없습니다. id: 42"}}
                                                        """)))
    })
    @interface RestoreItemDocs {}
}
