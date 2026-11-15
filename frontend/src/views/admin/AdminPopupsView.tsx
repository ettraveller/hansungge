'use client';

import { useState } from 'react';

import { Alert } from '@/components/admin/Alert';
import { Button } from '@/components/admin/Button';
import { Field, TextArea, TextInput } from '@/components/admin/Field';
import { LinkTargetPicker } from '@/components/admin/LinkTargetPicker';
import { Panel } from '@/components/admin/Panel';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useAdminSession } from '@/hooks/admin/useAdminSession';
import {
  useAdminPopups,
  useCreatePopup,
  useDeletePopup,
  useSetPopupActive,
  useUpdatePopup,
} from '@/hooks/admin/usePopups';
import { ApiError } from '@/types/api';
import { POPUP_KIND_LABEL, type AdminPopup, type PopupInput, type PopupKind } from '@/types/popup';
import { formatDateTime } from '@/utils/format';
import styles from '@/components/admin/admin.module.css';

const EMPTY: PopupInput = {
  name: '',
  kind: 'CONTENT',
  title: '',
  body: '',
  linkHref: '',
  linkLabel: '',
  startsAt: null,
  endsAt: null,
  active: true,
  sort: 0,
  dismissible: true,
};

/**
 * 팝업 관리 (`/admin/popups`).
 *
 * <h2>「지금 떠 있는가」를 서버가 판정해 보여준다</h2>
 * 켬/끔과 노출 기간을 관리자가 머릿속으로 합쳐 보게 두지 않는다 — 기간을 거꾸로
 * 넣거나 어제로 끝난 것을 켜 둔 채 "왜 안 뜨지" 하는 일이 실제로 생긴다.
 *
 * <h2>지우기보다 끄기를 먼저 권한다</h2>
 * 끄면 기간과 내용이 남아 있어 다음에 다시 켜면 된다. 명절 인사처럼 해마다
 * 쓰는 것이 그렇다.
 */
