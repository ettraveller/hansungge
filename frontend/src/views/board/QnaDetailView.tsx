import Link from 'next/link';

import { StatusBadge } from '@/components/ui/StatusBadge';
import { QNA_BOARD_LABEL, QNA_BOARD_PATH, QNA_STATUS_LABEL, type QnaDetail } from '@/types/board';
import { formatBoardDate, formatDateTime } from '@/utils/format';
import styles from './BoardPage.module.css';
import board from '@/components/board/board.module.css';

interface Props {
  qna: QnaDetail;
  /** 본인 글일 때 수정·삭제를 그린다. 서버 렌더링에서는 판단할 수 없어 기본이 false 다 */
  showOwnerActions?: boolean;
}

/**
 * 문의 상세 본문.
 *
 * <p><b>지시어가 없다</b>({@code 'use client'} 도 서버 전용도 아니다). 공개 문의는
 * 서버가 그려 초기 HTML 에 싣고, 비밀글은 브라우저가 토큰으로 다시 받아 같은 컴포넌트로
 * 그린다. 두 경로가 같은 화면을 만들어야 하므로 훅을 쓰지 않는다.
 */
export function QnaDetailView({ qna, showOwnerActions = false }: Props) {
  const basePath = QNA_BOARD_PATH[qna.boardType];
  const answered = qna.status === 'ANSWERED';

  return (
    <div className={styles.page}>
      <div className="wrap">
        <article className={board.detail}>
          <header className={board.detailHead}>
            <h1 className={board.detailTitle}>
              {qna.productName && <span className={board.prefix}>[{qna.productName}]</span>}
              {qna.title}
              <StatusBadge tone={answered ? 'success' : 'neutral'}>
                {QNA_STATUS_LABEL[answered ? 'ANSWERED' : 'PENDING']}
              </StatusBadge>
            </h1>
            <p className={board.detailMeta}>
              <span>{QNA_BOARD_LABEL[qna.boardType]}</span>
              <span>{qna.authorName}</span>
              <span className="tabular">{formatBoardDate(qna.createdAt)}</span>
              <span className="tabular">조회 {qna.viewCount}</span>
              {qna.secret && <span>비밀글</span>}
            </p>
          </header>

          <div className={board.detailBody}>{qna.question}</div>

          {answered && qna.answer ? (
            <div className={board.answer}>
              <p className={board.answerHead}>
                답변
                {qna.answeredAt && (
                  <span className={board.detailMeta}>{formatDateTime(qna.answeredAt)}</span>
                )}
              </p>
              <div className={board.answerBody}>{qna.answer}</div>
            </div>
          ) : (
            <p className={board.answerWaiting}>
              답변을 준비하고 있습니다. 확인 후 이 자리에 답변을 남겨 드립니다.
            </p>
          )}
        </article>

        <div className={board.actions}>
          <Link href={basePath} className={board.ghostBtn}>
            목록으로
          </Link>

          {/* 답변이 달린 뒤에는 서버가 수정·삭제를 거절한다. 버튼도 그때 사라져야
              고객이 눌렀다가 오류를 보는 일이 없다 */}
          {showOwnerActions && qna.mine && !answered && (
            <span className={board.actionsRight}>
              <Link href={`${basePath}/${qna.id}/edit`} className={board.ghostBtn}>
                수정
              </Link>
            </span>
          )}
        </div>
      </div>
    </div>
  );
}
