'use client';

import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { Notice } from '@/components/auth/Notice';
import { MyPageShell, myPageStyles as shell } from '@/layouts/MyPageShell';
import { qnaApi } from '@/api/board';
import { useUserSession } from '@/hooks/useUserSession';
import { ApiError } from '@/types/api';
import {
  QNA_BOARD_LABEL,
  QNA_BOARD_PATH,
  QNA_STATUS_LABEL,
  type QnaBoardType,
  type QnaSummary,
} from '@/types/board';
import { formatBoardDate } from '@/utils/format';
import styles from '@/views/MyInquiryView.module.css';

/**
 * 마이페이지 「내 문의」.
 *
 * <h2>왜 마이페이지 안에 두나</h2>
 * 전에는 왼쪽 메뉴의 「1:1 문의」·「상품 문의」가 곧바로 게시판({@code /support/...})으로
 * 나갔다. 그러면 <b>왼쪽 메뉴가 통째로 사라져</b> 마이페이지를 벗어난 것처럼 보이고,
 * 돌아오려면 뒤로가기밖에 없다.
 *
 * <p>내가 쓴 글은 게시판이 아니라 <b>내 계정에 속한 정보</b>이기도 하다. 비밀글로 쓴 문의는
 * 목록에서 제목만 보이므로, 내가 뭘 물었고 답이 왔는지 확인할 곳이 따로 있어야 한다.
 *
 * <p>여기서는 <b>내 글만</b> 본다. 남의 문의까지 보려면 게시판으로 가는 링크를 따로 둔다.
 */

const TABS: { key: 'ALL' | QnaBoardType; name: string }[] = [
  { key: 'ALL', name: '전체' },
  { key: 'GENERAL', name: '1:1 문의' },
  { key: 'PRODUCT', name: '상품 문의' },
];

export function MyInquiryView() {
  return (
    <MyPageShell title="내 문의" lead="답변이 오면 여기에 표시됩니다." showSummary={false}>
      <InquiryList />
    </MyPageShell>
  );
}

function InquiryList() {
  const { isAuthenticated } = useUserSession();
  const [tab, setTab] = useState<'ALL' | QnaBoardType>('ALL');
  const [page, setPage] = useState(0);

  const { data, isPending, error } = useQuery({
    queryKey: ['qna', 'mine', page],
    queryFn: () => qnaApi.mine({ page, size: 20 }),
    enabled: isAuthenticated,
  });

  if (isPending) {
    return <p className={shell.loading}>불러오는 중…</p>;
  }

  if (error) {
    return (
      <Notice tone="error" title="문의를 불러오지 못했습니다">
        {error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요.'}
      </Notice>
    );
  }

  const all = data?.content ?? [];
  const shown = tab === 'ALL' ? all : all.filter((q) => q.boardType === tab);

  return (
    <>
      <div className={shell.tabs} role="tablist" aria-label="문의 종류">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            role="tab"
            aria-selected={t.key === tab}
            className={t.key === tab ? `${shell.tab} ${shell.tabOn}` : shell.tab}
            onClick={() => setTab(t.key)}
          >
            {t.name}
            <span className={shell.tabCount}>
              {t.key === 'ALL' ? all.length : all.filter((q) => q.boardType === t.key).length}
            </span>
          </button>
        ))}
      </div>

      {shown.length === 0 ? (
        <div className={shell.empty}>
          <p>{all.length === 0 ? '아직 남기신 문의가 없습니다.' : '이 종류의 문의가 없습니다.'}</p>
          <div className={shell.emptyAction}>
            <Link className={shell.pillDark} href={`${QNA_BOARD_PATH.GENERAL}/write`}>
              문의하기
            </Link>
          </div>
        </div>
      ) : (
        <>
          <div>
            {shown.map((qna) => (
              <InquiryRow key={qna.id} qna={qna} />
            ))}
          </div>

          <div className={styles.foot}>
            <Link className={shell.pillDark} href={`${QNA_BOARD_PATH.GENERAL}/write`}>
              문의하기
            </Link>
            {/* 남의 문의까지 보려면 게시판으로. 여기는 내 글만 있다 */}
            <Link className={shell.pill} href={QNA_BOARD_PATH.GENERAL}>
              문의게시판 전체 보기
            </Link>
          </div>
        </>
      )}

      {data && data.totalPages > 1 && (
        <div className={styles.pager}>
          <button
            type="button"
            className={shell.pill}
            disabled={data.page === 0}
            onClick={() => setPage((p) => Math.max(p - 1, 0))}
          >
            이전
          </button>
          <span className={styles.pagerNow}>
            {data.page + 1} / {data.totalPages}
          </span>
          <button
            type="button"
            className={shell.pill}
            disabled={data.last}
            onClick={() => setPage((p) => p + 1)}
          >
            다음
          </button>
        </div>
      )}
    </>
  );
}

function InquiryRow({ qna }: { qna: QnaSummary }) {
  const answered = qna.status === 'ANSWERED';

  return (
    <article className={styles.row}>
      <div className={styles.head}>
        <strong className={answered ? `${styles.state} ${styles.stateOk}` : styles.state}>
          {QNA_STATUS_LABEL[qna.status]}
        </strong>
        <span className={styles.kind}>{QNA_BOARD_LABEL[qna.boardType]}</span>
        <span className={styles.when}>{formatBoardDate(qna.createdAt)}</span>
      </div>

      <Link href={`${QNA_BOARD_PATH[qna.boardType]}/${qna.id}`} className={styles.title}>
        {/* 비밀글은 목록에서 제목만 보인다. 자물쇠로 그 사실을 알린다 */}
        {qna.secret && (
          <span className={styles.lock} aria-label="비밀글">
            <svg viewBox="0 0 16 16" aria-hidden="true">
              <rect x="3.5" y="7" width="9" height="6.5" rx="1" />
              <path d="M5.5 7V5a2.5 2.5 0 0 1 5 0v2" />
            </svg>
          </span>
        )}
        {qna.title}
      </Link>

      {qna.productName && <div className={styles.product}>{qna.productName}</div>}
    </article>
  );
}