export function AdminPopupsView() {
  const { can } = useAdminSession();
  const canEdit = can('MANAGER');
  const { data, isLoading, error } = useAdminPopups();

  const create = useCreatePopup();
  const update = useUpdatePopup();
  const setActive = useSetPopupActive();
  const remove = useDeletePopup();

  /** null = 닫힘, 0 = 새로 만들기, 그 외 = 그 id 수정 */
  const [editing, setEditing] = useState<number | null>(null);
  const [form, setForm] = useState<PopupInput>(EMPTY);
  const [confirmDelete, setConfirmDelete] = useState<number | null>(null);

  const patch = (next: Partial<PopupInput>) => setForm((prev) => ({ ...prev, ...next }));

  const openNew = () => {
    setForm(EMPTY);
    setEditing(0);
  };

  const openEdit = (popup: AdminPopup) => {
    setForm({
      name: popup.name,
      kind: popup.kind,
      title: popup.title ?? '',
      body: popup.body ?? '',
      imageKey: popup.imageKey ?? '',
      linkHref: popup.linkHref ?? '',
      linkLabel: popup.linkLabel ?? '',
      startsAt: popup.startsAt ?? null,
      endsAt: popup.endsAt ?? null,
      active: popup.active,
      sort: popup.sort,
      dismissible: popup.dismissible,
    });
    setEditing(popup.id);
  };

  const submit = () => {
    const body: PopupInput = { ...form, name: form.name.trim() };
    const done = () => {
      setEditing(null);
      setForm(EMPTY);
    };
    if (editing === 0) create.mutate(body, { onSuccess: done });
    else if (editing != null) update.mutate({ id: editing, body }, { onSuccess: done });
  };

  const busy = create.isPending || update.isPending;
  const failure = create.error ?? update.error ?? setActive.error ?? remove.error;

  if (isLoading) {
    return (
      <div className={styles.page}>
        <p className={styles.loading}>불러오는 중…</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className={styles.page}>
        <Alert tone="danger" title="팝업을 불러오지 못했습니다">
          {error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요.'}
        </Alert>
      </div>
    );
  }

  const popups = data ?? [];

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <div>
          <h1 className={styles.title}>팝업 관리</h1>
          <p className={styles.subtitle}>
            메인 첫 화면에 띄우는 알림창이다. 여러 개가 살아 있으면 차례대로 하나씩 보여준다.
          </p>
        </div>
        {canEdit && (
          <div className={styles.headActions}>
            <Button variant="primary" onClick={openNew}>
              팝업 추가
            </Button>
          </div>
        )}
      </header>

      {failure && (
        <Alert tone="danger">
          {failure instanceof ApiError ? failure.message : '처리하지 못했습니다.'}
        </Alert>
      )}

      {editing != null && canEdit && (
        <Panel
          title={editing === 0 ? '팝업 추가' : '팝업 수정'}
          note="노출 기간을 비우면 끌 때까지 계속 뜬다. 끝나는 날 없는 팝업은 잊혀지기 쉽다."
        >
          <form
            className={styles.form}
            onSubmit={(e) => {
              e.preventDefault();
              if (!busy) submit();
            }}
          >
            <Field label="이름" htmlFor="pp-name" required hint="관리자용이다. 고객에게 보이지 않는다.">
              <TextInput
                id="pp-name"
                maxLength={100}
                placeholder="예: 매입 계산기 안내"
                value={form.name}
                onChange={(e) => patch({ name: e.target.value })}
              />
            </Field>

            <Field label="종류" htmlFor="pp-kind">
              <div className={styles.formActions}>
                {(['CALCULATOR', 'CONTENT'] as PopupKind[]).map((k) => (
                  <Button
                    key={k}
                    type="button"
                    variant={form.kind === k ? 'primary' : undefined}
                    onClick={() => patch({ kind: k })}
                  >
                    {POPUP_KIND_LABEL[k]}
                  </Button>
                ))}
              </div>
            </Field>

            {/* 계산기는 내용이 코드에 있다. 적을 칸을 내주면 적어도 안 쓰인다 */}
            {form.kind === 'CONTENT' && (
              <>
                <Field label="제목" htmlFor="pp-title" required>
                  <TextInput
                    id="pp-title"
                    maxLength={200}
                    value={form.title ?? ''}
                    onChange={(e) => patch({ title: e.target.value })}
                  />
                </Field>
                <Field label="본문" htmlFor="pp-body" hint="줄바꿈이 그대로 보인다.">
                  <TextArea
                    id="pp-body"
                    rows={5}
                    value={form.body ?? ''}
                    onChange={(e) => patch({ body: e.target.value })}
                  />
                </Field>
              </>
            )}

            <Field
              label="누르면 갈 곳"
              htmlFor="pp-href"
              hint="메뉴나 상품에서 고른다. 비우면 버튼을 그리지 않는다."
            >
              <LinkTargetPicker
                id="pp-href"
                value={form.linkHref ?? ''}
                onChange={(linkHref) => patch({ linkHref })}
              />
            </Field>

            {form.linkHref?.trim() ? (
              <Field label="버튼 글자" htmlFor="pp-label">
                <TextInput
                  id="pp-label"
                  maxLength={60}
                  placeholder="자세히 보기"
                  value={form.linkLabel ?? ''}
                  onChange={(e) => patch({ linkLabel: e.target.value })}
                />
              </Field>
            ) : null}

            <Field label="노출 시작" htmlFor="pp-start" hint="비우면 지금부터.">
              <TextInput
                id="pp-start"
                type="datetime-local"
                value={toLocalInput(form.startsAt)}
                onChange={(e) => patch({ startsAt: fromLocalInput(e.target.value) })}
              />
            </Field>

            <Field label="노출 종료" htmlFor="pp-end" hint="비우면 끌 때까지 계속 뜬다.">
              <TextInput
                id="pp-end"
                type="datetime-local"
                value={toLocalInput(form.endsAt)}
                onChange={(e) => patch({ endsAt: fromLocalInput(e.target.value) })}
              />
            </Field>

            <Field label="차례" htmlFor="pp-sort" hint="작을수록 먼저 뜬다.">
              <TextInput
                id="pp-sort"
                inputMode="numeric"
                value={String(form.sort)}
                onChange={(e) => patch({ sort: Number(e.target.value.replace(/[^0-9]/g, '')) || 0 })}
              />
            </Field>

            <Field
              label="「오늘 하루 보지 않기」"
              htmlFor="pp-dismiss"
              hint="휴무 안내처럼 반드시 봐야 하는 것은 꺼 둔다 — 하루 숨기면 그날 방문한 사람이 끝내 못 본다."
            >
              <label className={styles.hint}>
                <input
                  id="pp-dismiss"
                  type="checkbox"
                  checked={form.dismissible}
                  onChange={(e) => patch({ dismissible: e.target.checked })}
                />{' '}
                제공한다
              </label>
            </Field>

            <Field label="켜기" htmlFor="pp-active">
              <label className={styles.hint}>
                <input
                  id="pp-active"
                  type="checkbox"
                  checked={form.active}
                  onChange={(e) => patch({ active: e.target.checked })}
                />{' '}
                지금 노출한다
              </label>
            </Field>

            <div className={styles.formActions}>
              <Button type="button" onClick={() => setEditing(null)} disabled={busy}>
                취소
              </Button>
              <Button type="submit" variant="primary" disabled={busy || !form.name.trim()}>
                {busy ? '저장 중…' : '저장'}
              </Button>
            </div>
          </form>
        </Panel>
      )}

      <Panel title={`등록된 팝업 ${popups.length}개`} flush>
        {popups.length === 0 ? (
          <p className={styles.empty}>등록된 팝업이 없습니다.</p>
        ) : (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <caption className="sr-only">팝업 목록</caption>
              <thead>
                <tr>
                  <th scope="col" className={styles.num}>
                    차례
                  </th>
                  <th scope="col">이름</th>
                  <th scope="col">종류</th>
                  <th scope="col">노출 기간</th>
                  <th scope="col">상태</th>
                  <th scope="col">최근 수정</th>
                  {canEdit && <th scope="col">관리</th>}
                </tr>
              </thead>
              <tbody>
                {popups.map((popup) => (
                  <tr key={popup.id}>
                    <td data-label="차례" className={styles.num}>
                      {popup.sort}
                    </td>
                    <td data-label="이름" data-card-title className={styles.strong}>
                      {popup.name}
                    </td>
                    <td data-label="종류">{POPUP_KIND_LABEL[popup.kind]}</td>
                    <td data-label="노출 기간" className={styles.muted}>
                      {period(popup)}
                    </td>
                    <td data-label="상태">
                      {/* 켬/끔과 기간을 합친 결과다. 둘을 따로 보여 주면
                          관리자가 머릿속으로 합쳐야 한다 */}
                      <StatusBadge tone={popup.live ? 'success' : undefined}>
                        {popup.live ? '떠 있음' : popup.active ? '기간 아님' : '꺼짐'}
                      </StatusBadge>
                    </td>
                    <td data-label="최근 수정" className={styles.muted}>
                      {formatDateTime(popup.updatedAt)}
                      {popup.updatedBy ? ` · ${popup.updatedBy}` : ''}
                    </td>
                    {canEdit && (
                      <td data-label="관리">
                        <div className={styles.formActions}>
                          <Button type="button" onClick={() => openEdit(popup)}>
                            수정
                          </Button>
                          <Button
                            type="button"
                            disabled={setActive.isPending}
                            onClick={() => setActive.mutate({ id: popup.id, active: !popup.active })}
                          >
                            {popup.active ? '끄기' : '켜기'}
                          </Button>
                          {confirmDelete === popup.id ? (
                            <>
                              <Button
                                type="button"
                                variant="danger"
                                disabled={remove.isPending}
                                onClick={() =>
                                  remove.mutate(popup.id, {
                                    onSuccess: () => setConfirmDelete(null),
                                  })
                                }
                              >
                                정말 삭제
                              </Button>
                              <Button type="button" onClick={() => setConfirmDelete(null)}>
                                취소
                              </Button>
                            </>
                          ) : (
                            <Button type="button" onClick={() => setConfirmDelete(popup.id)}>
                              삭제
                            </Button>
                          )}
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>
    </div>
  );
}

/** 「비움」을 그대로 적는다 — 빈 칸을 두면 못 넣은 것인지 안 넣은 것인지 모른다 */
function period(popup: AdminPopup): string {
  if (!popup.startsAt && !popup.endsAt) return '제한 없음 (끌 때까지)';
  const from = popup.startsAt ? formatDateTime(popup.startsAt) : '지금부터';
  const to = popup.endsAt ? formatDateTime(popup.endsAt) : '끌 때까지';
  return `${from} ~ ${to}`;
}

/** ISO → datetime-local 입력칸 값. 자리수가 다르면 브라우저가 빈 칸으로 만든다 */
function toLocalInput(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** datetime-local → ISO. 빈 칸은 null 이다 ("제한 없음") */
function fromLocalInput(value: string): string | null {
  if (!value) return null;
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? null : d.toISOString();
}
