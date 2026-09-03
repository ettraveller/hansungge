import Link from 'next/link';

import type { Loaded } from '@/api/server';
import { Pager } from '@/components/board/Pager';
import { SectionHead } from '@/components/ui/SectionHead';
import { StatusBadge, type StatusTone } from '@/components/ui/StatusBadge';
import type { PageResponse } from '@/types/api';
import { EVENT_STATUS_LABEL, type EventStatus, type EventSummary } from '@/types/board';
import { formatBoardDate } from '@/utils/format';
import styles from './EventView.module.css';
import page_ from './BoardPage.module.css';
import board from '@/components/board/board.module.css';

interface Props {
  events: Loaded<PageResponse<EventSummary>>;
  page: number;
}

/** 상태별 배지 색. 진행중만 눈에 띄면 된다. */
const TONE: Record<EventStatus, StatusTone> = {
  OPEN: 'gold',
  UPCOMING: 'neutral',
  CLOSED: 'neutral',
};

/**
 * 이벤트 목록 (`/event`).
 *
 * <h2>표가 아니라 카드다</h2>
 * 다른 게시판과 달리 이벤트는 <b>고르는 것</b>이다. 번호·조회수를 세로로 훑는 표보다
 * 제목과 기간이 먼저 보이는 카드가 맞는다. 게시판이라고 다 같은 모양일 이유는 없다.
 *
 * <h2>종료된 이벤트도 남긴다</h2>
 * 통째로 감추면 "당첨자 발표는 어디서 보나"라는 문의가 곧바로 들어온다. 상태 배지로
 * 구분하고 목록에는 둔다.
 */
export function EventListView({ events, page }: Props) {
  const data = events.data;

  return (
    <div className={page_.page}>
      <div className="wrap">
        <SectionHead as="h1" title="이벤트" desc="진행 중인 이벤트를 확인하고 참여해 보세요" />

        {!data ? (
          <p className={page_.loadError}>
            이벤트를 불러올 수 없습니다. 잠시 후 다시 시도해 주세요.
          </p>
        ) : data.content.length === 0 ? (
          <p className={board.empty}>진행 중인 이벤트가 없습니다.</p>
        ) : (
          <>
            <ul className={styles.list}>
              {data.content.map((event) => (
                <li key={event.id}>
                  <Link href={`/event/${event.id}`} className={styles.card}>
                    <span className={styles.cardHead}>
                      <StatusBadge tone={TONE[event.status]}>
                        {EVENT_STATUS_LABEL[event.status]}
                      </StatusBadge>
                    </span>

                    <span className={styles.cardTitle}>{event.title}</span>

                    <span className={`${styles.cardPeriod} tabular`}>
                      {formatBoardDate(event.startAt)} ~ {formatBoardDate(event.endAt)}
                    </span>

                    <span className={styles.cardMeta}>
                      <span className="tabular">응모 {event.entryCount}</span>
                      <span className="tabular">조회 {event.viewCount}</span>
                    </span>
                  </Link>
                </li>
              ))}
            </ul>

            <Pager page={page} totalPages={data.totalPages} basePath="/event" />
          </>
        )}
      </div>
    </div>
  );
}
