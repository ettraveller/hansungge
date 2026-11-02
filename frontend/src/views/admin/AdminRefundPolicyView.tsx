'use client';

import { Fragment, useState } from 'react';

import { Alert } from '@/components/admin/Alert';
import { Button } from '@/components/admin/Button';
import { Field, TextArea, TextInput } from '@/components/admin/Field';
import { Pagination } from '@/components/admin/Pagination';
import { Panel } from '@/components/admin/Panel';
import {
  usePublishRefundPolicy,
  useRefundPolicy,
  useRefundPolicyHistory,
} from '@/hooks/admin/usePolicies';
import { useAdminSession } from '@/hooks/admin/useAdminSession';
import { formatDateTime } from '@/utils/format';
import { ApiError } from '@/types/api';
import styles from '@/components/admin/admin.module.css';

/**
 * 환불규정 관리.
 *
 * <p>이 문서는 결제 직전에 고객이 읽고 동의하는 거래 조건이다. 저장하면 쇼핑몰과
 * 주문서에 <b>바로</b> 보이고, 지난 판은 이력에 그대로 남는다 — 이미 만들어진 주문들이
 * "동의한 그 내용"이라 고칠 수 없다.
 */
export function AdminRefundPolicyView() {
  const { can } = useAdminSession();
  const allowed = can('ADMIN');

  if (!allowed) {
    return (
      <div className={styles.page}>
        <Alert tone="warning" title="권한이 없습니다">
          환불규정은 모든 주문의 거래 조건이라 관리자(ADMIN) 이상만 고칠 수 있습니다.
        </Alert>
      </div>
    );
  }
  return <Editor />;
}

function Editor() {
  const current = useRefundPolicy();
  const publish = usePublishRefundPolicy();

  // 현재 판을 불러온 뒤 한 번만 편집칸에 채운다. 쿼리가 다시 돌 때마다 덮어쓰면
  // 고치던 내용이 사라진다.
  const [draft, setDraft] = useState<string | null>(null);
  const [changeNote, setChangeNote] = useState('');

  const content = draft ?? current.data?.content ?? '';
  const unchanged = current.data != null && content.trim() === current.data.content;
  const canSubmit =
    current.data != null &&
    content.trim().length > 0 &&
    changeNote.trim().length > 0 &&
    !unchanged &&
    !publish.isPending;

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <div>
          <h1 className={styles.title}>환불규정</h1>
          <p className={styles.subtitle}>
            결제 직전에 고객이 읽고 동의하는 문서다. 저장하면 쇼핑몰과 주문서에 바로
            보인다. 지난 판은 그 판에 동의한 주문들의 증거라 고칠 수 없고, 이력으로 남는다.
          </p>
        </div>
      </header>

      <Panel
        title={
          current.data
            ? `현재 ${current.data.id}판 · ${current.data.createdBy} · ${formatDateTime(current.data.createdAt)}`
            : '현재 판'
        }
        note="수정 후 「새 판으로 발행」을 누르면 적용된다."
      >
        {current.isLoading ? (
          <p className={styles.loading}>불러오는 중…</p>
        ) : !current.data ? (
          <Alert tone="danger">
            {current.error instanceof ApiError
              ? current.error.message
              : '환불규정을 불러오지 못했습니다.'}
          </Alert>
        ) : (
          <form
            className={styles.form}
            onSubmit={(e) => {
              e.preventDefault();
              if (!canSubmit) return;
              publish.mutate(
                { content: content.trim(), changeNote: changeNote.trim() },
                {
                  onSuccess: () => {
                    setDraft(null);
                    setChangeNote('');
                  },
                },
              );
            }}
          >
            {publish.error && (
              <Alert tone="danger">
                {publish.error instanceof ApiError
                  ? publish.error.message
                  : '발행하지 못했습니다.'}
              </Alert>
            )}
            {publish.isSuccess && !draft && (
              <Alert tone="success">
                새 판을 발행했다. 쇼핑몰과 주문서에 바로 반영된다.
              </Alert>
            )}

            <Field label="규정 전문" htmlFor="rp-content" required>
              <TextArea
                id="rp-content"
                rows={22}
                value={content}
                onChange={(e) => setDraft(e.target.value)}
              />
            </Field>

            <Field
              label="개정 사유"
              htmlFor="rp-note"
              required
              hint="감사로그와 개정 이력에 그대로 남는다."
            >
              <TextInput
                id="rp-note"
                maxLength={200}
                placeholder="예: 반품 검수 기간을 7일로 명시"
                value={changeNote}
                onChange={(e) => setChangeNote(e.target.value)}
              />
            </Field>

            <div className={styles.formActions}>
              {draft != null && !unchanged && (
                <Button type="button" onClick={() => setDraft(null)} disabled={publish.isPending}>
                  고친 내용 버리기
                </Button>
              )}
              <Button
                type="submit"
                variant="gold"
                disabled={!canSubmit}
                title={unchanged ? '내용이 현재 판과 같습니다.' : undefined}
              >
                {publish.isPending ? '발행 중…' : '새 판으로 발행'}
              </Button>
            </div>
          </form>
        )}
      </Panel>

      <History />
    </div>
  );
}

function History() {
  const [page, setPage] = useState(0);
  const { data, isLoading } = useRefundPolicyHistory(page);
  const [openedId, setOpenedId] = useState<number | null>(null);

  const rows = data?.content ?? [];

  return (
    <Panel title="개정 이력" flush>
      {isLoading ? (
        <p className={styles.loading}>불러오는 중…</p>
      ) : rows.length === 0 ? (
        <p className={styles.empty}>이력이 없습니다.</p>
      ) : (
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th className={styles.num}>판</th>
                <th>개정 사유</th>
                <th>발행자</th>
                <th>발행 시각</th>
                <th aria-label="본문" />
              </tr>
            </thead>
            <tbody>
              {rows.map((v) => (
                <Fragment key={v.id}>
                  <tr>
                    <td data-label="판" className={`${styles.num} ${styles.strong}`}>
                      {v.id}
                    </td>
                    <td data-label="개정 사유" data-card-title>{v.changeNote}</td>
                    <td data-label="발행자" className={styles.muted}>{v.createdBy}</td>
                    <td data-label="발행 시각" className={styles.muted}>
                      {formatDateTime(v.createdAt)}
                    </td>
                    <td data-label="본문" data-card-actions>
                      <Button
                        small
                        onClick={() => setOpenedId(openedId === v.id ? null : v.id)}
                      >
                        {openedId === v.id ? '본문 닫기' : '본문 보기'}
                      </Button>
                    </td>
                  </tr>
                  {openedId === v.id && (
                    <tr>
                      <td colSpan={5}>
                        <p className={styles.preserveLines}>{v.content}</p>
                      </td>
                    </tr>
                  )}
                </Fragment>
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
  );
}
