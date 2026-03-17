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

public interface IssueRecommendationSwaggerDocs {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "추천 목록 조회 성공",
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
                          "id": 101,
                          "title": "NullPointerException in UserService",
                          "status": "RECOMMENDED",
                          "severity": "HIGH",
                          "severityScore": 85,
                          "assigneeId": "123e4567-e89b-12d3-a456-426614174000",
                          "occurrenceCount": 42,
                          "firstOccurredAt": "2024-03-11T10:00:00Z",
                          "lastOccurredAt": "2024-03-11T15:30:00Z",
                          "createdAt": "2024-03-11T10:00:00Z",
                          "updatedAt": "2024-03-11T15:30:00Z"
                        }
                      ]
                    }
                    """)))
    })
    @interface GetRecommendationListDocs {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "추천 상세 조회 성공",
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
                      "data": {
                        "id": 101,
                        "assigneeId": "123e4567-e89b-12d3-a456-426614174000",
                        "projectId": "123e4567-e89b-12d3-a456-426614174001",
                        "title": "NullPointerException in UserService",
                        "description": "유저 서비스에서 NPE 발생",
                        "fingerprint": "a1b2c3d4e5f6...",
                        "issueType": "BUG",
                        "status": "RECOMMENDED",
                        "priority": null,
                        "severity": "HIGH",
                        "severityScore": 85,
                        "errorType": "NULL_POINTER",
                        "stackKey": "UserService.java:42:getUser",
                        "occurrenceCount": 42,
                        "resolutionNote": null,
                        "firstOccurredAt": "2024-03-11T10:00:00Z",
                        "lastOccurredAt": "2024-03-11T15:30:00Z",
                        "resolvedAt": null,
                        "createdAt": "2024-03-11T10:00:00Z",
                        "updatedAt": "2024-03-11T15:30:00Z"
                      }
                    }
                    """))),
        @ApiResponse(
                responseCode = "404",
                description = "추천 이슈를 찾을 수 없음",
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
                      "success": false,
                      "error": {
                        "message": "추천 이슈를 찾을 수 없습니다: 999"
                      }
                    }
                    """)))
    })
    @interface GetRecommendationDetailDocs {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "승인 성공",
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
                      "message": "추천 이슈가 승인되어 이슈로 생성되었습니다."
                    }
                    """))),
        @ApiResponse(
                responseCode = "400",
                description = "이미 승인/거부된 추천",
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
                      "success": false,
                      "error": {
                        "message": "추천 대기 상태(RECOMMENDED)가 아닙니다. 현재 상태: TODO"
                      }
                    }
                    """)))
    })
    @interface ApproveRecommendationDocs {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "거부 성공",
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
                      "message": "추천 이슈가 거부되었습니다."
                    }
                    """))),
        @ApiResponse(
                responseCode = "400",
                description = "이미 승인/거부된 추천",
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
                      "success": false,
                      "error": {
                        "message": "추천 대기 상태(RECOMMENDED)가 아닙니다. 현재 상태: REJECTED"
                      }
                    }
                    """)))
    })
    @interface RejectRecommendationDocs {}
}
