'use client';

import { useState } from 'react';

import { Alert } from '@/components/admin/Alert';
import { Button } from '@/components/admin/Button';
import { Field, TextArea, TextInput } from '@/components/admin/Field';
import { Modal } from '@/components/admin/Modal';
import { Pagination } from '@/components/admin/Pagination';
import { Panel } from '@/components/admin/Panel';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useAdminSession } from '@/hooks/admin/useAdminSession';
import {
  useAdminNotices,
  useCreateNotice,
  useHideNotice,
  useUpdateNotice,
} from '@/hooks/admin/useNotices';
import type { AdminNotice } from '@/api/admin/notices';
import { ApiError } from '@/types/api';
import { formatDateTime } from '@/utils/format';
import styles from '@/components/admin/admin.module.css';

/**
 * 공지 관리.
 *
 * <p>이 화면이 없으면 <b>공지를 올릴 방법이 없다.</b> 백엔드와 고객 화면은 진작 있었는데
 * 등록하는 곳만 비어 있어서, 배송 지연이나 휴무 같은 것을 알릴 수단이 전화뿐이었다.
 *
 * <p>내리기가 삭제가 아닌 이유는 공지의 성격 때문이다. "그때 그렇게 안내했다"가 근거로
 * 남아야 한다 — 지워 버리면 나중에 분쟁이 생겼을 때 증명할 것이 없다.
 */
