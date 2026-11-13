'use client';

import { useEffect, useRef, useState } from 'react';

import type { BannerImageEffect } from '@/api/banners';
import styles from '../admin.module.css';

/**
 * 고를 수 있는 효과 전부.
 *
 * <p>자유 숫자를 받지 않는다. 이 값은 손님 화면에서 {@code style} 로 들어가므로
 * {@code blur(9999px)} 한 줄이면 화면이 지워진다. 서버도 같은 목록으로 되돌린다
 * ({@code BannerContentValidator}).
 */
const ROWS: {
  key: keyof BannerImageEffect;
  title: string;
  options: { value: string | number; label: string }[];
}[] = [
  {
    key: 'fade',
    title: '가장자리 녹이기',
    options: [
      { value: 'NONE', label: '없음' },
      { value: 'SOFT', label: '사방 조금' },
      { value: 'STRONG', label: '사방 많이' },
      { value: 'LEFT', label: '왼쪽' },
      { value: 'RIGHT', label: '오른쪽' },
      { value: 'TOP', label: '위' },
      { value: 'BOTTOM', label: '아래' },
    ],
  },
  {
    key: 'veil',
    title: '어둠막',
    options: [
      { value: 'NONE', label: '없음' },
      { value: 'GRADIENT', label: '왼쪽부터' },
      { value: 'SOLID', label: '고르게' },
    ],
  },
  {
    key: 'crop',
    title: '비율 맞춰 자르기',
    options: [
      { value: 'NONE', label: '원본' },
      { value: 'SQUARE', label: '1:1' },
      { value: 'WIDE', label: '16:9' },
      { value: 'BANNER', label: '16:7' },
    ],
  },
  {
    key: 'tone',
    title: '색조',
    options: [
      { value: 'NONE', label: '원본' },
      { value: 'GRAY', label: '흑백' },
    ],
  },
  {
    key: 'blur',
    title: '흐리게',
    options: [
      { value: 0, label: '없음' },
      { value: 4, label: '4px' },
      { value: 8, label: '8px' },
      { value: 16, label: '16px' },
    ],
  },
  {
    key: 'opacity',
    title: '투명도',
    options: [
      { value: 100, label: '100%' },
      { value: 85, label: '85%' },
      { value: 70, label: '70%' },
      { value: 50, label: '50%' },
    ],
  },
  {
    key: 'radius',
    title: '둥근 모서리',
    options: [
      { value: 'NONE', label: '없음' },
      { value: 'SMALL', label: '조금' },
      { value: 'LARGE', label: '많이' },
    ],
  },
  {
    key: 'shadow',
    title: '그림자',
    options: [
      { value: 'NONE', label: '없음' },
      { value: 'SOFT', label: '부드럽게' },
    ],
  },
];

/** 아무것도 고르지 않은 사진의 모양. 효과를 걸기 전과 같아야 한다. */
const DEFAULTS: Required<BannerImageEffect> = {
  fade: 'NONE',
  blur: 0,
  opacity: 100,
  radius: 'SMALL',
  shadow: 'NONE',
  veil: 'NONE',
  crop: 'NONE',
  tone: 'NONE',
};

/**
 * 사진 효과 고르기 — 눌러서 펴고, 한 번 눌러 바로 걸린다.
 *
 * <h2>왜 띠에 펼쳐 두지 않는가</h2>
 * 사진 줄은 이미 「사진 · 너비 · 설명 · 사진 교체 · 자리 풀기」로 차 있다. 효과 다섯
 * 줄을 거기에 더 늘어놓으면 창이 조금만 좁아도 띠가 두 줄로 접혀서, 결과(배너)가
 * 그만큼 아래로 밀린다 — 효과는 <b>결과를 보면서</b> 고르는 것이라 그 밀림이 곧
 * 쓸모의 손실이다. 색판과 같은 모양으로 묶어 둔다.
 *
 * <h2>닫지 않는다</h2>
 * 색판은 고르면 닫히지만 여기는 열어 둔다. 흐림을 한 단계 올리고 투명도를 한 단계
 * 내리는 식으로 <b>두세 개를 연달아</b> 만지는 자리라, 매번 닫히면 다시 여는 손짓이
 * 효과 수만큼 붙는다.
 */
export function ImageEffectPicker({
  value,
  onChange,
}: {
  value: BannerImageEffect | null | undefined;
  onChange: (effect: BannerImageEffect) => void;
}) {
  const [open, setOpen] = useState(false);
  const box = useRef<HTMLDivElement>(null);

  // 밖을 누르면 닫는다. 고르려다 만 판이 떠 있으면 배너를 가린다.
  useEffect(() => {
    if (!open) return;
    const onDown = (e: PointerEvent) => {
      if (!box.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('pointerdown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('pointerdown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const current = (key: keyof BannerImageEffect) => value?.[key] ?? DEFAULTS[key];

  // 걸린 것이 하나라도 있으면 단추에 표시한다. 판을 열어 보지 않고도 "이 사진은
  // 손을 댔다" 를 알 수 있어야 한다.
  const touched = ROWS.some(({ key }) => current(key) !== DEFAULTS[key]);

  return (
    <div className={styles.palette} ref={box}>
      <button
        type="button"
        className={styles.paletteBtn}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label={`사진 효과${touched ? ' (걸려 있음)' : ''}`}
        onClick={() => setOpen((v) => !v)}
      >
        효과{touched ? ' •' : ''}
        <span aria-hidden="true">▾</span>
      </button>

      {open && (
        <div className={styles.palettePop} role="dialog" aria-label="사진 효과">
          {ROWS.map(({ key, title, options }) => (
            <div key={key}>
              <p className={styles.paletteTitle}>{title}</p>
              <div className={styles.effectRow}>
                {options.map((option) => (
                  <button
                    key={option.value}
                    type="button"
                    aria-pressed={current(key) === option.value}
                    className={
                      current(key) === option.value ? styles.effectChipOn : styles.effectChip
                    }
                    onClick={() =>
                      // 고른 칸만 바꾸고 나머지는 지금 값 그대로 둔다. 표에서 온
                      // 값이라 모양이 어긋날 수 없어 여기서 한 번 단언한다.
                      onChange({ ...DEFAULTS, ...value, [key]: option.value } as BannerImageEffect)
                    }
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
