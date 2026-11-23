import Link from 'next/link';

import styles from './NotFoundContent.module.css';

/**
 * 없는 주소에 왔을 때의 본문.
 *
 * <p>전에는 Next 의 기본 화면("404 This page could not be found.")이 그대로 나갔다 —
 * 영어 한 줄에 머리띠도 홈으로 갈 링크도 없어서, 지운 상품의 주소를 들고 온 손님은
 * 창을 닫는 것 말고 할 수 있는 일이 없었다 (2026-11-23 QA-041).
 *
 * <p>머리띠·푸터는 여기 없다. 부르는 쪽이 붙인다 — {@code (shop)} 안에서는 레이아웃이
 * 이미 그리고 있고, 그 밖(어디에도 맞지 않는 주소)에서는 {@code app/not-found.tsx} 가
 * 레이아웃을 직접 감싼다.
 */
export function NotFoundContent() {
  return (
    <div className={styles.box}>
      <p className={styles.code}>404</p>
      <h1 className={styles.title}>페이지를 찾을 수 없습니다</h1>
      <p className={styles.desc}>
        주소가 바뀌었거나 판매가 끝난 상품일 수 있습니다.
        <br />
        아래에서 찾으시는 곳으로 이동해 주세요.
      </p>
      <div className={styles.links}>
        <Link href="/" className={styles.primary}>
          홈으로
        </Link>
        <Link href="/shop" className={styles.ghost}>
          전체 상품
        </Link>
        <Link href="/price" className={styles.ghost}>
          오늘의 시세
        </Link>
        <Link href="/support" className={styles.ghost}>
          고객센터
        </Link>
      </div>
    </div>
  );
}
