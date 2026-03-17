package kr.java.documind.global.config;

import kr.java.documind.domain.archive.vector.infrastructure.LineOverlapTextSplitter;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EtlConfig {

    @Bean
    public DocumentTransformer documentTransformer() {
        return new LineOverlapTextSplitter(1500, 3);
    }
}
