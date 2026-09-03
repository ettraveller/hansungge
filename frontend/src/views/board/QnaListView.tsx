import Link from 'next/link';

import type { Loaded } from '@/api/server';
import { BoardTable, type BoardRow } from '@/components/board/BoardTable';
import { Pager } from '@/components/board/Pager';
import { SectionHead } from '@/components/ui/SectionHead';
import { StatusBadge } from '@/components/ui/StatusBadge';
import type { PageResponse } from '@/types/api';
import {
  QNA_BOARD_LABEL,
  QNA_BOARD_PATH,
  QNA_STATUS_LABEL,
  type QnaBoardType,
  type QnaSummary,
} from '@/types/board';
import { formatBoardDate } from '@/utils/format';
import styles from './BoardPage.module.css';
import board from '@/components/board/board.module.css';

interface Props {
  boardType: QnaBoardType;
  qnas: Loaded<PageResponse<QnaSummary>>;
  page: number;
}

const HEAD: Record<QnaBoardType, { title: string; desc: string }> = {
  GENERAL: {
    title: '문의게시판',
    desc: '주문·배송·매입 등 무엇이든 물어보세요',
  },
  PRODUCT: {
    title: '제품문의',
    desc: '상품의 규격·재고·각인에 관한 질문을 남기실 수 있습니다',
  },
};

/**
 * 문의 목록 (`/support/qna`, `/support/product-qna`).
 *
 * <h2>두 게시판이 한 컴포넌트다</h2>
 * 화면은 둘이지만 표도 규칙도 같다. {@code boardType} 만 다르다.
 *
 * <h2>목록을 공개하는 이유</h2>
 * 다른 사람의 문의와 답변이 보이면 <b>같은 질문을 또 하지 않는다</b> — 게시판을 두는
 * 실질적인 이유가 그것이다. 대신 작성자명은 서버가 가려서 보내고(`김**`), 비밀글은
 * 제목만 보인다.
 */
export function QnaListView({ boardType, qnas, page }: Props) {
  const data = qnas.data;
  const basePath = QNA_BOARD_PATH[boardType];
  const head = HEAD[boardType];

  return (
    <div className={styles.page}>
      <div className="wrap">
        <SectionHead as="h1" title={head.title} desc={head.desc} />

        {/* 두 게시판 사이 이동. 링크라 각 게시판에 고유 URL 이 남는다 */}
        <nav className={styles.boardTabs} aria-label="문의 게시판 선택">
          {(Object.keys(QNA_BOARD_PATH) as QnaBoardType[]).map((type) => (
            <Link
              key={type}
              href={QNA_BOARD_PATH[type]}
              className={`${styles.boardTab} ${type === boardType ? styles.boardTabOn : ''}`}
              aria-current={type === boardType ? 'page' : undefined}
            >
              {QNA_BOARD_LABEL[type]}
            </Link>
          ))}
        </nav>

        {!data ? (
          <p className={styles.loadError}>
            문의 목록을 불러올 수 없습니다. 잠시 후 다시 시도해 주세요.
          </p>
        ) : (
          <>
            <p className={board.count}>
              전체 <strong className="tabular">{data.totalElements}</strong>건
            </p>

            <BoardTable
              caption={`${head.title} 목록`}
              basePath={basePath}
              rows={toRows(data)}
              showAuthor
              emptyText="등록된 문의가 없습니다. 첫 문의를 남겨 보세요."
            />

            <Pager page={page} totalPages={data.totalPages} basePath={basePath} />
          </>
        )}

        <div className={board.actions}>
          <Link href={`${basePath}/write`} className={board.primaryBtn}>
            문의 작성
          </Link>
        </div>
      </div>
    </div>
  );
}

function toRows(data: PageResponse<QnaSummary>): BoardRow[] {
  const firstNo = data.totalElements - data.page * data.size;

  return data.content.map((qna, index) => ({
    id: qna.id,
    title: qna.title,
    no: firstNo - index,
    author: qna.authorName,
    viewCount: qna.viewCount,
    date: formatBoardDate(qna.createdAt),
    secret: qna.secret,
    prefix: qna.productName ?? undefined,
    badge:
      qna.status === 'ANSWERED' ? (
        <StatusBadge tone="success">{QNA_STATUS_LABEL.ANSWERED}</StatusBadge>
      ) : (
        <StatusBadge tone="neutral">{QNA_STATUS_LABEL.PENDING}</StatusBadge>
      ),
  }));
}
