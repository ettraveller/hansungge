'use client';

import Link from 'next/link';
import { useState } from 'react';

import { Notice } from '@/components/auth/Notice';
import { Toast } from '@/components/ui/Toast';
import { MyPageShell, myPageStyles as shell } from '@/layouts/MyPageShell';
import { useReorder } from '@/hooks/useCart';
import { useMyPageSummary } from '@/hooks/useInterest';
import { useOrders } from '@/hooks/useOrder';
import { ApiError } from '@/types/api';
import type { Order, OrderStatusGroup } from '@/types/order';
import { formatDateTime, formatKrw } from '@/utils/format';
import styles from '@/views/OrderListView.module.css';

/**
 * 주문/배송내역.
 *
 * <p>금액은 <b>주문 시점 스냅샷</b>이라 다시 계산하지 않는다. 장바구니처럼 현재 시세로
 * 고쳐 보여주면, 지난달에 낸 돈이 오늘 시세로 바뀌어 보인다.
 */

/**
 * 상태 묶음. 고객은 'PREPARING' 과 'PAID' 를 구분해 생각하지 않는다.
 *
 * <p>거르기는 <b>서버</b>가 한다. 예전에는 받은 한 장(20건)을 화면에서 걸렀는데, 탭의
 * 숫자는 서버가 센 총계라 둘이 어긋났다 — "결제 대기 3" 을 눌렀는데 목록이 비는 일이
 * 생긴다(그 3건이 다음 장에 있을 때). 값은 백엔드 OrderStatusGroup 과 같아야 한다.
 */
const FILTERS: { key: OrderStatusGroup; name: string }[] = [
  { key: 'ALL', name: '전체' },
  { key: 'AWAITING_PAYMENT', name: '결제 대기' },
  { key: 'PREPARING', name: '준비 중' },
  { key: 'SHIPPING', name: '배송' },
  { key: 'CANCELLED', name: '취소·환불' },
];

/**
 * 기간 버튼. 삼성금거래소·네이버 등 국내 쇼핑몰이 쓰는 묶음과 같게 둔다.
 *
 * <p>기본값을 "전체"로 두는 이유 — 금은 한 해에 한두 번 사는 물건이다. 3개월로 잘라
 * 두면 작년에 산 골드바를 찾으러 온 사람이 "주문이 없습니다"를 본다.
 */
const PERIODS: { key: string; name: string; days: number | null }[] = [
  { key: 'all', name: '전체', days: null },
  { key: 'd1', name: '오늘', days: 0 },
  { key: 'd7', name: '1주일', days: 7 },
  { key: 'm1', name: '1개월', days: 30 },
  { key: 'm3', name: '3개월', days: 90 },
  { key: 'm6', name: '6개월', days: 180 },
];

/**
 * 고를 수 있는 가장 이른 날.
 *
 * <p>2025년 이전 주문은 없다. 달력을 열어 2003년까지 내려갈 수 있으면 잘못 고르기만
 * 쉽고 얻는 것이 없다.
 */
const MIN_DATE = '2025-01-01';

function todayText(): string {
  return toDateText(new Date());
}

