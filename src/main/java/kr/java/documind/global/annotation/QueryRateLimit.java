package kr.java.documind.global.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그 탐색기 쿼리 API에 Rate Limit을 적용하는 마커 어노테이션.
 *
 * <p>{@code QueryRateLimitInterceptor}가 이 어노테이션이 붙은 핸들러에 Rate Limit을 적용한다. projectId 기반으로 버킷을 관리한다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface QueryRateLimit {}
