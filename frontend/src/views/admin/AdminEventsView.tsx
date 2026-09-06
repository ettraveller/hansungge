'use client';

import { useState } from 'react';

import { Alert } from '@/components/admin/Alert';
import { Button } from '@/components/admin/Button';
import { Field, TextArea, TextInput } from '@/components/admin/Field';
import { Modal } from '@/components/admin/Modal';
import { Pagination } from '@/components/admin/Pagination';
import { Panel } from '@/components/admin/Panel';
import { StatusBadge, type StatusTone } from '@/components/ui/StatusBadge';
import { useAdminSession } from '@/hooks/admin/useAdminSession';
import {
  useAdminEvents,
  useCreateEvent,
  useEventEntries,
  useHideEvent,
  useUpdateEvent,
} from '@/hooks/admin/useEvents';
import type { AdminEvent } from '@/api/admin/events';
import { ApiError } from '@/types/api';
import { EVENT_STATUS_LABEL, type EventStatus } from '@/types/board';
import { formatDateTime } from '@/utils/format';
import styles from '@/components/admin/admin.module.css';

const STATUS_TONE: Record<EventStatus, StatusTone> = {
  UPCOMING: 'neutral',
  OPEN: 'success',
  CLOSED: 'neutral',
};

/**
 * 이벤트 관리.
 *
 * <p>진행 상태(진행예정·진행중·종료)는 <b>서버가 정해서 내려준다.</b> 화면에서 현재
 * 시각과 기간을 비교해 다시 계산하지 않는다 — 브라우저 시계는 틀릴 수 있고, 그러면
 * 관리자 화면은 "진행중"인데 응모는 409 로 거절되는 상태가 된다.
 *
 * <p>기간을 고치는 것이 이 화면에서 가장 위험한 조작이다. 종료 시각을 당기면 응모가
 * 그 순간부터 막힌다. 그래서 응모 건수를 목록에 같이 띄우고, 눌러서 내용까지 볼 수 있게
 * 했다 — 무엇이 걸려 있는지 모르는 채로 기간을 바꾸지 않도록.
 */
