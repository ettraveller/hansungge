# domain/order

- 책임: ★ 주문, OrderItem Snapshot, 구성요소 스냅샷, 조정
- 착수: STEP 13

## 하위 계층 (구현 시 생성)

```
order/
├── controller/   REST 엔드포인트. 위임만 한다. 비즈니스 로직 금지
├── service/      비즈니스 로직 전용. @Transactional 경계
├── repository/   Spring Data JPA + QueryDSL
├── entity/       JPA 엔티티. API 로 직접 반환 금지
└── dto/          요청/응답 DTO (record)
```

참조 구현: `domain/setting` (조회·캐시·갱신), `domain/admin` (엔티티·부트스트랩).
