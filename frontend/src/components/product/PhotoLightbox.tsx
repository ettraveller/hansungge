'use client';

import { useEffect, useRef } from 'react';

import styles from './PhotoLightbox.module.css';

/**
 * 상품 사진 크게 보기.
 *
 * <p>상세의 사진은 화면 절반 폭이라 각인이나 잠금쇠 같은 세부가 보이지 않았다. 값나가는
 * 귀금속을 사진만 보고 사는 자리인데 크게 볼 길이 없었다 (2026-11-23 QA-035).
 *
 * <h2>브라우저의 dialog 를 쓴다</h2>
 * {@code showModal()} 이 Esc 로 닫기, 초점 가두기, 뒤 화면 막기를 다 해 준다. 직접
 * 만들면 그 셋 중 하나는 빠뜨린다.
 *
 * <h2>사진은 줄이지도 자르지도 않는다</h2>
 * 화면 안에 통째로 들어오게만 맞춘다({@code object-fit: contain}). 휴대폰에서는
 * 두 손가락으로 더 벌려 볼 수 있다 — 화면 확대를 막지 않는다.
 */
export function PhotoLightbox({
  photos,
  index,
  name,
  onIndexChange,
  onClose,
}: {
  photos: string[];
  /** 보여 줄 사진. null 이면 닫혀 있다 */
  index: number | null;
  name: string;
  onIndexChange: (next: number) => void;
  onClose: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const open = index !== null;

  useEffect(() => {
    const el = dialog.current;
    if (!el) return;
    if (open && !el.open) el.showModal();
    if (!open && el.open) el.close();
  }, [open]);

  const many = photos.length > 1;
  const step = (by: number) => {
    if (index === null) return;
    onIndexChange((index + by + photos.length) % photos.length);
  };

  return (
    <dialog
      ref={dialog}
      className={styles.box}
      aria-label={`${name} 사진 크게 보기`}
      // Esc 나 바깥 누르기로 닫혀도 부모의 상태를 맞춘다
      onClose={onClose}
      onClick={(e) => {
        // 사진 바깥(어두운 자리)을 누르면 닫는다
        if (e.target === e.currentTarget) onClose();
      }}
      onKeyDown={(e) => {
        if (e.key === 'ArrowLeft') step(-1);
        if (e.key === 'ArrowRight') step(1);
      }}
    >
      {open && (
        <>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img className={styles.photo} src={photos[index]} alt={name} />

          <button type="button" className={styles.close} aria-label="닫기" onClick={onClose}>
            ✕
          </button>

          {many && (
            <>
              <button
                type="button"
                className={`${styles.nav} ${styles.prev}`}
                aria-label="이전 사진"
                onClick={() => step(-1)}
              >
                ‹
              </button>
              <button
                type="button"
                className={`${styles.nav} ${styles.next}`}
                aria-label="다음 사진"
                onClick={() => step(1)}
              >
                ›
              </button>
              <span className={styles.count} aria-live="polite">
                {index + 1} / {photos.length}
              </span>
            </>
          )}
        </>
      )}
    </dialog>
  );
}
