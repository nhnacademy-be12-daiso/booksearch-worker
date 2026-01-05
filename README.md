# Search Service

도서 검색(기본/AI) API와, 도서 데이터 변경(등록/수정/삭제)을 Elasticsearch에 반영하는 **RabbitMQ 기반 Worker**를 함께 담은 Spring Boot 프로젝트입니다.  
검색 API는 **하이브리드 검색(BM25 + Vector)** 흐름을 기반으로 하고, Worker는 **임베딩 생성(Ollama) + ES Upsert/Delete + Retry/DLQ** 처리를 담당합니다.

---

## 기술 스택

### Backend
- Java 21
- Spring Boot
- Spring Web
- Spring WebFlux
- Spring Data Elasticsearch + Elasticsearch Java Client
- Redis
- RabbitMQ (Spring AMQP)
- Docker / Docker Compose

### AI/검색 관련
- 하이브리드 검색: 텍스트 + 벡터 임베딩 기반 후보 검색
- Ollama API: 도서 메타데이터 기반 임베딩 생성 (bge-m3 모델 사용)
- Reranker API: 후보 도서 리랭킹 (검색결과 향상 및 사용자 경험 개선)
- Gemini API 2.5 Flash: 개인 맞춤형 도서 추천 및 간단한 설명 생성

### 기타
- JUnit5, Mockito: 단위 테스트 및 통합 테스트
- SonarQube: 코드 품질 관리 및 테스트 커버리지 분석
- Lombok: 코드 간결화
- Komoran: 한글 형태소 분석기

---

## 주요 기능

### 1. 도서 검색 기능 구현 (Search API)

#### 일반 검색
- 하이브리드 검색(BM25 + Vector)을 통한 유사 도서 검색 기능 제공
  - 도서명, 저자명, 출판사명, 카테고리 등을 기반으로 검색
  - Elasticsearch를 활용한 고성능 검색 기능 제공
  - 할인율 정보 적용을 통한 최종 가격 계산 (Redis 캐싱 활용)
- 검색 결과에 대한 페이징 처리 및 정렬 기능 제공

#### AI 검색
- Ollama API를 활용한 벡터 생성 및 유사도 측정 (bge-m3 모델 사용)
  - 도서명, 저자명, 출판사명 등의 메타데이터 기반 벡터 생성
  - 사용자 검색어와 도서 벡터 간의 유사도 측정
- Reranker API를 활용한 개인 맞춤형 도서 추천 기능 구현
  - 검색결과 향상 및 사용자 경험 개선
  - 책과 검색어 간의 연관도 측정
- Gemini API 2.5 Flash 모델과의 연동을 통한 추천 정확도 향상
  - 사용자가 입력한 검색어 기반으로 관련 도서 추천
  - 추천 도서에 대한 간단한 설명 제공
- Redis를 활용한 AI 검색 결과 캐싱 및 성능 최적화

---

### 2. 도서 데이터 변경 처리 (Worker)

- RabbitMQ 기반 Worker 구현
- 실패 시 재시도 로직 구현 (최대 3회) 및 DLQ 처리

#### 도서 등록 / 수정 처리
- 도서 메타데이터 기반 임베딩 생성 (Ollama API 사용)
- Elasticsearch 문서 Upsert 처리

#### 도서 삭제 처리
- Elasticsearch 문서 삭제 처리

---

### 3. 테스트 전략
- JUnit5와 Mockito를 활용한 단위 테스트 및 통합 테스트 작성
- 주요 기능에 대한 테스트 커버리지 80% 이상 달성
- SonarQube를 활용한 코드 품질 관리 및 지속적인 개선

#### Search API 테스트
- Controller Test
  - 원하는 검색 결과가 반환되는지 검증
- Service Test
  - 검색 로직 및 할인 정책 적용 검증
  - Redis 캐싱 동작 검증
- Component Test
  - Elasticsearch 연동 및 검색 성능 검증
  - Ollama, Reranker, Gemini API 연동 검증

#### Worker 테스트
- RabbitMQ 메시지 처리 검증
  - 도서 등록/수정 메시지 처리 및 ES Upsert 검증
  - 도서 삭제 메시지 처리 및 ES 삭제 검증
- 실패 및 재시도 로직 검증

---
