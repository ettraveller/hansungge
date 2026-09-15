'use client';

import { useState } from 'react';

import { Alert } from '../Alert';
import { Button } from '../Button';
import { Field, MoneyInput, SelectInput, TextArea } from '../Field';
import { Panel } from '../Panel';
import { PriceErrorAlert } from './PriceErrorAlert';
import { QuoteChangeTable } from './QuoteChangeTable';
import { QuoteConfirmDialog } from './QuoteConfirmDialog';
import {
  useCurrentPrices,
  useDeclarePair,
  useDerivations,
  usePreviewPair,
} from '@/hooks/admin/usePrices';
import { formatDateTime, formatPricePerGram } from '@/utils/format';
import { METAL_LABEL, METAL_ORDER, type MetalType, type PriceSide } from '@/types/price';
import type { QuotePlan, QuotePublishResult } from '@/types/admin';
import styles from '../admin.module.css';

/** 소수점 포함 양수만 통과시킨다. 서버 검증과 겹치지만, 오타를 왕복 없이 잡아준다. */
const DECIMAL_PATTERN = /^\d{1,13}(\.\d{1,2})?$/;

/**
 * 시세 고시 — 살 때와 팔 때를 <b>한 번에</b> 받는다.
 *
 * <h2>입력한 값이 곧 고시가다</h2>
 * 외부 시세를 받아 보정하던 방식은 없앴다. 여기 적은 숫자가 그대로 쇼핑몰 시세가 된다.
 *
 * <h2>왜 한쪽씩 고시할 수 없게 했는가</h2>
 * 한쪽씩 고시하면 그 사이에 <b>한쪽만 값이 있는 시세표</b>가 고객 화면에 걸린다.
 * 화면에서 고를 수 없게 만드는 것이 확실하다.
 *
 * <h2>고시는 두 단계다</h2>
 * 먼저 서버에 "고시하면 무엇이 얼마로 바뀌나"를 묻는다. 직전보다 크게 바뀌는 값이 있으면
 * 표로 보여주고 한 번 더 묻는다. 없으면 바로 고시한다 — 매번 확인창을 띄우면 확인을
 * 읽지 않고 누르는 습관이 생긴다.
 */
