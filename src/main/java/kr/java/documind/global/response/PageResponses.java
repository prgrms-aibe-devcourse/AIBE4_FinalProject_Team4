package kr.java.documind.global.response;

import java.util.List;
import org.springframework.data.domain.Page;

public final class PageResponses {

    private PageResponses() {}

    public static <T> ApiResponse<List<T>> of(Page<T> page) {
        return ApiResponse.success(page.getContent(), PageMeta.from(page));
    }
}
