package kr.java.documind.domain.issue.controller;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public interface IssueManagementSwaggerDocs {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "담당자 지정 성공",
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
                      "message": "담당자가 지정되었습니다."
                    }
                    """))),
        @ApiResponse(
                responseCode = "404",
                description = "이슈를 찾을 수 없음",
                content =
                        @Content(
                                schema =
                                        @Schema(
                                                implementation =
                                                        kr.java.documind.global.response.ApiResponse
                                                                .class),
                                examples =
                                        @ExampleObject(
                                                name = "NotFound",
                                                summary = "존재하지 않는 이슈 ID",
                                                value =
                                                        """
                    {
                      "success": false,
                      "error": {
                        "message": "이슈를 찾을 수 없습니다: 999"
                      }
                    }
                    """)))
    })
    @interface AssignIssueDocs {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "상태 변경 성공",
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
                      "message": "이슈 상태가 변경되었습니다."
                    }
                    """))),
        @ApiResponse(
                responseCode = "400",
                description = "잘못된 요청",
                content =
                        @Content(
                                schema =
                                        @Schema(
                                                implementation =
                                                        kr.java.documind.global.response.ApiResponse
                                                                .class),
                                examples = {
                                    @ExampleObject(
                                            name = "InvalidTransition",
                                            summary = "허용되지 않는 상태 전환",
                                            value =
                                                    """
                    {
                      "success": false,
                      "error": {
                        "message": "허용되지 않는 상태 전환입니다: TODO → RESOLVED"
                      }
                    }
                    """),
                                    @ExampleObject(
                                            name = "SameStatus",
                                            summary = "동일한 상태로 전환 시도",
                                            value =
                                                    """
                    {
                      "success": false,
                      "error": {
                        "message": "현재 상태와 동일한 상태로는 변경할 수 없습니다: IN_PROGRESS"
                      }
                    }
                    """)
                                })),
        @ApiResponse(
                responseCode = "404",
                description = "이슈를 찾을 수 없음",
                content =
                        @Content(
                                schema =
                                        @Schema(
                                                implementation =
                                                        kr.java.documind.global.response.ApiResponse
                                                                .class),
                                examples =
                                        @ExampleObject(
                                                name = "NotFound",
                                                summary = "존재하지 않는 이슈 ID",
                                                value =
                                                        """
                    {
                      "success": false,
                      "error": {
                        "message": "이슈를 찾을 수 없습니다: 999"
                      }
                    }
                    """)))
    })
    @interface UpdateStatusDocs {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "이력 조회 성공",
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
                          "id": 1,
                          "issueId": 101,
                          "modifierId": "123e4567-e89b-12d3-a456-426614174000",
                          "fieldName": "STATUS",
                          "beforeValue": "TODO",
                          "afterValue": "IN_PROGRESS",
                          "createdAt": "2024-03-11T10:30:00Z"
                        }
                      ]
                    }
                    """))),
        @ApiResponse(
                responseCode = "404",
                description = "이슈를 찾을 수 없음",
                content =
                        @Content(
                                schema =
                                        @Schema(
                                                implementation =
                                                        kr.java.documind.global.response.ApiResponse
                                                                .class),
                                examples =
                                        @ExampleObject(
                                                name = "NotFound",
                                                summary = "존재하지 않는 이슈 ID",
                                                value =
                                                        """
                    {
                      "success": false,
                      "error": {
                        "message": "이슈를 찾을 수 없습니다: 999"
                      }
                    }
                    """)))
    })
    @interface GetHistoriesDocs {}
}
