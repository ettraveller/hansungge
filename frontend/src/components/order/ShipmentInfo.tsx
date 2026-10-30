'use client';

import { useState } from 'react';

import { useShipment } from '@/hooks/useOrder';
import type { OrderStatus, Shipment } from '@/types/order';
import { formatDateTime } from '@/utils/format';
import styles from './ShipmentInfo.module.css';

/**
 * 고객이 보는 배송 정보.
 *
 * <h2>송장번호를 복사하게 한다</h2>
 * 조회 링크에 번호가 붙어 있지 않다 — 우체국이 번호를 GET 으로 받는 공개 주소를
 * 보장하지 않아서, 붙인 링크는 어느 날 조용히 빈 화면이 된다. 그래서 번호를 눌러
 * 복사하고 조회 페이지에 붙이는 길로 만든다. 휴대폰에서 13자리를 손으로 옮겨 적는
 * 것이 이 화면에서 가장 짜증나는 일이라, 복사가 되는지 눈에 보여야 한다.
 *
 * <h2>단계는 색이 아니라 굵기로 알린다</h2>
 * 결제완료 › 상품준비 › 배송중 › 배송완료 중 지금 자리만 굵게 둔다. 이 프로젝트의
 * 주문서 단계 표시가 이미 그 방식이고({@code CheckoutView.module.css .stepOn}),
 * 색을 쓰면 흰 화면에서 전부 경고처럼 읽힌다.
 *
 * <h2>발송 전에도 띄운다</h2>
 * 배송칸이 발송 뒤에만 나타나면, 그 전에 들어온 고객은 "내 주문에는 배송 정보가
 * 없다"고 읽는다. 「발송 준비 중」이라고 적어 두는 쪽이 기다리게 만든다.
 */
export function ShipmentInfo({ orderNo, orderStatus }: {
  orderNo: string;
  orderStatus: OrderStatus;
}) {
  // 결제 전 주문에는 묻지 않는다. 보낼 것이 없는 주문에 배송칸을 띄우면
  // "준비 중"이 아니라 "뭔가 빠진 화면"으로 읽힌다.
  const relevant = SHIPPABLE.includes(orderStatus);
  const { data, isLoading } = useShipment(orderNo, relevant);

  if (!relevant) return null;

  if (isLoading || !data) {
    // 실패해도 조용히 접는다. 배송 조회가 안 되는 것 때문에 주문 상세 전체가
    // 오류 화면이 되면 고객은 주문이 잘못된 줄 안다.
    return null;
  }

  const shipped = data.status !== 'PENDING';

  return (
    <section className={styles.box} aria-labelledby="shipment-title">
      <div className={styles.head}>
        <h2 id="shipment-title" className={styles.title}>
          배송 정보
        </h2>
        <span className={styles.state}>{data.statusLabel}</span>
      </div>

      <ol className={styles.steps}>
        {STEPS.map((step) => (
          <li
            key={step.key}
            className={step.key === stepOf(orderStatus) ? styles.stepOn : undefined}
            aria-current={step.key === stepOf(orderStatus) ? 'step' : undefined}
          >
            {step.label}
          </li>
        ))}
      </ol>

      {shipped ? (
        <Tracking shipment={data} />
      ) : (
        <p className={styles.note}>
          상품을 준비하고 있습니다. 발송하면 송장번호가 이 자리에 올라옵니다.
        </p>
      )}
    </section>
  );
}

function Tracking({ shipment }: { shipment: Shipment }) {
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    if (!shipment.trackingNo) return;
    try {
      await navigator.clipboard.writeText(shipment.trackingNo);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      // 클립보드를 막아 둔 브라우저가 있다. 번호는 화면에 그대로 있으니
      // 실패를 알리지 않는다 — 손으로 적으면 된다.
    }
  };

  return (
    <>
      <dl className={styles.rows}>
        <div>
          <dt>택배사</dt>
          <dd>{shipment.carrierName ?? '우체국택배'}</dd>
        </div>
        <div>
          <dt>송장번호</dt>
          <dd>
            <button type="button" className={styles.trackingNo} onClick={copy}>
              <span>{shipment.trackingNo}</span>
              <span className={styles.copyHint}>{copied ? '복사했습니다' : '복사'}</span>
            </button>
          </dd>
        </div>
        <div>
          <dt>발송</dt>
          <dd>{formatDateTime(shipment.shippedAt)}</dd>
        </div>
        {shipment.deliveredAt && (
          <div>
            <dt>수령</dt>
            <dd>{formatDateTime(shipment.deliveredAt)}</dd>
          </div>
        )}
      </dl>

      {shipment.trackingUrl && (
        <a
          className={styles.trackLink}
          href={shipment.trackingUrl}
          target="_blank"
          rel="noreferrer"
        >
          우체국에서 배송 조회 ↗
        </a>
      )}
      <p className={styles.note}>
        조회 화면에서 위 송장번호를 붙여 넣으면 진행 상황이 나옵니다.
      </p>
    </>
  );
}

/** 배송이 있을 수 있는 주문 상태. 이것 말고는 배송칸을 그리지 않는다. */
const SHIPPABLE: OrderStatus[] = ['PAID', 'PREPARING', 'SHIPPED', 'DELIVERED'];

type StepKey = 'PAID' | 'PREPARING' | 'SHIPPED' | 'DELIVERED';

const STEPS: { key: StepKey; label: string }[] = [
  { key: 'PAID', label: '결제완료' },
  { key: 'PREPARING', label: '상품준비' },
  { key: 'SHIPPED', label: '배송중' },
  { key: 'DELIVERED', label: '배송완료' },
];

/**
 * 지금 어느 단계인가.
 *
 * <p>주문 상태로 정한다 — 배송 상태가 아니라. 둘은 같이 움직이지만(ShipmentService),
 * 고객이 위에서 본 주문 뱃지와 이 줄이 다른 말을 하면 그게 먼저 눈에 띈다.
 */
function stepOf(status: OrderStatus): StepKey {
  return (STEPS.find((s) => s.key === status)?.key) ?? 'PAID';
}
