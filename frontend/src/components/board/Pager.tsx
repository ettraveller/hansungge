import Link from 'next/link';

import styles from './board.module.css';

interface Props {
  /** 현재 페이지 (1-base). 화면과 URL 이 쓰는 번호다 */
  page: number;
  totalPages: number;
  /** 페이지 번호를 뺀 현재 URL 의 쿼리. 검색어·게시판 구분이 유지되어야 한다 */
  params?: Record<string, string | number | undefined>;
  /** 기준 경로. 생략하면 현재 경로에 쿼리만 붙인다 */
  basePath?: string;
}

/** 현재 페이지 주변으로 보여줄 번호 개수 (양쪽 각각). */
const SPAN = 2;

/**
 * 페이지 이동 — <b>링크</b>로 만든다.
 *
 * <h2>버튼이 아니라 링크인 이유</h2>
 * 클릭 핸들러로 상태만 바꾸면 2페이지에 <b>고유 URL 이 없다.</b> 그러면
 * <ul>
 *   <li>검색엔진이 색인할 페이지가 1페이지 하나로 줄어든다 — 공지·문의 본문이 통째로
 *       색인에서 빠진다</li>
 *   <li>고객이 특정 페이지를 링크로 공유하거나 새로고침할 수 없다</li>
 *   <li>뒤로가기가 목록이 아니라 이전 화면으로 나간다</li>
 * </ul>
 * 상품 목록의 카테고리 탭을 {@code <Link>} 로 만든 것과 같은 이유다.
 *
 * <p>관리자 화면의 {@code components/admin/Pagination} 을 쓰지 않는 것도 이 때문이다 —
 * 그쪽은 {@code onChange} 콜백 방식이라 URL 이 바뀌지 않고, 스타일도 관리자 CSS 에 묶여 있다.
 */
export function Pager({ page, totalPages, params, basePath }: Props) {
  if (totalPages <= 1) return null;

  const href = (target: number) => {
    const query = new URLSearchParams();
    for (const [key, value] of Object.entries(params ?? {})) {
      if (value != null && value !== '') query.set(key, String(value));
    }
    // 1페이지는 번호를 붙이지 않는다. 같은 목록에 주소가 둘이면 검색엔진이 중복으로 본다
    if (target > 1) query.set('page', String(target));

    const qs = query.toString();
    return `${basePath ?? ''}${qs ? `?${qs}` : ''}` || '?';
  };

  const from = Math.max(1, page - SPAN);
  const to = Math.min(totalPages, page + SPAN);
  const numbers = Array.from({ length: to - from + 1 }, (_, i) => from + i);

  return (
    <nav className={styles.pager} aria-label="페이지 이동">
      {page > 1 && (
        <Link className={styles.pageBtn} href={href(page - 1)} rel="prev">
          이전
        </Link>
      )}

      {from > 1 && (
        <>
          <Link className={styles.pageBtn} href={href(1)}>
            1
          </Link>
          {from > 2 && <span className={styles.pageGap}>…</span>}
        </>
      )}

      {numbers.map((n) =>
        n === page ? (
          <span key={n} className={`${styles.pageBtn} ${styles.pageOn}`} aria-current="page">
            {n}
          </span>
        ) : (
          <Link key={n} className={styles.pageBtn} href={href(n)}>
            {n}
          </Link>
        ),
      )}

      {to < totalPages && (
        <>
          {to < totalPages - 1 && <span className={styles.pageGap}>…</span>}
          <Link className={styles.pageBtn} href={href(totalPages)}>
            {totalPages}
          </Link>
        </>
      )}

      {page < totalPages && (
        <Link className={styles.pageBtn} href={href(page + 1)} rel="next">
          다음
        </Link>
      )}
    </nav>
  );
}
