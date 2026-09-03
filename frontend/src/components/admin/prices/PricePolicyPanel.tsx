'use client';

import { useState } from 'react';

import { Alert } from '../Alert';
import { Button } from '../Button';
import { DecimalInput, Field, SelectInput, TextArea } from '../Field';
import { Modal } from '../Modal';
import { Panel } from '../Panel';
import { usePolicies, useUpdatePolicy } from '@/hooks/admin/usePrices';
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
                  <td className={styles.strong}>{METAL_LABEL[policy.metalType]}</td>
                  <td>{sideLabelOf(policy.priceSide)}</td>
                  <td>{MODE_LABEL[policy.mode]}</td>
                  <td className={styles.num}>{describe(policy)}</td>
                  <td className={styles.muted}>{policy.updatedBy ?? '-'}</td>
                  <td className={styles.muted}>{formatDateTime(policy.updatedAt)}</td>
                  {canEdit && (
                    <td>
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
    </Panel>
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
            <DecimalInput
              id="policy-adj-amount"
              value={adjAmount}
              invalid={valueInvalid}
              onChange={(e) => setAdjAmount(e.target.value)}
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
            <DecimalInput
              id="policy-manual"
              value={manualPrice}
              invalid={valueInvalid || manualInvalid}
              onChange={(e) => setManualPrice(e.target.value)}
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
