package com.nhnacademy.bookssearchworker.search.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.WriteTypeHint;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // ES에 있는데 여기 없는 필드(review_content 등) 무시
@Document(indexName = "books", createIndex = false, writeTypeHint = WriteTypeHint.FALSE)
public class Book {

    @Id
    private String id; // ES의 _id로 사용됨

    @Field(type = FieldType.Keyword)
    private String isbn;

    @Field(type = FieldType.Text, analyzer = "korean_analyzer")
    private String title;

    // JSON 매핑과 일치시키기 위해 Keyword로 변경 (이름 검색 필요시 Text 권장)
    @Field(type = FieldType.Keyword)
    private String author;

    @Field(type = FieldType.Keyword)
    private String publisher;

    @Field(type = FieldType.Text, analyzer = "korean_analyzer")
    private String description;

    // JSON 데이터가 List 형태이므로 자동으로 매핑됨
    @Field(type = FieldType.Keyword)
    private List<String> categories;

    // 날짜 포맷 지정
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd")
    @JsonSerialize(using = LocalDateSerializer.class)
    @JsonDeserialize(using = LocalDateDeserializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate pubDate;

    @Field(type = FieldType.Integer)
    private Integer price;

    // ES 필드명(book_vector)과 매핑
    @Field(name = "book_vector", type = FieldType.Dense_Vector, dims = 1024, index = true)
    private List<Double> embedding;

    // ES 필드명(image_url)과 매핑
    @Field(name = "image_url", type = FieldType.Keyword, index = false)
    @JsonProperty("image_url") // Jackson이 JSON 파싱할 때도 인식하게 함
    private String imageUrl;

    @Field(name = "publisherId", type = FieldType.Long)
    @JsonProperty("publisherId")
    @JsonAlias({"publisher_id"})
    private Long publisherId;

    @Field(name = "categoryId", type = FieldType.Long)
    @JsonProperty("categoryId")
    @JsonAlias({"category_id"})
    private Long categoryId;
}