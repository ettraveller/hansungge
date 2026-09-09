'use client';

import { useState } from 'react';

import { Alert } from '../Alert';
import { Button } from '../Button';
import { Field, MoneyInput, SelectInput, TextArea } from '../Field';
import { Panel } from '../Panel';
import { useDeclarePair } from '@/hooks/admin/usePrices';
import { formatAdjustment, formatPricePerGram } from '@/utils/format';
import { METAL_LABEL, METAL_ORDER, type MetalType } from '@/types/price';
import type { PriceApplyPairResult, PriceApplyResult } from '@/types/admin';
import { ApiError } from '@/types/api';
import styles from '../admin.module.css';

/** 소수점 포함 양수만 통과시킨다. 서버 검증과 겹치지만, 오타를 왕복 없이 잡아준다. */
const DECIMAL_PATTERN = /^\d{1,13}(\.\d{1,2})?$/;

/**
 * 수동 시세 고시 — 살 때와 팔 때를 <b>한 번에</b> 받는다.
 *
 * <h2>왜 한쪽씩 고시할 수 없게 했는가</h2>
 * 전에는 "구분(판매/매입)"을 골라 한쪽씩 고시했다. 그러면 살 때를 고시한 순간부터
 * 팔 때를 고시하기까지 <b>한쪽만 값이 있는 시세표</b>가 고객 화면에 걸린다.
 * 두 번째 입력을 잊거나, 그 사이에 자리를 뜨거나, 두 번째에서 오류가 나면 그 상태가
 * 그대로 남는다. 매입 문의를 받는 곳에서 매입가만 비어 있는 화면은 그 자체로 사고다.
 *
 * <p>화면에서 고를 수 없게 만드는 것이 확실하다. 규칙을 적어 두고 지키기를 바라는
 * 방식은 바쁜 날에 먼저 무너진다.
 *
 * <p>입력값은 <b>원본 시세(KRW/1g)</b>다 — 최종 판매가가 아니다. 보정은 금속·매입/판매마다
 * 정해진 정책이 적용한다. 이 구분을 흐리면 "10,000원 더해서 넣었는데 또 더해졌다"
 * 같은 사고가 난다.
 */
