package com.nhnacademy.bookssearchworker.search.service;

import com.nhnacademy.bookssearchworker.search.component.ai.EmbeddingClient;
import com.nhnacademy.bookssearchworker.search.domain.Book;
import com.nhnacademy.bookssearchworker.search.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookManagementService {

    private final BookRepository bookRepository;
    private final EmbeddingClient embeddingClient; // ✅ 추가

    /**
     * 도서 등록 및 수정
     * - 배포 환경에서 "검색 서비스가 끊기지" 않도록 예외를 던지지 않고 결과를 반환한다.
     * - 저장 시 임베딩도 함께 생성하여 ES 문서에 포함한다. (실패해도 저장은 진행)
     */
    @Transactional // ES는 트랜잭션 대상이 아니지만 기존 설정 유지
    public OperationResult upsertBook(Book book) {

        // 1) 임베딩 생성 시도 (실패해도 저장은 진행)
        try {
            String embedText = buildEmbeddingText(book);
            List<Float> vector = embeddingClient.createEmbedding(embedText);
            List<Double> doubleVector = vector.stream().map(Double::valueOf).toList();

            if (vector != null && !vector.isEmpty()) {
                book.setEmbedding(doubleVector); // ✅ ES에 함께 저장
                log.info("🧠 임베딩 생성 성공: ISBN={}, dim={}", book.getIsbn(), vector.size());
            } else {
                // 빈 벡터면 null로 정리 (ES 매핑/검색 안정성)
                book.setEmbedding(null);
                log.warn("⚠️ 임베딩 결과가 비어있음: ISBN={}", book.getIsbn());
            }

        } catch (Exception e) {
            // EmbeddingClient가 EmbeddingException을 던져도 여기서 잡아서 서비스 안끊기게
            book.setEmbedding(null); // ✅ fallback: 임베딩 없이 저장
            log.warn("⚠️ 임베딩 생성 실패 → 임베딩 없이 저장 진행: ISBN={}", book.getIsbn(), e);
        }

        // 2) ES 저장 (기존 로직 유지)
        boolean ok = bookRepository.save(book);
        if (ok) {
            log.info("✅ 도서 저장 성공: ISBN={}, Title={}", book.getIsbn(), book.getTitle());
            return OperationResult.success("도서 정보가 성공적으로 저장되었습니다.");
        }

        log.warn("⚠️ 도서 저장 실패(ES write failure): ISBN={}", book.getIsbn());
        return OperationResult.failure("도서 저장에 실패했습니다. (ES 저장 오류)");
    }

    @Transactional
    public OperationResult deleteBook(String isbn) {
        boolean ok = bookRepository.deleteById(isbn);
        if (ok) {
            log.info("🗑️ 도서 삭제 성공: ISBN={}", isbn);
            return OperationResult.success("도서가 삭제되었습니다.");
        }
        log.warn("⚠️ 도서 삭제 실패(ES delete failure): ISBN={}", isbn);
        return OperationResult.failure("도서 삭제에 실패했습니다. (ES 삭제 오류)");
    }

    /**
     * 임베딩 입력 텍스트 구성
     * - "네가 만든 로직을 유지" 관점에서: 책 필드들을 단순히 합쳐서 사용 (임의의 고급 가공 X)
     * - 나중에 원하면 여기만 조정하면 됨.
     */
    private String buildEmbeddingText(Book book) {
        StringBuilder sb = new StringBuilder();

        if (book.getTitle() != null && !book.getTitle().isBlank())
            sb.append(book.getTitle()).append("\n");

        if (book.getAuthor() != null && !book.getAuthor().isBlank())
            sb.append(book.getAuthor()).append("\n");

        if (book.getPublisher() != null && !book.getPublisher().isBlank())
            sb.append(book.getPublisher()).append("\n");

        if (book.getCategories() != null && !book.getCategories().isEmpty())
            sb.append(String.join(" / ", book.getCategories())).append("\n");

        if (book.getDescription() != null && !book.getDescription().isBlank())
            sb.append(book.getDescription());

        return sb.toString();
    }

    /**
     * 컨트롤러가 HTTP 응답을 안정적으로 구성할 수 있도록 쓰기 작업 결과를 표준화.
     */
    public record OperationResult(boolean success, String message) {
        public static OperationResult success(String message) { return new OperationResult(true, message); }
        public static OperationResult failure(String message) { return new OperationResult(false, message); }
    }
}
