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

/** PatchNoteApiController Swagger 응답 명세. */
public interface PatchNoteSwaggerDocs {

    // POST /patch-note (저장)

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "저장 성공",
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
                                                        {"success":true,"data":{"id":1}}
                                                        """))),
        @ApiResponse(
                responseCode = "400",
                description = "입력값 오류",
                content =
                        @Content(
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {"success":false,"error":{"message":"제목은 필수입니다."}}
                                                        """))),
        @ApiResponse(
                responseCode = "409",
                description = "버전 중복",
                content =
                        @Content(
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {"success":false,"error":{"message":"이미 존재하는 버전입니다. v1.2.0"}}
                                                        """)))
    })
    @interface SavePatchNoteDocs {}

    // GET /patch-note (목록)

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "목록 조회 성공",
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
                                                              "title": "v1.2.0 업데이트",
                                                              "versionLabel": "v1.2.0",
                                                              "status": "DRAFT",
                                                              "createdAt": "2025-06-01T09:00:00Z"
                                                            }
                                                          ]
                                                        }
                                                        """)))
    })
    @interface ListPatchNotesDocs {}

    // GET /patch-note/{id} (단건)

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "단건 조회 성공",
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
                                                            "id": 1,
                                                            "title": "v1.2.0 업데이트",
                                                            "content": "## 수정\\n- 결제 오류가 수정되었습니다.",
                                                            "versionLabel": "v1.2.0",
                                                            "majorVersion": 1,
                                                            "minorVersion": 2,
                                                            "patchVersion": 0,
                                                            "status": "DRAFT",
                                                            "createdAt": "2025-06-01T09:00:00Z"
                                                          }
                                                        }
                                                        """))),
        @ApiResponse(
                responseCode = "404",
                description = "패치노트 없음",
                content =
                        @Content(
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {"success":false,"error":{"message":"패치노트를 찾을 수 없습니다. id: 1"}}
                                                        """)))
    })
    @interface GetPatchNoteDetailDocs {}

    // DELETE /patch-note/{id} (soft delete)

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "삭제 성공",
                content =
                        @Content(
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {"success":true,"data":"패치노트가 삭제되었습니다."}
                                                        """))),
        @ApiResponse(
                responseCode = "404",
                description = "패치노트 없음",
                content =
                        @Content(
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {"success":false,"error":{"message":"패치노트를 찾을 수 없습니다. id: 1"}}
                                                        """)))
    })
    @interface DeletePatchNoteDocs {}
}
