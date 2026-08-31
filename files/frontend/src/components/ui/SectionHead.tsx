import Link from 'next/link';
import styles from './SectionHead.module.css';

interface Props {
  title: string;
  desc?: string;
  /** 우측 더보기 링크. 둘 다 있어야 그려진다 */
  moreHref?: string;
  moreLabel?: string;
  /** 페이지 최상단이면 h1, 페이지 안의 섹션이면 h2 */
  as?: 'h1' | 'h2';
}

/**
 * 섹션 머리 — 좌측정렬 제목 + 설명, 우측 더보기.
 *
 * <h2>가운데정렬 3줄을 쓰지 않는 이유</h2>
 * 예전에는 섹션마다 <b>작은영문 / 큰제목 / 설명</b>을 가운데 정렬로 반복했다. 같은 형태가
 * 네 번 이어지면 스크롤에 리듬이 없어져 어디까지 읽었는지 알기 어렵고, 시선이 매번
 * 화면 가운데로 돌아왔다가 다시 왼쪽 본문으로 내려간다.
 *
 * <p>영문 라벨(TODAY'S PRICE, PRODUCTS)도 뺐다. 한국어 사이트에서 그 줄은 읽히지 않고
 * 자리만 차지한다 — 같은 뜻이 바로 아래 한글 제목에 다시 나온다.
 */
export function SectionHead({ title, desc, moreHref, moreLabel, as = 'h2' }: Props) {
  const Heading = as;

  return (
    <div className={styles.head}>
      <Heading className={styles.title}>{title}</Heading>
      {desc && <p className={styles.desc}>{desc}</p>}
      {moreHref && moreLabel && (
        <Link href={moreHref} className={styles.more}>
          {moreLabel}
        </Link>
      )}
    </div>
  );
}
