package kr.java.documind.domain.patchnote.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import kr.java.documind.domain.issue.model.enums.IssueType;
import kr.java.documind.domain.patchnote.model.enums.PatchType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("PatchTypeResolver 단위 테스트")
class PatchTypeResolverTest {

    private final PatchTypeResolver resolver = new PatchTypeResolver();

    @Nested
    @DisplayName("resolveFromIssueType()")
    class ResolveFromIssueType {

        @Test
        @DisplayName("PatchType 결정: issueType=null → FIX")
        void resolveFromIssueType_null_FIX반환() {
            // Given
            IssueType issueType = null;

            // When
            PatchType result = resolver.resolveFromIssueType(issueType);

            // Then
            assertThat(result).isEqualTo(PatchType.FIX);
        }

        @ParameterizedTest(name = "issueType={0} → FIX")
        @MethodSource("fixIssueTypes")
        @DisplayName("PatchType 결정: BUG/CRASH/DATA_INCONSISTENCY/SECURITY/PAYMENT/UNKNOWN → FIX")
        void resolveFromIssueType_FIX계열_FIX반환(IssueType issueType) {
            // Given & When
            PatchType result = resolver.resolveFromIssueType(issueType);

            // Then
            assertThat(result).isEqualTo(PatchType.FIX);
        }

        static Stream<IssueType> fixIssueTypes() {
            return Stream.of(
                    IssueType.BUG,
                    IssueType.CRASH,
                    IssueType.DATA_INCONSISTENCY,
                    IssueType.SECURITY,
                    IssueType.PAYMENT,
                    IssueType.UNKNOWN);
        }

        @ParameterizedTest(name = "issueType={0} → CHANGE")
        @MethodSource("changeIssueTypes")
        @DisplayName("PatchType 결정: PERFORMANCE/NETWORK/BALANCE/UX → CHANGE")
        void resolveFromIssueType_CHANGE계열_CHANGE반환(IssueType issueType) {
            // Given & When
            PatchType result = resolver.resolveFromIssueType(issueType);

            // Then
            assertThat(result).isEqualTo(PatchType.CHANGE);
        }

        static Stream<IssueType> changeIssueTypes() {
            return Stream.of(
                    IssueType.PERFORMANCE,
                    IssueType.NETWORK,
                    IssueType.BALANCE,
                    IssueType.UX);
        }

        @ParameterizedTest(name = "issueType={0} → MAINTENANCE")
        @MethodSource("maintenanceIssueTypes")
        @DisplayName("PatchType 결정: DEPENDENCY/CONFIGURATION → MAINTENANCE")
        void resolveFromIssueType_MAINTENANCE계열_MAINTENANCE반환(IssueType issueType) {
            // Given & When
            PatchType result = resolver.resolveFromIssueType(issueType);

            // Then
            assertThat(result).isEqualTo(PatchType.MAINTENANCE);
        }

        static Stream<IssueType> maintenanceIssueTypes() {
            return Stream.of(IssueType.DEPENDENCY, IssueType.CONFIGURATION);
        }
    }
}
