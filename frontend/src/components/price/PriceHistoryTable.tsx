import { formatDateTime, formatKrw } from '@/utils/format';
import styles from './PriceHistoryTable.module.css';

/** 한 시점의 고시 — 살 때와 팔 때를 짝지은 줄. */
export interface HistoryRow {
  at: string;
  sellPerDon: number | null;
  buyPerDon: number | null;
  /** 직전 고시 대비 살 때 변동액(원/돈). 첫 줄이면 null */
  changePerDon: number | null;
}

interface Props {
  rows: HistoryRow[];
  metalName: string;
}

/**
 * 고시 이력 표.
 *
 * <h2>살 때와 팔 때를 한 줄에 놓는다</h2>
 * 두 값은 <b>같은 순간에 함께 정해진다.</b> 표를 두 개로 나누면 "이 시점에 스프레드가
 * 얼마였나"를 보려고 두 표를 눈으로 맞춰야 한다.
 *
 * <h2>모바일에서는 표를 접는다</h2>
 * 네 칸을 390px 에 넣으면 금액이 줄바꿈된다. 관리자 화면과 게시판에서 이미 쓰는
 * 방식대로 줄마다 카드로 편다.
 */
export function PriceHistoryTable({ rows, metalName }: Props) {
  if (rows.length === 0) {
    return <p className={styles.empty}>이 기간에 고시된 시세가 없습니다.</p>;
  }

  return (
    <table className={styles.table}>
      <caption className="sr-only">{metalName} 고시 이력</caption>
      <thead>
        <tr>
          <th scope="col" className={styles.colDate}>
            고시일시
          </th>
          <th scope="col">내가 살 때</th>
          <th scope="col">내가 팔 때</th>
          <th scope="col" className={styles.colChange}>
            변동
          </th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.at}>
            <td className={`${styles.colDate} tabular`}>{formatDateTime(row.at)}</td>
            <td className={`${styles.price} ${styles.sellCell} tabular`}>
              <span className={styles.cellLabel}>살 때</span>
              {formatKrw(row.sellPerDon)}
            </td>
            <td className={`${styles.price} ${styles.buyCell} tabular`}>
              <span className={styles.cellLabel}>팔 때</span>
              {formatKrw(row.buyPerDon)}
            </td>
            <td className={`${styles.colChange} tabular`}>
              <Change amount={row.changePerDon} />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/**
 * 변동 표시.
 *
 * <p>화살표를 CSS {@code ::before} 가 아니라 글자로 넣는다 — 의사요소는 복사할 때와
 * 화면 낭독기에서 사라진다. 시세표에서 방향은 금액만큼 중요한 정보다.
 * ({@code utils/format} 의 {@code formatDelta} 와 같은 판단이다.)
 */
function Change({ amount }: { amount: number | null }) {
  if (amount == null) return <span className={styles.flat}>—</span>;

  // 원 미만을 먼저 버린다. 1g 시세가 소수점 두 자리라 3.75 를 곱한 돈 단가에는 자리가
  // 남고, 그 차를 그대로 찍으면 "4,999.988" 같은 금액이 나온다. 옆 칸의 금액은 이미
  // formatKrw 가 원 단위로 맞추고 있어서, 안 맞추면 같은 줄에서 단위가 어긋난다.
  const won = Math.round(amount);
  if (won === 0) return <span className={styles.flat}>— 0</span>;

  const up = won > 0;
  return (
    <span className={up ? styles.up : styles.down}>
      {up ? '▲' : '▼'} {Math.abs(won).toLocaleString('ko-KR')}
    </span>
  );
}
