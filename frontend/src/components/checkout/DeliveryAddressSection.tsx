'use client';

import { useState } from 'react';
import { createPortal } from 'react-dom';

import { PostcodeSearch } from '@/components/ui/PostcodeSearch';
import { useDeleteAddress } from '@/hooks/useAddresses';
import { useIsBrowser } from '@/hooks/useIsBrowser';
import type { DeliveryAddress } from '@/types/order';
import { formatPhone, formatPhoneInput } from '@/utils/format';
import styles from './DeliveryAddressSection.module.css';

export interface AddressForm {
  receiverName: string;
  receiverPhone: string;
  zipcode: string;
  address1: string;
  address2: string;
}

export const EMPTY_ADDRESS: AddressForm = {
  receiverName: '',
  receiverPhone: '',
  zipcode: '',
  address1: '',
  address2: '',
};

export function toAddressForm(a: DeliveryAddress): AddressForm {
  return {
    receiverName: a.receiverName,
    receiverPhone: a.receiverPhone,
    zipcode: a.zipcode,
    address1: a.address1,
    address2: a.address2 ?? '',
  };
}

interface Props {
  list: DeliveryAddress[];
  isLoading: boolean;
  /** 고른 저장 배송지. null 이면 직접 입력 중이다. */
  chosen: DeliveryAddress | null;
  /** 직접 입력 중일 때의 값 */
  form: AddressForm;
  onForm: (patch: Partial<AddressForm>) => void;
  onChoose: (choice: number | 'new') => void;
  saveForNext: boolean;
  onSaveForNextChange: (value: boolean) => void;
}

/**
 * 주문서의 배송지 영역.
 *
 * <h2>두 모습이 있다</h2>
 * 저장해 둔 배송지가 있으면 <b>고른 주소를 한 장의 카드로</b> 보여 주고 「변경」으로 바꾼다.
 * 하나도 없으면 입력칸을 바로 펼친다 — 아무것도 없는데 "변경" 버튼만 있으면 무엇을
 * 눌러야 할지 알 수 없다.
 *
 * <h2>상태를 들고 있지 않다</h2>
 * "지금 어느 주소인가"는 {@code CheckoutView} 가 <b>계산해서</b> 내려 준다. 예전에는 여기서
 * effect 로 기본 배송지를 집어넣었는데, 목록이 다시 불려올 때마다 사용자가 고른 것을
 * 되돌릴 위험이 있었다 — 주소를 바꾼 줄 알았던 사람이 엉뚱한 곳으로 받게 된다.
 */
