'use client';

import { useState } from 'react';

import { usePaymentConfig } from '@/hooks/useOrder';
import type { Order } from '@/types/order';
import { formatDateTime, formatKrw } from '@/utils/format';
import styles from './DepositGuide.module.css';

/**
 * 무통장입금 안내 — 어디로, 얼마를, 어떤 이름으로 보내는가.
 *
 * <h2>주문 상세 맨 위에 둔다</h2>
 * 무통장 주문자가 이 화면에 오는 이유는 하나다 — 계좌번호를 보려고. 상품 목록을 지나
 * 내려가야 나오면 올 때마다 한 동작씩 길어지고, 은행 앱과 오가는 동안 그 동작이 반복된다.
 *
 * <h2>계좌번호를 복사하게 한다</h2>
 * 휴대폰으로 열두 자리를 손으로 옮겨 적는 것이 이 화면에서 가장 짜증나는 일이다.
 * 한 자리만 틀려도 돈이 엉뚱한 곳으로 가고, 되찾는 데 몇 주가 걸린다.
 *
 * <h2>금액을 함께 크게 적는다</h2>
 * 계좌만 적어 두면 얼마를 보내야 하는지 위로 올라가 확인해야 한다. 덜 보낸 입금은
 * 사람이 전화해서 풀어야 하는 일이라, 틀릴 여지를 줄이는 편이 싸다.
 *
 * <h2>「기한이 지나면 취소됩니다」라고 쓰지 않는다</h2>
 * 자동취소를 두지 않았다. 입금하려고 계좌를 열어 둔 사이에 주문이 사라지면 보낸 돈이
 * 갈 곳을 잃고, 사람이 찾아 되돌려야 하며, 그 사이 고객은 떼인 줄 안다. 지키지 않을
 * 약속을 화면에 적으면 그것이 곧 문의가 된다.
 */
export function DepositGuide({ order, justPlaced }: { order: Order; justPlaced?: boolean }) {
  const { data: config } = usePaymentConfig();
  const [copied, setCopied] = useState(false);

  const account = config?.bankAccount ?? null;

  const copy = async () => {
    if (!account) return;
    try {
      // 하이픈을 뺀 숫자만 넣는다 — 은행 앱 입력칸이 하이픈을 받지 않는 곳이 있다.
      await navigator.clipboard.writeText(account.number.replace(/[^0-9]/g, ''));
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      // 클립보드를 막아 둔 브라우저가 있다. 번호는 화면에 그대로 있으니
      // 실패를 알리지 않는다 — 손으로 적으면 된다.
    }
  };

  return (
    <section className={styles.root} aria-labelledby="deposit-guide-title">
      <h2 id="deposit-guide-title" className={styles.title}>
        {justPlaced ? '주문이 접수되었습니다' : '입금 안내'}
      </h2>
      <p className={styles.lead}>
        아래 계좌로 입금해 주세요. 입금이 확인되면 상품 준비를 시작합니다.
      </p>

      <div className={styles.amount}>
        <span className={styles.amountLabel}>입금하실 금액</span>
        <strong className={styles.amountValue}>{formatKrw(order.totalAmount)}</strong>
      </div>

      {account ? (
        <dl className={styles.rows}>
          <div className={styles.row}>
            <dt>입금 계좌</dt>
            <dd>
              <button type="button" className={styles.account} onClick={copy}>
                {/* 은행명과 번호를 따로 싼다. 한 덩어리로 두면 좁은 화면에서 줄이
                    바뀔 때 숫자 한가운데서 꺾인다 — 「123456-78-」 / 「901234」.
                    눈으로 받아 적는 값이라 그 자리에서 한 자리를 흘린다. */}
                <span className={styles.accountText}>
                  <span className={styles.accountBank}>{account.bank}</span>{' '}
                  <span className={styles.accountNo}>{account.number}</span>
                </span>
                <span className={styles.copyHint}>{copied ? '복사했습니다' : '복사'}</span>
              </button>
            </dd>
          </div>
          <div className={styles.row}>
            <dt>예금주</dt>
            <dd>{account.holder}</dd>
          </div>
          {order.depositorName && (
            <div className={styles.row}>
              <dt>입금자명</dt>
              <dd>{order.depositorName}</dd>
            </div>
          )}
          {order.depositDueAt && (
            <div className={styles.row}>
              <dt>입금 기한</dt>
              <dd>{formatDateTime(order.depositDueAt)}</dd>
            </div>
          )}
        </dl>
      ) : (
        // 계좌 설정이 비었다. 주문을 받을 때 막고 있으므로 보통 오지 않는 자리지만,
        // 주문 뒤에 설정이 지워졌을 수 있다. 빈 칸 대신 연락할 곳을 준다.
        <p className={styles.noAccount}>
          입금 계좌 안내를 불러오지 못했습니다. 고객센터(02-743-4176)로 문의해 주세요.
        </p>
      )}

      <p className={styles.note}>
        적어 주신 입금자명과 다른 이름으로 입금하시면 확인이 늦어질 수 있습니다.
        금 시세가 계속 움직이므로 입금이 늦어지면 주문을 다시 확인해 드릴 수 있습니다.
      </p>
    </section>
  );
}
