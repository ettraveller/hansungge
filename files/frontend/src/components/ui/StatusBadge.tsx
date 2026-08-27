import styles from './StatusBadge.module.css';

export type StatusTone = 'neutral' | 'success' | 'warning' | 'danger' | 'gold';

/** 상태 표시 배지. 색으로만 구분하지 않고 항상 문구를 함께 제공한다. */
export function StatusBadge({
  tone = 'neutral',
  children,
}: {
  tone?: StatusTone;
  children: React.ReactNode;
}) {
  return <span className={`${styles.badge} ${styles[tone]}`}>{children}</span>;
}
