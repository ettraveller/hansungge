import {
  DELIVERY_COMMON,
  DELIVERY_PERIOD_NOTICE,
  IN_STOCK_DELIVERY,
  MADE_TO_ORDER_DELIVERY,
  MADE_TO_ORDER_WITHDRAWAL,
} from '@/content/delivery';
import styles from './DeliveryTerms.module.css';

/**
 * 상품 상세의 배송·청약철회 안내.
 *
 * <h2>구분은 적고, 기간은 상세설명으로 보낸다 (2026-11-05)</h2>
 * 전에는 「영업일 7일 이내」·「결제일부터 최대 6주」를 이 칸에 찍었다. 그 두 값은
 * 상품의 성격이 아니라 <b>등록 화면의 체크박스 하나</b>로 갈렸고, 체크가 틀린
 * 상품은 지킬 수 없는 기간을 약속했다. 실제 기간은 같은 주문제작 안에서도
 * 제품마다 다르다.
 *
 * <p>구분(「재고 보유」·「주문제작」)은 그대로 보인다 — 그것은 체크박스가 실제로
 * 아는 사실이다. 기간만 {@link DELIVERY_PERIOD_NOTICE} 로 바뀐다.
 *
 * <p>⚠️ 그래서 <b>상세설명에 기간이 적혀 있어야 한다.</b> PG 심사가 상품페이지
 * 내 기재를 요구하고(V33), 전자상거래법 제13조 제2항의 공급시기 고지이기도 하다.
 * 고지를 면제받은 것이 아니라 적는 자리를 옮긴 것이다.
 *
 * <h2>청약철회 제한은 꾸밈이 아니라 고지다</h2>
 * 전자상거래법 제17조 제2항 제1호는 주문을 받아 개별 생산되는 재화의 청약철회를
 * 제한할 수 있게 하지만, 같은 항 단서가 <b>미리 알릴 것</b>을 조건으로 둔다.
 * 고지가 없으면 제한이 무효가 되어 제작이 끝난 주문도 환불해야 한다. 주문제작
 * 상품에서 이 문구를 지우면 법적 근거가 사라진다.
 *
 * <h2>문구는 여기서 만들지 않는다</h2>
 * 같은 기간을 이용약관·환불약관·이용안내가 함께 말한다. {@code content/delivery.ts}
 * 하나에서 읽어 — 각자 적어 두면 한 곳만 고치는 날 공개된 약속이 어긋난다.
 */
export function DeliveryTerms({ madeToOrder }: { madeToOrder: boolean }) {
  const terms = madeToOrder ? MADE_TO_ORDER_DELIVERY : IN_STOCK_DELIVERY;

  return (
    <section className={styles.box} aria-labelledby="delivery-terms">
      <h2 id="delivery-terms" className={styles.srOnly}>
        배송 안내
      </h2>

      <dl className={styles.rows}>
        <div>
          <dt>구분</dt>
          <dd className={styles.kind}>{terms.label}</dd>
        </div>
        <div>
          <dt>배송기간</dt>
          <dd>
            <strong>{DELIVERY_PERIOD_NOTICE}</strong>
          </dd>
        </div>
        <div>
          <dt>택배사</dt>
          <dd>{DELIVERY_COMMON.carrier}</dd>
        </div>
      </dl>

      {/* 주문제작만. 재고 상품에 붙이면 있지도 않은 제한을 알리는 것이 되고,
          그것은 고객의 청약철회권을 근거 없이 깎는 안내다. */}
      {madeToOrder && <p className={styles.withdrawal}>{MADE_TO_ORDER_WITHDRAWAL}</p>}
    </section>
  );
}
