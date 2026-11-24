'use client';

import { useMemo, useState } from 'react';

import {
  MAX_FAQS,
  MAX_FAQ_ANSWER,
  MAX_FAQ_CATEGORY,
  MAX_FAQ_QUESTION,
  type Faq,
} from '@/api/faqs';
import { Alert } from '@/components/admin/Alert';
import { Button } from '@/components/admin/Button';
import { TextArea, TextInput } from '@/components/admin/Field';
import { Panel } from '@/components/admin/Panel';
import { useAdminSession } from '@/hooks/admin/useAdminSession';
import { useAdminFaqs, useReplaceFaqs } from '@/hooks/admin/useFaqs';
import { ApiError } from '@/types/api';
import styles from '@/components/admin/admin.module.css';

/** 고치는 중인 한 줄. uid 는 화면이 줄을 알아보는 이름이다 (자리 번호를 key 로 쓰지 않는다). */
interface Row {
  uid: string;
  category: string;
  question: string;
  answer: string;
  active: boolean;
}

let nextUid = 0;
const newUid = () => `faq-${nextUid++}`;

const toRow = (faq: Faq): Row => ({
  uid: newUid(),
  category: faq.category,
  question: faq.question,
  answer: faq.answer,
  active: faq.active,
});

/** 저장을 막을 이유. 없으면 null. 첫 번째 것만 알린다. */
function problemOf(rows: Row[]): string | null {
  for (let i = 0; i < rows.length; i += 1) {
    const where = `${i + 1}번째 질문`;
    if (rows[i].category.trim() === '') return `${where}에 묶음 이름을 적어 주세요. (예: 주문·결제)`;
    if (rows[i].question.trim() === '') return `${where}에 질문을 적어 주세요.`;
    if (rows[i].answer.trim() === '') return `${where}에 답을 적어 주세요.`;
  }
  return null;
}

/**
 * 자주 묻는 질문 관리 (`/admin/faqs`).
 *
 * <h2>통째로 저장한다</h2>
 * 줄을 더하고 옮기고 지운 뒤 「저장」을 한 번 누른다. 그 전에는 손님 화면이 바뀌지
 * 않는다 — 차례를 바꾸는 도중의 목록이 걸리지 않는다.
 *
 * <h2>묶음은 이름으로 정한다</h2>
 * 묶음 이름이 같은 질문끼리 손님 화면에서 한 덩어리로 모인다. 묶음을 따로 만들어
 * 고르게 하지 않는 이유는, 질문이 스무 개 남짓인 화면에 묶음 관리 화면을 하나 더
 * 두는 것이 값을 못 하기 때문이다. 이미 쓴 이름은 입력칸이 제안한다.
 */
