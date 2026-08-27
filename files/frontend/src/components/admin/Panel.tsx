import styles from './admin.module.css';

/** 관리자 화면의 기본 상자. 제목 + 설명 + 오른쪽 액션. */
export function Panel({
  title,
  note,
  actions,
  flush,
  children,
}: {
  title?: React.ReactNode;
  /** 이 패널이 무엇을 바꾸는지 한 줄 설명. 오조작을 줄이는 데 제목보다 효과가 크다. */
  note?: React.ReactNode;
  actions?: React.ReactNode;
  /** 표를 그대로 넣을 때처럼 안쪽 여백이 방해될 때 */
  flush?: boolean;
  children: React.ReactNode;
}) {
  return (
    <section className={styles.panel}>
      {(title || actions) && (
        <header className={styles.panelHead}>
          <div>
            {title && <h2 className={styles.panelTitle}>{title}</h2>}
            {note && <p className={styles.panelNote}>{note}</p>}
          </div>
          {actions && <div className={styles.headActions}>{actions}</div>}
        </header>
      )}
      <div className={flush ? styles.panelBodyFlush : styles.panelBody}>{children}</div>
    </section>
  );
}