export function PriceDeclareForm() {
  const preview = usePreviewPair();
  const declare = useDeclarePair();
  const { data: prices } = useCurrentPrices();
  const { data: derivations } = useDerivations();

  const [metalType, setMetalType] = useState<MetalType>('GOLD_24K');
  const [sellPrice, setSellPrice] = useState('');
  const [buyPrice, setBuyPrice] = useState('');
  const [changeReason, setChangeReason] = useState('');
  const [confirming, setConfirming] = useState<QuotePlan | null>(null);
  const [result, setResult] = useState<QuotePublishResult | null>(null);

  // 18K·14K 가 환산으로 설정돼 있으면 여기서 고시할 수 없다. 서버도 막지만, 입력을 다
  // 마친 뒤에 거절당하면 헛수고다.
  const derived =
    derivations?.some((d) => d.metalType === metalType && d.mode === 'DERIVED') ?? false;
  const followers =
    metalType === 'GOLD_24K' ? (derivations ?? []).filter((d) => d.mode === 'DERIVED') : [];

  const currentOf = (side: PriceSide) =>
    prices?.find((p) => p.metalType === metalType && p.priceSide === side && p.available);

  const sellInvalid = sellPrice !== '' && !DECIMAL_PATTERN.test(sellPrice);
  const buyInvalid = buyPrice !== '' && !DECIMAL_PATTERN.test(buyPrice);

  // 서버도 같은 것을 본다. 여기서 먼저 잡는 이유는 왕복 한 번을 아끼려는 것이지
  // 서버 검사를 대신하려는 것이 아니다.
  const spreadInverted =
    DECIMAL_PATTERN.test(sellPrice) &&
    DECIMAL_PATTERN.test(buyPrice) &&
    Number(sellPrice) <= Number(buyPrice);

  const busy = preview.isPending || declare.isPending;

  const canSubmit =
    !derived &&
    DECIMAL_PATTERN.test(sellPrice) &&
    DECIMAL_PATTERN.test(buyPrice) &&
    Number(sellPrice) > 0 &&
    Number(buyPrice) > 0 &&
    !spreadInverted &&
    changeReason.trim().length > 0 &&
    !busy;

  const clearFeedback = () => {
    preview.reset();
    declare.reset();
    setResult(null);
  };

  const publish = (confirmLargeChange: boolean) => {
    declare.mutate(
      {
        metalType,
        sellPricePerGram: sellPrice,
        buyPricePerGram: buyPrice,
        changeReason: changeReason.trim(),
        confirmLargeChange,
      },
      {
        onSuccess: (published) => {
          setConfirming(null);
          setResult(published);
          setSellPrice('');
          setBuyPrice('');
          setChangeReason('');
        },
      },
    );
  };

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    clearFeedback();
    preview.mutate(
      { metalType, sellPricePerGram: sellPrice, buyPricePerGram: buyPrice },
      {
        onSuccess: (plan) => {
          if (plan.needsConfirm) {
            setConfirming(plan);
          } else {
            publish(false);
          }
        },
      },
    );
  };

  const hintFor = (side: PriceSide, base: string) => {
    const current = currentOf(side);
    return current ? `${base} 지금 ${formatPricePerGram(current.pricePerGram)}` : base;
  };

  return (
    <Panel
      title="시세 고시"
      note="살 때와 팔 때를 함께 고시한다. 입력한 값(1g 기준)이 그대로 쇼핑몰 시세가 된다."
    >
      <form className={styles.form} onSubmit={submit}>
        {/* 확인창이 떠 있는 동안에는 오류를 창 안에서 보여준다. 두 곳에 같은 오류가 겹치지 않게. */}
        {!confirming && (
          <PriceErrorAlert error={preview.error ?? declare.error} fallback="고시하지 못했습니다." />
        )}

        {result && (
          <Alert
            tone="success"
            title={`고시되었습니다 · ${formatDateTime(result.effectiveFrom)}`}
            onClose={() => setResult(null)}
          >
            <QuoteChangeTable lines={result.lines} />
          </Alert>
        )}

        <Field label="금속" htmlFor="declare-metal" required>
          <SelectInput
            id="declare-metal"
            value={metalType}
            onChange={(e) => {
              setMetalType(e.target.value as MetalType);
              clearFeedback();
            }}
          >
            {METAL_ORDER.map((m) => (
              <option key={m} value={m}>
                {METAL_LABEL[m]}
              </option>
            ))}
          </SelectInput>
        </Field>

        {derived ? (
          <Alert tone="info" title={`${METAL_LABEL[metalType]} 시세는 24K 환산으로 고시되고 있습니다`}>
            24K 를 고시하면 함께 바뀝니다. 따로 입력하려면 아래 <strong>18K·14K 환산 설정</strong>
            에서 &lsquo;직접 입력&rsquo;으로 바꿔 주세요.
          </Alert>
        ) : (
          <>
            <div className={styles.formGrid}>
              <Field
                label="살 때 (원/1g)"
                htmlFor="declare-sell"
                required
                hint={hintFor('SELL', '고객이 사는 가격. 1돈(3.75g) 값이 아니다.')}
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
                label="팔 때 (원/1g)"
                htmlFor="declare-buy"
                required
                hint={hintFor('BUY', '고객이 파는 가격.')}
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

            {followers.length > 0 && (
              <p className={styles.hint}>
                함께 고시되는 금속:{' '}
                {followers
                  .map((d) => `${METAL_LABEL[d.metalType]} (24K × ${d.ratio ?? '-'})`)
                  .join(', ')}
              </p>
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
                placeholder="예: 09:00 오전 고시"
                maxLength={300}
                onChange={(e) => setChangeReason(e.target.value)}
              />
            </Field>

            <div className={styles.formActions}>
              <Button type="submit" variant="gold" disabled={!canSubmit}>
                {busy ? '고시 중…' : '두 값 함께 고시하기'}
              </Button>
              <span className={styles.hint}>
                직전보다 크게 바뀌는 값이 있으면 고시 전에 한 번 더 확인합니다.
              </span>
            </div>
          </>
        )}
      </form>

      {confirming && (
        <QuoteConfirmDialog
          plan={confirming}
          confirmLabel="확인하고 고시"
          pending={declare.isPending}
          error={declare.error}
          onConfirm={() => publish(true)}
          onClose={() => {
            setConfirming(null);
            declare.reset();
          }}
        />
      )}
    </Panel>
  );
}
