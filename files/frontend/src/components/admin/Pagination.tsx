'use client';

import styles from './admin.module.css';

/** 현재 페이지 주변 번호만 노출한다. 전체 페이지가 많아도 버튼 줄이 터지지 않게. */
function windowOf(page: number, totalPages: number, span = 2): number[] {
  const start = Math.max(0, Math.min(page - span, totalPages - span * 2 - 1));
  const end = Math.min(totalPages - 1, Math.max(page + span, span * 2));
  const out: number[] = [];
  for (let i = start; i <= end; i += 1) out.push(i);
  return out;
}

export function Pagination({
  page,
  totalPages,
  totalElements,
  onChange,
}: {
  /** 0부터 */
  page: number;
  totalPages: number;
  totalElements: number;
  onChange: (page: number) => void;
}) {
  if (totalPages <= 1) {
    return (
      <div className={styles.pagination}>
        <span className={styles.pageInfo}>총 {totalElements.toLocaleString('ko-KR')}건</span>
      </div>
    );
  }

  return (
    <nav className={styles.pagination} aria-label="페이지">
      <span className={styles.pageInfo}>
        총 {totalElements.toLocaleString('ko-KR')}건 · {page + 1}/{totalPages}
      </span>
      <button
        type="button"
        className={styles.pageBtn}
        onClick={() => onChange(page - 1)}
        disabled={page <= 0}
        aria-label="이전 페이지"
      >
        ‹
      </button>
      {windowOf(page, totalPages).map((p) => (
        <button
          key={p}
          type="button"
          className={`${styles.pageBtn} ${p === page ? styles.pageBtnActive : ''}`}
          onClick={() => onChange(p)}
          aria-current={p === page ? 'page' : undefined}
        >
          {p + 1}
        </button>
      ))}
      <button
        type="button"
        className={styles.pageBtn}
        onClick={() => onChange(page + 1)}
        disabled={page >= totalPages - 1}
        aria-label="다음 페이지"
      >
        ›
      </button>
    </nav>
  );
}