export function AdminFaqsView() {
  const { can } = useAdminSession();
  const canEdit = can('MANAGER');

  const { data, isLoading, error } = useAdminFaqs();
  const replace = useReplaceFaqs();

  const [draft, setDraft] = useState<Row[] | null>(null);
  const [saved, setSaved] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);

  // 받은 목록이 바뀔 때만 uid 를 새로 붙인다. 그릴 때마다 붙이면 입력칸이 다시 만들어진다.
  const loaded = useMemo(() => (data ?? []).map(toRow), [data]);
  const rows = draft ?? loaded;
  const categories = [...new Set(rows.map((r) => r.category.trim()).filter(Boolean))];

  const update = (next: Row[]) => {
    setDraft(next);
    setSaved(false);
    setProblem(null);
  };

  const edit = (uid: string, patch: Partial<Row>) =>
    update(rows.map((row) => (row.uid === uid ? { ...row, ...patch } : row)));

  const move = (index: number, by: -1 | 1) => {
    const target = index + by;
    if (target < 0 || target >= rows.length) return;
    const next = [...rows];
    [next[index], next[target]] = [next[target], next[index]];
    update(next);
  };

  const save = () => {
    const found = problemOf(rows);
    setProblem(found);
    if (found) return;
    replace.mutate(
      rows.map((row) => ({
        category: row.category.trim(),
        question: row.question.trim(),
        answer: row.answer.trim(),
        active: row.active,
      })),
      {
        onSuccess: () => {
          setDraft(null);
          setSaved(true);
        },
      },
    );
  };

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <div>
          <h1 className={styles.title}>자주 묻는 질문</h1>
          <p className={styles.subtitle}>
            고객센터의 「자주 묻는 질문」에 나가는 글이다. 묶음 이름이 같은 질문끼리 모여서
            보이고, 위에서부터 차례대로 나간다. 하나도 내걸지 않으면 고객센터에 메뉴가 뜨지
            않는다.
          </p>
        </div>
      </header>

      {error && (
        <Alert tone="danger" title="불러오지 못했습니다">
          {error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요.'}
        </Alert>
      )}
      {problem && (
        <Alert tone="danger" title="저장하기 전에 고쳐 주세요">
          {problem}
        </Alert>
      )}
      {replace.error && (
        <Alert tone="danger" title="저장하지 못했습니다">
          {replace.error instanceof ApiError ? replace.error.message : '잠시 후 다시 시도해 주세요.'}
        </Alert>
      )}
      {saved && draft == null && (
        <Alert tone="success" title="저장했습니다">
          손님 화면에 바로 반영됩니다.
        </Alert>
      )}

      <Panel
        title={`질문 ${rows.length}건`}
        note="답은 적은 글자 그대로 나간다. 줄을 바꾸면 손님 화면에서도 줄이 바뀐다."
        actions={
          canEdit && (
            <Button variant="gold" onClick={save} disabled={draft == null || replace.isPending}>
              {replace.isPending ? '저장 중…' : '저장'}
            </Button>
          )
        }
      >
        {/* 이미 쓴 묶음 이름을 제안한다. 「배송」과 「배송 」처럼 어긋나면 묶음이 둘로 갈린다. */}
        <datalist id="faq-categories">
          {categories.map((name) => (
            <option key={name} value={name} />
          ))}
        </datalist>

        {isLoading ? (
          <p className={styles.hint}>불러오는 중…</p>
        ) : rows.length === 0 ? (
          <p className={styles.hint}>
            아직 적은 질문이 없다. 주문·결제, 배송, 교환·반품, 매입처럼 자주 받는 질문부터 적어
            두면 문의가 줄어든다.
          </p>
        ) : (
          <ol className={styles.sectionRows}>
            {rows.map((row, index) => {
              const name = row.question.trim() || `${index + 1}번째 질문`;
              return (
                <li key={row.uid} className={row.active ? styles.sectionRow : styles.sectionRowOff}>
                  <div className={styles.sectionRowHead}>
                    <span className={styles.bannerNo}>{index + 1}</span>
                    <div className={styles.sectionRowName}>
                      <TextInput
                        aria-label={`${name}의 묶음 이름`}
                        list="faq-categories"
                        value={row.category}
                        maxLength={MAX_FAQ_CATEGORY}
                        disabled={!canEdit}
                        placeholder="묶음 이름 (예: 주문·결제)"
                        onChange={(e) => edit(row.uid, { category: e.target.value })}
                      />
                    </div>
                    <label className={styles.sectionToggle}>
                      <input
                        type="checkbox"
                        checked={row.active}
                        disabled={!canEdit}
                        onChange={(e) => edit(row.uid, { active: e.target.checked })}
                      />
                      {row.active ? '내거는 중' : '내림'}
                    </label>
                    {canEdit && (
                      <div className={styles.sectionRowOrder}>
                        <Button
                          small
                          aria-label={`${name} 위로`}
                          onClick={() => move(index, -1)}
                          disabled={index === 0}
                        >
                          ↑
                        </Button>
                        <Button
                          small
                          aria-label={`${name} 아래로`}
                          onClick={() => move(index, 1)}
                          disabled={index === rows.length - 1}
                        >
                          ↓
                        </Button>
                        <Button
                          small
                          variant="danger"
                          aria-label={`${name} 삭제`}
                          onClick={() => update(rows.filter((r) => r.uid !== row.uid))}
                        >
                          삭제
                        </Button>
                      </div>
                    )}
                  </div>

                  <div className={styles.sectionRowFields}>
                    <TextInput
                      aria-label={`${index + 1}번째 질문`}
                      value={row.question}
                      maxLength={MAX_FAQ_QUESTION}
                      disabled={!canEdit}
                      placeholder="질문 (예: 무통장입금도 되나요?)"
                      onChange={(e) => edit(row.uid, { question: e.target.value })}
                    />
                    <TextArea
                      aria-label={`${name}의 답`}
                      value={row.answer}
                      maxLength={MAX_FAQ_ANSWER}
                      disabled={!canEdit}
                      rows={3}
                      placeholder="답"
                      onChange={(e) => edit(row.uid, { answer: e.target.value })}
                    />
                  </div>
                </li>
              );
            })}
          </ol>
        )}

        {canEdit && (
          <div className={styles.topLinkAdd}>
            <Button
              onClick={() =>
                update([
                  ...rows,
                  {
                    uid: newUid(),
                    // 바로 위 줄의 묶음을 이어 받는다 — 같은 묶음을 연달아 적는 일이 많다
                    category: rows[rows.length - 1]?.category ?? '',
                    question: '',
                    answer: '',
                    active: true,
                  },
                ])
              }
              disabled={rows.length >= MAX_FAQS}
            >
              + 질문 추가
            </Button>
            <span className={styles.hint}>
              {rows.length >= MAX_FAQS
                ? `${MAX_FAQS}건까지 적을 수 있다.`
                : '「저장」을 눌러야 손님 화면에 걸린다.'}
            </span>
          </div>
        )}
      </Panel>
    </div>
  );
}
