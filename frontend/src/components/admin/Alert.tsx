import styles from './admin.module.css';

export type AlertTone = 'info' | 'success' | 'warning' | 'danger';

const TONE_CLASS: Record<AlertTone, string> = {
  info: styles.alertInfo,
  success: styles.alertSuccess,
  warning: styles.alertWarning,
  danger: styles.alertDanger,
};

/**
 * 알림 박스.
 *
 * 역할을 role 로 구분한다 — danger/warning 은 화면 낭독기가 즉시 읽어야 한다.
 */
export function Alert({
  tone = 'info',
  title,
  onClose,
  children,
}: {
  tone?: AlertTone;
  title?: React.ReactNode;
  onClose?: () => void;
  children: React.ReactNode;
}) {
  const assertive = tone === 'danger' || tone === 'warning';
  return (
    <div
      className={`${styles.alert} ${TONE_CLASS[tone]}`}
      role={assertive ? 'alert' : 'status'}
    >
      <div className={styles.alertBody}>
        {title && <div className={styles.alertTitle}>{title}</div>}
        {children}
      </div>
      {onClose && (
        <button type="button" className={styles.alertClose} onClick={onClose} aria-label="닫기">
          ×
        </button>
      )}
    </div>
  );
}
