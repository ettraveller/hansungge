'use client';

import { useMemo } from 'react';

import { SelectInput } from './Field';
import { useAdminCategories } from '@/hooks/admin/useCategories';
import { useAdminProducts } from '@/hooks/admin/useProducts';
import { buildNav } from '@/layouts/navigation';
import type { ProductStatus } from '@/types/product';
import styles from './admin.module.css';

/**
 * 셀렉트에 담을 상품 수.
 *
 * <p>상품은 수백 개가 되기 어려운 품목이라 한 번에 받아 전부 담는다. 그래도 넘치면
 * 아래에 몇 개가 빠졌는지 적어 둔다 — 조용히 자르면 "내 상품이 목록에 없다" 가 된다.
 */
const PRODUCT_LIMIT = 300;

/** 팔지 않는 상품은 이름 뒤에 이유를 붙인다. 모르고 고르면 손님이 빈 화면을 본다. */
const STATUS_SUFFIX: Partial<Record<ProductStatus, string>> = {
  SOLD_OUT: ' (품절)',
  HIDDEN: ' (숨김)',
  DISCONTINUED: ' (판매종료)',
};

interface Props {
  id?: string;
  /** 지금 들어 있는 주소. 빈 문자열이면 「없음」이다. */
  value: string;
  disabled?: boolean;
  onChange: (href: string) => void;
}

/**
 * 「누르면 갈 곳」을 메뉴나 상품에서 고르는 셀렉트.
 *
 * <h2>왜 직접 입력이 아닌가</h2>
 * 주소를 손으로 적으면 <b>오타가 404 가 되고, 그 사실이 저장할 때 드러나지 않는다.</b>
 * 카테고리 번호(`/shop?categoryId=7`)는 더 나쁘다 — 번호를 알 길이 화면에 없어서
 * 관리자 화면을 두 개 열고 옮겨 적어야 한다. 갈 수 있는 곳은 메뉴와 상품으로 정해져
 * 있으니 그 목록에서 고르게 한다.
 *
 * <h2>목록에 없는 주소도 잃지 않는다</h2>
 * 예전에 손으로 적어 둔 주소가 있으면 맨 위에 그 값 그대로 한 줄을 만들어 띄운다.
 * 목록에 없다고 빈 값으로 떨어뜨리면, 저장을 누르는 순간 멀쩡히 걸려 있던 배너
 * 링크가 조용히 사라진다.
 *
 * <h2>숨긴 카테고리는 넣지 않는다</h2>
 * 관리자 목록에는 숨긴 카테고리까지 들어 있지만 손님 메뉴에는 없다. 그런 곳으로
 * 보내면 상품이 0개인 화면이 나온다.
 */
export function LinkTargetPicker({ id, value, disabled, onChange }: Props) {
  const { data: categories } = useAdminCategories();
  const { data: products } = useAdminProducts({ size: PRODUCT_LIMIT });

  /** 메뉴에 실제로 보이는 것만. buildNav 가 고정 메뉴와 카테고리를 같은 모양으로 준다. */
  const menuOptions = useMemo(() => {
    const visible = (categories ?? []).filter((c) => c.active);
    const out: { href: string; label: string }[] = [];
    for (const item of buildNav(visible)) {
      out.push({ href: item.href, label: item.label });
      for (const child of item.children) {
        out.push({ href: child.href, label: `${item.label} > ${child.label}` });
      }
    }
    return out;
  }, [categories]);

  const productOptions = useMemo(
    () =>
      (products?.content ?? []).map((p) => ({
        href: `/shop/product/${p.id}`,
        label: `${p.name}${STATUS_SUFFIX[p.status] ?? ''}`,
      })),
    [products],
  );

  const known = useMemo(
    () => new Set([...menuOptions, ...productOptions].map((o) => o.href)),
    [menuOptions, productOptions],
  );

  const missing = (products?.totalElements ?? 0) - productOptions.length;

  return (
    <>
      <SelectInput
        id={id}
        value={value}
        disabled={disabled}
        onChange={(e) => onChange(e.target.value)}
      >
        <option value="">없음 (누를 수 없는 배너)</option>

        {/* 목록에 없는 값은 선택된 채로 남긴다 — 없으면 브라우저가 첫 줄로 튕긴다 */}
        {value !== '' && !known.has(value) && (
          <option value={value}>직접 적어 둔 주소: {value}</option>
        )}

        <optgroup label="메뉴">
          {menuOptions.map((o) => (
            <option key={o.href} value={o.href}>
              {o.label}
            </option>
          ))}
        </optgroup>

        <optgroup label="상품">
          {productOptions.map((o) => (
            <option key={o.href} value={o.href}>
              {o.label}
            </option>
          ))}
        </optgroup>
      </SelectInput>

      {missing > 0 && (
        <p className={styles.hintWarn}>
          상품이 많아 최근 {PRODUCT_LIMIT}개만 담았다. {missing}개가 목록에 없다.
        </p>
      )}
    </>
  );
}
