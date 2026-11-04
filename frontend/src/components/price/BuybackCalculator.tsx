'use client';

import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { priceApi } from '@/api/prices';
import { METAL_LABEL, METAL_ORDER, type MetalType } from '@/types/price';
import { formatKrw } from '@/utils/format';
import styles from './BuybackCalculator.module.css';

/** 1돈 = 3.75g. 무게 단위 환산이라 금액이 아니다 — 원칙 P1 이 막는 것은 금액이다. */
const GRAMS_PER_DON = 3.75;

type Unit = 'DON' | 'GRAM';

/**
 * 매입 계산기 — "내 금 팔면 얼마인가".
 *
 * <h2>금액을 여기서 만들지 않는다</h2>
 * 시세 × 중량을 서버가 낸다 (원칙 P1). 곱셈 한 줄이라 사소해 보이지만, 반올림 자리가
 * 한쪽만 바뀌는 날 <b>화면의 숫자와 가게가 쳐 주는 값이 갈라진다.</b> 그 차이를 들고
 * 온 고객을 설득할 방법은 없다. 무게의 돈↔g 환산만 여기서 한다 — 그것은 금액이 아니다.
 *
 * <h2>「예상」이라고 분명히 적는다</h2>
 * 실제 지급액은 저울에 올린 중량과 순도 검사로 정해진다. 계산기 숫자를 받을 돈으로
 * 알고 왔다가 다른 금액을 들으면, 그 자리에서 가게를 의심하게 된다. 숫자 옆이 아니라
 * <b>숫자 아래 눈에 띄는 자리</b>에 적는다.
 *
 * <h2>차감을 따로 하지 않는다</h2>
 * 고시된 매입가에 이미 가게 몫이 들어 있다 — 18K 매입가는 24K 매입가 ×0.73 인데 18K 의
 * 실제 금 함량은 0.75 다. 여기서 또 빼면 두 번 빼는 셈이 되어 실제보다 낮게 안내된다.
 */
export function BuybackCalculator() {
  const [metal, setMetal] = useState<MetalType>('GOLD_24K');
  const [unit, setUnit] = useState<Unit>('DON');
  const [input, setInput] = useState('');

  const typed = Number(input);
  const valid = input.trim() !== '' && Number.isFinite(typed) && typed > 0;
  const weightG = valid ? (unit === 'DON' ? typed * GRAMS_PER_DON : typed) : 0;

  const quote = useQuery({
    queryKey: ['buyback', metal, weightG],
    queryFn: () => priceApi.buyback(metal, weightG),
    // 중량을 지우면 직전 결과를 들고 있지 않는다 — 입력과 다른 숫자가 남아 있으면
    // 그것이 지금 답으로 읽힌다.
    enabled: valid,
  });

  const result = valid ? quote.data : undefined;

  return (
    <div className={styles.root}>
      <div className={styles.form}>
        <div className={styles.field}>
          <span className={styles.label} id="bc-metal">
            종류
          </span>
          <div className={styles.metals} role="group" aria-labelledby="bc-metal">
            {METAL_ORDER.map((m) => (
              <button
                key={m}
                type="button"
                aria-pressed={m === metal}
                className={m === metal ? styles.metalOn : styles.metal}
                onClick={() => setMetal(m)}
              >
                {METAL_LABEL[m]}
              </button>
            ))}
          </div>
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="bc-weight">
            무게
          </label>
          <div className={styles.weightRow}>
            <input
              id="bc-weight"
              className={styles.input}
              inputMode="decimal"
              placeholder="0"
              value={input}
              // 숫자와 소수점만. 한글이 섞이면 서버가 400 을 주고, 그 오류를
              // 화면에 띄우는 것보다 애초에 못 넣게 하는 편이 낫다.
              onChange={(e) => setInput(e.target.value.replace(/[^0-9.]/g, ''))}
              aria-describedby="bc-unit-hint"
            />
            <div className={styles.units} role="group" aria-label="무게 단위">
              <button
                type="button"
                aria-pressed={unit === 'DON'}
                className={unit === 'DON' ? styles.unitOn : styles.unit}
                onClick={() => setUnit('DON')}
              >
                돈
              </button>
              <button
                type="button"
                aria-pressed={unit === 'GRAM'}
                className={unit === 'GRAM' ? styles.unitOn : styles.unit}
                onClick={() => setUnit('GRAM')}
              >
                g
              </button>
            </div>
          </div>
          <span id="bc-unit-hint" className={styles.hint}>
            1돈은 3.75g 입니다. 반지 한 돈, 목걸이 두세 돈이 보통입니다.
          </span>
        </div>
      </div>

      <div className={styles.result} aria-live="polite">
        {!valid ? (
          <p className={styles.resultIdle}>무게를 넣으면 예상 금액이 나옵니다.</p>
        ) : quote.isPending ? (
          <p className={styles.resultIdle}>계산 중…</p>
        ) : quote.isError ? (
          <p className={styles.resultIdle}>
            금액을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.
          </p>
        ) : result && !result.available ? (
          // 0 원을 적지 않는다 — "안 쳐 준다"로 읽힌다 (원칙 P6)
          <div>
            {/* 금속 이름 뒤에 조사를 붙이지 않는다. 「은」에 「은」이 붙어
                「은은 시세문의」가 된다 — 받침에 따라 은/는을 고르는 함수를 두느니
                조사가 필요 없는 문장으로 쓴다. */}
            <p className={styles.inquiry}>시세문의</p>
            <p className={styles.resultNote}>
              아직 {result.metalName} 매입 시세가 고시되지 않았습니다. 지점으로 문의해 주세요.
            </p>
          </div>
        ) : result ? (
          <div>
            <p className={styles.amountLabel}>매입 예상 금액</p>
            <p className={styles.amount}>{formatKrw(result.amount)}</p>
            <p className={styles.basis}>
              {result.metalName} {formatWeight(result.weightDon)}돈
              <span className={styles.dim}> ({formatWeight(result.weightG)}g)</span>
              {result.pricePerDon != null && ` · 매입가 ${formatKrw(result.pricePerDon)}/돈`}
            </p>
          </div>
        ) : null}
      </div>

      {/*
        숫자 아래, 눈에 띄는 자리에 둔다. 계산기 숫자를 받을 돈으로 알고 왔다가
        다른 금액을 들으면 그 자리에서 가게를 의심하게 된다.
      */}
      <p className={styles.caveat}>
        <strong>예상 금액입니다.</strong> 실제 지급액은 저울에 올린 중량과 순도 검사로
        정해지며, 제품 상태에 따라 달라질 수 있습니다. 시세는 수시로 바뀌므로 방문하신
        시각의 고시 시세가 적용됩니다.
      </p>
    </div>
  );
}

/** 끝자리 0 을 떼고 보여준다 — 2.0000 돈은 2돈이다. */
function formatWeight(value: number | undefined): string {
  if (value == null) return '-';
  return String(Number(value.toFixed(4)));
}
