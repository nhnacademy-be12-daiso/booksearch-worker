package com.nhnacademy.bookssearchworker.worker.es;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicRequestLine;
import org.apache.http.message.BasicStatusLine;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {
        EsBookDocumentClient.class,
        EsBookDocumentClientTest.TestConfig.class
})
@TestPropertySource(properties = {
        "booksearch.es.index=test-index"
})
class EsBookDocumentClientTest {

    @Autowired
    EsBookDocumentClient client;

    @Autowired
    ObjectMapper om;

    @MockitoBean
    RestClient restClient;

    @BeforeEach
    void setUp() {
        reset(restClient);
    }

    @TestConfiguration
    static class TestConfig {
        // RestClient 실제 빈(더미) 만들어두고 -> @MockitoBean이 override
        @Bean
        RestClient restClient() {
            // 실제로 호출 안됨(override되므로). null 반환은 컨텍스트가 싫어해서 더미 생성.
            return RestClient.builder(new HttpHost("localhost", 9200, "http")).build();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    // ---------------- helpers ----------------

    /**
     * org.elasticsearch.client.Response 는 public 생성자가 없을 수 있어서
     * 테스트에서 리플렉션으로 "진짜 Response"를 만든다.
     */
    private static Response newEsResponse(int statusCode, String bodyJson) {
        try {
            ProtocolVersion pv = new ProtocolVersion("HTTP", 1, 1);

            RequestLine requestLine = new BasicRequestLine("GET", "/", pv);
            HttpHost host = new HttpHost("localhost", 9200, "http");

            StatusLine statusLine = new BasicStatusLine(pv, statusCode, "");
            BasicHttpResponse httpResponse = new BasicHttpResponse(statusLine);
            httpResponse.setEntity(new StringEntity(bodyJson, ContentType.APPLICATION_JSON));

            Constructor<Response> ctor =
                    Response.class.getDeclaredConstructor(RequestLine.class, HttpHost.class, HttpResponse.class);
            ctor.setAccessible(true);
            return ctor.newInstance(requestLine, host, httpResponse);
        } catch (NoSuchMethodException e) {
            // 버전에 따라 시그니처가 다를 수 있어서, 터지면 메시지를 선명하게
            throw new AssertionError(
                    "Response 생성자 시그니처가 예상과 다릅니다. " +
                            "elasticsearch-rest-client 버전을 확인하고 Response 생성 방식을 맞춰야 합니다.", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ==========================================================
    // getSourceById()
    // ==========================================================

    @Nested
    @DisplayName("getSourceById()")
    class GetSourceByIdTests {

        @Test
        @DisplayName("성공: _source JSON을 JsonNode로 파싱해 Optional로 반환한다 + 요청 경로 검증")
        void success_returnsParsedJson_andVerifiesPath() throws Exception {
            // given
            String isbnId = "9780000000001";
            String respJson = "{\"title\":\"abc\",\"price\":12000}";

            when(restClient.performRequest(any(Request.class)))
                    .thenReturn(newEsResponse(200, respJson));

            // when
            Optional<JsonNode> result = client.getSourceById(isbnId);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().get("title").asText()).isEqualTo("abc");
            assertThat(result.get().get("price").asInt()).isEqualTo(12000);

            // request 검증 (정확히 어디가 문제인지 보이게)
            var captor = org.mockito.ArgumentCaptor.forClass(Request.class);
            verify(restClient, times(1)).performRequest(captor.capture());

            Request sent = captor.getValue();
            assertThat(sent.getMethod()).as("HTTP method").isEqualTo("GET");
            assertThat(sent.getEndpoint()).as("endpoint path")
                    .isEqualTo("/test-index/_source/" + isbnId);
        }

        @Test
        @DisplayName("예외 전파: RestClient가 예외를 던지면 그대로 throws 된다")
        void throws_whenRestClientFails() throws Exception {
            when(restClient.performRequest(any(Request.class)))
                    .thenThrow(new RuntimeException("ES down"));

            assertThatThrownBy(() -> client.getSourceById("978X"))
                    .as("getSourceById는 예외를 잡지 않고 던져야 함")
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("ES down");

            verify(restClient, times(1)).performRequest(any(Request.class));
        }
    }

    // ==========================================================
    // updateById()
    // ==========================================================

    @Nested
    @DisplayName("updateById()")
    class UpdateByIdTests {

        @Test
        @DisplayName("성공: POST /{index}/_update/{id} + body에 doc/doc_as_upsert가 들어간다")
        void success_sendsUpdateRequest_withExpectedBody() throws Exception {
            // given
            String isbnId = "9780000000002";
            Map<String, Object> partialDoc = Map.of("title", "new-title", "stock", 3);

            // RestClient.performRequest는 Response를 반환하지만 updateById는 반환값을 쓰진 않음
            when(restClient.performRequest(any(Request.class)))
                    .thenReturn(newEsResponse(200, "{\"result\":\"updated\"}"));

            // when
            client.updateById(isbnId, partialDoc);

            // then
            var captor = org.mockito.ArgumentCaptor.forClass(Request.class);
            verify(restClient, times(1)).performRequest(captor.capture());

            Request sent = captor.getValue();
            assertThat(sent.getMethod()).as("HTTP method").isEqualTo("POST");
            assertThat(sent.getEndpoint()).as("endpoint path")
                    .isEqualTo("/test-index/_update/" + isbnId);

            // body 검증: 문자열 그대로 비교하면 순서 때문에 깨질 수 있으니 JsonNode로 파싱해서 비교
            String body = sent.getEntity().toString(); // 내부 구현상 StringEntity면 toString에 요약이 섞일 수 있음
            // 그래서 안전하게 "StringEntity로 들어간 JSON을 다시 만든다" 방식 대신,
            // Request.getEntity()가 HttpEntity면 content를 읽는 방식으로 검증한다.
            HttpEntity entity = sent.getEntity();
            assertThat(entity).as("update request must have body").isNotNull();

            String json = org.apache.http.util.EntityUtils.toString(entity, StandardCharsets.UTF_8);
            JsonNode node = om.readTree(json);

            assertThat(node.has("doc")).as("body must contain 'doc'").isTrue();
            assertThat(node.get("doc").get("title").asText()).isEqualTo("new-title");
            assertThat(node.get("doc").get("stock").asInt()).isEqualTo(3);

            assertThat(node.has("doc_as_upsert")).as("body must contain 'doc_as_upsert'").isTrue();
            assertThat(node.get("doc_as_upsert").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("예외 전파: update 중 RestClient 예외는 그대로 throws")
        void throws_whenRestClientFails() throws Exception {
            when(restClient.performRequest(any(Request.class)))
                    .thenThrow(new RuntimeException("update failed"));

            assertThatThrownBy(() -> client.updateById("978Y", Map.of("a", 1)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("update failed");

            verify(restClient, times(1)).performRequest(any(Request.class));
        }
    }

    // ==========================================================
    // deleteById()
    // ==========================================================

    @Nested
    @DisplayName("deleteById()")
    class DeleteByIdTests {

        @Test
        @DisplayName("성공: DELETE /{index}/_doc/{id} 요청을 보낸다")
        void success_sendsDeleteRequest() throws Exception {
            when(restClient.performRequest(any(Request.class)))
                    .thenReturn(newEsResponse(200, "{\"result\":\"deleted\"}"));

            String isbnId = "9780000000003";
            client.deleteById(isbnId);

            var captor = org.mockito.ArgumentCaptor.forClass(Request.class);
            verify(restClient, times(1)).performRequest(captor.capture());

            Request sent = captor.getValue();
            assertThat(sent.getMethod()).as("HTTP method").isEqualTo("DELETE");
            assertThat(sent.getEndpoint()).as("endpoint path")
                    .isEqualTo("/test-index/_doc/" + isbnId);
        }

        @Test
        @DisplayName("예외 전파: delete 중 RestClient 예외는 그대로 throws")
        void throws_whenRestClientFails() throws Exception {
            when(restClient.performRequest(any(Request.class)))
                    .thenThrow(new RuntimeException("delete failed"));

            assertThatThrownBy(() -> client.deleteById("978Z"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("delete failed");

            verify(restClient, times(1)).performRequest(any(Request.class));
        }
    }
}
