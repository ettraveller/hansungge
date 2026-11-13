'use client';

import { useEffect, useRef, useState } from 'react';

import { LinkTargetPicker } from '../LinkTargetPicker';
import styles from '../admin.module.css';

/**
 * 버튼이 「누르면 갈 곳」 — 고리 모양을 눌러 펴고, 메뉴·상품에서 고른다.
 *
 * <h2>왜 셀렉트를 바로 띠에 두지 않는가</h2>
 * 띠에 둔 셀렉트는 폭이 260px 로 잘려 있어서 「고객센터 &gt; 제품문의」 같은 이름이
 * 중간에서 끊긴다. 게다가 이름표가 없어 그 칸이 <b>주소를 고르는 칸인지 보이지
 * 않았다</b> — 버튼에 링크를 다는 기능이 없다고 읽힌 이유다. 고리를 눌러 펴면 칸이
 * 제 폭을 쓰고, 고리 자체가 "여기서 주소를 단다"를 말한다.
 *
 * <p>주소가 걸려 있으면 고리를 칠해 둔다. 판을 열어 보지 않고도 이 버튼이 어딘가로
 * 가는지 알 수 있어야 한다.
 */
export function LinkPopover({
  value,
  onChange,
}: {
  value: string;
  onChange: (href: string) => void;
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

  return (
    <div className={styles.palette} ref={box}>
      <button
        type="button"
        className={value ? styles.designIconBtnOn : styles.designIconBtn}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label={value ? `누르면 갈 곳 (${value})` : '누르면 갈 곳'}
        title={value || '누르면 갈 곳'}
        onClick={() => setOpen((v) => !v)}
      >
        🔗
      </button>

      {open && (
        <div
          className={`${styles.palettePop} ${styles.linkPop}`}
          role="dialog"
          aria-label="누르면 갈 곳"
        >
          <p className={styles.paletteTitle}>누르면 갈 곳</p>
          <LinkTargetPicker value={value} onChange={onChange} />
        </div>
      )}
    </div>
  );
}