export function DeliveryAddressSection({
  list,
  isLoading,
  chosen,
  form,
  onForm,
  onChoose,
  saveForNext,
  onSaveForNextChange,
}: Props) {
  const remove = useDeleteAddress();
  const mounted = useIsBrowser();

  const [picking, setPicking] = useState(false);
  const [manual, setManual] = useState(false);

  if (isLoading) {
    return <p className={styles.loading}>배송지를 불러오는 중…</p>;
  }

  const choose = (choice: number | 'new') => {
    onChoose(choice);
    setPicking(false);
  };

  return (
    <>
      {chosen ? (
        <div className={styles.card}>
          <div className={styles.cardHead}>
            <strong className={styles.cardName}>
              {chosen.receiverName}
              {chosen.label && <span className={styles.label}>({chosen.label})</span>}
            </strong>
            <button type="button" className={styles.change} onClick={() => setPicking(true)}>
              변경
            </button>
          </div>
          <p className={styles.cardPhone}>{formatPhone(chosen.receiverPhone)}</p>
          <p className={styles.cardAddress}>
            {chosen.address1}
            {chosen.address2 ? ` ${chosen.address2}` : ''} ({chosen.zipcode})
          </p>
        </div>
      ) : (
        <div className={styles.form}>
          {list.length > 0 && (
            <button type="button" className={styles.backToSaved} onClick={() => setPicking(true)}>
              저장된 배송지에서 고르기
            </button>
          )}

          <div className={styles.field}>
            <label className={styles.fieldLabel} htmlFor="co-name">
              받는 분
            </label>
            <input
              id="co-name"
              className={styles.input}
              value={form.receiverName}
              onChange={(e) => onForm({ receiverName: e.target.value })}
              maxLength={100}
              autoComplete="name"
              required
            />
          </div>

          <div className={styles.field}>
            <label className={styles.fieldLabel} htmlFor="co-phone">
              연락처
            </label>
            {/*
              하이픈은 치지 않는다 — 숫자만 받아 모양은 우리가 잡는다.
              inputMode="numeric" 은 휴대폰에서 숫자 자판을 먼저 띄운다.
              maxLength 는 완성된 모양("010-1234-5678")의 길이다.
            */}
            <input
              id="co-phone"
              className={styles.input}
              value={form.receiverPhone}
              onChange={(e) => onForm({ receiverPhone: formatPhoneInput(e.target.value) })}
              placeholder="숫자만 입력"
              inputMode="numeric"
              maxLength={13}
              autoComplete="tel"
              required
            />
          </div>

          <div className={styles.field}>
            <label className={styles.fieldLabel} htmlFor="co-zip">
              우편번호
            </label>
            <div className={styles.zipRow}>
              <input
                id="co-zip"
                className={styles.inputShort}
                value={form.zipcode}
                onChange={(e) => onForm({ zipcode: e.target.value })}
                maxLength={10}
                inputMode="numeric"
                autoComplete="postal-code"
                readOnly={!manual}
                placeholder={manual ? undefined : '우편번호 찾기'}
                required
              />
              <PostcodeSearch
                onPick={({ zipcode, address }) => {
                  onForm({ zipcode, address1: address });
                  document.getElementById('co-addr2')?.focus();
                }}
                onUnavailable={() => setManual(true)}
              />
            </div>
          </div>

          <div className={styles.field}>
            <label className={styles.fieldLabel} htmlFor="co-addr1">
              주소
            </label>
            <input
              id="co-addr1"
              className={styles.input}
              value={form.address1}
              onChange={(e) => onForm({ address1: e.target.value })}
              maxLength={200}
              autoComplete="address-line1"
              readOnly={!manual}
              placeholder={manual ? undefined : '우편번호를 찾으면 채워집니다'}
              required
            />
          </div>

          <div className={styles.field}>
            <label className={styles.fieldLabel} htmlFor="co-addr2">
              상세 주소 <span className={styles.optional}>(선택)</span>
            </label>
            <input
              id="co-addr2"
              className={styles.input}
              value={form.address2}
              onChange={(e) => onForm({ address2: e.target.value })}
              maxLength={200}
              autoComplete="address-line2"
            />
          </div>

          <label className={styles.saveRow}>
            <input
              type="checkbox"
              checked={saveForNext}
              onChange={(e) => onSaveForNextChange(e.target.checked)}
            />
            다음에도 사용할게요
          </label>
        </div>
      )}

      {mounted && picking &&
        createPortal(
          <div
            className={styles.dim}
            role="dialog"
            aria-modal="true"
            aria-label="배송지 선택"
            onClick={(e) => {
              if (e.target === e.currentTarget) setPicking(false);
            }}
          >
            <div className={styles.panel}>
              <div className={styles.panelHead}>
                <strong className={styles.panelTitle}>배송지 선택</strong>
                <button
                  type="button"
                  className={styles.close}
                  aria-label="닫기"
                  onClick={() => setPicking(false)}
                >
                  ×
                </button>
              </div>

              <ul className={styles.pickList}>
                {list.map((address) => (
                  <li key={address.id} className={styles.pickItem}>
                    <button type="button" className={styles.pickButton} onClick={() => choose(address.id)}>
                      <span className={styles.pickName}>
                        {address.receiverName}
                        {address.label && <span className={styles.label}>({address.label})</span>}
                        {address.isDefault && <span className={styles.badge}>기본</span>}
                      </span>
                      <span className={styles.pickAddress}>
                        {address.address1}
                        {address.address2 ? ` ${address.address2}` : ''} ({address.zipcode})
                      </span>
                      <span className={styles.pickPhone}>{formatPhone(address.receiverPhone)}</span>
                    </button>
                    <button
                      type="button"
                      className={styles.remove}
                      disabled={remove.isPending}
                      onClick={() => {
                        remove.mutate(address.id);
                        // 고르고 있던 것을 지웠으면 직접 입력으로 넘긴다 —
                        // 지운 주소로 주문이 나가지 않게.
                        if (chosen?.id === address.id) {
                          choose('new');
                        }
                      }}
                    >
                      삭제
                    </button>
                  </li>
                ))}
              </ul>

              <button type="button" className={styles.addNew} onClick={() => choose('new')}>
                + 새 배송지 입력
              </button>
            </div>
          </div>,
          document.body,
        )}
    </>
  );
}
