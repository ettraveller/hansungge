'use client';

import { useState } from 'react';

import { Alert } from '../Alert';
import { Button } from '../Button';
import { Field, TextInput } from '../Field';
import { Panel } from '../Panel';
import type { FooterInfo } from '@/api/footer';
import { useFooterInfo, useSaveFooter } from '@/hooks/admin/useFooter';
import { useAdminSession } from '@/hooks/admin/useAdminSession';
import { ApiError } from '@/types/api';
import styles from '../admin.module.css';

/**
 * 고칠 수 있는 칸 목록.
 *
 * <p>원천은 백엔드의 {@code FooterField} 다. 칸마다 입력칸을 손으로 적지 않고 이
 * 목록을 돌린다 — 칸을 더하는 일이 한 줄이 되고, 라벨만 적어 두고 저장을 빠뜨리는
 * 실수가 생기지 않는다.
 *
 * <p>{@code max} 는 서버가 자르는 길이와 같다. 화면에서 먼저 막으면 "저장했는데
 * 뒤가 잘렸다" 를 겪지 않는다.
 */
const FOOTER_FIELDS: {
  name: keyof FooterInfo;
  label: string;
  max: number;
  hint?: string;
}[] = [
  { name: 'companyName', label: '상호', max: 80, hint: '법인명이다. 로고의 「한성금거래소」는 브랜드명이라 법적 표시가 되지 않는다.' },
  { name: 'ceo', label: '대표자', max: 40 },
  { name: 'bizNo', label: '사업자등록번호', max: 40 },
  {
    name: 'mailOrderNo',
    label: '통신판매업신고번호',
    max: 60,
    hint: '아직 받지 못했다. 비워 두면 그 줄을 아예 그리지 않는다 — 받는 날 여기 적으면 되고 배포가 필요 없다.',
  },
  {
    name: 'address',
    label: '사업장 소재지',
    max: 200,
    hint: '사업자등록증과 같아야 한다 (전자상거래법 §13). 이용약관·개인정보처리방침에도 같은 주소가 있어 그쪽은 따로 고쳐야 한다.',
  },
  { name: 'email', label: '이메일', max: 120 },
  { name: 'tel', label: '대표전화', max: 40, hint: '고객센터 화면의 번호는 아직 따로 관리된다.' },
  { name: 'hours', label: '운영시간', max: 80, hint: '예: 월-토 10:00 ~ 19:00' },
  { name: 'holiday', label: '휴무', max: 80, hint: '비우면 그 줄을 그리지 않는다.' },
  {
    name: 'tagline',
    label: '한 줄 문구',
    max: 200,
    hint: '로고 아래 작은 글씨. 비우면 그 줄을 그리지 않는다.',
  },
];

/**
 * 푸터 글자 편집.
 *
 * <h2>왜 배너와 같은 화면인가</h2>
 * 둘 다 <b>손님 화면에 그대로 나가는 겉모습</b>이고, 배너를 바꾸러 들어온 사람이
 * 그 아래 푸터까지 함께 확인하게 된다. 미리보기가 메인 한 화면을 통째로 띄우니
 * 배너와 푸터가 같은 그림 안에 있다 — 고치는 자리도 같은 화면에 둔다.
 *
 * <h2>비우면 기본값으로 돌아간다</h2>
 * 상호·주소처럼 법으로 표시해야 하는 칸은 빈칸으로 둘 수 없다. 지우고 저장하면
 * 코드에 적힌 기본값이 다시 나간다 — 푸터가 통째로 비는 것보다 낫다. 비워 두는 것이
 * 정답인 칸(통신판매업신고번호·휴무·한 줄 문구)은 기본값이 비어 있어 그대로 지워진다.
 */
export function FooterPanel() {
  const { can } = useAdminSession();
  const canEdit = can('MANAGER');

  const { data, isLoading, error } = useFooterInfo();
  const save = useSaveFooter();

  /** 손대기 전에는 null 이고, 그때는 서버가 준 것을 그대로 쓴다. */
  const [draft, setDraft] = useState<FooterInfo | null>(null);
  const [saved, setSaved] = useState(false);

  const info = draft ?? data ?? null;
  const changed = draft != null;

  const edit = (name: keyof FooterInfo, value: string) => {
    if (!info) return;
    setDraft({ ...info, [name]: value });
    setSaved(false);
  };

  const submit = () => {
    if (!info) return;
    setSaved(false);
    save.mutate(info, {
      onSuccess: () => {
        setDraft(null);
        setSaved(true);
      },
    });
  };

  return (
    <Panel
      title="푸터"
      note="모든 화면 맨 아래에 나가는 회사 정보다. 저장하면 바로 반영된다."
      actions={
        canEdit && (
          <Button variant="gold" onClick={submit} disabled={!changed || save.isPending}>
            {save.isPending ? '저장 중…' : '저장'}
          </Button>
        )
      }
    >
      {error && (
        <Alert tone="danger" title="불러오지 못했습니다">
          {error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요.'}
        </Alert>
      )}
      {save.error && (
        <Alert tone="danger" title="저장하지 못했습니다">
          {save.error instanceof ApiError ? save.error.message : '잠시 후 다시 시도해 주세요.'}
        </Alert>
      )}
      {saved && !changed && (
        <Alert tone="success" title="저장했습니다">
          손님 화면 푸터에 바로 반영됩니다.
        </Alert>
      )}

      {isLoading || !info ? (
        <p className={styles.hint}>불러오는 중…</p>
      ) : (
        <div className={styles.footerFields}>
          {FOOTER_FIELDS.map((field) => (
            <Field
              key={field.name}
              label={field.label}
              htmlFor={`footer-${field.name}`}
              hint={field.hint}
            >
              <TextInput
                id={`footer-${field.name}`}
                value={info[field.name]}
                maxLength={field.max}
                disabled={!canEdit}
                onChange={(e) => edit(field.name, e.target.value)}
              />
            </Field>
          ))}
        </div>
      )}
    </Panel>
  );
}
