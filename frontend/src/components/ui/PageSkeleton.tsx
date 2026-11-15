import styles from './PageSkeleton.module.css';

/** 뼈대에 그릴 줄 수. 게시판 한 쪽이 보통 10건이라 그만큼 둔다. */
const ROWS = 6;

/**
 * 화면이 바뀌는 동안 본문 자리를 지키는 뼈대.
 *
 * <h2>왜 필요한가</h2>
 * 머리띠와 푸터는 이미 공유 레이아웃({@code (shop)/layout.tsx})이라 화면을 옮겨도
 * 다시 만들어지지 않는다. 그런데 {@code loading.tsx} 가 없으면 Next 는 새 화면이
 * 서버에서 올 때까지 <b>이전 화면을 그대로 둔 채 기다렸다가</b> 한 번에 갈아 끼운다.
 * 몇백 ms 동안 아무 일도 일어나지 않다가 화면 전체가 툭 바뀌니, 바뀌지도 않은
 * 머리띠까지 다시 그려진 것처럼 읽힌다.
 *
 * <p>이 뼈대를 두면 그 사이에 <b>본문 자리만</b> 바뀐다. 머리띠·푸터가 그대로 있는 것이
 * 눈에 보이고, 눌렀다는 사실이 즉시 응답으로 돌아온다.
 *
 * <h2>글자를 넣지 않는다</h2>
 * 「불러오는 중…」 같은 문구는 0.2초 뒤에 사라질 글자라, 읽기 시작한 사람의 눈만
 * 끌고 끝난다. 들어올 내용의 모양만 비워 둔다.
 */
export function PageSkeleton() {
  return (
    <div className={styles.page} aria-busy="true" aria-live="polite" aria-label="불러오는 중">
      <div className="wrap">
        <div className={styles.bar} />
        <div className={styles.title} />
        <div className={styles.rows}>
          {Array.from({ length: ROWS }, (_, i) => (
            <div key={i} className={styles.row} />
          ))}
        </div>
      </div>
    </div>
  );
}
