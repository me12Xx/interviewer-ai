package com.interviewer.ai.config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PromptLoader {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() throws IOException {
        String common = load("prompt/common.md");
        for (int stage = 1; stage <= 3; stage++) {
            String stageContent = load("prompt/stage" + stage + ".md");
            for (String position : List.of("java", "ai")) {
                String positionContent = load("prompt/position-" + position + ".md");
                cache.put(stage + "-" + position,
                        common + "\n\n" + stageContent + "\n\n" + positionContent);
            }
        }
    }

    public String getPrompt(int stage, String position) {
        String key = stage + "-" + position;
        String prompt = cache.get(key);
        if (prompt == null) {
            throw new IllegalArgumentException(
                    "Unknown stage/position combination: " + key +
                    "。合法值: stage=1/2/3, position=java/ai");
        }
        return prompt;
    }

    private String load(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
