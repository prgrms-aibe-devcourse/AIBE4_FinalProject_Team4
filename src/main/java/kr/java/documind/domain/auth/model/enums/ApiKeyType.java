package kr.java.documind.domain.auth.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApiKeyType {
    INGEST("수집", "dmi_"),
    QUERY("조회", "dmq_");

    private final String description;
    private final String prefix;
}
