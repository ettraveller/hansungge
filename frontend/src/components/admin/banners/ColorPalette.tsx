'use client';

import { useEffect, useRef, useState } from 'react';

import styles from '../admin.module.css';

/**
 * 테마 색 — 토큰의 잉크 램프와 금 램프를 그대로 깐다.
 *
 * <p>자유롭게 고르게 두면 배너마다 조금씩 다른 남색이 쌓여 화면이 흐트러진다.
 * 램프 안에서 고르면 어느 배너를 열어도 같은 색 계열이다. 그래도 필요하면
 * 「다른 색」으로 어떤 값이든 고를 수 있다 — 막는 것이 아니라 기본을 두는 것이다.
 */
const THEME: string[][] = [
  ['#ffffff', '#f4f6f9', '#e2e7ee', '#cfd8e4', '#93a1b3'],
  ['#5b6677', '#44505f', '#2b3949', '#1a2739', '#0f1b2d'],
  ['#fbf8f2', '#f5efe2', '#e9ddc3', '#dcc89d', '#d1b57c'],
  ['#c6a15b', '#a8863f', '#8a6a2f', '#665024', '#443618'],
];

/** 표준 색. 세일·공지처럼 브랜드 밖의 색이 필요할 때만 쓴다. */
const STANDARD = [
  '#000000',
  '#b00020',
  '#e53935',
  '#fb8c00',
  '#fdd835',
  '#43a047',
  '#00897b',
  '#1e88e5',
  '#3949ab',
  '#8e24aa',
];

interface Props {
  /** 지금 색. null 이면 그 역할의 기본색을 쓰는 중이다. */
  value: string | null | undefined;
  /** 열림 단추에 보일 이름 */
  label: string;
  /** 「기본색으로」를 띄울지. 배경색처럼 비울 수 없는 자리는 false. */
  clearable?: boolean;
  disabled?: boolean;
  /** 판 맨 아래에 덧붙일 것. 배경색 판이 「이미지로 첨부」를 여기에 둔다. */
  footer?: React.ReactNode;
  onChange: (color: string | null) => void;
}

/**
 * PPT 의 글자색 단추처럼, 눌러서 펴고 한 번 눌러 바로 칠하는 색판.
 *
 * <h2>왜 {@code input[type=color]} 하나로 두지 않는가</h2>
 * 운영체제 색 고르기 창은 <b>화면 밖에서 열린다.</b> 창이 뜨는 순간 배너가 가려져서,
 * 지금 고르는 색이 그 글자 위에서 어떻게 보이는지를 볼 수 없다. 자주 쓰는 색을
 * 바로 옆에 깔아 두면 창을 열 일 자체가 드물다.
 *
 * <h2>칸에 들어가는 모양만 고를 수 있다</h2>
 * 깔린 색도, 「다른 색」의 {@code input[type=color]} 도 {@code #rrggbb} 만 돌려준다.
 * 고를 수 있는 모양이 곧 서버가 받는 모양이라 어긋날 자리가 없다 — 이 값은 손님
 * 화면에서 {@code style} 로 들어가므로, 자유 문자열을 받으면 CSS 한 줄이 섞인다.
 */
export function ColorPalette({ value, label, clearable, disabled, footer, onChange }: Props) {
  const [open, setOpen] = useState(false);
  const box = useRef<HTMLDivElement>(null);

  // 밖을 누르면 닫는다. 색을 고르려다 만 상태로 판이 떠 있으면 배너를 가린다.
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

  const pick = (color: string | null) => {
    onChange(color);
    setOpen(false);
  };

  return (
    <div className={styles.palette} ref={box}>
      <button
        type="button"
        className={styles.paletteBtn}
        disabled={disabled}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label={`${label}${value ? ` (${value})` : ' (기본색)'}`}
        onClick={() => setOpen((v) => !v)}
      >
        <span className={styles.paletteChip} style={{ background: value ?? 'transparent' }} />
        {label}
        <span aria-hidden="true">▾</span>
      </button>

      {open && (
        <div className={styles.palettePop} role="dialog" aria-label={label}>
          <p className={styles.paletteTitle}>테마 색</p>
          <div className={styles.paletteGrid}>
            {THEME.map((row) =>
              row.map((hex) => (
                <button
                  key={hex}
                  type="button"
                  className={hex === value ? styles.swatchOn : styles.swatch}
                  style={{ background: hex }}
                  aria-label={hex}
                  title={hex}
                  onClick={() => pick(hex)}
                />
              )),
            )}
          </div>

          <p className={styles.paletteTitle}>표준 색</p>
          <div className={styles.paletteGrid}>
            {STANDARD.map((hex) => (
              <button
                key={hex}
                type="button"
                className={hex === value ? styles.swatchOn : styles.swatch}
                style={{ background: hex }}
                aria-label={hex}
                title={hex}
                onClick={() => pick(hex)}
              />
            ))}
          </div>

          <div className={styles.paletteFoot}>
            <label className={styles.paletteOther}>
              <input
                type="color"
                className={styles.designColor}
                value={value ?? '#ffffff'}
                onChange={(e) => onChange(e.target.value)}
              />
              다른 색
            </label>
            {clearable && (
              <button type="button" className={styles.designClearColor} onClick={() => pick(null)}>
                기본색으로
              </button>
            )}
          </div>

          {footer}
        </div>
      )}
    </div>
  );
}