export function AdminNoticesView() {
  const { can } = useAdminSession();
  const canWrite = can('MANAGER');

  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<AdminNotice | 'new' | null>(null);
  const [hiding, setHiding] = useState<AdminNotice | null>(null);

  const { data, isLoading, error } = useAdminNotices({ page });
  const rows = data?.content ?? [];

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <div>
          <h1 className={styles.title}>공지 관리</h1>
          <p className={styles.subtitle}>
            고객 화면의 공지사항 게시판에 그대로 나갑니다. 저장하면 즉시 공개됩니다.
          </p>
        </div>
      </header>

      {!canWrite && (
        <Alert tone="info">
          등록·수정·내리기는 MANAGER 이상이 할 수 있습니다. 조회만 가능합니다.
        </Alert>
      )}

      <Panel
        title="공지 목록"
        note="내린 공지도 함께 보입니다 — 무엇을 왜 내렸는지 확인할 수 있어야 합니다."
        actions={
          canWrite && (
            <Button variant="gold" onClick={() => setEditing('new')}>
              공지 등록
            </Button>
          )
        }
        flush
      >
        {error ? (
          <Alert tone="danger" title="공지를 불러오지 못했습니다">
            {error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요.'}
          </Alert>
        ) : isLoading ? (
          <p className={styles.empty}>불러오는 중…</p>
        ) : rows.length === 0 ? (
          <p className={styles.empty}>등록된 공지가 없습니다.</p>
        ) : (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
            <thead>
              <tr>
                <th scope="col">제목</th>
                <th scope="col">상태</th>
                <th scope="col">조회수</th>
                <th scope="col">작성자</th>
                <th scope="col">등록</th>
                <th scope="col">작업</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((notice) => (
                <tr key={notice.id} className={notice.active ? undefined : styles.muted}>
                  <td data-label="제목" data-card-title>
                    <span className={styles.wrapText}>
                      {notice.pinned && <span className={styles.tag}>고정</span>}
                      {notice.title}
                    </span>
                  </td>
                  <td data-label="상태">
                    <StatusBadge tone={notice.active ? 'success' : 'neutral'}>
                      {notice.active ? '노출중' : '내림'}
                    </StatusBadge>
                  </td>
                  <td data-label="조회수" className="tabular">{notice.viewCount.toLocaleString()}</td>
                  <td data-label="작성자">{notice.createdBy}</td>
                  <td data-label="등록" className="tabular">{formatDateTime(notice.createdAt)}</td>
                  <td data-label="작업" data-card-actions>
                    <div className={styles.rowActions}>
                      <Button small onClick={() => setEditing(notice)}>
                        {canWrite ? '수정' : '보기'}
                      </Button>
                      {canWrite && notice.active && (
                        <Button small variant="danger" onClick={() => setHiding(notice)}>
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
        <NoticeDialog
          notice={editing === 'new' ? null : editing}
          readOnly={!canWrite}
          onClose={() => setEditing(null)}
        />
      )}
      {hiding && <HideDialog notice={hiding} onClose={() => setHiding(null)} />}
    </div>
  );
}

/* ------------------------------------------------------------------ */

function NoticeDialog({
  notice,
  readOnly,
  onClose,
}: {
  notice: AdminNotice | null;
  readOnly: boolean;
  onClose: () => void;
}) {
  const isNew = notice == null;

  const [title, setTitle] = useState(notice?.title ?? '');
  const [content, setContent] = useState(notice?.content ?? '');
  const [pinned, setPinned] = useState(notice?.pinned ?? false);
  const [active, setActive] = useState(notice?.active ?? true);

  const create = useCreateNotice();
  const update = useUpdateNotice();
  const pending = create.isPending || update.isPending;
  const failure = create.error ?? update.error;

  const filled = title.trim().length > 0 && content.trim().length > 0;

  const submit = () => {
    if (!filled) return;
    // active 는 등록 요청에도 실리지만 백엔드가 무시한다 — 항상 노출로 시작한다.
    const body = { title: title.trim(), content: content.trim(), pinned, active };
    if (notice == null) {
      create.mutate(body, { onSuccess: onClose });
    } else {
      update.mutate({ id: notice.id, body }, { onSuccess: onClose });
    }
  };

  return (
    <Modal
      open
      title={isNew ? '공지 등록' : '공지 수정'}
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose}>닫기</Button>
          {!readOnly && (
            <Button variant="gold" disabled={pending || !filled} onClick={submit}>
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

      <Field label="제목" required>
        <TextInput
          value={title}
          maxLength={200}
          disabled={readOnly || pending}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="예) 추석 연휴 배송 안내"
        />
      </Field>

      <Field label="본문" required hint="줄바꿈은 고객 화면에 그대로 보입니다.">
        <TextArea
          value={content}
          rows={10}
          disabled={readOnly || pending}
          onChange={(e) => setContent(e.target.value)}
          placeholder="공지 내용을 입력하세요"
        />
      </Field>

      <label className={styles.checkRow}>
        <input
          type="checkbox"
          checked={pinned}
          disabled={readOnly || pending}
          onChange={(e) => setPinned(e.target.checked)}
        />
        목록 맨 위에 고정
      </label>

      {isNew ? (
        <p className={styles.hint}>
          등록한 공지는 바로 노출됩니다. 내리려면 목록에서 내리기를 누르세요.
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

function HideDialog({ notice, onClose }: { notice: AdminNotice; onClose: () => void }) {
  const hide = useHideNotice();

  return (
    <Modal
      open
      title="공지 내리기"
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose}>취소</Button>
          <Button
            variant="danger"
            disabled={hide.isPending}
            onClick={() => hide.mutate(notice.id, { onSuccess: onClose })}
          >
            {hide.isPending ? '처리 중…' : '내리기'}
          </Button>
        </>
      }
    >
      <p className={styles.dialogText}>
        <strong>{notice.title}</strong> 을(를) 공개 목록에서 내립니다.
      </p>
      <Alert tone="warning">
        공지는 지워지지 않고 노출만 중단됩니다. 다시 올리려면 수정 화면에서 &ldquo;고객
        화면에 노출&rdquo;을 켜면 됩니다.
      </Alert>
      {hide.error && (
        <Alert tone="danger" title="내리지 못했습니다">
          {hide.error instanceof ApiError ? hide.error.message : '잠시 후 다시 시도해 주세요.'}
        </Alert>
      )}
    </Modal>
  );
}
