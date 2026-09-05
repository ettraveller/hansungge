'use client';

import { useState } from 'react';

import { Alert } from '@/components/admin/Alert';
import { Button } from '@/components/admin/Button';
import { Field, TextArea } from '@/components/admin/Field';
import { Modal } from '@/components/admin/Modal';
import { Pagination } from '@/components/admin/Pagination';
import { Panel } from '@/components/admin/Panel';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useAdminSession } from '@/hooks/admin/useAdminSession';
import { useAdminQnas, useAnswerQna, useHideQna } from '@/hooks/admin/useQnas';
import type { AdminQna } from '@/api/admin/qnas';
import { ApiError } from '@/types/api';
import type { QnaBoardType } from '@/types/board';
import { formatDateTime } from '@/utils/format';
import styles from '@/components/admin/admin.module.css';

type BoardFilter = QnaBoardType | 'ALL';

const BOARD_LABEL: Record<QnaBoardType, string> = {
  GENERAL: '문의게시판',
  PRODUCT: '제품문의',
};

/**
 * 문의 관리 — 답변을 다는 곳.
 *
 * <p>이 화면이 없으면 <b>게시판이 굴러가지 않는다.</b> 고객이 묻고 아무도 답할 수 없는
 * 상태가 되고, 그 상태는 목록에 쌓인 "답변대기"로만 드러난다.
 *
 * <p>기본 필터를 <b>미답변만</b>으로 둔 이유가 그것이다. 전체 목록을 먼저 보여주면
 * 답변된 글에 묻혀 밀린 문의가 눈에 띄지 않는다. 이 화면을 여는 이유는 대개 하나다 —
 * 답할 것이 남았는지 보려고.
 *
 * <p>비밀글도 여기서는 본문이 보인다. 답을 하려면 질문을 읽어야 한다.
 */
