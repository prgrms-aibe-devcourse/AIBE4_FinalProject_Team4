package kr.java.documind.global.config;

import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EtlConfig {

    @Bean
    public DocumentTransformer documentTransformer() {
        return new TokenTextSplitter();
    }
}
