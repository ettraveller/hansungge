'use client';

import { useState } from 'react';

import { Alert } from '../Alert';
import { Button } from '../Button';
import { DecimalInput, Field, MoneyInput, SelectInput, TextArea } from '../Field';
import { Modal } from '../Modal';
import { Panel } from '../Panel';
import { ReasonDialog } from '../ReasonDialog';
import { usePolicies, useSwitchQuoteMode, useUpdatePolicy } from '@/hooks/admin/usePrices';
import { formatAdjustment, formatDateTime, formatPricePerGram } from '@/utils/format';
import { METAL_LABEL, type MetalType, type PricePolicyMode, type PriceSide } from '@/types/price';
import type { PricePolicy } from '@/types/admin';
import { ApiError } from '@/types/api';
import styles from '../admin.module.css';

const MODE_LABEL: Record<PricePolicyMode, string> = {
  AUTO: '보정 없음',
  FIXED_ADJ: '정액 보정',
  PERCENT_ADJ: '정률 보정',
  MANUAL: '지정가',
};

const MODE_HINT: Record<PricePolicyMode, string> = {
  AUTO: '원본 시세를 그대로 쓴다.',
  FIXED_ADJ: '원본 시세에 정해진 금액을 더한다. 음수면 뺀다.',
  PERCENT_ADJ: '원본 시세에 비율을 적용한다. -7 이면 7% 차감.',
  MANUAL: '원본 시세와 무관하게 지정한 값을 쓴다.',
};

const DECIMAL_PATTERN = /^-?\d{1,13}(\.\d{1,4})?$/;

/** 정책 표시 문구. 방식마다 의미 있는 값이 달라 한 칸에 그대로 몰아 보여주면 오해를 부른다. */
function describe(policy: PricePolicy): string {
  switch (policy.mode) {
    case 'FIXED_ADJ':
      return formatAdjustment(policy.adjAmount);
    case 'PERCENT_ADJ':
      return `${policy.adjPercent > 0 ? '+' : ''}${policy.adjPercent}%`;
    case 'MANUAL':
      return formatPricePerGram(policy.manualPrice);
    default:
      return '-';
  }
}

function sideLabelOf(side: PriceSide): string {
  return side === 'SELL' ? '판매' : '매입';
}

/**
 * 시세 보정 정책.
 *
 * ⚠️ 정책을 바꿔도 <b>이미 발행된 시세는 바뀌지 않는다.</b> 다음 고시부터 반영된다 —
 * append-only 이력이 과거 시점의 판매가를 그대로 증명해야 하기 때문이다.
 */
