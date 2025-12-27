package com.nhnacademy.bookssearchworker.worker.es;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EsBookDocumentClient {

    private final RestClient restClient;
    private final ObjectMapper om;

    @Value("${booksearch.es.index}")
    private String index;

    public Optional<JsonNode> getSourceById(String isbnId) throws Exception {
        Request req = new Request("GET", "/" + index + "/_source/" + isbnId);
        Response resp = restClient.performRequest(req);
        String json = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
        return Optional.of(om.readTree(json));
    }

    public void updateById(String isbnId, Object partialDoc) throws Exception {
        // doc/doc_as_upsert JSON 확정
        String body = om.writeValueAsString(new UpdateRequest(partialDoc, true));

        Request req = new Request("POST", "/" + index + "/_update/" + isbnId);
        req.setJsonEntity(body);
        restClient.performRequest(req);
    }

    public void deleteById(String isbnId) throws Exception {
        Request req = new Request("DELETE", "/" + index + "/_doc/" + isbnId);
        restClient.performRequest(req);
    }

    private record UpdateRequest(
            @JsonProperty("doc") Object doc,
            @JsonProperty("doc_as_upsert") boolean docAsUpsert
    ) {}
}
