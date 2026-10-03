'use client';

import { useState } from 'react';

import styles from './DeliveryMemoSelect.module.css';

/**
 * 자주 쓰는 요청사항.
 *
 * <p>직접 쓰게만 두면 대부분 비워 둔다 — 쓸 말이 떠오르지 않아서지, 요청이 없어서가
 * 아니다. 고를 수 있게 하면 "문 앞에 놓아주세요"가 실제로 전달된다.
 */
const PRESETS = [
  '문 앞에 놓아주세요',
  '택배함에 놓아주세요',
  '경비실에 맡겨주세요',
  '부재 시 연락 부탁드려요',
  '배송 전 미리 연락해 주세요',
  '파손 위험 상품입니다. 취급 주의해주세요',
] as const;

const CUSTOM = '__custom__';

interface Props {
  value: string;
  onChange: (value: string) => void;
}

/**
 * 배송 요청사항 — 고르거나 직접 쓴다.
 *
 * <p>고른 값과 직접 쓴 값을 <b>한 상태로</b> 다룬다. 바깥(주문서)은 "무슨 글자가
 * 들어갔는가"만 알면 되고, 그것을 고른 것인지 쓴 것인지는 알 필요가 없다.
 */
export function DeliveryMemoSelect({ value, onChange }: Props) {
  // 직접 입력을 고른 상태인지는 값만으로 알 수 없다 — 직접 쓴 글자가 우연히 보기와
  // 같을 수도 있고, 고른 뒤 지우면 빈 값이 된다. 그래서 따로 들고 있는다.
  const [custom, setCustom] = useState(() => (value && !isPreset(value) ? true : false));

  const selectValue = custom ? CUSTOM : isPreset(value) ? value : '';

  return (
    <>
      <select
        className={styles.select}
        value={selectValue}
        onChange={(e) => {
          const next = e.target.value;
          if (next === CUSTOM) {
            setCustom(true);
            onChange('');
            return;
          }
          setCustom(false);
          onChange(next);
        }}
      >
        <option value="">배송 요청사항을 선택해 주세요</option>
        {PRESETS.map((preset) => (
          <option key={preset} value={preset}>
            {preset}
          </option>
        ))}
        <option value={CUSTOM}>직접 입력하기</option>
      </select>

      {custom && (
        <input
          className={styles.custom}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          maxLength={300}
          placeholder="요청사항을 입력해 주세요"
          aria-label="배송 요청사항 직접 입력"
          autoFocus
        />
      )}
    </>
  );
}

function isPreset(value: string): boolean {
  return (PRESETS as readonly string[]).includes(value);
}
