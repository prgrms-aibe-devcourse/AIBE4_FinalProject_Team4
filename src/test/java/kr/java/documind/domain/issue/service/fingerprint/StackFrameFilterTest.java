package kr.java.documind.domain.issue.service.fingerprint;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StackFrameFilter 테스트")
class StackFrameFilterTest {

    private StackFrameFilter filter;

    @BeforeEach
    void setUp() {
        filter = new StackFrameFilter();
    }

    @Test
    @DisplayName("애플리케이션 프레임만 추출 (라이브러리 프레임 제외)")
    void extractAppFramesOnly() {
        // given
        String stackTrace =
                """
                at kr.java.documind.domain.player.service.PlayerService.loadPlayer(PlayerService.java:42)
                at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:186)
                at kr.java.documind.domain.game.controller.GameController.startGame(GameController.java:15)
                at java.base/java.lang.reflect.Method.invoke(Method.java:566)
                at kr.java.documind.domain.inventory.service.InventoryService.addItem(InventoryService.java:28)
                """;

        // when
        List<String> appFrames = filter.extractAppFrames(stackTrace);

        // then
        assertThat(appFrames).hasSize(3);
        assertThat(appFrames)
                .containsExactly(
                        "kr.java.documind.domain.player.service.PlayerService.loadPlayer(PlayerService.java:40)",
                        "kr.java.documind.domain.game.controller.GameController.startGame(GameController.java:10)",
                        "kr.java.documind.domain.inventory.service.InventoryService.addItem(InventoryService.java:20)");
    }

    @Test
    @DisplayName("라인 번호를 10 단위로 반올림")
    void normalizeLineNumbers() {
        // given
        String stackTrace =
                """
                at kr.java.documind.service.TestService.testMethod(TestService.java:42)
                at kr.java.documind.service.TestService.anotherMethod(TestService.java:107)
                at kr.java.documind.service.TestService.thirdMethod(TestService.java:5)
                """;

        // when
        List<String> appFrames = filter.extractAppFrames(stackTrace);

        // then
        assertThat(appFrames).hasSize(3);
        assertThat(appFrames.get(0))
                .isEqualTo("kr.java.documind.service.TestService.testMethod(TestService.java:40)");
        assertThat(appFrames.get(1))
                .isEqualTo(
                        "kr.java.documind.service.TestService.anotherMethod(TestService.java:100)");
        assertThat(appFrames.get(2))
                .isEqualTo("kr.java.documind.service.TestService.thirdMethod(TestService.java:0)");
    }

    @Test
    @DisplayName("람다 표현식 정리")
    void normalizeLambdaExpressions() {
        // given
        String stackTrace =
                """
                at kr.java.documind.service.StreamService.$lambda$0(StreamService.java:25)
                at kr.java.documind.service.StreamService.$lambda$1(StreamService.java:30)
                """;

        // when
        List<String> appFrames = filter.extractAppFrames(stackTrace);

        // then
        assertThat(appFrames).hasSize(2);
        assertThat(appFrames.get(0))
                .isEqualTo("kr.java.documind.service.StreamService.$lambda(StreamService.java:20)");
        assertThat(appFrames.get(1))
                .isEqualTo("kr.java.documind.service.StreamService.$lambda(StreamService.java:30)");
    }

    @Test
    @DisplayName("Spring Framework 프레임 제외")
    void excludeSpringFrameworkFrames() {
        // given
        String stackTrace =
                """
                at kr.java.documind.service.MyService.doWork(MyService.java:10)
                at org.springframework.cglib.proxy.MethodProxy.invoke(MethodProxy.java:218)
                at org.springframework.aop.framework.CglibAopProxy$CglibMethodInvocation.invokeJoinpoint(CglibAopProxy.java:793)
                at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
                """;

        // when
        List<String> appFrames = filter.extractAppFrames(stackTrace);

        // then
        assertThat(appFrames).hasSize(1);
        assertThat(appFrames.get(0))
                .isEqualTo("kr.java.documind.service.MyService.doWork(MyService.java:10)");
    }

    @Test
    @DisplayName("JDK 내부 프레임 제외")
    void excludeJdkFrames() {
        // given
        String stackTrace =
                """
                at kr.java.documind.util.Helper.process(Helper.java:55)
                at java.base/java.lang.Thread.run(Thread.java:829)
                at jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
                at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
                """;

        // when
        List<String> appFrames = filter.extractAppFrames(stackTrace);

        // then
        assertThat(appFrames).hasSize(1);
        assertThat(appFrames.get(0))
                .isEqualTo("kr.java.documind.util.Helper.process(Helper.java:50)");
    }

    @Test
    @DisplayName("null 또는 빈 스택트레이스는 빈 리스트 반환")
    void extractFromNullOrEmpty() {
        assertThat(filter.extractAppFrames(null)).isEmpty();
        assertThat(filter.extractAppFrames("")).isEmpty();
        assertThat(filter.extractAppFrames("No stack trace here")).isEmpty();
    }

    @Test
    @DisplayName("동일한 에러 발생 위치는 동일한 프레임 리스트 생성")
    void extractSameFramesFromSimilarErrors() {
        // given
        String stackTrace1 =
                """
                at kr.java.documind.service.PlayerService.loadPlayer(PlayerService.java:42)
                at kr.java.documind.controller.GameController.start(GameController.java:18)
                """;

        String stackTrace2 =
                """
                at kr.java.documind.service.PlayerService.loadPlayer(PlayerService.java:44)
                at kr.java.documind.controller.GameController.start(GameController.java:18)
                """;

        // when
        List<String> frames1 = filter.extractAppFrames(stackTrace1);
        List<String> frames2 = filter.extractAppFrames(stackTrace2);

        // then - 라인 번호는 10단위 반올림되므로 동일한 프레임으로 인식
        assertThat(frames1).isEqualTo(frames2);
    }

    @Test
    @DisplayName("라인 번호가 없는 프레임도 처리 가능 (Native Method)")
    void handleFramesWithoutLineNumber() {
        // given
        String stackTrace =
                """
                at kr.java.documind.service.NativeService.nativeMethod(Native Method)
                at kr.java.documind.service.NativeService.caller(NativeService.java:10)
                """;

        // when
        List<String> appFrames = filter.extractAppFrames(stackTrace);

        // then
        assertThat(appFrames).hasSize(2);
        assertThat(appFrames.get(0))
                .isEqualTo("kr.java.documind.service.NativeService.nativeMethod(Native Method)");
        assertThat(appFrames.get(1))
                .isEqualTo("kr.java.documind.service.NativeService.caller(NativeService.java:10)");
    }
}
