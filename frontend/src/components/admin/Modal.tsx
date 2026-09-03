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
  children,
}: {
  open: boolean;
  title: React.ReactNode;
  onClose: () => void;
  footer?: React.ReactNode;
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
      className={styles.dialog}
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
