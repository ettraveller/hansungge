import Link from 'next/link';

import type { Loaded } from '@/api/server';
import { StatusBadge, type StatusTone } from '@/components/ui/StatusBadge';
import type { PageResponse } from '@/types/api';
import {
  EVENT_STATUS_LABEL,
  type EventDetail,
  type EventEntry,
  type EventStatus,
} from '@/types/board';
import { formatBoardDate } from '@/utils/format';
import { EventEntryForm } from './EventEntryForm';
import styles from './EventView.module.css';
import page_ from './BoardPage.module.css';
import board from '@/components/board/board.module.css';

interface Props {
  event: EventDetail;
  entries: Loaded<PageResponse<EventEntry>>;
}

const TONE: Record<EventStatus, StatusTone> = {
  OPEN: 'gold',
  UPCOMING: 'neutral',
  CLOSED: 'neutral',
};

/**
 * 이벤트 상세 (`/event/[id]`).
 *
 * <h2>공지 + 응모</h2>
 * 위쪽은 관리자가 연 이벤트 안내(서버 렌더링), 아래쪽은 고객 응모 목록이다. 응모 폼만
 * 클라이언트 컴포넌트다 — 로그인 여부와 제출이 필요하기 때문이다.
 *
 * <h2>비밀 응모글은 목록에서 지우지 않는다</h2>
 * 몇 명이 응모했는지 보여야 참여를 유도한다. 내용만 가린다 — 레퍼런스도 자물쇠만 붙여
 * 목록에 남긴다.
 */
export function EventDetailView({ event, entries }: Props) {
  const closed = event.status === 'CLOSED';

  return (
    <div className={page_.page}>
      <div className="wrap">
        <article className={board.detail}>
          <header className={board.detailHead}>
            <h1 className={board.detailTitle}>
              <StatusBadge tone={TONE[event.status]}>
                {EVENT_STATUS_LABEL[event.status]}
              </StatusBadge>
              {event.title}
            </h1>
            <p className={board.detailMeta}>
              <span className="tabular">조회 {event.viewCount}</span>
              <span className="tabular">응모 {event.entryCount}</span>
            </p>
          </header>

          <div className={board.detailBody}>
            <p className={styles.period}>
              <span className={styles.periodLabel}>응모 기간</span>
              <strong className="tabular">
                {formatBoardDate(event.startAt)} ~ {formatBoardDate(event.endAt)}
              </strong>
            </p>

            {event.content ?? '자세한 내용은 지점으로 문의해 주세요.'}
          </div>
        </article>

        {/* ---------- 응모 ---------- */}
        <section className={styles.entrySection} aria-labelledby="event-entries">
          <div className={styles.entryHead}>
            <h2 id="event-entries" className={styles.entryTitle}>
              응모 현황
            </h2>
            <span className={`${styles.entryCount} tabular`}>{event.entryCount}건</span>
          </div>

          <EventEntryForm
            eventId={event.id}
            status={event.status}
            alreadyEntered={event.entered}
          />

          {!entries.data ? (
            <p className={page_.loadError}>응모 목록을 불러올 수 없습니다.</p>
          ) : entries.data.content.length === 0 ? (
            <p className={board.empty}>
              {closed ? '응모 없이 종료된 이벤트입니다.' : '아직 응모가 없습니다. 첫 응모를 남겨 보세요.'}
            </p>
          ) : (
            <ul className={styles.entries}>
              {entries.data.content.map((entry) => (
                <li key={entry.id} className={styles.entry}>
                  <p className={styles.entryMeta}>
                    <span className={styles.entryAuthor}>{entry.authorName}</span>
                    <span className="tabular">{formatBoardDate(entry.createdAt)}</span>
                    {entry.mine && <span className={styles.entryMine}>내 응모</span>}
                  </p>

                  {entry.content ? (
                    <p className={styles.entryBody}>{entry.content}</p>
                  ) : (
                    <p className={styles.entryLocked}>비밀 응모글입니다.</p>
                  )}
                </li>
              ))}
            </ul>
          )}
        </section>

        <div className={board.actions}>
          <Link href="/event" className={board.ghostBtn}>
            목록으로
          </Link>
        </div>
      </div>
    </div>
  );
}
