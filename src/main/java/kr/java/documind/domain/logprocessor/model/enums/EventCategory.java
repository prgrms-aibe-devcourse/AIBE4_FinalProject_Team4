package kr.java.documind.domain.logprocessor.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 게임 로그 이벤트 카테고리를 정의하는 Enum
 *
 * <p>게임 내에서 발생한 사건의 종류를 분류
 */
public enum EventCategory {
    SYSTEM("시스템", "SYSTEM"), // 부팅, 종료, 크래시, OS 시그널
    ERROR("에러", "ERROR"), // 예외, 런타임 오류, 핸들링 실패
    PERFORMANCE("성능", "PERFORMANCE"), // FPS, 메모리, 프레임타임, 온도
    NETWORK("네트워크", "NETWORK"), // 연결 상태, RTT, 타임아웃, 패킷 손실

    GAMEPLAY("게임플레이", "GAMEPLAY"), // 장르 공통 플레이 이벤트 상위 도메인
    COMBAT("전투", "COMBAT"), // 공격, 피격, 데미지 계산
    SKILL("스킬", "SKILL"), // 스킬 사용, 쿨타임, 버프/디버프
    ITEM("아이템", "ITEM"), // 획득, 사용, 장착, 강화
    QUEST("퀘스트", "QUEST"), // 수락, 완료, 실패
    STAGE("스테이지", "STAGE"), // 던전 입장, 클리어, 실패
    MATCH("매치", "MATCH"), // 매칭 시작/완료/취소
    SOCIAL("소셜", "SOCIAL"), // 친구, 파티, 길드, 채팅

    ECONOMY("재화", "ECONOMY"), // 골드, 재화 획득/소비
    PAYMENT("결제", "PAYMENT"), // 인앱 결제, PG 응답

    AUTH("인증", "AUTH"), // 로그인, 토큰 갱신
    ADMIN("관리", "ADMIN"), // 운영툴, GM 액션
    CHEAT("치트", "CHEAT"); // 비정상 행위 탐지

    private final String description;
    private final String value;

    EventCategory(String description, String value) {
        this.description = description;
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Enum을 JSON으로 직렬화할 때 사용할 값
     *
     * @return 이벤트 카테고리 영문 코드 (대문자)
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * JSON 역직렬화 및 String → Enum 변환 (대소문자 무시)
     *
     * @param value 이벤트 카테고리 문자열 (영문 또는 한글)
     * @return EventCategory enum
     * @throws IllegalArgumentException 지원하지 않는 카테고리인 경우
     */
    @JsonCreator
    public static EventCategory fromString(String value) {
        if (value == null || value.isBlank()) {
            return SYSTEM; // 기본값
        }

        for (EventCategory category : EventCategory.values()) {
            if (category.value.equalsIgnoreCase(value.trim())
                    || category.description.equals(value.trim())) {
                return category;
            }
        }

        throw new IllegalArgumentException(
                "Unknown event category: '"
                        + value
                        + "'. Supported values: SYSTEM(시스템), ERROR(에러), PERFORMANCE(성능), "
                        + "NETWORK(네트워크), GAMEPLAY(게임플레이), COMBAT(전투), SKILL(스킬), "
                        + "ITEM(아이템), QUEST(퀘스트), STAGE(스테이지), MATCH(매치), SOCIAL(소셜), "
                        + "ECONOMY(재화), PAYMENT(결제), AUTH(인증), ADMIN(관리), CHEAT(치트)");
    }

    /**
     * DB에 저장할 때 사용 (VARCHAR 컬럼)
     *
     * @return 이벤트 카테고리 영문 코드 (대문자)
     */
    @Override
    public String toString() {
        return value;
    }
}
