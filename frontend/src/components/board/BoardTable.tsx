import Link from 'next/link';

import styles from './board.module.css';

/**
 * 게시판 한 줄.
 *
 * <p>네 게시판이 같은 표를 쓰되 칸은 조금씩 다르다 — 공지에는 글쓴이가 없고, 문의에는
 * 답변 상태가 있다. 게시판마다 컴포넌트를 따로 만드는 대신 <b>있는 값만 그린다.</b>
 */
export interface BoardRow {
  id: number;
  title: string;
  /** 목록의 번호. 고정글이면 대신 "공지" 배지가 나간다 */
  no?: number;
  pinned?: boolean;
  author?: string;
  viewCount?: number;
  /** 이미 포맷된 날짜 문자열. 서버·클라이언트가 다른 결과를 내지 않도록 미리 만든다 */
  date: string;
  /** 잠금 아이콘 */
  secret?: boolean;
  /** 제목 오른쪽 배지 (답변완료 등) */
  badge?: React.ReactNode;
  /** 제목 왼쪽 말머리 (상품명 등) */
  prefix?: string;
}

interface Props {
  rows: BoardRow[];
  /** 상세 링크 앞부분 → `${basePath}/${row.id}` */
  basePath: string;
  /** 표 제목. 화면에는 안 보이고 스크린리더만 읽는다 */
  caption: string;
  showAuthor?: boolean;
  showViews?: boolean;
  emptyText?: string;
}

/**
 * 게시판 목록 표.
 *
 * <h2>왜 {@code <table>} 인가</h2>
 * 번호·제목·글쓴이·조회·날짜는 <b>같은 종류의 값이 줄마다 반복</b>되는 자료다. div 로
 * 만들면 스크린리더가 "3열 5행" 이라는 구조를 읽어 주지 못하고, 어느 숫자가 조회수이고
 * 어느 것이 날짜인지 사용자가 순서를 외워야 한다.
 *
 * <h2>모바일에서는 표를 접는다</h2>
 * 좁은 화면에서 5칸을 가로로 유지하면 제목이 두 글자마다 줄바꿈된다. CSS 에서 번호·
 * 조회수를 숨기고 제목 아래로 글쓴이·날짜를 내린다 — 가로 스크롤을 만들지 않는다.
 */
export function BoardTable({
  rows,
  basePath,
  caption,
  showAuthor = false,
  showViews = true,
  emptyText = '등록된 글이 없습니다.',
}: Props) {
  if (rows.length === 0) {
    return <p className={styles.empty}>{emptyText}</p>;
  }

  return (
    <table className={styles.table}>
      <caption className="sr-only">{caption}</caption>
      <thead>
        <tr>
          <th scope="col" className={styles.colNo}>
            번호
          </th>
          <th scope="col">제목</th>
          {showAuthor && (
            <th scope="col" className={styles.colAuthor}>
              글쓴이
            </th>
          )}
          {showViews && (
            <th scope="col" className={styles.colViews}>
              조회
            </th>
          )}
          <th scope="col" className={styles.colDate}>
            날짜
          </th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.id} className={row.pinned ? styles.rowPinned : undefined}>
            <td className={styles.colNo}>
              {row.pinned ? (
                <span className={styles.noticeTag}>공지</span>
              ) : (
                <span className="tabular">{row.no}</span>
              )}
            </td>

            <td className={styles.cellTitle}>
              <Link href={`${basePath}/${row.id}`} className={styles.titleLink}>
                {row.prefix && <span className={styles.prefix}>[{row.prefix}]</span>}
                {row.secret && (
                  <span className={styles.lock} aria-label="비밀글">
                    <LockIcon />
                  </span>
                )}
                <span className={styles.titleText}>{row.title}</span>
              </Link>
              {row.badge && <span className={styles.badgeSlot}>{row.badge}</span>}

              {/* 모바일에서 번호·글쓴이·조회 칸이 접히면 이 줄이 그 값들을 대신한다 */}
              <span className={styles.metaMobile}>
                {row.pinned && <span className={styles.noticeTag}>공지</span>}
                {showAuthor && row.author && <span>{row.author}</span>}
                <span className="tabular">{row.date}</span>
                {showViews && <span className="tabular">조회 {row.viewCount ?? 0}</span>}
              </span>
            </td>

            {showAuthor && <td className={styles.colAuthor}>{row.author ?? '-'}</td>}
            {showViews && (
              <td className={`${styles.colViews} tabular`}>{row.viewCount ?? 0}</td>
            )}
            <td className={`${styles.colDate} tabular`}>{row.date}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/** 자물쇠. 색만으로 비밀글을 표시하지 않도록 aria-label 과 함께 쓴다. */
function LockIcon() {
  return (
    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect
        x="4"
        y="10"
        width="16"
        height="11"
        rx="2"
        stroke="currentColor"
        strokeWidth="1.5"
      />
      <path
        d="M8 10V7a4 4 0 0 1 8 0v3"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
      />
    </svg>
  );
}
