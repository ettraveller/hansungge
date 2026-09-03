import Link from 'next/link';
import styles from './BoardList.module.css';

export interface BoardPost {
  id: number;
  title: string;
  date: string;
  /** 새 글 표시 */
  fresh?: boolean;
}

interface Props {
  title: string;
  moreHref: string;
  posts: BoardPost[];
  /** 게시글 상세 경로의 앞부분 */
  basePath: string;
}

/**
 * 뉴스센터 · 공지사항 목록 (레퍼런스와 같은 형태, 각 4건).
 *
 * <p>게시판 도메인은 STEP 15~16 이라 아직 API 가 없다. 지금은 화면 구조만 세워 두고
 * 데이터는 props 로 받는다 — 백엔드가 붙을 때 <b>이 컴포넌트는 바뀌지 않고</b>
 * 호출부의 데이터 출처만 바뀐다.
 *
 * <p>비어 있을 때 섹션을 통째로 감추지 않는다. 자리가 사라지면 화면 구성이 달라 보이고,
 * 운영자가 "글이 안 올라간 건지 기능이 없는 건지" 구분하지 못한다.
 */
export function BoardList({ title, moreHref, posts, basePath }: Props) {
  return (
    <div className={styles.board}>
      <div className={styles.head}>
        <h3 className={styles.title}>{title}</h3>
        <Link href={moreHref} className={styles.more}>
          더보기
          <span aria-hidden="true"> +</span>
        </Link>
      </div>

      {posts.length === 0 ? (
        <p className={styles.empty}>등록된 글이 없습니다.</p>
      ) : (
        <ul className={styles.list}>
          {posts.map((post) => (
            <li key={post.id}>
              <Link href={`${basePath}/${post.id}`} className={styles.item}>
                <span className={styles.postTitle}>
                  {post.title}
                  {post.fresh && <span className={styles.new}>NEW</span>}
                </span>
                <time className={styles.date} dateTime={post.date}>
                  {post.date}
                </time>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
