import { METAL_ORDER, type MarketPrice, type MetalType } from '@/types/price';
import { PRICE_INQUIRY_LABEL, formatKrw } from '@/utils/format';
import styles from './MarketPriceTable.module.css';

interface Row {
  metalType: MetalType;
  metalName: string;
  sell?: MarketPrice;
  buy?: MarketPrice;
}

/**
 * 고시 시세표 — 레퍼런스와 같은 열 구성이다.
 *
 * <pre>
 * 항목 | 내가 살 때(가격·변동률) | 내가 팔 때(가격·변동률)     단위: 3.75g / 1돈
 * </pre>
 *
 * <p>백엔드 기준으로 <b>내가 살 때 = SELL</b>(회사가 파는 가격), <b>내가 팔 때 = BUY</b> 다.
 * 이름이 반대로 보이지만 주체가 다르다 — 여기서 뒤집히면 매입가로 판매하게 된다.
 *
 * <p><b>변동률은 아직 비어 있다.</b> 백엔드 현재시세 응답에 직전값이 없다. 프론트엔드가
 * 뺄셈으로 만들어낼 수는 있지만, 금액 연산을 화면에서 하지 않는다는 원칙(P1)을 지키기 위해
 * 서버가 값을 줄 때까지 자리만 지킨다. 응답에 직전값이 추가되면 {@code changeCell} 만
 * 채우면 된다.
 */
export function MarketPriceTable({ prices }: { prices: MarketPrice[] }) {
  const rows = toRows(prices);

  return (
    <table className={styles.quote}>
      <caption className="sr-only">금속별 매입·판매 고시 시세 (1돈 3.75g 기준)</caption>
      <thead>
        <tr>
          <th scope="col" rowSpan={2} className={styles.itemCol}>
            항목
          </th>
          <th scope="col" colSpan={2} className={styles.buyGroup}>
            내가 살 때
          </th>
          <th scope="col" colSpan={2} className={styles.sellGroup}>
            내가 팔 때
          </th>
        </tr>
        <tr>
          <th scope="col" className={styles.buyGroup}>
            가격
          </th>
          <th scope="col" className={styles.buyGroup}>
            변동률
          </th>
          <th scope="col" className={styles.sellGroup}>
            가격
          </th>
          <th scope="col" className={styles.sellGroup}>
            변동률
          </th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.metalType}>
            <th scope="row" className={styles.name}>
              {row.metalName} <span className={styles.unit}>(3.75g)</span>
            </th>
            <td className={styles.strong}>{priceCell(row.sell?.pricePerDon)}</td>
            <td className={styles.change}>{changeCell()}</td>
            <td>{priceCell(row.buy?.pricePerDon)}</td>
            <td className={styles.change}>{changeCell()}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function priceCell(value: number | null | undefined) {
  if (value == null) {
    return <span className={styles.inquiry}>{PRICE_INQUIRY_LABEL}</span>;
  }
  return <span className="tabular">{formatKrw(value)}</span>;
}

/** 백엔드가 직전값을 줄 때까지 자리만 지킨다. */
function changeCell() {
  return <span className={styles.flat}>-</span>;
}

/**
 * 금속 하나가 한 줄이 되도록 SELL/BUY 를 묶는다.
 *
 * 백엔드는 금속 × side 를 평평한 배열로 주고 고시되지 않은 조합도 포함해서 준다.
 * 그대로 그리면 같은 금속이 두 줄로 나온다.
 */
function toRows(prices: MarketPrice[]): Row[] {
  return METAL_ORDER.map((metalType) => {
    const sell = prices.find((p) => p.metalType === metalType && p.priceSide === 'SELL');
    const buy = prices.find((p) => p.metalType === metalType && p.priceSide === 'BUY');
    return {
      metalType,
      metalName: sell?.metalName ?? buy?.metalName ?? metalType,
      sell,
      buy,
    };
  });
}
