'use client';

import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';

import { qnaApi } from '@/api/board';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useUserSession } from '@/hooks/useUserSession';
import { QNA_BOARD_LABEL, QNA_BOARD_PATH, QNA_STATUS_LABEL } from '@/types/board';
import { formatBoardDate } from '@/utils/format';
import styles from '@/components/auth/auth.module.css';
import panel from './MyInquiryPanel.module.css';

/** 마이페이지에 보여줄 최근 문의 건수. 전체는 게시판에서 본다. */
const RECENT = 5;

/**
 * 마이페이지 「내 문의」 패널.
 *
 * <h2>여기에 두는 이유</h2>
 * 내가 쓴 글은 <b>게시판이 아니라 내 계정</b>에 속한 정보다. 비밀글로 쓴 문의는 목록에서
 * 제목만 보이므로, 내가 뭘 물었고 답이 왔는지 확인할 곳이 따로 있어야 한다.
 *
 * <p>답변 여부를 배지로 보여준다 — 이 화면에 들어오는 이유가 대개 "답변 왔나" 이기 때문이다.
 */
export function MyInquiryPanel() {
  const { isAuthenticated } = useUserSession();

  const { data, isPending, isError } = useQuery({
    queryKey: ['qna', 'mine', RECENT],
    queryFn: () => qnaApi.mine({ size: RECENT }),
    enabled: isAuthenticated,
  });

  return (
    <section className={styles.panel}>
      <header className={styles.panelHead}>
        <h2 className={styles.sectionTitle}>내 문의</h2>
        {/* 마이페이지 안쪽의 「내 문의」로 보낸다. 게시판으로 곧장 내보내면 왼쪽 메뉴가 사라진다 */}
        <Link href="/mypage/inquiries" className={panel.more}>
          전체 보기
        </Link>
      </header>

      <div className={styles.panelBody}>
        {isPending ? (
          <p className={styles.loading}>불러오는 중…</p>
        ) : isError ? (
          <p className={panel.empty}>문의 내역을 불러오지 못했습니다.</p>
        ) : !data || data.content.length === 0 ? (
          <p className={panel.empty}>
            남기신 문의가 없습니다.{' '}
            <Link href={`${QNA_BOARD_PATH.GENERAL}/write`}>문의 남기기</Link>
          </p>
        ) : (
          <>
            <ul className={panel.list}>
              {data.content.map((qna) => (
                <li key={qna.id} className={panel.item}>
                  <Link href={`${QNA_BOARD_PATH[qna.boardType]}/${qna.id}`} className={panel.link}>
                    <span className={panel.board}>{QNA_BOARD_LABEL[qna.boardType]}</span>
                    <span className={panel.title}>{qna.title}</span>
                  </Link>
                  <span className={panel.meta}>
                    <StatusBadge tone={qna.status === 'ANSWERED' ? 'success' : 'neutral'}>
                      {QNA_STATUS_LABEL[qna.status]}
                    </StatusBadge>
                    <span className="tabular">{formatBoardDate(qna.createdAt)}</span>
                  </span>
                </li>
              ))}
            </ul>

            {data.totalElements > RECENT && (
              <p className={panel.foot}>
                전체 <strong className="tabular">{data.totalElements}</strong>건 중 최근{' '}
                {RECENT}건입니다.
              </p>
            )}
          </>
        )}
      </div>
    </section>
  );
}
