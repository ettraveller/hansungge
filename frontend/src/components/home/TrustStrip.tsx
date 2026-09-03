import styles from './TrustStrip.module.css';

/**
 * 시세 보드 바로 아래 신뢰 스트립.
 *
 * <p>여기 적힌 세 가지는 마케팅 문구가 아니라 <b>이 시스템이 실제로 하는 일</b>이다.
 * 시세 3층 구조(원본·보정·최종 append-only), 주문 시점 서버 재계산, 전 지점 동일 시세.
 * 구현되지 않은 것을 여기 적으면 안 된다 — 고객이 이 문장을 근거로 주문한다.
 */
export function TrustStrip() {
  return (
    <section className={styles.strip} aria-label="거래 원칙">
      <div className={styles.inner}>
        <Item
          title="고시 시세를 그대로 반영"
          desc="보정 내역과 원본 시세를 모두 보관합니다"
          icon={
            <>
              <path d="M12 3l7 3v6c0 4-3 7.5-7 9-4-1.5-7-5-7-9V6l7-3z" />
              <path d="M9 12l2 2 4-4" />
            </>
          }
        />
        <Item
          title="주문 시점에 다시 계산"
          desc="결제 금액은 서버가 확정합니다"
          icon={
            <>
              <circle cx="12" cy="12" r="9" />
              <path d="M12 7v5l3 2" />
            </>
          }
        />
        <Item
          title="전 지점 동일 시세"
          desc="온라인과 매장 가격이 같습니다"
          icon={
            <>
              <path d="M12 21s-7-5.2-7-10a7 7 0 0 1 14 0c0 4.8-7 10-7 10z" />
              <circle cx="12" cy="11" r="2.5" />
            </>
          }
        />
      </div>
    </section>
  );
}

function Item({
  title,
  desc,
  icon,
}: {
  title: string;
  desc: string;
  icon: React.ReactNode;
}) {
  return (
    <div className={styles.item}>
      {/* 선 굵기는 사이트 전체에서 1.5 하나로 통일한다 — 섞이면 아이콘이 들쭉날쭉해 보인다 */}
      <svg
        className={styles.icon}
        width="22"
        height="22"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        aria-hidden="true"
      >
        {icon}
      </svg>
      <div>
        <p className={styles.title}>{title}</p>
        <p className={styles.desc}>{desc}</p>
      </div>
    </div>
  );
}
