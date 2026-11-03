'use client';

import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';

import { Notice } from '@/components/auth/Notice';
import {
  DeliveryAddressSection,
  EMPTY_ADDRESS,
  toAddressForm,
  type AddressForm,
} from '@/components/checkout/DeliveryAddressSection';
import { DeliveryMemoSelect } from '@/components/checkout/DeliveryMemoSelect';
import { RefundPolicyConsent } from '@/components/checkout/RefundPolicyConsent';
import { useCart } from '@/hooks/useCart';
import { policyKeys, useRefundPolicy } from '@/hooks/usePolicy';
import { useAddresses, useCreateAddress } from '@/hooks/useAddresses';
import { useCreateOrder, usePaymentConfig } from '@/hooks/useOrder';
import { useUserSession } from '@/hooks/useUserSession';
import { ApiError } from '@/types/api';
import type { PaymentMethod } from '@/types/order';
import { formatKrw, formatPhoneInput } from '@/utils/format';
import styles from '@/views/CheckoutView.module.css';


/**
 * 주문서 작성 — 배송지를 받고 주문을 만든다.
 *
 * <h2>금액을 보내지 않는다</h2>
 * 화면이 보여주는 합계는 <b>안내용</b>이다. 실제 금액은 주문을 만드는 순간 서버가
 * 장바구니를 다시 읽고 그 시점 시세로 계산한다 (원칙 P1). 그래서 화면 합계와 결제 금액이
 * 다를 수 있고, 다음 화면에서 확정 금액을 다시 보여준다.
 *
 * <h2>여기서 결제하지 않는다</h2>
 * 주문서를 먼저 만들고 결제는 다음 단계다. 한 화면에서 둘을 같이 하면, 결제창에서
 * 취소한 고객의 주문이 아예 남지 않아 "무엇을 사려 했는지"가 사라진다.
 */
