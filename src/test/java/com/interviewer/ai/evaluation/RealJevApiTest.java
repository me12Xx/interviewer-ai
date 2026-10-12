package com.interviewer.ai.evaluation;

import com.interviewer.ai.service.TypeSafeClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Tag("external-api")
class RealJevApiTest {
    @Test
    void returnsRealScoresForTwoPassages() {
        String apiKey = System.getProperty("typesafe.api-key", System.getenv("TYPESAFE_API_KEY"));
        assertTrue(apiKey != null && !apiKey.isBlank(),
                "TYPESAFE_API_KEY or -Dtypesafe.api-key must contain a real key");

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        String proxyHost = System.getProperty("https.proxyHost");
        if (proxyHost != null && !proxyHost.isBlank()) {
            int proxyPort = Integer.parseInt(System.getProperty("https.proxyPort", "443"));
            factory.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
            System.out.printf("JEV proxy=%s:%d%n", proxyHost, proxyPort);
        }
        RestTemplate template = new RestTemplate(factory);
        AtomicInteger status = new AtomicInteger();
        template.getInterceptors().add((request, body, execution) -> {
            var response = execution.execute(request, body);
            status.set(response.getStatusCode().value());
            return response;
        });
        TypeSafeClient client = new TypeSafeClient(apiKey, true, template);

        String query = "How does a Java HashMap resolve hash collisions?";
        Map<String, Object> state = Map.of(
                "query", query,
                "chunk_0", "Java HashMap stores colliding keys in the same bucket using a linked list, "
                        + "which can become a red-black tree when the bucket is large enough.",
                "chunk_1", "CSS controls the colors, fonts and layout of a web page.");
        String[] criteria = {"not_relevant", "somewhat_relevant", "highly_relevant"};
        Map<String, TypeSafeClient.ScoreQuestion> questions = Map.of(
                "chunk_0", new TypeSafeClient.ScoreQuestion(
                        "Score the relevance of state.chunk_0 to answering state.query.", criteria),
                "chunk_1", new TypeSafeClient.ScoreQuestion(
                        "Score the relevance of state.chunk_1 to answering state.query.", criteria));

        long start = System.nanoTime();
        var response = client.systemOne(state, questions);
        assertNotNull(response, "JEV returned no response; HTTP status=" + status.get()
                + " (0 means no HTTP response). Check the API/network error above; no fallback is used.");
        assertEquals(200, status.get());
        for (String key : questions.keySet()) {
            var answer = response.getScore(key);
            assertNotNull(answer, "Missing JEV answer for " + key);
            assertEquals("score", answer.getType(), "Unexpected answer type for " + key);
            assertNotNull(answer.getScore(), "Missing score for " + key);
            assertTrue(Double.isFinite(answer.getScore())
                    && answer.getScore() >= 0 && answer.getScore() <= criteria.length - 1,
                    "Invalid numeric score for " + key);
            assertNotNull(answer.getConfidence(), "Missing confidence for " + key);
            assertTrue(Double.isFinite(answer.getConfidence())
                    && answer.getConfidence() >= 0 && answer.getConfidence() <= 1,
                    "Invalid confidence for " + key);
            System.out.printf("JEV %s score=%s confidence=%.6f%n",
                    key, answer.getScore(), answer.getConfidence());
        }
        System.out.printf("JEV success http=%d elapsedMs=%d%n",
                status.get(), (System.nanoTime() - start) / 1_000_000);
    }
}