export function PricePolicyPanel({ canEdit }: { canEdit: boolean }) {
  const { data, isLoading, error } = usePolicies();
  const [editing, setEditing] = useState<PricePolicy | null>(null);
  // 전환 대상과 "무엇으로 바꿀지". 사유를 받아야 하므로 한 번에 실행하지 않는다.
  const [switching, setSwitching] = useState<{ policy: PricePolicy; to: boolean } | null>(null);
  const switchMode = useSwitchQuoteMode();

  return (
    <Panel
      title="보정 정책"
      note="정책을 바꿔도 이미 발행된 시세는 그대로다. 다음 고시부터 반영된다."
      flush
    >
      {error && (
        <div className={styles.panelBody}>
          <Alert tone="danger">
            {error instanceof ApiError ? error.message : '정책을 불러오지 못했습니다.'}
          </Alert>
        </div>
      )}

      {isLoading ? (
        <p className={styles.loading}>불러오는 중…</p>
      ) : !data || data.length === 0 ? (
        <p className={styles.empty}>등록된 정책이 없습니다.</p>
      ) : (
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>금속</th>
                <th>구분</th>
                <th>고시 방식</th>
                <th>보정 방식</th>
                <th className={styles.num}>보정값</th>
                <th>변경자</th>
                <th>변경 시각</th>
                {canEdit && <th aria-label="작업" />}
              </tr>
            </thead>
            <tbody>
              {data.map((policy) => (
                <tr key={policy.id}>
                  <td data-label="금속" data-card-title className={styles.strong}>{METAL_LABEL[policy.metalType]}</td>
                  <td data-label="구분">{sideLabelOf(policy.priceSide)}</td>
                  <td data-label="고시 방식">
                    <QuoteModeCell
                      policy={policy}
                      canEdit={canEdit}
                      onSwitch={(to) => setSwitching({ policy, to })}
                    />
                  </td>
                  <td data-label="보정 방식">{MODE_LABEL[policy.mode]}</td>
                  <td data-label="보정값" className={styles.num}>{describe(policy)}</td>
                  <td data-label="변경자" className={styles.muted}>{policy.updatedBy ?? '-'}</td>
                  <td data-label="변경 시각" className={styles.muted}>{formatDateTime(policy.updatedAt)}</td>
                  {canEdit && (
                    <td data-label="작업" data-card-actions>
                      <Button small onClick={() => setEditing(policy)}>
                        변경
                      </Button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {editing && (
        // key 를 줘서 대상이 바뀌면 폼 상태가 그 정책 기준으로 다시 만들어지게 한다.
        // useEffect 로 되돌리는 것보다 확실하고, 이전 값이 남아 잘못 저장될 여지가 없다.
        <PolicyEditDialog
          key={editing.id}
          policy={editing}
          onClose={() => setEditing(null)}
        />
      )}

      {/*
        전환에도 사유를 받는다. 자동에서 수동으로 내린 기록이 없으면, 나중에
        "왜 그날 시세가 멈춰 있었는가"를 아무도 설명하지 못한다.

        조건부로 마운트한다 — open 을 false 로 걸어두면 닫힌 동안에도 이전 대상의
        상태가 남아, 다음에 열 때 옛 값이 잠깐 비친다.
      */}
      {switching && (
        <ReasonDialog
          key={`${switching.policy.id}-${String(switching.to)}`}
          open
          title={switching.to ? '자동고시로 전환' : '수동고시로 전환'}
          description={
            switching.to
              ? `${METAL_LABEL[switching.policy.metalType]} ${sideLabelOf(switching.policy.priceSide)} — 다음 수집분부터 보정을 적용해 자동으로 발행합니다. 변동률이 허용치를 넘으면 발행하지 않고 승인 대기로 넘어갑니다.`
              : `${METAL_LABEL[switching.policy.metalType]} ${sideLabelOf(switching.policy.priceSide)} — 수집은 계속되지만 발행은 멈춥니다. 시세는 관리자가 직접 고시할 때까지 지금 값 그대로입니다.`
          }
          confirmLabel={switching.to ? '자동고시 시작' : '수동고시로 전환'}
          danger={!switching.to}
          placeholder={switching.to ? '예: 피드 정상 확인, 자동고시 재개' : '예: 피드 이상값 확인, 수동 전환'}
          pending={switchMode.isPending}
          error={
            switchMode.error instanceof ApiError
              ? switchMode.error.message
              : switchMode.error
                ? '고시 방식을 바꾸지 못했습니다.'
                : null
          }
          onConfirm={(reason) =>
            switchMode.mutate(
              {
                metalType: switching.policy.metalType,
                priceSide: switching.policy.priceSide,
                autoQuote: switching.to,
                changeReason: reason,
              },
              { onSuccess: () => setSwitching(null) },
            )
          }
          onClose={() => setSwitching(null)}
        />
      )}
    </Panel>
  );
}

/**
 * 지금 자동인지 수동인지, 그리고 반대로 바꾸는 버튼.
 *
 * <p>토글 스위치를 쓰지 않는다. 스위치는 눌리는 순간 값이 바뀐다고 읽히는데
 * 여기서는 사유를 받는 창이 먼저 뜬다 — 눌렀는데 안 바뀐 것처럼 보인다.
 * 현재 상태를 글로 적고, 바꾸는 것은 버튼으로 둔다.
 */
function QuoteModeCell({
  policy,
  canEdit,
  onSwitch,
}: {
  policy: PricePolicy;
  canEdit: boolean;
  onSwitch: (to: boolean) => void;
}) {
  return (
    <div className={styles.quoteMode}>
      <span className={policy.autoQuote ? styles.badgeAuto : styles.badgeManual}>
        {policy.autoQuote ? '자동' : '수동'}
      </span>
      {canEdit && (
        <Button small onClick={() => onSwitch(!policy.autoQuote)}>
          {policy.autoQuote ? '수동으로' : '자동으로'}
        </Button>
      )}
    </div>
  );
}

function PolicyEditDialog({ policy, onClose }: { policy: PricePolicy; onClose: () => void }) {
  const update = useUpdatePolicy();

  const [mode, setMode] = useState<PricePolicyMode>(policy.mode);
  const [adjAmount, setAdjAmount] = useState(String(policy.adjAmount ?? '0'));
  const [adjPercent, setAdjPercent] = useState(String(policy.adjPercent ?? '0'));
  const [manualPrice, setManualPrice] = useState(
    policy.manualPrice == null ? '' : String(policy.manualPrice),
  );
  const [changeReason, setChangeReason] = useState('');

  const value =
    mode === 'FIXED_ADJ' ? adjAmount : mode === 'PERCENT_ADJ' ? adjPercent : manualPrice;
  const needsValue = mode !== 'AUTO';
  const valueInvalid = needsValue && (value === '' || !DECIMAL_PATTERN.test(value));
  // 지정가는 0 이하가 될 수 없다 — 어느 경로로도 가격을 0/NULL 로 만들지 않는다 (원칙 P6).
  const manualInvalid = mode === 'MANUAL' && !valueInvalid && Number(manualPrice) <= 0;

  const canSubmit =
    !valueInvalid && !manualInvalid && changeReason.trim().length > 0 && !update.isPending;

  const submit = () => {
    if (!canSubmit) return;
    update.mutate(
      {
        metalType: policy.metalType,
        priceSide: policy.priceSide,
        mode,
        adjAmount: mode === 'FIXED_ADJ' ? adjAmount : null,
        adjPercent: mode === 'PERCENT_ADJ' ? adjPercent : null,
        manualPrice: mode === 'MANUAL' ? manualPrice : null,
        changeReason: changeReason.trim(),
      },
      { onSuccess: onClose },
    );
  };

  return (
    <Modal
      open
      title={`${METAL_LABEL[policy.metalType as MetalType]} · ${sideLabelOf(policy.priceSide)} 보정 정책`}
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose} disabled={update.isPending}>
            취소
          </Button>
          <Button variant="primary" disabled={!canSubmit} onClick={submit}>
            {update.isPending ? '저장 중…' : '저장'}
          </Button>
        </>
      }
    >
      <div className={styles.form}>
        {update.error && (
          <Alert tone="danger">
            {update.error instanceof ApiError
              ? update.error.message
              : '정책을 저장하지 못했습니다.'}
          </Alert>
        )}

        <Alert tone="info">
          저장해도 현재 고시된 시세는 바뀌지 않는다. 다음 고시부터 적용된다.
        </Alert>

        <Field label="보정 방식" htmlFor="policy-mode" required hint={MODE_HINT[mode]}>
          <SelectInput
            id="policy-mode"
            value={mode}
            onChange={(e) => setMode(e.target.value as PricePolicyMode)}
          >
            {(Object.keys(MODE_LABEL) as PricePolicyMode[]).map((m) => (
              <option key={m} value={m}>
                {MODE_LABEL[m]}
              </option>
            ))}
          </SelectInput>
        </Field>

        {mode === 'FIXED_ADJ' && (
          <Field
            label="정액 보정 (원/1g)"
            htmlFor="policy-adj-amount"
            required
            hint="음수를 넣으면 차감된다. 예: -3000"
            error={valueInvalid ? '숫자를 입력해 주세요.' : null}
          >
            <MoneyInput
              id="policy-adj-amount"
              value={adjAmount}
              invalid={valueInvalid}
              onValueChange={setAdjAmount}
            />
          </Field>
        )}

        {mode === 'PERCENT_ADJ' && (
          <Field
            label="정률 보정 (%)"
            htmlFor="policy-adj-percent"
            required
            hint="-7 이면 원본에서 7% 차감."
            error={valueInvalid ? '숫자를 입력해 주세요.' : null}
          >
            <DecimalInput
              id="policy-adj-percent"
              value={adjPercent}
              invalid={valueInvalid}
              onChange={(e) => setAdjPercent(e.target.value)}
            />
          </Field>
        )}

        {mode === 'MANUAL' && (
          <Field
            label="지정가 (원/1g)"
            htmlFor="policy-manual"
            required
            hint="원본 시세를 무시하고 이 값을 쓴다."
            error={
              valueInvalid
                ? '숫자를 입력해 주세요.'
                : manualInvalid
                  ? '지정가는 0보다 커야 합니다.'
                  : null
            }
          >
            <MoneyInput
              id="policy-manual"
              value={manualPrice}
              invalid={valueInvalid || manualInvalid}
              onValueChange={setManualPrice}
            />
          </Field>
        )}

        <Field label="변경 사유" htmlFor="policy-reason" required hint="감사로그에 남는다.">
          <TextArea
            id="policy-reason"
            value={changeReason}
            maxLength={300}
            placeholder="예: 판매 마진 정책 조정"
            onChange={(e) => setChangeReason(e.target.value)}
          />
        </Field>
      </div>
    </Modal>
  );
}
