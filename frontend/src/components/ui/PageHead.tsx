import styles from './PageHead.module.css';

interface Props {
  title: string;
  /** 한 줄 설명. 덧붙일 말이 없으면 비운다 — 자리를 채우려고 쓰면 읽히지 않는다. */
  lead?: string;
}

/**
 * 페이지 머리글 — 제목 + 밑줄.
 *
 * <h2>왜 페이지에서 쓰나</h2>
 * 주문서·결제 계열 화면은 상황마다 다른 것을 돌려준다 — 불러오는 중, 주문할 것이 없음,
 * 결제할 수 없는 상태, 본문. 머리글을 뷰 안에 두면 그중 한 분기에서 빠지기 쉽고,
 * 빠진 분기는 그 상황이 실제로 날 때까지 아무도 모른다. 페이지에서 한 번 쓰면
 * 어느 분기로 가든 제목과 위쪽 여백이 남는다.
 *
 * <h2>{@code SectionHead} 와 다른 것</h2>
 * 저쪽은 한 화면 <b>안의</b> 구획을 가른다(시세 안내, 국제 시세…). 이것은 화면 자체의
 * 이름이라 {@code h1} 이고, 아래에 굵은 밑줄이 있다.
 */
export function PageHead({ title, lead }: Props) {
  return (
    <header className={styles.head}>
      <h1 className={styles.title}>{title}</h1>
      {lead && <p className={styles.lead}>{lead}</p>}
    </header>
  );
}