export function PriceDeclareForm() {
  const declare = useDeclarePair();

  const [metalType, setMetalType] = useState<MetalType>('GOLD_24K');
  const [sellPrice, setSellPrice] = useState('');
  const [buyPrice, setBuyPrice] = useState('');
  const [changeReason, setChangeReason] = useState('');
  const [result, setResult] = useState<PriceApplyPairResult | null>(null);

  const sellInvalid = sellPrice !== '' && !DECIMAL_PATTERN.test(sellPrice);
  const buyInvalid = buyPrice !== '' && !DECIMAL_PATTERN.test(buyPrice);

  // 서버도 같은 것을 본다. 여기서 먼저 잡는 이유는 왕복 한 번을 아끼려는 것이지
  // 서버 검사를 대신하려는 것이 아니다.
  const spreadInverted =
    DECIMAL_PATTERN.test(sellPrice) &&
    DECIMAL_PATTERN.test(buyPrice) &&
    Number(sellPrice) <= Number(buyPrice);

  const canSubmit =
    DECIMAL_PATTERN.test(sellPrice) &&
    DECIMAL_PATTERN.test(buyPrice) &&
    Number(sellPrice) > 0 &&
    Number(buyPrice) > 0 &&
    !spreadInverted &&
    changeReason.trim().length > 0 &&
    !declare.isPending;

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    setResult(null);
    declare.mutate(
      {
        metalType,
        sellPricePerGram: sellPrice,
        buyPricePerGram: buyPrice,
        changeReason: changeReason.trim(),
      },
      {
        onSuccess: (res) => {
          setResult(res);
          setSellPrice('');
          setBuyPrice('');
          setChangeReason('');
        },
      },
    );
  };

  return (
    <Panel
      title="수동 시세 고시"
      note="살 때와 팔 때를 함께 고시한다. 입력값은 원본 시세(원/1g)이고, 보정은 자동고시 설정대로 적용된다."
    >
      <form className={styles.form} onSubmit={submit}>
        {declare.error && (
          <Alert tone="danger" title="고시하지 못했습니다">
            {declare.error instanceof ApiError
              ? declare.error.message
              : '알 수 없는 오류가 발생했습니다.'}
          </Alert>
        )}

        {result && <PairResult result={result} onClose={() => setResult(null)} />}

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

        <div className={styles.formGrid}>
          <Field
            label="내가 살 때 (원/1g)"
            htmlFor="declare-sell"
            required
            hint="고객이 구매하는 가격. 1돈(3.75g) 값이 아니다."
            error={sellInvalid ? '숫자만, 소수점 두 자리까지 입력할 수 있습니다.' : null}
          >
            <MoneyInput
              id="declare-sell"
              value={sellPrice}
              invalid={sellInvalid || spreadInverted}
              placeholder="220,800"
              onValueChange={setSellPrice}
            />
          </Field>

          <Field
            label="내가 팔 때 (원/1g)"
            htmlFor="declare-buy"
            required
            hint="회사가 매입하는 가격."
            error={buyInvalid ? '숫자만, 소수점 두 자리까지 입력할 수 있습니다.' : null}
          >
            <MoneyInput
              id="declare-buy"
              value={buyPrice}
              invalid={buyInvalid || spreadInverted}
              placeholder="188,000"
              onValueChange={setBuyPrice}
            />
          </Field>
        </div>

        {spreadInverted && (
          <Alert tone="danger" title="살 때가 팔 때보다 크지 않습니다">
            고객이 사는 값이 파는 값보다 싸면 사고 되팔기만 해도 이익이 납니다. 두 값이
            바뀌지 않았는지 확인해 주세요.
          </Alert>
        )}

        <Field
          label="고시 사유"
          htmlFor="declare-reason"
          required
          hint="시세 이력과 감사로그에 그대로 남는다."
        >
          <TextArea
            id="declare-reason"
            value={changeReason}
            placeholder="예: 09:00 국제 금시세 반영"
            maxLength={300}
            onChange={(e) => setChangeReason(e.target.value)}
          />
        </Field>

        <div className={styles.formActions}>
          <Button type="submit" variant="gold" disabled={!canSubmit}>
            {declare.isPending ? '고시 중…' : '두 값 함께 고시하기'}
          </Button>
          <span className={styles.hint}>
            한쪽이라도 변동률 허용치를 넘으면 둘 다 발행되지 않는다.
          </span>
        </div>
      </form>
    </Panel>
  );
}

/**
 * 고시 결과.
 *
 * <p>{@code applied === false} 는 실패가 아니라 임계값 초과로 승인 대기에 걸린 것이다.
 * 그 둘을 같은 색으로 보여주면 관리자가 잘못 판단한다.
 */
function PairResult({
  result,
  onClose,
}: {
  result: PriceApplyPairResult;
  onClose: () => void;
}) {
  if (!result.applied) {
    return (
      <Alert
        tone="warning"
        title="승인 대기로 등록되었습니다 (현재 시세는 그대로)"
        onClose={onClose}
      >
        아래 <strong>승인 대기</strong>에서 승인하면 그때 반영된다.
      </Alert>
    );
  }

  return (
    <Alert tone="success" title="살 때·팔 때가 함께 고시되었습니다" onClose={onClose}>
      <Line label="내가 살 때" side={result.sell} />
      <br />
      <Line label="내가 팔 때" side={result.buy} />
    </Alert>
  );
}

function Line({ label, side }: { label: string; side: PriceApplyResult }) {
  return (
    <>
      {label} — 원본 {formatPricePerGram(side.rawPricePerGram)} · 보정{' '}
      {formatAdjustment(side.adjustment)} → 최종{' '}
      <strong>{formatPricePerGram(side.pricePerGram)}</strong>
    </>
  );
}
