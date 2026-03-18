package kr.java.documind.domain.logexplorer.model.enums;

import java.util.Arrays;
import java.util.Optional;
import kr.java.documind.global.exception.InvalidQueryException;
import lombok.Getter;

/** 조회 허용 컬럼 화이트리스트. SQL Injection 방어의 핵심 — 여기에 없는 컬럼은 쿼리에 사용 불가. */
@Getter
public enum QueryableColumn {
    SESSION_ID("session_id", "string", false),
    USER_ID("user_id", "string", false),
    SEVERITY("severity", "string", false),
    EVENT_CATEGORY("event_category", "string", false),
    ARCHIVE("archive", "string", false),
    OCCURRED_AT("occurred_at", "datetime", false),
    TRACE_ID("trace_id", "string", false),
    SPAN_ID("span_id", "string", false),
    ATTRIBUTES("attributes", "jsonb", true),
    RESOURCE("resource", "jsonb", true),

    // 동적 별칭(Alias) 지원을 위한 가상 컬럼
    VIRTUAL_ALIAS("virtual_alias", "alias", false);

    private static final java.util.regex.Pattern JSONB_SEGMENT_PATTERN =
            java.util.regex.Pattern.compile("^[a-zA-Z0-9_]{1,64}$");
    private static final java.util.regex.Pattern ALIAS_PATTERN =
            java.util.regex.Pattern.compile("^[a-zA-Z0-9_]{1,64}$");

    private final String dbName;
    private final String dataType;
    private final boolean jsonb;

    QueryableColumn(String dbName, String dataType, boolean jsonb) {
        this.dbName = dbName;
        this.dataType = dataType;
        this.jsonb = jsonb;
    }

    public static Optional<QueryableColumn> fromDbName(String dbName) {
        return Arrays.stream(values()).filter(c -> c.dbName.equals(dbName)).findFirst();
    }

    /**
     * 컬럼 참조(예: "severity", "attributes.level", "attributes.game.fps")를 파싱해 해당 enum 반환.
     *
     * @throws InvalidQueryException 허용되지 않는 컬럼
     */
    public static QueryableColumn parseColumn(String columnRef) {
        if (columnRef == null || columnRef.isBlank()) {
            throw new InvalidQueryException("컬럼명이 비어있습니다.");
        }
        String baseName = columnRef.contains(".") ? columnRef.split("\\.")[0] : columnRef;

        Optional<QueryableColumn> matchedColumn = fromDbName(baseName);
        if (matchedColumn.isPresent()) {
            return matchedColumn.get();
        }

        // 물리 컬럼이 아니더라도, SQL Injection 방어용 정규식을 통과하고 점(.)이 없는 단일 문자열이라면,
        // 이를 프론트엔드가 보낸 '안전한 별칭'으로 간주하고 가상 컬럼(VIRTUAL_ALIAS)으로 우회 통과시킴
        if (!columnRef.contains(".") && ALIAS_PATTERN.matcher(columnRef).matches()) {
            return VIRTUAL_ALIAS;
        }

        throw new InvalidQueryException("허용되지 않는 컬럼: " + baseName);
    }

    /**
     * JSONB 경로 세그먼트를 추출. 예: "attributes.game.fps" → ["game", "fps"]
     *
     * <p>각 세그먼트를 {@code ^[a-zA-Z0-9_]{1,64}$} 패턴으로 검증한다.
     */
    public static String[] parseJsonbPath(String columnRef) {
        String[] parts = columnRef.split("\\.");
        if (parts.length <= 1) {
            return new String[0];
        }
        String[] path = java.util.Arrays.copyOfRange(parts, 1, parts.length);
        for (String segment : path) {
            if (!JSONB_SEGMENT_PATTERN.matcher(segment).matches()) {
                throw new InvalidQueryException("유효하지 않은 JSONB 경로 세그먼트: " + segment);
            }
        }
        return path;
    }

    /** alias가 허용 패턴인지 검증 */
    public static void validateAlias(String alias) {
        if (alias != null && !alias.isBlank() && !ALIAS_PATTERN.matcher(alias).matches()) {
            throw new InvalidQueryException("유효하지 않은 alias: " + alias);
        }
    }
}