/** Date → 'YYYY-MM-DD'. toISOString 은 UTC 라 한국 시간으로 자정 근처에 하루가 밀린다. */
function toDateText(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** 기간 버튼 → 시작 날짜. null 이면 전체다. */
function startDateOf(days: number | null): string | undefined {
  if (days == null) return undefined;
  const base = new Date();
  if (days > 0) base.setDate(base.getDate() - days);
  return toDateText(base);
}

/** 'YYYY-MM-DD' → 그날 00:00(현지) ISO. 서버는 [from, to) 로 본다. */
function startOfDay(text: string | undefined): string | undefined {
  if (!text) return undefined;
  const d = new Date(`${text}T00:00:00`);
  return Number.isNaN(d.getTime()) ? undefined : d.toISOString();
}

/** 'YYYY-MM-DD' → <b>다음날</b> 00:00 ISO. 그래야 고른 날 하루가 통째로 들어온다. */
function endOfDay(text: string | undefined): string | undefined {
  if (!text) return undefined;
  const d = new Date(`${text}T00:00:00`);
  if (Number.isNaN(d.getTime())) return undefined;
  d.setDate(d.getDate() + 1);
  return d.toISOString();
}

export function OrderListView() {
  return (
    <MyPageShell title="주문/배송내역" lead="주문하신 시각의 시세로 확정된 금액입니다.">
      <OrderList />
    </MyPageShell>
  );
}

function OrderList() {
  const [page, setPage] = useState(0);
  const [filter, setFilter] = useState<OrderStatusGroup>('ALL');
  const [period, setPeriod] = useState('all');
  // 직접 고른 날짜. 버튼을 누르면 여기도 같이 채워 달력과 버튼이 어긋나지 않게 한다.
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  // 알림은 목록에 하나다. 주문마다 두면 연달아 담았을 때 겹쳐 쌓인다.
  const [toast, setToast] = useState<string | null>(null);

  // 시작이 끝보다 뒤면 조회하지 않는다. 그대로 보내면 늘 빈 목록이 와서
  // "주문이 없다" 로 읽힌다 — 날짜를 잘못 고른 것인데.
  const rangeInvalid = from !== '' && to !== '' && from > to;

  const { data, isLoading, error } = useOrders({
    page,
    size: 20,
    group: filter,
    from: rangeInvalid ? undefined : startOfDay(from || undefined),
    to: rangeInvalid ? undefined : endOfDay(to || undefined),
  });
  const { data: summary } = useMyPageSummary();

  // 조건을 바꾸면 첫 장으로. 3페이지를 보다 거르면 결과가 한 장뿐이라 빈 화면이 된다.
  const change = (next: () => void) => {
    next();
    setPage(0);
  };

  const pickPeriod = (key: string, days: number | null) =>
    change(() => {
      setPeriod(key);
      setFrom(startDateOf(days) ?? '');
      setTo(days == null ? '' : todayText());
    });

  // 달력을 직접 건드리면 버튼 선택을 푼다 — 둘 다 켜져 있으면 어느 쪽이 적용됐는지 모른다.
  const pickDate = (which: 'from' | 'to', value: string) =>
    change(() => {
      setPeriod('custom');
      (which === 'from' ? setFrom : setTo)(value);
    });

  if (isLoading) {
    return <p className={shell.loading}>불러오는 중…</p>;
  }

  if (error) {
    return (
      <Notice tone="error" title="주문 내역을 불러오지 못했습니다">
        {error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요.'}
      </Notice>
    );
  }

  const shown = data?.content ?? [];

  // 탭의 숫자는 서버가 센 총계다. 기간을 걸어도 이 숫자는 바뀌지 않는다 —
  // "전체 기간에 이만큼 있다"는 안내이고, 목록은 고른 기간만 보여 준다.
  const counts: Record<OrderStatusGroup, number | undefined> = {
    ALL: summary?.orderCount,
    AWAITING_PAYMENT: summary?.awaitingPayment,
    PREPARING: summary?.preparing,
    SHIPPING: summary ? summary.shipping + summary.delivered : undefined,
    CANCELLED: summary?.cancelled,
  };

  return (
    <>
      <div className={shell.tabs} role="tablist" aria-label="주문 상태">
        {FILTERS.map((f) => (
          <button
            key={f.key}
            type="button"
            role="tab"
            aria-selected={f.key === filter}
            className={f.key === filter ? `${shell.tab} ${shell.tabOn}` : shell.tab}
            onClick={() => change(() => setFilter(f.key))}
          >
            {f.name}
            {counts[f.key] != null && <span className={shell.tabCount}>{counts[f.key]}</span>}
          </button>
        ))}
      </div>

      <div className={styles.filterBar}>
        <div className={styles.periods} role="group" aria-label="조회 기간">
          {PERIODS.map((p) => (
            <button
              key={p.key}
              type="button"
              aria-pressed={p.key === period}
              className={p.key === period ? `${styles.period} ${styles.periodOn}` : styles.period}
              onClick={() => pickPeriod(p.key, p.days)}
            >
              {p.name}
            </button>
          ))}
        </div>

        <div className={styles.dates}>
          <input
            type="date"
            className={styles.date}
            aria-label="조회 시작일"
            value={from}
            min={MIN_DATE}
            max={todayText()}
            onChange={(e) => pickDate('from', e.target.value)}
          />
          <span className={styles.dateTilde} aria-hidden="true">
            ~
          </span>
          <input
            type="date"
            className={styles.date}
            aria-label="조회 종료일"
            value={to}
            min={from || MIN_DATE}
            max={todayText()}
            onChange={(e) => pickDate('to', e.target.value)}
          />
        </div>
      </div>

      {rangeInvalid && (
        <p className={styles.rangeWarn} role="alert">
          시작일이 종료일보다 뒤입니다. 날짜를 다시 골라 주세요.
        </p>
      )}

      {shown.length === 0 ? (
        <div className={shell.empty}>
          <p>
            {filter === 'ALL' && period === 'all' && from === '' && to === ''
              ? '아직 주문하신 내역이 없습니다.'
              : '고르신 조건에 맞는 주문이 없습니다.'}
          </p>
          {filter === 'ALL' && period === 'all' && from === '' && to === '' && (
            <div className={shell.emptyAction}>
              <Link className={shell.pillDark} href="/shop">
                상품 보러 가기
              </Link>
            </div>
          )}
        </div>
      ) : (
        <div>
          {shown.map((order) => (
            <OrderRow key={order.orderNo} order={order} onReordered={setToast} />
          ))}
        </div>
      )}

      <Toast
        message={toast}
        onDone={() => setToast(null)}
        action={{ href: '/cart', label: '장바구니 보기' }}
      />

      {data && data.totalPages > 1 && (
        <div className={styles.pager}>
          <button
            type="button"
            className={shell.pill}
            disabled={data.first}
            onClick={() => setPage((p) => Math.max(p - 1, 0))}
          >
            이전
          </button>
          <span className={styles.pagerNow}>
            {data.number + 1} / {data.totalPages}
          </span>
          <button
            type="button"
            className={shell.pill}
            disabled={data.last}
            onClick={() => setPage((p) => p + 1)}
          >
            다음
          </button>
        </div>
      )}
    </>
  );
}

function OrderRow({
  order,
  onReordered,
}: {
  order: Order;
  onReordered: (message: string) => void;
}) {
  const item = order.items[0];
  const reorder = useReorder();

  /**
   * 같은 상품을 다시 담는다.
   *
   * <p>수량과 규격까지 그대로 가져간다. 금액은 <b>가져가지 않는다</b> — 주문 금액은
   * 그때의 시세로 굳은 값이고, 장바구니는 오늘 시세로 다시 계산한다. 담은 뒤 값이
   * 달라져 있으면 장바구니가 차액을 알려 준다.
   */
  const addAllToCart = () => {
    const lines = order.items.map((i) => ({
      productId: i.productId,
      optionId: i.optionId,
      quantity: i.quantity,
    }));

    reorder.mutate(lines, {
      onSuccess: ({ added, failed }) => {
        if (added === 0) {
          onReordered('지금 담을 수 있는 상품이 없습니다');
        } else if (failed > 0) {
          onReordered(`${added}개를 담았습니다 · ${failed}개는 지금 담을 수 없습니다`);
        } else {
          onReordered(`${added}개를 장바구니에 담았습니다`);
        }
      },
      onError: () => onReordered('장바구니에 담지 못했습니다. 잠시 후 다시 시도해 주세요'),
    });
  };

  return (
    <article className={styles.row}>
      <div className={styles.rowHead}>
        <strong className={order.status === 'PAID' ? `${styles.state} ${styles.stateOk}` : styles.state}>
          {order.statusLabel}
        </strong>
        <Link href={`/mypage/orders/${order.orderNo}`} className={styles.orderNo}>
          {order.orderNo}
        </Link>
        <span className={styles.when}>{formatDateTime(order.orderedAt)}</span>
      </div>

      <div className={styles.body}>
        {/* 상품 사진 자리. 주문 항목은 주문 당시를 복사한 스냅샷이라 이미지 키를 들고
                있지 않다 — 넣으려면 스냅샷에 키를 추가하는 마이그레이션이 먼저다 */}
        <div className={styles.thumb} aria-hidden="true">
          상품
          <br />
          사진
        </div>

        <div className={styles.info}>
          <div className={styles.name}>{order.orderName}</div>
          <div className={styles.price}>{formatKrw(order.totalAmount)}</div>
          {item && (
            <div className={styles.sub}>
              {item.quantity}개
              {item.marketPrice != null && ` · 주문 시각 시세 ${formatKrw(item.marketPrice)}/g`}
            </div>
          )}
        </div>
      </div>

      <div className={styles.acts}>
        {order.payable ? (
          <>
            <Link href={`/checkout/${order.orderNo}`} className={shell.pillDark}>
              결제하기
            </Link>
            <Link href={`/mypage/orders/${order.orderNo}`} className={shell.pill}>
              주문 상세
            </Link>
          </>
        ) : (
          <>
            {/* 결제 대기 주문에는 두지 않는다. 거기서 할 일은 다시 담는 것이 아니라 결제다. */}
            <button
              type="button"
              className={shell.pillDark}
              disabled={reorder.isPending}
              onClick={addAllToCart}
            >
              장바구니 담기
            </button>
            <Link href={`/mypage/orders/${order.orderNo}`} className={shell.pill}>
              주문 상세
            </Link>
            <Link href="/support/qna/write" className={shell.pill}>
              교환·반품 문의
            </Link>
          </>
        )}
      </div>
    </article>
  );
}
