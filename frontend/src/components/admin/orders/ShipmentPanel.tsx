'use client';

import { useState } from 'react';

import { Alert } from '../Alert';
import { Button } from '../Button';
import { Field, TextInput } from '../Field';
import { Panel } from '../Panel';
import { useMarkDelivered, useShip, useShipment } from '@/hooks/admin/useOrders';
import { ApiError } from '@/types/api';
import type { Shipment } from '@/types/order-admin';
import { TrackingSteps, parseTrackingSteps } from '@/components/order/TrackingSteps';
import { formatDateTime } from '@/utils/format';
import styles from '../admin.module.css';

/**
 * 배송 — 송장 입력 · 발송 · 수령 기록.
 *
 * <h2>세 모습이다</h2>
 * 발송 전에는 송장번호 입력칸, 발송 후에는 번호와 조회 링크와 「배송완료 처리」,
 * 완료 뒤에는 수령 시각. 한 화면에 다 띄우면 지금 뭘 해야 하는지가 묻힌다.
 *
 * <h2>수령은 사람이 누른다</h2>
 * 택배사가 "배송완료"라고 해도 그것으로 확정하지 않는다. 고가 귀금속의 수령 확인은
 * 사람이 남기는 기록이어야 분쟁에서 근거가 된다 (이용약관 제10조).
 */
export function ShipmentPanel({ orderId, canEdit }: { orderId: number; canEdit: boolean }) {
  const { data, isLoading, error } = useShipment(orderId);
  const ship = useShip();
  const deliver = useMarkDelivered();

  // 번호를 고치는 중인가. 발송 뒤에도 입력칸을 열 수 있어야 한다 — 잘못 적는 일이
  // 실제로 생기고, 그때 상태를 되감게 하는 것보다 번호만 바꾸는 쪽이 안전하다.
  const [editing, setEditing] = useState(false);
  const [trackingNo, setTrackingNo] = useState('');

  if (isLoading) {
    return (
      <Panel title="배송">
        <p className={styles.loading}>불러오는 중…</p>
      </Panel>
    );
  }

  if (error || !data) {
    return (
      <Panel title="배송">
        <Alert tone="danger">
          {error instanceof ApiError ? error.message : '배송 정보를 불러오지 못했습니다.'}
        </Alert>
      </Panel>
    );
  }

  const shipped = data.status !== 'PENDING';
  const delivered = data.status === 'DELIVERED';
  const showForm = canEdit && (!shipped || editing);

  const submit = () =>
    ship.mutate(
      { id: orderId, trackingNo },
      {
        onSuccess: () => {
          setTrackingNo('');
          setEditing(false);
        },
      },
    );

  return (
    <Panel title="배송" note={noteFor(data)}>
      <div className={styles.form}>
        {ship.error && (
          <Alert tone="danger">
            {ship.error instanceof ApiError ? ship.error.message : '발송 처리에 실패했습니다.'}
          </Alert>
        )}
        {deliver.error && (
          <Alert tone="danger">
            {deliver.error instanceof ApiError
              ? deliver.error.message
              : '배송완료 처리에 실패했습니다.'}
          </Alert>
        )}

        <dl className={styles.detailList}>
          <div>
            <dt>택배사</dt>
            <dd>{data.carrierName ?? '우체국택배'}</dd>
          </div>
          {shipped && (
            <>
              <div>
                <dt>송장번호</dt>
                <dd className={styles.num}>
                  <strong>{data.trackingNo}</strong>
                  {data.trackingUrl && (
                    <>
                      {' '}
                      <a href={data.trackingUrl} target="_blank" rel="noreferrer">
                        우체국에서 조회 ↗
                      </a>
                    </>
                  )}
                </dd>
              </div>
              <div>
                <dt>발송 시각</dt>
                <dd className={styles.muted}>{formatDateTime(data.shippedAt)}</dd>
              </div>
            </>
          )}
          {delivered && (
            <div>
              <dt>수령 시각</dt>
              <dd>
                <strong style={{ color: 'var(--ok)' }}>{formatDateTime(data.deliveredAt)}</strong>
              </dd>
            </div>
          )}
        </dl>

        {showForm && (
          <Field
            label="송장번호"
            htmlFor="tracking-no"
            hint="숫자 13자리. 하이픈·공백은 넣어도 서버가 지운다."
          >
            <TextInput
              id="tracking-no"
              value={trackingNo}
              inputMode="numeric"
              maxLength={20}
              onChange={(e) => setTrackingNo(e.target.value)}
            />
          </Field>
        )}

        {/*
          우체국이 준 진행 단계. 고객 화면과 같은 것을 보여준다 — 다른 것을 보면
          "고객에게는 뭐가 보이나"를 묻는 전화를 받고도 답할 수 없다.

          키가 없거나 아직 집화 전이면 비어서 안 그린다. 빈 상자를 띄워 두면
          "조회가 안 되는 건가" 하고 기다리게 된다.
        */}
        <TrackingSteps
          steps={parseTrackingSteps(data.trackingDetail)}
          syncedAt={data.trackingSyncedAt}
        />

        {/* 단계가 없는데 조회는 돌았을 때. 마지막 상태만이라도 알린다. */}
        {parseTrackingSteps(data.trackingDetail).length === 0
          && data.trackingSyncedAt && data.trackingStatus && (
          <p className={styles.hint}>
            택배사 상태 <strong>{data.trackingStatus}</strong> ·{' '}
            {formatDateTime(data.trackingSyncedAt)} 기준
          </p>
        )}

        {canEdit && (
          <div className={styles.formActions}>
            {showForm ? (
              <>
                <Button
                  variant="gold"
                  disabled={ship.isPending || trackingNo.trim() === ''}
                  onClick={submit}
                >
                  {ship.isPending ? '처리 중…' : shipped ? '송장번호 저장' : '발송 처리'}
                </Button>
                {shipped && (
                  <Button
                    disabled={ship.isPending}
                    onClick={() => {
                      setEditing(false);
                      setTrackingNo('');
                    }}
                  >
                    취소
                  </Button>
                )}
              </>
            ) : (
              <>
                {!delivered && (
                  <Button
                    variant="primary"
                    disabled={deliver.isPending}
                    onClick={() => deliver.mutate(orderId)}
                  >
                    {deliver.isPending ? '처리 중…' : '배송완료 처리'}
                  </Button>
                )}
                {!delivered && (
                  <Button onClick={() => setEditing(true)}>송장번호 수정</Button>
                )}
              </>
            )}
          </div>
        )}

        {!canEdit && (
          <p className={styles.hint}>배송 처리는 매니저(MANAGER) 이상만 할 수 있습니다.</p>
        )}
      </div>
    </Panel>
  );
}

function noteFor(shipment: Shipment): string {
  switch (shipment.status) {
    case 'PENDING':
      return '송장번호를 넣으면 주문이 배송중으로 바뀌고 고객에게 번호가 보인다.';
    case 'DELIVERED':
      return '수령이 기록됐다. 분실·수령거부를 다툴 때 근거가 되므로 지우지 않는다.';
    default:
      return '고객 마이페이지에 송장번호와 우체국 조회 링크가 보인다.';
  }
}
