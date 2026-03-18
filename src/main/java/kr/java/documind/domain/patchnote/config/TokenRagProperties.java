package kr.java.documind.domain.patchnote.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.patchnote.rag")
public record TokenRagProperties(int tokenLimit, int promptOverhead) {}
