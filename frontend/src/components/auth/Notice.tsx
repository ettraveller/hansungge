import styles from './auth.module.css';

const TONE_CLASS = {
  info: styles.noticeInfo,
  warn: styles.noticeWarn,
  error: styles.noticeError,
} as const;

/** 안내 상자. 오류·경고는 화면 낭독기가 즉시 읽도록 role 을 나눈다. */
export function Notice({
  tone = 'info',
  title,
  children,
}: {
  tone?: 'info' | 'warn' | 'error';
  title?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <div className={`${styles.notice} ${TONE_CLASS[tone]}`} role={tone === 'info' ? 'status' : 'alert'}>
      <div>
        {title && <strong className={styles.noticeTitle}>{title}</strong>}
        {children}
      </div>
    </div>
  );
}
