package kr.java.documind.domain.logexplorer.model.enums;

/** WHERE 절 비교 연산자. */
public enum FilterOperator {
    EQ,
    NEQ,
    GT,
    LT,
    GTE,
    LTE,
    BETWEEN,
    NOT_BETWEEN,
    IS_NULL,
    IS_NOT_NULL,
    CONTAINS,
    NOT_CONTAINS,
    STARTS_WITH,
    ENDS_WITH,
    IS_EMPTY,
    IS_NOT_EMPTY,
    ANY_IN,
    NOT_IN;

    public boolean requiresOneValue() {
        return this == EQ
                || this == NEQ
                || this == GT
                || this == LT
                || this == GTE
                || this == LTE
                || this == CONTAINS
                || this == NOT_CONTAINS
                || this == STARTS_WITH
                || this == ENDS_WITH;
    }

    public boolean requiresTwoValues() {
        return this == BETWEEN || this == NOT_BETWEEN;
    }

    public boolean requiresListValues() {
        return this == ANY_IN || this == NOT_IN;
    }

    public boolean requiresNoValue() {
        return this == IS_NULL || this == IS_NOT_NULL || this == IS_EMPTY || this == IS_NOT_EMPTY;
    }
}
