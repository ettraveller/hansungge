'use client';

import { useEffect, useRef } from 'react';

import styles from './admin.module.css';

/**
 * 모달.
 *
 * 네이티브 {@code <dialog>} 의 showModal() 을 쓴다. 포커스 가두기·ESC 닫기·배경
 * 비활성화를 브라우저가 처리해 주므로, 직접 구현한 오버레이보다 접근성 사고가 적다.
 */
export function Modal({
  open,
  title,
  onClose,
  footer,
  width = 'default',
  children,
}: {
  open: boolean;
  title: React.ReactNode;
  onClose: () => void;
  footer?: React.ReactNode;
  /**
   * 창 너비.
   *
   * - `default` — 확인·입력용. 560px.
   * - `wide` — 고객 화면을 그대로 보여주는 미리보기용. 560px 에 2단 레이아웃을 넣으면
   *   한 칸이 235px 이 되어 실제와 전혀 다르게 보인다.
   */
  width?: 'default' | 'wide';
  children: React.ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    if (open && !el.open) el.showModal();
    if (!open && el.open) el.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      className={width === 'wide' ? `${styles.dialog} ${styles.dialogWide}` : styles.dialog}
      // ESC 로 닫아도 부모의 open 상태가 따라오게 한다. 안 그러면 다시 열리지 않는다.
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
      onClose={onClose}
    >
      <header className={styles.dialogHead}>
        <h2 className={styles.dialogTitle}>{title}</h2>
        <button type="button" className={styles.alertClose} onClick={onClose} aria-label="닫기">
          ×
        </button>
      </header>
      <div className={styles.dialogBody}>{children}</div>
      {footer && <div className={styles.dialogFoot}>{footer}</div>}
    </dialog>
  );
}
