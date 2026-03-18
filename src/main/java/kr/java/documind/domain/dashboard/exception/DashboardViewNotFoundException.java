package kr.java.documind.domain.dashboard.exception;

import kr.java.documind.global.exception.NotFoundException;

public class DashboardViewNotFoundException extends NotFoundException {

    public DashboardViewNotFoundException(String id) {
        super("대시보드 뷰를 찾을 수 없습니다: " + id);
    }
}
