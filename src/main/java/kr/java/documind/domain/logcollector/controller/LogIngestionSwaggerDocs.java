package kr.java.documind.domain.logcollector.controller;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public interface LogIngestionSwaggerDocs {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "로그 수집 접수 성공 (비동기 처리)",
            content = @Content(
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(
                    name = "Success",
                    summary = "수집 성공",
                    value = """
                    {
                      "success": true,
                      "message": "로그 수집이 성공적으로 접수되었습니다."
                    }
                    """
                )
            )),

        @ApiResponse(responseCode = "401", description = "인증 실패",
            content = @Content(
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(
                    name = "Unauthorized",
                    summary = "유효하지 않은 API Key",
                    value = """
                    {
                      "success": false,
                      "error": {
                        "message": "유효하지 않거나 정지된 API Key입니다."
                      }
                    }
                    """
                )
            )),

        @ApiResponse(responseCode = "429", description = "요청 한도 초과 (Rate Limit)",
            content = @Content(
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(
                    name = "TooManyRequests",
                    summary = "초당 요청 횟수 초과",
                    value = """
                    {
                      "success": false,
                      "error": {
                        "message": "요청 한도를 초과했습니다."
                      }
                    }
                    """
                )
            )),

        @ApiResponse(responseCode = "503", description = "일시적인 서비스 장애 (Redis 등 인프라 이슈)",
            content = @Content(
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(
                    name = "ServiceUnavailable",
                    summary = "Redis 연결 실패로 인한 재시도 요청",
                    value = """
                    {
                      "success": false,
                      "error": {
                        "message": "일시적인 서비스 장애가 발생했습니다. 잠시 후 다시 시도해주세요."
                      }
                    }
                    """
                )
            ))
    })
    @interface IngestLogDocs {
    }
}