export function CheckoutView() {
  const router = useRouter();
  const { user } = useUserSession();
  const { data: cart, isLoading } = useCart();
  const createOrder = useCreateOrder();
  const createAddress = useCreateAddress();
  const queryClient = useQueryClient();
  const refundPolicy = useRefundPolicy();
  const { data: addresses, isLoading: addressesLoading } = useAddresses();
  const paymentConfig = usePaymentConfig();

  // 직접 입력 중일 때의 값. 저장해 둔 것을 고른 동안에는 쓰이지 않는다.
  const [form, setForm] = useState<AddressForm>(EMPTY_ADDRESS);
  // null = 아직 아무것도 고르지 않았다 → 기본 배송지를 쓴다.
  const [choice, setChoice] = useState<number | 'new' | null>(null);
  const [saveForNext, setSaveForNext] = useState(true);
  /**
   * 환불규정(청약철회 제한) 동의 — "어느 판에 동의했는가" 로 든다.
   *
   * <p>불리언으로 들면 읽는 사이 규정이 개정됐을 때 "옛 판을 읽고 새 판에 동의한"
   * 상태가 남는다. 판 번호로 들면 새 판이 오는 순간 저절로 무효가 된다. 기본값은
   * 비동의이고, 전문을 끝까지 스크롤해야 켤 수 있다 ({@link RefundPolicyConsent}).
   */
  const [agreedPolicyId, setAgreedPolicyId] = useState<number | null>(null);
  const [orderMemo, setOrderMemo] = useState('');

  /**
   * 고른 결제수단. null = 아직 고르지 않았다 → 고를 수 있는 쪽으로 떨어진다.
   *
   * <p>상태를 effect 로 맞추지 않는다. 장바구니가 다시 불려올 때마다 덮어쓰면,
   * 무통장을 고른 사람이 카드로 되돌아간다 — 결제 직전 화면에서 그것은 알아차리기
   * 어려운 종류의 오류다. 고르지 않았을 때만 기본값으로 읽는다.
   */
  const [methodChoice, setMethodChoice] = useState<PaymentMethod | null>(null);
  const [depositorName, setDepositorName] = useState('');

  // 회원 정보를 기본값으로 넣어 둔다. 대부분 자기 이름·번호로 받는다.
  const [prefilled, setPrefilled] = useState(false);
  if (!prefilled && user) {
    setPrefilled(true);
    setForm((prev) => ({
      ...prev,
      receiverName: user.name ?? '',
      // formatPhone 을 쓰면 안 된다 — 번호가 없는 회원(본인인증 전 소셜 가입)에게
      // "-" 한 글자가 적힌 칸이 나온다. 입력칸용은 빈 문자열을 돌려준다.
      receiverPhone: formatPhoneInput(user.phone),
    }));
  }

  /**
   * 지금 어느 주소로 보내는가.
   *
   * <p>상태를 맞추지 않고 <b>계산한다.</b> 목록이 다시 불려올 때마다 effect 로 덮어쓰면,
   * 주소를 바꾼 줄 알았던 사람이 기본 배송지로 받게 된다 — 결제 직전 화면에서 그것은
   * 되돌릴 수 없는 종류의 오류다.
   */
  const addressList = addresses ?? [];
  const chosen =
    choice === null
      ? (addressList[0] ?? null)
      : choice === 'new'
        ? null
        : (addressList.find((a) => a.id === choice) ?? null);
  const address: AddressForm = chosen ? toAddressForm(chosen) : form;

  const orderableItems = cart?.items.filter((item) => item.orderable) ?? [];
  // 수량 합. 장바구니의 totalQuantity 를 쓰지 않는다 — 그 값은 지금 주문할 수 없는
  // 줄까지 세므로, 아래 목록에 보이지 않는 개수가 요약에 섞인다.
  const orderQuantity = orderableItems.reduce((sum, item) => sum + item.quantity, 0);

  const withdrawalConsent =
    refundPolicy.data != null && agreedPolicyId === refundPolicy.data.id;

  /**
   * 카드로 결제할 수 있는 장바구니인가. <b>서버가 판정한 값을 그대로 쓴다.</b>
   *
   * <p>순금·순은·골드바·24K·시세 연동 상품은 카드사가 온라인 거래를 제한해 PG 심사를
   * 통과하지 못한다(2026-11-03 토스 회신). 화면에서 금종을 보고 다시 따지면 두 판정이
   * 갈라지고, 갈라진 쪽이 화면이면 고객은 되지도 않는 버튼을 누르게 된다.
   *
   * <p>장바구니를 아직 못 받았으면 카드 불가로 둔다 — 모르는 것을 허용 쪽으로
   * 기울이지 않는다.
   */
  const cardPayable = cart?.cardPayable === true;
  // 계좌가 설정되지 않았으면 무통장을 내걸 수 없다. 눌러야만 실패를 아는 칸을 만들지 않는다.
  const bankAccount = paymentConfig.data?.bankAccount ?? null;
  const paymentMethod: PaymentMethod =
    methodChoice ?? (cardPayable ? 'EASY_PAY' : 'BANK_TRANSFER');
  const isBankTransfer = paymentMethod === 'BANK_TRANSFER';

  const canSubmit =
    orderableItems.length > 0 &&
    address.receiverName.trim() !== '' &&
    address.receiverPhone.trim() !== '' &&
    address.zipcode.trim() !== '' &&
    address.address1.trim() !== '' &&
    withdrawalConsent &&
    (!isBankTransfer || (bankAccount != null && depositorName.trim() !== '')) &&
    !createOrder.isPending;

  if (isLoading) {
    return <p className={styles.loading}>불러오는 중…</p>;
  }

  if (orderableItems.length === 0) {
    return (
      <div className={styles.empty}>
        <p>주문할 수 있는 상품이 없습니다.</p>
        <Link className={styles.emptyLink} href="/cart">
          장바구니로 돌아가기
        </Link>
      </div>
    );
  }

  const submit = () => {
    if (!canSubmit || !refundPolicy.data) return;
    createOrder.mutate(
      {
        refundPolicyId: refundPolicy.data.id,
        receiverName: address.receiverName.trim(),
        receiverPhone: address.receiverPhone.trim(),
        zipcode: address.zipcode.trim(),
        address1: address.address1.trim(),
        address2: address.address2.trim() || undefined,
        orderMemo: orderMemo.trim() || undefined,
        withdrawalConsent,
        paymentMethod,
        depositorName: isBankTransfer ? depositorName.trim() : undefined,
      },
      {
        onSuccess: (order) => {
          // 주소 저장은 주문이 된 뒤에 곁들인다. 기다리지 않는다 —
          // 주소록에 못 넣었다고 주문을 막으면 본말이 뒤집힌다.
          if (saveForNext && !chosen) {
            createAddress.mutate({
              receiverName: address.receiverName.trim(),
              receiverPhone: address.receiverPhone.trim(),
              zipcode: address.zipcode.trim(),
              address1: address.address1.trim(),
              address2: address.address2.trim() || undefined,
              setDefault: false,
            });
          }
          // 무통장은 결제창이 없다. 주문 상세로 보낸다 — 거기에 계좌 안내가 있고,
          // 고객은 입금하러 갔다가 계좌번호를 다시 보러 돌아온다. 전용 완료 화면을
          // 따로 만들면 같은 안내가 두 곳에 생기고, 돌아올 길은 결국 주문 상세다.
          router.push(
            isBankTransfer
              ? `/mypage/orders/${order.orderNo}?placed=1`
              : `/checkout/${order.orderNo}`,
          );
        },
        onError: (error) => {
          // 읽는 사이 규정이 개정됐다. 새 판을 받아오면 동의 구역이 스스로
          // 읽음·체크를 되돌린다 — 옛 판을 읽고 새 판에 동의할 수는 없다.
          if (error instanceof ApiError && error.code === 'REFUND_POLICY_CHANGED') {
            void queryClient.invalidateQueries({ queryKey: policyKeys.refund() });
          }
        },
      },
    );
  };

  return (
    <form
      onSubmit={(e) => {
        // 여기로 오는 경로는 없어야 한다 (버튼이 type="button" 이다).
        // 혹시 브라우저가 암묵적으로 제출하더라도 주문이 만들어지지 않게 막는다.
        e.preventDefault();
      }}
      onKeyDown={(e) => {
        // 입력칸에서 Enter 를 눌러도 주문서가 만들어지지 않게 한다.
        //
        // 브라우저는 폼 안에서 Enter 를 누르면 제출 버튼을 누른 것으로 친다. 주소를
        // 고른 뒤 상세 주소로 커서가 옮겨 가는데, 거기서 Enter 를 치면 <b>배송지를
        // 입력하자마자 결제 화면으로 넘어간다.</b> 주문은 되돌리기 어려운 동작이라
        // 눌렀다는 뜻이 분명할 때만 만든다.
        if (e.key === "Enter" && (e.target as HTMLElement).tagName === "INPUT") {
          e.preventDefault();
        }
      }}
    >
      <Steps />

      <div className={styles.layout}>
        <div className={styles.main}>
          {createOrder.error && (
            <div className={styles.noticeWrap}>
              <Notice tone="error" title="주문서를 만들지 못했습니다">
                {createOrder.error instanceof ApiError
                  ? createOrder.error.message
                  : '잠시 후 다시 시도해 주세요.'}
              </Notice>
            </div>
          )}

          <section className={styles.card}>
            <h2 className={styles.cardTitle}>배송지</h2>
            <DeliveryAddressSection
              list={addressList}
              isLoading={addressesLoading}
              chosen={chosen}
              form={form}
              onForm={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
              onChoose={(next) => {
                setChoice(next);
                // 새로 입력하러 갈 때만 '다음에도 사용' 을 켠다. 저장해 둔 것을
                // 고른 경우에는 이미 주소록에 있으므로 물을 필요가 없다.
                setSaveForNext(next === 'new');
              }}
              saveForNext={saveForNext}
              onSaveForNextChange={setSaveForNext}
            />

            <div className={styles.memo}>
              <DeliveryMemoSelect value={orderMemo} onChange={setOrderMemo} />
            </div>
          </section>

          <section className={styles.card}>
            <h2 className={styles.cardTitle}>
              주문 상품 <span className={styles.titleCount}>{orderableItems.length}건</span>
            </h2>
            <ul className={styles.items}>
              {orderableItems.map((item) => (
                <li key={item.id} className={styles.item}>
                  <div className={styles.itemName}>
                    {item.productName}
                    {item.optionName && <span className={styles.itemOption}> · {item.optionName}</span>}
                  </div>
                  <div className={styles.itemMeta}>
                    <span>
                      {item.quantity}개
                      {/* 「개당」은 2개 이상일 때만 뜻이 있다 — 1개면 줄 합계와 같은 값이다 */}
                      {item.quantity > 1 && item.unitPrice != null && ` · 개당 ${formatKrw(item.unitPrice)}`}
                    </span>
                    <strong>{formatKrw(item.lineAmount)}</strong>
                  </div>
                </li>
              ))}
            </ul>
          </section>

          <section className={styles.card}>
            <h2 className={styles.cardTitle}>결제수단</h2>

            {/* 왜 하나뿐인지를 먼저 적는다. 고를 것이 없는 화면에서 이유가 없으면
                「카드가 안 뜬다」가 고장으로 읽히고, 그대로 문의가 된다. */}
            {!cardPayable && (
              <p className={styles.methodWhy}>
                순금·순은·골드바와 시세에 연동되는 상품은 카드사의 온라인 거래 제한으로
                카드·간편결제를 이용할 수 없습니다. 무통장입금으로 주문해 주세요.
              </p>
            )}

            <div className={styles.methods} role="radiogroup" aria-label="결제수단">
              {cardPayable && (
                <label className={styles.method}>
                  <input
                    type="radio"
                    name="paymentMethod"
                    checked={!isBankTransfer}
                    onChange={() => setMethodChoice('EASY_PAY')}
                  />
                  <span className={styles.methodName}>간편결제</span>
                  <span className={styles.methodNote}>카드 · 계좌이체 · 휴대폰</span>
                </label>
              )}

              <label className={styles.method}>
                <input
                  type="radio"
                  name="paymentMethod"
                  checked={isBankTransfer}
                  disabled={bankAccount == null}
                  onChange={() => {
                    setMethodChoice('BANK_TRANSFER');
                    // 대부분 자기 이름으로 부친다. 틀리면 고치면 된다.
                    if (depositorName === '') setDepositorName(user?.name ?? '');
                  }}
                />
                <span className={styles.methodName}>무통장입금</span>
                <span className={styles.methodNote}>
                  {bankAccount == null ? '지금은 이용할 수 없습니다' : '계좌로 직접 입금'}
                </span>
              </label>
            </div>

            {isBankTransfer && bankAccount && (
              <div className={styles.deposit}>
                <dl className={styles.depositAccount}>
                  <div>
                    <dt>입금 계좌</dt>
                    <dd>
                      {bankAccount.bank} {bankAccount.number}
                    </dd>
                  </div>
                  <div>
                    <dt>예금주</dt>
                    <dd>{bankAccount.holder}</dd>
                  </div>
                </dl>

                <div className={styles.field}>
                  <label className={styles.label} htmlFor="depositor-name">
                    입금자명
                  </label>
                  <input
                    id="depositor-name"
                    className={styles.input}
                    value={depositorName}
                    onChange={(e) => setDepositorName(e.target.value)}
                    maxLength={60}
                    placeholder="통장에 찍히는 이름"
                    aria-describedby="depositor-hint"
                  />
                  {/* 이 안내가 이 칸의 존재 이유다 — 다른 이름으로 부치는 사람이
                      적지 않고, 그때 입금 확인이 사람 손으로 넘어간다. */}
                  <span id="depositor-hint" className={styles.hint}>
                    주문자와 다른 이름으로 입금하실 경우 그 이름을 적어 주세요.
                  </span>
                </div>

                {/* 기한은 안내다. 지나도 주문이 사라지지 않는다 — 입금하려고 계좌를
                    열어 둔 사이에 취소되면 보낸 돈이 갈 곳을 잃는다. 그래서
                    「취소됩니다」라고 적지 않는다. 적으면 지키지 않는 약속이 된다. */}
                <p className={styles.depositNote}>
                  주문 후 {paymentConfig.data?.depositDueHours ?? 3}시간 이내에 입금해 주세요.
                  금 시세가 계속 움직이므로, 늦어지면 주문을 다시 확인해 드려야 할 수
                  있습니다. 입금이 확인되면 상품 준비를 시작합니다.
                </p>
              </div>
            )}
          </section>

          {/* 안내만으로는 청약철회 제한이 성립하지 않는다. 전자상거래법 제17조 제2항
              단서가 "소비자가 쉽게 알 수 있는 곳에 명기" 를 요구하고, 분쟁에서는
              "이 주문에서 어느 내용에 동의했다" 를 보여야 한다. 그래서 결제 직전에
              규정 전문을 끝까지 읽게 하고, 동의한 판 번호까지 주문에 남긴다. */}
          <section className={styles.card}>
            <h2 className={styles.cardTitle}>교환·반품·환불 규정</h2>
            <p className={styles.consentBody}>
              금·은 현물은 <strong>국제 시세에 따라 값이 계속 바뀌는 상품</strong>이라
              <strong> 단순 변심에 의한 청약철회가 제한</strong>됩니다. 아래 규정을 끝까지
              읽으신 뒤 동의해 주세요.
            </p>

            <RefundPolicyConsent
              policy={refundPolicy.data}
              isLoading={refundPolicy.isLoading}
              checked={withdrawalConsent}
              onChange={(checked) =>
                setAgreedPolicyId(checked && refundPolicy.data ? refundPolicy.data.id : null)
              }
            />
          </section>
        </div>

        <aside className={styles.summary} aria-label="결제 금액">
          <h2 className={styles.summaryTitle}>결제 금액</h2>

          <ul className={styles.rows}>
            <li>
              <span>주문 상품</span>
              <strong>
                {orderableItems.length}종 · {orderQuantity}개
              </strong>
            </li>
          </ul>

          {/* 배송비 줄을 두지 않는다. 지금 이 화면에는 배송비를 알 방법이 없고
              (서버 설정값이라 장바구니 응답에 없다), 「무료」라고 적으면 그 줄이
              곧 약속이 된다. 확정 금액은 다음 화면에서 서버가 보여 준다. */}
          <div className={styles.total}>
            <span>결제 예정 금액</span>
            <strong>{formatKrw(cart?.subtotal)}</strong>
          </div>

          <p className={styles.disclaimer}>
            <strong>최종 금액은 주문서를 만드는 시각의 시세로 확정됩니다.</strong> 금이 시세 상품이라
            지금 보이는 금액과 다를 수 있고, 다음 화면에서 확정 금액을 다시 확인하신 뒤 결제하시게
            됩니다.
          </p>
        </aside>
      </div>

      {/* 화면 아래에 고정한다. 긴 주문서에서 "다음" 버튼을 찾으러 끝까지 내려가지
          않게 — 결제 직전 화면에서 그 스크롤이 이탈로 이어진다. */}
      <div className={styles.payBar}>
        <div className={styles.payBarInner}>
          <p className={styles.agree}>약관 및 주문 내용을 확인하였으며, 정보 제공 등에 동의합니다.</p>

          {/* 금액을 버튼 글자에서 꺼내 옆에 세운다. 버튼 안에 넣어 두면 금액이
              「누르는 곳」의 일부로 읽혀, 얼마를 내는지 확인하는 눈이 그 위를 그냥
              지나간다. 결제 직전에 가장 먼저 읽혀야 하는 숫자다. */}
          <div className={styles.payBarRight}>
            <p className={styles.payBarTotal}>
              <span>{isBankTransfer ? '입금 예정' : '결제 예정'}</span>
              <strong>{formatKrw(cart?.subtotal)}</strong>
            </p>
            {/* type="submit" 이 아니다 — 폼의 암묵적 제출 경로를 아예 없앤다.
                Enter 든, 레이어가 닫히며 밑으로 떨어진 클릭이든, 이 버튼을 직접 누른
                것이 아니면 주문서가 만들어지지 않는다. 주문은 되돌리기 어렵다. */}
            <button type="button" className={styles.submit} disabled={!canSubmit} onClick={submit}>
              {/* "주문서 작성" 이 아니라 "결제하기" 다. 누르는 사람이 하려는 일은 결제이고,
                  주문서는 그 과정에서 서버가 만드는 것이라 굳이 이름 붙일 필요가 없다.

                  무통장은 「결제하기」가 아니다 — 눌러도 돈이 나가지 않고 결제창도 뜨지
                  않는다. 「결제하기」라고 적으면 누르고 나서 낸 줄 알고 기다린다. */}
              {isBankTransfer
                ? createOrder.isPending
                  ? '주문하는 중…'
                  : '주문하기'
                : createOrder.isPending
                  ? '결제 화면으로 넘어가는 중…'
                  : '결제하기'}
            </button>
          </div>
        </div>
      </div>
    </form>
  );
}

/**
 * 장바구니 → 주문/결제 → 주문완료. 지금 어디쯤인지 보여 준다.
 *
 * <p>번호를 붙여 가운데에 세운다. 오른쪽 구석의 작은 「장바구니 › 주문/결제 › 완료」
 * 는 글자 크기도 위치도 안내문과 같아서, 단계 표시로 읽히지 않고 그냥 지나간다.
 * 결제 직전 화면에서 "앞으로 한 단계 더 남았다"는 것은 이탈을 줄이는 정보다.
 *
 * <p>번호는 {@code ol} 이 이미 매기고 있으므로 동그라미 안의 숫자는 장식이다 —
 * 스크린리더에 두 번 읽히지 않게 가린다.
 */
function Steps() {
  const steps = ['장바구니', '주문/결제', '주문완료'];
  return (
    <ol className={styles.steps} aria-label="주문 단계">
      {steps.map((label, index) => {
        const current = index === 1;
        return (
          <li
            key={label}
            className={current ? `${styles.step} ${styles.stepOn}` : styles.step}
            aria-current={current ? 'step' : undefined}
          >
            <span className={styles.stepNo} aria-hidden="true">
              {index + 1}
            </span>
            {/* 지나온 단계만 돌아갈 수 있다. 아직 안 만든 주문의 완료 화면은 주소가 없다 */}
            {index === 0 ? <Link href="/cart">{label}</Link> : label}
          </li>
        );
      })}
    </ol>
  );
}