export function AdminQnasView() {
  const { can } = useAdminSession();
  const canAnswer = can('MANAGER');

  const [board, setBoard] = useState<BoardFilter>('ALL');
  const [unansweredOnly, setUnansweredOnly] = useState(true);
  const [page, setPage] = useState(0);

  const [answering, setAnswering] = useState<AdminQna | null>(null);
  const [hiding, setHiding] = useState<AdminQna | null>(null);

  const { data, isLoading, error } = useAdminQnas({
    boardType: board === 'ALL' ? null : board,
    unansweredOnly,
    page,
  });

  const rows = data?.content ?? [];

  /** 필터를 바꾸면 첫 페이지로 돌아간다 — 3페이지에서 필터를 바꾸면 빈 화면이 나온다. */
  const changeFilter = (next: () => void) => {
    next();
    setPage(0);
  };

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <div>
          <h1 className={styles.title}>문의 관리</h1>
          <p className={styles.subtitle}>
            문의게시판과 제품문의에 답변합니다. 답변은 문의당 하나이며, 다시 저장하면
            덮어씁니다.
          </p>
        </div>
      </header>

      {!canAnswer && (
        <Alert tone="info">
          답변과 내리기는 MANAGER 이상이 할 수 있습니다. 조회만 가능합니다.
        </Alert>
      )}

      <Panel
        title="문의 목록"
        note="비밀글도 여기서는 본문이 보입니다 — 답하려면 질문을 읽어야 합니다."
        actions={
          <div className={styles.filters}>
            <select
              className={styles.select}
              value={board}
              onChange={(e) => changeFilter(() => setBoard(e.target.value as BoardFilter))}
              aria-label="게시판"
            >
              <option value="ALL">전체 게시판</option>
              <option value="GENERAL">{BOARD_LABEL.GENERAL}</option>
              <option value="PRODUCT">{BOARD_LABEL.PRODUCT}</option>
            </select>
            <label className={styles.checkRow}>
              <input
                type="checkbox"
                checked={unansweredOnly}
                onChange={(e) => changeFilter(() => setUnansweredOnly(e.target.checked))}
              />
              미답변만
            </label>
          </div>
        }
        flush
      >
        {error ? (
          <Alert tone="danger" title="문의를 불러오지 못했습니다">
            {error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요.'}
          </Alert>
        ) : isLoading ? (
          <p className={styles.empty}>불러오는 중…</p>
        ) : rows.length === 0 ? (
          <p className={styles.empty}>
            {unansweredOnly ? '답변을 기다리는 문의가 없습니다.' : '문의가 없습니다.'}
          </p>
        ) : (
          <table className={styles.table}>
            <thead>
              <tr>
                <th scope="col">게시판</th>
                <th scope="col">제목</th>
                <th scope="col">작성자</th>
                <th scope="col">상태</th>
                <th scope="col">등록</th>
                <th scope="col">작업</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((qna) => (
                <tr key={qna.id} className={qna.active ? undefined : styles.muted}>
                  <td>{BOARD_LABEL[qna.boardType]}</td>
                  <td>
                    <span className={styles.wrapText}>
                      {qna.secret && <span className={styles.tag}>비밀</span>}
                      {qna.title}
                    </span>
                    {qna.productName && (
                      <span className={styles.cellSub}>{qna.productName}</span>
                    )}
                    {!qna.active && <span className={styles.cellSub}>내려진 글</span>}
                  </td>
                  <td>{qna.authorName}</td>
                  <td>
                    <StatusBadge tone={qna.status === 'ANSWERED' ? 'success' : 'warning'}>
                      {qna.status === 'ANSWERED' ? '답변완료' : '답변대기'}
                    </StatusBadge>
                  </td>
                  <td className="tabular">{formatDateTime(qna.createdAt)}</td>
                  <td>
                    <div className={styles.rowActions}>
                      <Button small onClick={() => setAnswering(qna)}>
                        {qna.status === 'ANSWERED' ? '답변 보기' : '답변'}
                      </Button>
                      {canAnswer && qna.active && (
                        <Button small variant="danger" onClick={() => setHiding(qna)}>
                          내리기
                        </Button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
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

      {answering && (
        <AnswerDialog
          qna={answering}
          readOnly={!canAnswer}
          onClose={() => setAnswering(null)}
        />
      )}
      {hiding && <HideDialog qna={hiding} onClose={() => setHiding(null)} />}
    </div>
  );
}

/* ------------------------------------------------------------------ */

function AnswerDialog({
  qna,
  readOnly,
  onClose,
}: {
  qna: AdminQna;
  readOnly: boolean;
  onClose: () => void;
}) {
  const [answer, setAnswer] = useState(qna.answer ?? '');
  const answerQna = useAnswerQna();

  const submit = () => {
    if (!answer.trim()) return;
    answerQna.mutate({ id: qna.id, answer: answer.trim() }, { onSuccess: onClose });
  };

  return (
    <Modal
      open
      title={qna.status === 'ANSWERED' ? '답변 수정' : '답변 등록'}
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose}>닫기</Button>
          {!readOnly && (
            <Button
              variant="gold"
              disabled={answerQna.isPending || !answer.trim()}
              onClick={submit}
            >
              {answerQna.isPending ? '저장 중…' : '답변 저장'}
            </Button>
          )}
        </>
      }
    >
      <dl className={styles.detailList}>
        <div>
          <dt>제목</dt>
          <dd>{qna.title}</dd>
        </div>
        <div>
          <dt>작성자</dt>
          <dd>
            {qna.authorName}
            {qna.secret && <span className={styles.tag}>비밀글</span>}
          </dd>
        </div>
        {qna.productName && (
          <div>
            <dt>상품</dt>
            <dd>{qna.productName}</dd>
          </div>
        )}
        <div>
          <dt>문의 내용</dt>
          <dd className={styles.preserveLines}>{qna.question}</dd>
        </div>
        {qna.answeredAt && (
          <div>
            <dt>기존 답변</dt>
            <dd>
              {qna.answeredBy} · {formatDateTime(qna.answeredAt)}
            </dd>
          </div>
        )}
      </dl>

      {answerQna.error && (
        <Alert tone="danger" title="답변을 저장하지 못했습니다">
          {answerQna.error instanceof ApiError
            ? answerQna.error.message
            : '잠시 후 다시 시도해 주세요.'}
        </Alert>
      )}

      <Field label="답변" hint="고객 화면에 그대로 보입니다. 저장하면 즉시 공개됩니다.">
        <TextArea
          value={answer}
          rows={7}
          disabled={readOnly || answerQna.isPending}
          onChange={(e) => setAnswer(e.target.value)}
          placeholder="답변을 입력하세요"
        />
      </Field>
    </Modal>
  );
}

function HideDialog({ qna, onClose }: { qna: AdminQna; onClose: () => void }) {
  const hideQna = useHideQna();

  return (
    <Modal
      open
      title="문의 내리기"
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose}>취소</Button>
          <Button
            variant="danger"
            disabled={hideQna.isPending}
            onClick={() => hideQna.mutate(qna.id, { onSuccess: onClose })}
          >
            {hideQna.isPending ? '처리 중…' : '내리기'}
          </Button>
        </>
      }
    >
      <p className={styles.dialogText}>
        <strong>{qna.title}</strong> 을(를) 공개 목록에서 내립니다.
      </p>
      <Alert tone="warning">
        글은 지워지지 않고 노출만 중단됩니다. 무엇을 왜 내렸는지가 남아야 하기 때문입니다.
      </Alert>
      {hideQna.error && (
        <Alert tone="danger" title="내리지 못했습니다">
          {hideQna.error instanceof ApiError
            ? hideQna.error.message
            : '잠시 후 다시 시도해 주세요.'}
        </Alert>
      )}
    </Modal>
  );
}
