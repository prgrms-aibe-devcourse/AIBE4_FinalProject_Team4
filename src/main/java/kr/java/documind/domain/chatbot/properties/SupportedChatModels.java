package kr.java.documind.domain.chatbot.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.chat")
public record SupportedChatModels(
        /** RAG 챗봇 기본 모델 alias (사용자가 모델을 지정하지 않았을 때 사용) */
        String defaultModel,
        /** 패치노트 생성 기본 모델 alias (초안 생성·이슈·문서 요약에 공통 사용) */
        String patchnoteDefaultModel,
        List<ChatModelInfo> models) {}
