import Link from 'next/link';

import type { NoticeDetail } from '@/types/board';
import { formatBoardDate } from '@/utils/format';
import styles from './BoardPage.module.css';
import board from '@/components/board/board.module.css';

interface Props {
  notice: NoticeDetail;
}

/**
 * 공지 상세 (`/notice/[id]`).
 *
 * <h2>본문을 {@code dangerouslySetInnerHTML} 로 넣지 않는다</h2>
 * 관리자가 넣은 값이라도 그대로 HTML 로 해석하면 <b>관리자 계정 하나가 뚫렸을 때
 * 모든 방문자에게 스크립트가 실행된다.</b> 개발계획 §보안이 이 지점에 DOMPurify 를
 * 요구하는데, 지금은 본문 편집기가 서식을 쓰지 않으므로 <b>텍스트로 그리고 줄바꿈만
 * 살린다</b>({@code white-space: pre-wrap}). 서식 편집기를 붙이는 날 정화 계층을
 * 함께 붙인다 — 그때까지는 라이브러리를 미리 넣지 않는다.
 */
export function NoticeDetailView({ notice }: Props) {
  return (
    <div className={styles.page}>
      <div className="wrap">
        <article className={board.detail}>
          <header className={board.detailHead}>
            <h1 className={board.detailTitle}>
              {notice.pinned && <span className={board.noticeTag}>공지</span>}
              {notice.title}
            </h1>
            <p className={board.detailMeta}>
              <span className="tabular">{formatBoardDate(notice.createdAt)}</span>
              <span className="tabular">조회 {notice.viewCount}</span>
            </p>
          </header>

          <div className={board.detailBody}>{notice.content}</div>
        </article>

        <div className={board.actions}>
          <Link href="/notice" className={board.ghostBtn}>
            목록으로
          </Link>
        </div>
      </div>
    </div>
  );
}
