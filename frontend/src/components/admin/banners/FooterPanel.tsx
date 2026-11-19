'use client';

import { useState } from 'react';

import { Alert } from '../Alert';
import { Button } from '../Button';
import { Panel } from '../Panel';
import { EditableText } from './EditableText';
import type { FooterInfo } from '@/api/footer';
import { SiteFooter } from '@/layouts/SiteFooter';
import { useFooterInfo, useSaveFooter } from '@/hooks/admin/useFooter';
import { useAdminSession } from '@/hooks/admin/useAdminSession';
import { ApiError } from '@/types/api';
import styles from '../admin.module.css';

/**
 * 칸마다의 이름과 최대 길이.
 *
 * <p>이름은 빈 칸에 흐리게 떠서 "여기가 무엇을 적는 자리인지"를 알려 준다 — 글자를
 * 지우고 나면 그 자리가 어디였는지 보이지 않기 때문이다. 길이는 서버가 자르는 값과
 * 같다. 화면에서 먼저 막으면 "저장했는데 뒤가 잘렸다" 를 겪지 않는다.
 *
 * <p>원천은 백엔드의 {@code FooterField} 다.
 */
const FIELD_META: Record<keyof FooterInfo, { label: string; max: number }> = {
  companyName: { label: '상호', max: 80 },
  ceo: { label: '대표자', max: 40 },
  bizNo: { label: '사업자등록번호', max: 40 },
  mailOrderNo: { label: '통신판매업신고번호', max: 60 },
  address: { label: '사업장 소재지', max: 200 },
  email: { label: '이메일', max: 120 },
  tagline: { label: '한 줄 문구', max: 200 },
  tel: { label: '대표전화', max: 40 },
  hours: { label: '운영시간', max: 80 },
  holiday: { label: '휴무', max: 80 },
};

/**
 * 푸터 편집.
 *
 * <h2>진짜 푸터를 띄워 놓고 그 글자를 고친다</h2>
 * 입력칸을 늘어놓은 폼은 <b>고칠 때 보는 모양</b>과 <b>손님이 보는 모양</b>이 다르다.
 * 주소를 한 줄 늘렸을 때 줄이 어떻게 접히는지, 운영시간을 길게 적으면 오른쪽 칸이
 * 어떻게 밀리는지는 푸터 안에서만 보인다. 그래서 손님 화면과 <b>같은 컴포넌트</b>를
 * 그대로 그리고({@link SiteFooter}) 글자 자리만 편집기로 바꿔 끼운다.
 *
 * <h2>비워 두면 어떻게 되는가</h2>
 * 상호·주소처럼 법으로 표시해야 하는 칸은 지우고 저장하면 기본값이 다시 나간다 —
 * 푸터가 통째로 비는 것보다 낫다. 비워 두는 것이 정답인 칸(통신판매업신고번호·휴무·
 * 한 줄 문구)은 기본값이 비어 있어 그대로 지워지고, 손님 화면에서는 그 줄이 사라진다.
 * 고치는 동안에는 그 줄도 보인다 — 안 그리면 다시 적을 자리가 없다.
 */
export function FooterPanel({
  draft,
  onDraftChange,
}: {
  /**
   * 고치는 중인 값. 손대기 전에는 null 이고, 그때는 서버가 준 것을 그대로 쓴다.
   *
   * <p>이 상태를 패널이 아니라 <b>화면이</b> 들고 있는 이유는 미리보기다. 「미리보기」는
   * 저장하지 않은 배너와 함께 저장하지 않은 푸터도 보여 줘야 하고, 그 창은 이 패널의
   * 형제다 — 패널 안에 갇힌 값은 거기까지 가지 못한다.
   */
  draft: FooterInfo | null;
  onDraftChange: (draft: FooterInfo | null) => void;
}) {
  const { can } = useAdminSession();
  const canEdit = can('MANAGER');

  const { data, isLoading, error } = useFooterInfo();
  const save = useSaveFooter();

  const [saved, setSaved] = useState(false);

  const info = draft ?? data ?? null;
  const changed = draft != null;

  const submit = () => {
    if (!info) return;
    setSaved(false);
    save.mutate(info, {
      onSuccess: () => {
        onDraftChange(null);
        setSaved(true);
      },
    });
  };

  return (
    <Panel
      title="푸터"
      note="모든 화면 맨 아래에 나가는 그 푸터다. 글자를 눌러서 바로 고친다."
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
        <>
          <p className={styles.hint}>
            글자를 눌러서 바로 고칩니다. 비우면 기본값으로 돌아가고, 통신판매업신고번호 ·
            휴무 · 한 줄 문구는 비우면 손님 화면에서 그 줄이 사라집니다.
          </p>

          <div className={styles.footerCanvas}>
            <SiteFooter
              info={info}
              renderText={
                canEdit
                  ? (name, value) => (
                      <EditableText
                        value={value}
                        label={FIELD_META[name].label}
                        maxLength={FIELD_META[name].max}
                        onChange={(next) => {
                          onDraftChange({ ...info, [name]: next });
                          setSaved(false);
                        }}
                      />
                    )
                  : undefined
              }
            />
          </div>
        </>
      )}
    </Panel>
  );
}
