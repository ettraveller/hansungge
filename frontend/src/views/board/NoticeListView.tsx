import type { Loaded } from '@/api/server';
import { BoardTable, type BoardRow } from '@/components/board/BoardTable';
import { Pager } from '@/components/board/Pager';
import { SectionHead } from '@/components/ui/SectionHead';
import type { PageResponse } from '@/types/api';
import type { NoticeSummary } from '@/types/board';
import { formatBoardDate } from '@/utils/format';
import styles from './BoardPage.module.css';
import tableStyles from '@/components/board/board.module.css';

interface Props {
  notices: Loaded<PageResponse<NoticeSummary>>;
  /** 화면·URL 이 쓰는 1-base 페이지 */
  page: number;
}

/**
 * 공지사항 목록 (`/notice`).
 *
 * <h2>서버 컴포넌트다</h2>
 * 공지 제목이 초기 HTML 에 실려야 한다. "○○ 금거래소 휴무", "배송 안내"는 실제로
 * 검색되는 말이고, 목록이 스크립트로만 그려지면 그 검색에 걸리지 않는다.
 *
 * <h2>번호는 서버 페이지에서 역산한다</h2>
 * 백엔드가 번호를 주지 않는다 — 글이 내려가면 번호가 밀리므로 저장할 값이 아니다.
 * 전체 건수에서 현재 위치를 빼서 만든다. 고정 공지는 번호 대신 "공지" 배지가 나가므로
 * 번호 계산에서 빠져야 하는데, <b>정렬이 고정글을 앞으로 몰아 두기 때문에</b> 한 페이지
 * 안에서 그냥 세도 어긋나지 않는다.
 */
export function NoticeListView({ notices, page }: Props) {
  const data = notices.data;

  return (
    <div className={styles.page}>
      <div className="wrap">
        <SectionHead as="h1" title="공지사항" desc="배송·운영·시세 고시에 관한 안내입니다" />

        {!data ? (
          <p className={styles.loadError}>
            공지사항을 불러올 수 없습니다. 잠시 후 다시 시도해 주세요.
          </p>
        ) : (
          <>
            <p className={tableStyles.count}>
              전체 <strong className="tabular">{data.totalElements}</strong>건
            </p>

            <BoardTable
              caption="공지사항 목록"
              basePath="/notice"
              rows={toRows(data)}
              emptyText="등록된 공지사항이 없습니다."
            />

            <Pager page={page} totalPages={data.totalPages} basePath="/notice" />
          </>
        )}
      </div>
    </div>
  );
}

function toRows(data: PageResponse<NoticeSummary>): BoardRow[] {
  // 페이지 안에서의 순번 → 전체 기준 번호. 최신순이므로 위로 갈수록 큰 번호다
  const firstNo = data.totalElements - data.page * data.size;
  const pinnedCount = data.content.filter((n) => n.pinned).length;

  let offset = 0;
  return data.content.map((notice) => ({
    id: notice.id,
    title: notice.title,
    pinned: notice.pinned,
    // 고정글은 번호를 쓰지 않으므로 번호를 소비하지도 않는다
    no: notice.pinned ? undefined : firstNo - pinnedCount - offset++,
    viewCount: notice.viewCount,
    date: formatBoardDate(notice.createdAt),
  }));
}
