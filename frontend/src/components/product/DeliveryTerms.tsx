import {
  DELIVERY_COMMON,
  IN_STOCK_DELIVERY,
  MADE_TO_ORDER_DELIVERY,
  MADE_TO_ORDER_WITHDRAWAL,
} from '@/content/delivery';
import styles from './DeliveryTerms.module.css';

/**
 * 상품 상세의 배송·청약철회 안내.
 *
 * <h2>왜 상품마다 적는가</h2>
 * 사이트 공통 안내로 한 줄 두면 "이 상품이 어느 쪽인지"를 고객이 알 수 없다.
 * 재고를 들고 있는 골드바(영업일 7일)와 주문을 받아 만드는 반지(최대 6주)는
 * 기간이 몇 배 차이 나므로, 한 문장으로 적으면 둘 중 하나가 거짓이 된다.
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
            <strong>{terms.period}</strong>
            {madeToOrder && (
              <span className={styles.breakdown}> ({MADE_TO_ORDER_DELIVERY.breakdown})</span>
            )}
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
