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
import { formatDateTime, formatKrw, formatPricePerDon } from '@/utils/format';
import { METAL_LABEL, METAL_ORDER, type MetalType, type PriceSide } from '@/types/price';
import type { QuotePlan, QuotePublishResult } from '@/types/admin';
import styles from '../admin.module.css';

/** 소수점 포함 양수만 통과시킨다. 서버 검증과 겹치지만, 오타를 왕복 없이 잡아준다. */
const DECIMAL_PATTERN = /^\d{1,13}(\.\d{1,2})?$/;

/** 부가세율(%). 법정 세율이라 상수로 둔다 — 바뀌면 서버의 VAT_RATE 설정과 함께 고친다. */
const VAT_PERCENT = 10;

/**
 * 공급가액 → 고시될 판매가.
 *
 * <p>여기서 곱한 값을 관리자가 눈으로 확인하고 고시한다. 고시되는 숫자 자체는 바뀌지
 * 않는다 — 바뀌는 것은 <b>관리자가 치는 숫자</b>뿐이다. 그래서 상품 가격도, 고시 이력의
 * 뜻도 그대로다.
 *
 * <p>원 단위로 반올림한다. 고시가에 소수점이 붙으면 시세표에서 읽기 나쁘다.
 */
function addVat(supplyPrice: string): string {
  const value = Number(supplyPrice);
  if (!Number.isFinite(value) || value <= 0) return '';
  return String(Math.round(value * (1 + VAT_PERCENT / 100)));
}

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

  /** 실제로 고시될 판매가. 입력칸은 공급가액을 받는다. */
  const sellToPublish = DECIMAL_PATTERN.test(sellPrice) ? addVat(sellPrice) : '';

  // 서버도 같은 것을 본다. 여기서 먼저 잡는 이유는 왕복 한 번을 아끼려는 것이지
  // 서버 검사를 대신하려는 것이 아니다.
  //
  // 비교는 고시될 값으로 한다 — 서버가 보는 것이 그 값이라, 공급가로 비교하면
  // 화면에서는 걸리는데 서버에서는 통과하는(또는 그 반대의) 경우가 생긴다.
  const spreadInverted =
    sellToPublish !== '' &&
    DECIMAL_PATTERN.test(buyPrice) &&
    Number(sellToPublish) <= Number(buyPrice);

  const busy = preview.isPending || declare.isPending;

  const canSubmit =
    !derived &&
    sellToPublish !== '' &&
    DECIMAL_PATTERN.test(buyPrice) &&
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
        sellPricePerDon: sellToPublish,
        buyPricePerDon: buyPrice,
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
      { metalType, sellPricePerDon: sellToPublish, buyPricePerDon: buyPrice },
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

  // "지금" 값도 돈 단위로 보여준다. 입력은 돈인데 비교값이 g 면 방금 친 숫자가
  // 맞게 들어갔는지 확인할 방법이 없다. 돈 값은 서버가 계산해서 내려준다.
  const hintFor = (side: PriceSide, base: string) => {
    const current = currentOf(side);
    return current ? `${base} 지금 ${formatPricePerDon(current.pricePerDon)}` : base;
  };

  return (
    <Panel
      title="시세 고시"
      note="살 때와 팔 때를 함께 고시한다. 1돈 = 3.75g 기준이다. 살 때는 부가세를 뺀 공급가액으로 넣는다 — 발행될 때 서버가 부가세를 더한다."
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
                label="살 때 — 공급가액 (원/1돈)"
                htmlFor="declare-sell"
                required
                hint={hintFor('SELL', `부가세를 뺀 금액을 넣는다. 고시되는 값은 여기에 ${VAT_PERCENT}% 를 더한 금액이다.`)}
                error={sellInvalid ? '숫자만, 소수점 두 자리까지 입력할 수 있습니다.' : null}
              >
                {/* 곱한 값을 치는 칸 바로 옆에 둔다. 발행 버튼까지 가서야 보이면 자릿수를
                    잘못 친 것을 거기서 알게 되고, 그 자리에는 확인창이 떠 있다.
                    흐린 안내문으로 두지 않는 이유도 같다 — 실제로 나갈 금액이다. */}
                <div className={styles.declaredRow}>
                  <MoneyInput
                    id="declare-sell"
                    value={sellPrice}
                    invalid={sellInvalid || spreadInverted}
                    onValueChange={setSellPrice}
                  />
                  {sellToPublish !== '' && (
                    <p className={styles.declaredOut}>
                      <span className={styles.declaredLabel}>고시될 값</span>
                      <strong className={styles.declaredAmount}>
                        {formatKrw(Number(sellToPublish))}
                      </strong>
                      <span className={styles.declaredLabel}>부가세 {VAT_PERCENT}% 포함</span>
                    </p>
                  )}
                </div>
              </Field>

              <Field
                label="팔 때 (원/1돈)"
                htmlFor="declare-buy"
                required
                hint={hintFor('BUY', '고객이 파는 가격. 부가세가 붙지 않는다 — 넣은 값이 그대로 나간다.')}
                error={buyInvalid ? '숫자만, 소수점 두 자리까지 입력할 수 있습니다.' : null}
              >
                <MoneyInput
                  id="declare-buy"
                  value={buyPrice}
                  invalid={buyInvalid || spreadInverted}
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
                  .map(
                    (d) =>
                      `${METAL_LABEL[d.metalType]} (살 때 ×${d.sellRatio ?? '-'} · 팔 때 ×${d.buyRatio ?? '-'})`,
                  )
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
