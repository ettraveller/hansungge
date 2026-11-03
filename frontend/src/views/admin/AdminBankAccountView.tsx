'use client';

import { useState } from 'react';

import { Alert } from '@/components/admin/Alert';
import { Button } from '@/components/admin/Button';
import { Field, TextInput } from '@/components/admin/Field';
import { Panel } from '@/components/admin/Panel';
import { useAdminSession } from '@/hooks/admin/useAdminSession';
import { useBankAccount, useSaveBankAccount } from '@/hooks/admin/useSettings';
import { ApiError } from '@/types/api';
import styles from '@/components/admin/admin.module.css';

/**
 * 무통장입금 계좌 (`/admin/settings/bank-account`).
 *
 * <h2>왜 이 화면이 따로 있나</h2>
 * 순금·순은·골드바·24K·시세 연동 상품은 카드사가 온라인 거래를 제한해 카드로 팔 수
 * 없다 (2026-11-03 토스페이먼츠 회신). 그 상품들은 전부 이 계좌로 들어온다 —
 * 가게 매출의 큰 쪽이 이 네 칸에 달려 있다.
 *
 * <h2>비우면 꺼진다</h2>
 * 은행·계좌번호·예금주 중 하나라도 비면 서버가 무통장 주문을 받지 않는다. 「끄기」
 * 버튼을 따로 두지 않는 이유 — 입금받을 곳이 없는데 켜 둘 수 있는 상태를 만들지
 * 않는다. 켜고 끄는 일과 계좌를 적는 일이 같은 동작이다.
 */
export function AdminBankAccountView() {
  const { can } = useAdminSession();
  const canEdit = can('ADMIN');
  const { data, isLoading, error } = useBankAccount();
  const save = useSaveBankAccount();

  // null = 아직 아무것도 고치지 않았다 → 서버 값을 그대로 보여준다.
  // effect 로 맞추지 않는다 — 다시 불려올 때마다 덮어쓰면 적던 계좌번호가 사라진다.
  const [draft, setDraft] = useState<{
    bank: string;
    number: string;
    holder: string;
    depositDueHours: string;
  } | null>(null);

  const current = {
    bank: data?.bank ?? '',
    number: data?.number ?? '',
    holder: data?.holder ?? '',
    depositDueHours: String(data?.depositDueHours ?? 3),
  };
  const form = draft ?? current;
  const patch = (next: Partial<typeof current>) => setDraft({ ...form, ...next });

  // 셋이 모두 차야 무통장이 켜진다. 서버 판정과 같은 식이다.
  const willBeOn =
    form.bank.trim() !== '' && form.number.trim() !== '' && form.holder.trim() !== '';
  const isOn =
    current.bank.trim() !== '' && current.number.trim() !== '' && current.holder.trim() !== '';
  const changed = draft != null && JSON.stringify(draft) !== JSON.stringify(current);

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
        <Alert tone="danger" title="설정을 불러오지 못했습니다">
          {error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요.'}
        </Alert>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <div>
          <h1 className={styles.title}>무통장입금 계좌</h1>
          <p className={styles.subtitle}>
            순금·순은·골드바·24K와 시세 연동 상품은 카드로 결제할 수 없어 이 계좌로 받는다.
          </p>
        </div>
      </header>

      {/* 지금 켜져 있는지부터 알린다. 네 칸을 보고 머릿속으로 판정하게 두면,
          계좌번호 한 칸이 빈 것을 못 보고 "왜 주문이 안 들어오지" 가 된다. */}
      {isOn ? (
        <Alert tone="success">
          무통장입금을 받고 있다. 고객 주문서에 이 계좌가 안내된다.
        </Alert>
      ) : (
        <Alert tone="warning" title="무통장입금이 꺼져 있다">
          은행·계좌번호·예금주를 모두 채워야 켜진다. 지금은 순금·골드바 등 카드로 팔 수
          없는 상품을 <strong>아무도 주문할 수 없다.</strong>
        </Alert>
      )}

      <Panel
        title="계좌"
        note="셋 중 하나라도 비우면 무통장입금이 꺼진다 — 계좌를 내리는 길이 그것이다."
      >
        {!canEdit ? (
          <Alert tone="info">계좌는 ADMIN 이상만 바꿀 수 있다.</Alert>
        ) : (
          <form
            className={styles.form}
            onSubmit={(e) => {
              e.preventDefault();
              if (!changed || save.isPending) return;
              save.mutate(
                {
                  bank: form.bank.trim(),
                  number: form.number.trim(),
                  holder: form.holder.trim(),
                  depositDueHours: Number(form.depositDueHours) || 3,
                },
                { onSuccess: () => setDraft(null) },
              );
            }}
          >
            {save.error && (
              <Alert tone="danger">
                {save.error instanceof ApiError ? save.error.message : '저장하지 못했습니다.'}
              </Alert>
            )}
            {save.isSuccess && draft == null && (
              <Alert tone="success">저장했다. 고객 주문서에 바로 반영된다.</Alert>
            )}

            <Field label="은행" htmlFor="ba-bank">
              <TextInput
                id="ba-bank"
                maxLength={40}
                placeholder="예: 국민은행"
                value={form.bank}
                onChange={(e) => patch({ bank: e.target.value })}
              />
            </Field>

            <Field
              label="계좌번호"
              htmlFor="ba-number"
              hint="고객 화면에 적힌 그대로 보인다. 하이픈을 넣어 두면 받아 적기 쉽다."
            >
              <TextInput
                id="ba-number"
                maxLength={40}
                placeholder="예: 123456-78-901234"
                value={form.number}
                onChange={(e) => patch({ number: e.target.value })}
              />
            </Field>

            <Field
              label="예금주"
              htmlFor="ba-holder"
              hint="통장에 찍히는 이름과 같아야 한다. 다르면 고객이 보내기 전에 멈춘다."
            >
              <TextInput
                id="ba-holder"
                maxLength={60}
                placeholder="예: 주식회사 금한성"
                value={form.holder}
                onChange={(e) => patch({ holder: e.target.value })}
              />
            </Field>

            <Field
              label="입금 안내 기한 (시간)"
              htmlFor="ba-hours"
              hint="고객 화면에 「N시간 이내에 입금해 주세요」로 안내된다. 기한이 지나도 주문을 자동으로 취소하지 않는다 — 입금하려는 사이에 취소되면 보낸 돈이 갈 곳을 잃는다. 지난 주문은 주문 상세에 「기한 지남」으로 표시되고, 취소는 사람이 누른다."
            >
              <TextInput
                id="ba-hours"
                inputMode="numeric"
                maxLength={3}
                value={form.depositDueHours}
                onChange={(e) => patch({ depositDueHours: e.target.value.replace(/[^0-9]/g, '') })}
              />
            </Field>

            {/* 저장을 누르기 전에 결과를 말해 준다. 켜져 있던 것을 끄는 것은
                되돌리기는 쉽지만, 끈 줄 모르고 지나가면 주문이 끊긴다. */}
            {changed && isOn && !willBeOn && (
              <Alert tone="warning">
                저장하면 무통장입금이 <strong>꺼진다.</strong> 카드로 팔 수 없는 상품을
                주문할 수 없게 된다.
              </Alert>
            )}

            <div className={styles.formActions}>
              {changed && (
                <Button type="button" onClick={() => setDraft(null)} disabled={save.isPending}>
                  고친 내용 버리기
                </Button>
              )}
              <Button type="submit" variant="primary" disabled={!changed || save.isPending}>
                {save.isPending ? '저장 중…' : '저장'}
              </Button>
            </div>
          </form>
        )}
      </Panel>
    </div>
  );
}