export function AdminEventsView() {
  const { can } = useAdminSession();
  const canWrite = can('MANAGER');

  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<AdminEvent | 'new' | null>(null);
  const [hiding, setHiding] = useState<AdminEvent | null>(null);
  const [viewingEntries, setViewingEntries] = useState<AdminEvent | null>(null);

  const { data, isLoading, error } = useAdminEvents({ page });
  const rows = data?.content ?? [];

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <div>
          <h1 className={styles.title}>이벤트 관리</h1>
          <p className={styles.subtitle}>
            등록·수정과 응모 확인을 합니다. 진행 상태는 기간을 보고 서버가 정합니다.
          </p>
        </div>
      </header>

      {!canWrite && (
        <Alert tone="info">
          등록·수정·내리기는 MANAGER 이상이 할 수 있습니다. 조회와 응모 확인만 가능합니다.
        </Alert>
      )}

      <Panel
        title="이벤트 목록"
        note="내린 이벤트도 함께 보입니다. 응모 기록이 딸려 있어 행은 지우지 않습니다."
        actions={
          canWrite && (
            <Button variant="gold" onClick={() => setEditing('new')}>
              이벤트 등록
            </Button>
          )
        }
        flush
      >
        {error ? (
          <Alert tone="danger" title="이벤트를 불러오지 못했습니다">
            {error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요.'}
          </Alert>
        ) : isLoading ? (
          <p className={styles.empty}>불러오는 중…</p>
        ) : rows.length === 0 ? (
          <p className={styles.empty}>등록된 이벤트가 없습니다.</p>
        ) : (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
            <thead>
              <tr>
                <th scope="col">제목</th>
                <th scope="col">기간</th>
                <th scope="col">상태</th>
                <th scope="col">응모</th>
                <th scope="col">조회수</th>
                <th scope="col">작업</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((event) => (
                <tr key={event.id} className={event.active ? undefined : styles.muted}>
                  <td data-label="제목" data-card-title>
                    <span className={styles.wrapText}>{event.title}</span>
                    {!event.active && <span className={styles.cellSub}>내려진 이벤트</span>}
                  </td>
                  <td data-label="기간" className="tabular">
                    {formatDateTime(event.startAt)}
                    <span className={styles.cellSub}>~ {formatDateTime(event.endAt)}</span>
                  </td>
                  <td data-label="상태">
                    <StatusBadge tone={STATUS_TONE[event.status]}>
                      {EVENT_STATUS_LABEL[event.status]}
                    </StatusBadge>
                  </td>
                  <td data-label="응모" className="tabular">
                    {event.entryCount > 0 ? (
                      <Button small onClick={() => setViewingEntries(event)}>
                        {event.entryCount.toLocaleString()}건
                      </Button>
                    ) : (
                      <span className={styles.muted}>0건</span>
                    )}
                  </td>
                  <td data-label="조회수" className="tabular">{event.viewCount.toLocaleString()}</td>
                  <td data-label="작업" data-card-actions>
                    <div className={styles.rowActions}>
                      <Button small onClick={() => setEditing(event)}>
                        {canWrite ? '수정' : '보기'}
                      </Button>
                      {canWrite && event.active && (
                        <Button small variant="danger" onClick={() => setHiding(event)}>
                          내리기
                        </Button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
            </table>
          </div>
        )}

        {data && (
          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            onChange={setPage}
          />
        )}
      </Panel>

      {editing && (
        <EventDialog
          event={editing === 'new' ? null : editing}
          readOnly={!canWrite}
          onClose={() => setEditing(null)}
        />
      )}
      {hiding && <HideDialog event={hiding} onClose={() => setHiding(null)} />}
      {viewingEntries && (
        <EntriesDialog event={viewingEntries} onClose={() => setViewingEntries(null)} />
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ */

function EventDialog({
  event,
  readOnly,
  onClose,
}: {
  event: AdminEvent | null;
  readOnly: boolean;
  onClose: () => void;
}) {
  const isNew = event == null;

  const [title, setTitle] = useState(event?.title ?? '');
  const [content, setContent] = useState(event?.content ?? '');
  const [thumbnailKey, setThumbnailKey] = useState(event?.thumbnailKey ?? '');
  const [startAt, setStartAt] = useState(event ? toLocalInput(event.startAt) : '');
  const [endAt, setEndAt] = useState(event ? toLocalInput(event.endAt) : '');
  const [active, setActive] = useState(event?.active ?? true);

  const create = useCreateEvent();
  const update = useUpdateEvent();
  const pending = create.isPending || update.isPending;
  const failure = create.error ?? update.error;

  // 백엔드도 같은 검증을 한다. 여기서 한 번 더 보는 이유는 왕복 없이 바로 알려주기
  // 위해서다 — 검증 자체는 서버가 최종 판단한다.
  const periodInvalid = startAt !== '' && endAt !== '' && endAt <= startAt;
  const filled = title.trim() !== '' && startAt !== '' && endAt !== '';
  const canSubmit = filled && !periodInvalid;

  const submit = () => {
    if (!canSubmit) return;
    const body = {
      title: title.trim(),
      content: content.trim() === '' ? null : content.trim(),
      thumbnailKey: thumbnailKey.trim() === '' ? null : thumbnailKey.trim(),
      startAt: toOffsetIso(startAt),
      endAt: toOffsetIso(endAt),
      active,
    };
    if (event == null) {
      create.mutate(body, { onSuccess: onClose });
    } else {
      update.mutate({ id: event.id, body }, { onSuccess: onClose });
    }
  };

  return (
    <Modal
      open
      title={isNew ? '이벤트 등록' : '이벤트 수정'}
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose}>닫기</Button>
          {!readOnly && (
            <Button variant="gold" disabled={pending || !canSubmit} onClick={submit}>
              {pending ? '저장 중…' : '저장'}
            </Button>
          )}
        </>
      }
    >
      {failure && (
        <Alert tone="danger" title="저장하지 못했습니다">
          {failure instanceof ApiError ? failure.message : '잠시 후 다시 시도해 주세요.'}
        </Alert>
      )}

      {!isNew && event.entryCount > 0 && (
        <Alert tone="warning" title={`응모 ${event.entryCount.toLocaleString()}건이 있습니다`}>
          종료 시각을 당기면 그 순간부터 응모가 막힙니다. 이미 들어온 응모는 남습니다.
        </Alert>
      )}

      <Field label="제목" required>
        <TextInput
          value={title}
          maxLength={200}
          disabled={readOnly || pending}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="예) 9월 황금감사제"
        />
      </Field>

      <div className={styles.formGrid}>
        <Field label="시작" required>
          <TextInput
            type="datetime-local"
            value={startAt}
            disabled={readOnly || pending}
            onChange={(e) => setStartAt(e.target.value)}
          />
        </Field>

        <Field
          label="종료"
          required
          error={periodInvalid ? '종료 시각이 시작 시각보다 빠릅니다.' : null}
        >
          <TextInput
            type="datetime-local"
            value={endAt}
            invalid={periodInvalid}
            disabled={readOnly || pending}
            onChange={(e) => setEndAt(e.target.value)}
          />
        </Field>
      </div>

      <Field label="본문" hint="비워 두어도 됩니다. 줄바꿈은 고객 화면에 그대로 보입니다.">
        <TextArea
          value={content}
          rows={8}
          disabled={readOnly || pending}
          onChange={(e) => setContent(e.target.value)}
          placeholder="이벤트 내용을 입력하세요"
        />
      </Field>

      <Field
        label="대표 이미지 키"
        hint="URL 이 아니라 Object Storage 키입니다. 업로드 기능은 아직 없습니다 (STEP 10)."
      >
        <TextInput
          value={thumbnailKey}
          maxLength={300}
          disabled={readOnly || pending}
          onChange={(e) => setThumbnailKey(e.target.value)}
          placeholder="events/2026/september.jpg"
        />
      </Field>

      {isNew ? (
        <p className={styles.hint}>
          등록한 이벤트는 바로 노출됩니다. 시작 시각 전에는 진행예정으로 보입니다.
        </p>
      ) : (
        <label className={styles.checkRow}>
          <input
            type="checkbox"
            checked={active}
            disabled={readOnly || pending}
            onChange={(e) => setActive(e.target.checked)}
          />
          고객 화면에 노출
        </label>
      )}
    </Modal>
  );
}

function EntriesDialog({ event, onClose }: { event: AdminEvent; onClose: () => void }) {
  const [page, setPage] = useState(0);
  const { data, isLoading, error } = useEventEntries(event.id, page);
  const rows = data?.content ?? [];

  return (
    <Modal
      open
      title={`응모 목록 — ${event.title}`}
      onClose={onClose}
      footer={<Button onClick={onClose}>닫기</Button>}
    >
      <Alert tone="info">
        비밀 응모글의 내용도 보입니다. 이름은 관리자에게도 가려집니다 — 당첨자를 고르는 데
        실명이 필요하지 않습니다.
      </Alert>

      {error ? (
        <Alert tone="danger" title="응모를 불러오지 못했습니다">
          {error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요.'}
        </Alert>
      ) : isLoading ? (
        <p className={styles.empty}>불러오는 중…</p>
      ) : rows.length === 0 ? (
        <p className={styles.empty}>응모가 없습니다.</p>
      ) : (
        <div className={styles.tableWrap}>
          <table className={styles.table}>
          <thead>
            <tr>
              <th scope="col">작성자</th>
              <th scope="col">응모 내용</th>
              <th scope="col">응모 시각</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((entry) => (
              <tr key={entry.id}>
                <td data-label="작성자" data-card-title>
                  {entry.authorName}
                  {entry.secret && <span className={styles.tag}>비밀</span>}
                </td>
                <td data-label="응모 내용" className={styles.preserveLines}>{entry.content}</td>
                <td data-label="응모 시각" className="tabular">{formatDateTime(entry.createdAt)}</td>
              </tr>
            ))}
          </tbody>
          </table>
        </div>
      )}

      {data && data.totalPages > 1 && (
        <Pagination
          page={data.page}
          totalPages={data.totalPages}
          totalElements={data.totalElements}
          onChange={setPage}
        />
      )}
    </Modal>
  );
}

function HideDialog({ event, onClose }: { event: AdminEvent; onClose: () => void }) {
  const hide = useHideEvent();

  return (
    <Modal
      open
      title="이벤트 내리기"
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose}>취소</Button>
          <Button
            variant="danger"
            disabled={hide.isPending}
            onClick={() => hide.mutate(event.id, { onSuccess: onClose })}
          >
            {hide.isPending ? '처리 중…' : '내리기'}
          </Button>
        </>
      }
    >
      <p className={styles.dialogText}>
        <strong>{event.title}</strong> 을(를) 공개 목록에서 내립니다.
      </p>
      <Alert tone="warning">
        이벤트와 응모 {event.entryCount.toLocaleString()}건은 지워지지 않고 노출만
        중단됩니다. 응모한 회원의 기록이 딸려 있어 행 자체를 지울 수 없습니다.
      </Alert>
      {hide.error && (
        <Alert tone="danger" title="내리지 못했습니다">
          {hide.error instanceof ApiError ? hide.error.message : '잠시 후 다시 시도해 주세요.'}
        </Alert>
      )}
    </Modal>
  );
}

/* ------------------------------------------------------------------ */

/**
 * datetime-local 입력값 → 오프셋이 붙은 ISO 문자열.
 *
 * <p>{@code <input type="datetime-local">} 은 {@code 2026-09-05T14:30} 처럼 <b>오프셋이
 * 없는</b> 값을 준다. 백엔드는 {@code OffsetDateTime} 으로 받으므로 그대로 보내면 거절한다.
 *
 * <p>{@code new Date()} 가 이 문자열을 <b>브라우저 현지 시각</b>으로 해석하므로,
 * {@code toISOString()} 이 같은 순간을 UTC 로 옮겨 준다. 문자열을 손으로 이어 붙여
 * {@code +09:00} 을 다는 방법은 쓰지 않는다 — 관리자가 해외에서 접속하면 9시간 어긋난다.
 */
function toOffsetIso(local: string): string {
  return new Date(local).toISOString();
}

/**
 * 반대 방향. 서버가 준 시각을 datetime-local 이 읽을 수 있는 현지 시각 문자열로 되돌린다.
 *
 * <p>{@code toISOString()} 은 언제나 UTC 라 그대로 잘라 쓰면 한국에서 9시간 이르게 보인다.
 * 시간대 차이만큼 먼저 옮긴 뒤 잘라 낸다.
 */
function toLocalInput(iso: string): string {
  const date = new Date(iso);
  const shifted = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return shifted.toISOString().slice(0, 16);
}
