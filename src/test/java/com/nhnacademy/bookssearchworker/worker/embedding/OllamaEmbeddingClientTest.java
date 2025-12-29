package com.nhnacademy.bookssearchworker.worker.embedding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OllamaEmbeddingClientTest {

    private static OllamaEmbeddingClient newClientWithJsonResponse(String jsonBody, int expectedDim) {
        ExchangeFunction exchange = req -> Mono.just(jsonResponse(jsonBody));
        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost")
                .exchangeFunction(exchange)
                .build();

        OllamaEmbeddingClient client = new OllamaEmbeddingClient(webClient);
        ReflectionTestUtils.setField(client, "embeddingUrl", "/api/embeddings");
        ReflectionTestUtils.setField(client, "model", "test-model");
        ReflectionTestUtils.setField(client, "expectedDim", expectedDim);
        return client;
    }

    private static ClientResponse jsonResponse(String jsonBody) {
        DataBuffer buf = new DefaultDataBufferFactory().wrap(jsonBody.getBytes(StandardCharsets.UTF_8));
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(Flux.just(buf))
                .build();
    }

    @Test
    @DisplayName("embed: schema1 {embedding:[...]} 파싱 + expectedDim 통과")
    void embed_schema1_embeddingKey_ok() {
        String json = """
                {"embedding":[0.1, 0.2, 0.3]}
                """;
        OllamaEmbeddingClient client = newClientWithJsonResponse(json, 3);

        List<Float> vec = client.embed("hello");

        assertEquals(3, vec.size(), "벡터 길이가 expectedDim(3)이어야 합니다.");
        assertEquals(0.1f, vec.get(0), 1e-6, "첫 번째 값 파싱이 틀렸습니다.");
        assertEquals(0.2f, vec.get(1), 1e-6, "두 번째 값 파싱이 틀렸습니다.");
        assertEquals(0.3f, vec.get(2), 1e-6, "세 번째 값 파싱이 틀렸습니다.");
    }

    @Test
    @DisplayName("embed: schema2 {data:[{embedding:[...]}]} 파싱 + expectedDim 통과")
    void embed_schema2_dataEmbedding_ok() {
        String json = """
                {"data":[{"embedding":[1,2,3]}]}
                """;
        OllamaEmbeddingClient client = newClientWithJsonResponse(json, 3);

        List<Float> vec = client.embed("hello");

        assertEquals(List.of(1f,2f,3f), vec, "schema2 응답 파싱이 기대와 다릅니다.");
    }

    @Test
    @DisplayName("embed: text가 null이면 빈 문자열로 처리되며 정상 동작해야 함")
    void embed_nullText_treatedAsEmpty_ok() {
        String json = """
                {"embedding":[9, 8, 7]}
                """;
        OllamaEmbeddingClient client = newClientWithJsonResponse(json, 3);

        List<Float> vec = client.embed(null);

        assertEquals(List.of(9f, 8f, 7f), vec, "null 텍스트 처리 로직이 기대와 다릅니다.");
    }

    @Test
    @DisplayName("embed: resp가 null이면 IllegalStateException")
    void embed_nullResponse_throws() {
        String json = "null";
        OllamaEmbeddingClient client = newClientWithJsonResponse(json, 3);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> client.embed("x"),
                "응답이 null이면 예외가 나야 합니다.");
        assertTrue(ex.getMessage() == null || ex.getMessage().toLowerCase().contains("embedding"),
                "예외 메시지가 너무 예상과 다릅니다: " + ex.getMessage());
    }

    @Test
    @DisplayName("embed: embedding이 빈 배열이면 'embedding is empty' 예외")
    void embed_emptyEmbedding_throws() {
        String json = """
                {"embedding":[]}
                """;
        OllamaEmbeddingClient client = newClientWithJsonResponse(json, 3);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> client.embed("x"),
                "embedding이 비어있으면 예외가 나야 합니다.");
        assertTrue(ex.getMessage().contains("empty"), "empty 예외 메시지가 포함되어야 합니다. actual=" + ex.getMessage());
    }

    @Test
    @DisplayName("embed: expectedDim과 길이가 다르면 예외 (ES 400 방지)")
    void embed_dimMismatch_throws() {
        String json = """
                {"embedding":[0.1, 0.2]}
                """;
        OllamaEmbeddingClient client = newClientWithJsonResponse(json, 3);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> client.embed("x"),
                "벡터 길이가 expectedDim과 다르면 예외가 나야 합니다.");
        assertTrue(ex.getMessage().toLowerCase().contains("dim"),
                "dim 관련 메시지가 포함되어야 합니다. actual=" + ex.getMessage());
    }

    @Test
    @DisplayName("embed: 알 수 없는 응답 스키마면 예외")
    void embed_unknownSchema_throws() {
        String json = """
                {"foo":"bar"}
                """;
        OllamaEmbeddingClient client = newClientWithJsonResponse(json, 3);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> client.embed("x"),
                "Unknown schema면 예외가 나야 합니다.");
        assertTrue(ex.getMessage().toLowerCase().contains("unknown"),
                "Unknown schema 예외 메시지가 포함되어야 합니다. actual=" + ex.getMessage());
    }
}
