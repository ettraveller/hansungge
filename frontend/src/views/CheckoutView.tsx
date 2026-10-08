'use client';

import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { useState } from 'react';

import { Notice } from '@/components/auth/Notice';
import {
  DeliveryAddressSection,
  EMPTY_ADDRESS,
  toAddressForm,
  type AddressForm,
} from '@/components/checkout/DeliveryAddressSection';
import { DeliveryMemoSelect } from '@/components/checkout/DeliveryMemoSelect';
import { useCart } from '@/hooks/useCart';
import { useAddresses, useCreateAddress } from '@/hooks/useAddresses';
import { useCreateOrder } from '@/hooks/useOrder';
import { useUserSession } from '@/hooks/useUserSession';
import { ApiError } from '@/types/api';
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
  const { data: addresses, isLoading: addressesLoading } = useAddresses();

  // 직접 입력 중일 때의 값. 저장해 둔 것을 고른 동안에는 쓰이지 않는다.
  const [form, setForm] = useState<AddressForm>(EMPTY_ADDRESS);
  // null = 아직 아무것도 고르지 않았다 → 기본 배송지를 쓴다.
  const [choice, setChoice] = useState<number | 'new' | null>(null);
  const [saveForNext, setSaveForNext] = useState(true);
  const [orderMemo, setOrderMemo] = useState('');

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

  const canSubmit =
    orderableItems.length > 0 &&
    address.receiverName.trim() !== '' &&
    address.receiverPhone.trim() !== '' &&
    address.zipcode.trim() !== '' &&
    address.address1.trim() !== '' &&
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
    if (!canSubmit) return;
    createOrder.mutate(
      {
        receiverName: address.receiverName.trim(),
        receiverPhone: address.receiverPhone.trim(),
        zipcode: address.zipcode.trim(),
        address1: address.address1.trim(),
        address2: address.address2.trim() || undefined,
        orderMemo: orderMemo.trim() || undefined,
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
          router.push(`/checkout/${order.orderNo}`);
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
            <h2 className={styles.cardTitle}>주문 상품</h2>
            <ul className={styles.items}>
              {orderableItems.map((item) => (
                <li key={item.id} className={styles.item}>
                  <div className={styles.itemName}>
                    {item.productName}
                    {item.optionName && <span className={styles.itemOption}> · {item.optionName}</span>}
                  </div>
                  <div className={styles.itemMeta}>
                    <span>{item.quantity}개</span>
                    <strong>{formatKrw(item.lineAmount)}</strong>
                  </div>
                </li>
              ))}
            </ul>
          </section>
        </div>

        <aside className={styles.summary} aria-label="결제 금액">
          <h2 className={styles.summaryTitle}>결제 금액</h2>

          <div className={styles.total}>
            <span>예상 금액</span>
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
          {/* type="submit" 이 아니다 — 폼의 암묵적 제출 경로를 아예 없앤다.
              Enter 든, 레이어가 닫히며 밑으로 떨어진 클릭이든, 이 버튼을 직접 누른
              것이 아니면 주문서가 만들어지지 않는다. 주문은 되돌리기 어렵다. */}
          <button type="button" className={styles.submit} disabled={!canSubmit} onClick={submit}>
            {/* "주문서 작성" 이 아니라 "결제하기" 다. 누르는 사람이 하려는 일은 결제이고,
                주문서는 그 과정에서 서버가 만드는 것이라 굳이 이름 붙일 필요가 없다. */}
            {createOrder.isPending ? '결제 화면으로 넘어가는 중…' : `${formatKrw(cart?.subtotal)} 결제하기`}
          </button>
        </div>
      </div>
    </form>
  );
}

/** 장바구니 → 주문/결제 → 완료. 지금 어디쯤인지 보여 준다. */
function Steps() {
  return (
    <ol className={styles.steps} aria-label="주문 단계">
      <li>
        <Link href="/cart">장바구니</Link>
      </li>
      <li aria-current="step" className={styles.stepOn}>
        주문/결제
      </li>
      <li>완료</li>
    </ol>
  );
}
