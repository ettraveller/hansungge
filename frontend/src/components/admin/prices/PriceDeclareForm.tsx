'use client';

import { useState } from 'react';

import { Alert } from '../Alert';
import { Button } from '../Button';
import { DecimalInput, Field, SelectInput, TextArea } from '../Field';
import { Panel } from '../Panel';
import { useDeclarePrice } from '@/hooks/admin/usePrices';
import { formatAdjustment, formatKrw, formatPricePerGram } from '@/utils/format';
import { METAL_LABEL, METAL_ORDER, type MetalType, type PriceSide } from '@/types/price';
import type { PriceApplyResult } from '@/types/admin';
import { ApiError } from '@/types/api';
import styles from '../admin.module.css';

/** 소수점 포함 양수만 통과시킨다. 서버 검증과 겹치지만, 오타를 왕복 없이 잡아준다. */
const DECIMAL_PATTERN = /^\d{1,13}(\.\d{1,2})?$/;

/**
 * 수동 시세 고시.
 *
 * 입력값은 <b>원본 시세(KRW/1g)</b>다 — 최종 판매가가 아니다. 보정은 금속·매입/판매마다
 * 정해진 정책이 서버에서 적용한다. 이 구분을 화면에서 흐리면 "10,000원 더해서 넣었는데
 * 또 더해졌다" 같은 사고가 난다.
 */
export function PriceDeclareForm() {
  const declare = useDeclarePrice();

  const [metalType, setMetalType] = useState<MetalType>('GOLD_24K');
  const [priceSide, setPriceSide] = useState<PriceSide>('SELL');
  const [pricePerGram, setPricePerGram] = useState('');
  const [changeReason, setChangeReason] = useState('');
  const [result, setResult] = useState<PriceApplyResult | null>(null);

  const priceInvalid = pricePerGram !== '' && !DECIMAL_PATTERN.test(pricePerGram);
  const canSubmit =
    DECIMAL_PATTERN.test(pricePerGram) &&
    Number(pricePerGram) > 0 &&
    changeReason.trim().length > 0 &&
    !declare.isPending;

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    setResult(null);
    declare.mutate(
      { metalType, priceSide, pricePerGram, changeReason: changeReason.trim() },
      {
        onSuccess: (res) => {
          setResult(res);
          setPricePerGram('');
          setChangeReason('');
        },
      },
    );
  };

  return (
    <Panel
      title="수동 시세 고시"
      note="입력값은 원본 시세(원/1g)다. 보정은 아래 정책대로 서버가 적용한다."
    >
      <form className={styles.form} onSubmit={submit}>
        {declare.error && (
          <Alert tone="danger" title="고시하지 못했습니다">
            {declare.error instanceof ApiError
              ? declare.error.message
              : '알 수 없는 오류가 발생했습니다.'}
          </Alert>
        )}

        {result && <DeclareResult result={result} onClose={() => setResult(null)} />}

        <div className={styles.formGrid}>
          <Field label="금속" htmlFor="declare-metal" required>
            <SelectInput
              id="declare-metal"
              value={metalType}
              onChange={(e) => setMetalType(e.target.value as MetalType)}
            >
              {METAL_ORDER.map((m) => (
                <option key={m} value={m}>
                  {METAL_LABEL[m]}
                </option>
              ))}
            </SelectInput>
          </Field>

          <Field
            label="구분"
            htmlFor="declare-side"
            required
            hint="판매 = 고객이 살 때 / 매입 = 고객이 팔 때"
          >
            <SelectInput
              id="declare-side"
              value={priceSide}
              onChange={(e) => setPriceSide(e.target.value as PriceSide)}
            >
              <option value="SELL">판매 (고객이 살 때)</option>
              <option value="BUY">매입 (고객이 팔 때)</option>
            </SelectInput>
          </Field>

          <Field
            label="원본 시세 (원/1g)"
            htmlFor="declare-price"
            required
            hint="1돈(3.75g) 값이 아니다. 소수점 둘째 자리까지."
            error={priceInvalid ? '숫자만, 소수점 두 자리까지 입력할 수 있습니다.' : null}
          >
            <DecimalInput
              id="declare-price"
              value={pricePerGram}
              invalid={priceInvalid}
              placeholder="147000"
              onChange={(e) => setPricePerGram(e.target.value)}
            />
          </Field>
        </div>

        <Field
          label="고시 사유"
          htmlFor="declare-reason"
          required
          hint="시세 이력과 감사로그에 그대로 남는다."
        >
          <TextArea
            id="declare-reason"
            value={changeReason}
            placeholder="예: 2026-08-27 09:00 국제 금시세 반영"
            maxLength={300}
            onChange={(e) => setChangeReason(e.target.value)}
          />
        </Field>

        <div className={styles.formActions}>
          <Button type="submit" variant="gold" disabled={!canSubmit}>
            {declare.isPending ? '고시 중…' : '고시하기'}
          </Button>
          <span className={styles.hint}>
            변동률이 허용치를 넘으면 발행되지 않고 승인 대기로 넘어간다.
          </span>
        </div>
      </form>
    </Panel>
  );
}

/**
 * 고시 결과.
 *
 * {@code applied === false} 는 실패가 아니다 — 임계값 초과로 승인 대기에 걸린 것이고,
 * 현재 시세는 바뀌지 않았다. 이 둘을 같은 색으로 보여주면 관리자가 잘못 판단한다.
 */
function DeclareResult({ result, onClose }: { result: PriceApplyResult; onClose: () => void }) {
  const changeText =
    result.changePercent == null ? null : `${result.changePercent > 0 ? '+' : ''}${result.changePercent}%`;

  if (!result.applied) {
    return (
      <Alert tone="warning" title="승인 대기로 등록되었습니다 (현재 시세는 그대로)" onClose={onClose}>
        {result.message}
        <br />
        산출가 {formatKrw(result.pricePerGram)}/g
        {changeText && ` · 변동률 ${changeText}`}
        {result.pendingId != null && ` · 대기 #${result.pendingId}`}
        <br />
        아래 <strong>승인 대기</strong>에서 승인하면 그때 반영된다.
      </Alert>
    );
  }

  return (
    <Alert tone="success" title="고시되었습니다" onClose={onClose}>
      원본 {formatPricePerGram(result.rawPricePerGram)} · 보정{' '}
      {formatAdjustment(result.adjustment)} → 최종 <strong>{formatPricePerGram(result.pricePerGram)}</strong>
      {changeText && ` · 변동률 ${changeText}`}
    </Alert>
  );
}
