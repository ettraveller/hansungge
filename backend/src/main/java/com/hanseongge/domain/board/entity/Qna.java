package com.hanseongge.domain.board.entity;

import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 문의글 ({@code qnas}) — 문의게시판과 제품문의가 공유한다.
 *
 * <h2>답변이 달리면 질문을 고칠 수 없다</h2>
 * 답변은 그 시점의 질문에 대한 것이다. 질문이 나중에 바뀌면 답변이 <b>엉뚱한 질문에
 * 붙은 것처럼</b> 남고, 그 상태로 게시판에 공개된다. 삭제도 같은 이유로 막는다.
 *
 * <h2>비밀글</h2>
 * 목록에는 제목이 나가되 본문은 작성자와 관리자만 본다. 남이 열면 403 이 아니라
 * <b>404</b> — 403 은 "여기 글이 있다"를 알려 준다.
 */
@Entity
@Getter
@Table(name = "qnas")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Qna {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 작성자. FK 라 회원이 지워지면 남지 않는다 — 회원 탈퇴는 상태 변경이므로 문제없다 */
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** 제품문의에서 상품을 고른 경우에만 값이 있다 */
    @Column(name = "product_id")
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "board_type", length = 20, nullable = false, updatable = false)
    private QnaBoardType boardType;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "question", nullable = false)
    private String question;

    @Column(name = "secret", nullable = false)
    private boolean secret;

    @Column(name = "answer")
    private String answer;

    /** 답변한 관리자 로그인 아이디. 공개 응답에는 나가지 않는다 */
    @Column(name = "answered_by", length = 60)
    private String answeredBy;

    @Column(name = "answered_at")
    private OffsetDateTime answeredAt;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private Qna(Long userId, QnaBoardType boardType, Long productId,
                String title, String question, boolean secret) {
        this.userId = userId;
        this.boardType = boardType;
        this.productId = productId;
        this.title = title;
        this.question = question;
        this.secret = secret;
        this.viewCount = 0;
        this.active = true;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    public static Qna create(Long userId, QnaBoardType boardType, Long productId,
                             String title, String question, boolean secret) {
        return new Qna(userId, boardType, productId, title, question, secret);
    }

    public void update(String title, String question, boolean secret, Long productId) {
        requireNotAnswered();
        this.title = title;
        this.question = question;
        this.secret = secret;
        this.productId = productId;
        this.updatedAt = OffsetDateTime.now();
    }

    /** 작성자가 지울 때. 행은 남기고 내린다 — 답변 이력이 함께 사라지면 안 된다. */
    public void withdraw() {
        requireNotAnswered();
        this.active = false;
        this.updatedAt = OffsetDateTime.now();
    }

    /** 관리자가 내릴 때. 답변 여부와 무관하다 — 욕설·개인정보 노출은 즉시 내려야 한다. */
    public void hideByAdmin() {
        this.active = false;
        this.updatedAt = OffsetDateTime.now();
    }

    public void answer(String answer, String adminLoginId) {
        this.answer = answer;
        this.answeredBy = adminLoginId;
        this.answeredAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    public void increaseViewCount() {
        this.viewCount++;
    }

    public boolean isAnswered() {
        return answeredAt != null;
    }

    public boolean isOwnedBy(Long candidateUserId) {
        return candidateUserId != null && candidateUserId.equals(userId);
    }

    /**
     * 이 글의 본문을 볼 수 있는가.
     *
     * <p>공개글은 누구나, 비밀글은 작성자와 관리자만. 판단을 <b>엔티티에 두는 이유</b>는
     * 목록·상세·수정 세 곳이 같은 규칙을 써야 하는데, 서비스마다 조건을 다시 쓰면
     * 한 곳이 빠졌을 때 아무도 눈치채지 못하기 때문이다.
     */
    public boolean isReadableBy(Long candidateUserId, boolean admin) {
        return !secret || admin || isOwnedBy(candidateUserId);
    }

    private void requireNotAnswered() {
        if (isAnswered()) {
            throw new BusinessException(ErrorCode.QNA_ALREADY_ANSWERED,
                    "답변 완료된 문의 변경 시도: " + id);
        }
    }
}
