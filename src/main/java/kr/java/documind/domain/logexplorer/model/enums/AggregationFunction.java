package kr.java.documind.domain.logexplorer.model.enums;

import lombok.Getter;

/** SELECT 집계 함수. */
@Getter
public enum AggregationFunction {
    COUNT("COUNT"),
    COUNT_DISTINCT("COUNT DISTINCT"),
    SUM("SUM"),
    AVG("AVG"),
    MIN("MIN"),
    MAX("MAX");

    private final String sqlFunction;

    AggregationFunction(String sqlFunction) {
        this.sqlFunction = sqlFunction;
    }
}
