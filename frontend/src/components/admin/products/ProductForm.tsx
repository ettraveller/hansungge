'use client';

import { useMemo, useState } from 'react';

import { Alert } from '../Alert';
import { Button } from '../Button';
import { DecimalInput, Field, MoneyInput, SelectInput, TextArea, TextInput } from '../Field';
import { flattenCategories, useAdminCategories } from '@/hooks/admin/useCategories';
import type { ProductInput } from '@/api/admin/products';
import type { AdminProduct } from '@/types/admin';
import { METAL_LABEL, METAL_ORDER, type MetalType, type ProductPriceType } from '@/types/price';
import { ApiError } from '@/types/api';
import styles from '../admin.module.css';

const PRICE_TYPE_LABEL: Record<ProductPriceType, string> = {
  MARKET_LINKED: '시세 연동 (시세 × 중량 + 공임)',
  MARKET_PLUS: '시세 연동 + 옵션공임',
  MARKET_MULTIPLIER: '시세 연동 × 배수',
  FIXED: '고정가',
  MANUAL: '관리자 지정가',
};

const PRICE_TYPE_HINT: Record<ProductPriceType, string> = {
  MARKET_LINKED: '시세 × 중량 + 공임. 시세가 바뀌면 판매가도 함께 바뀐다.',
  MARKET_PLUS: '시세 × 중량 + 공임 + 옵션별 추가공임. 옵션마다 공임이 다른 상품에 쓴다.',
  MARKET_MULTIPLIER: '(시세 × 중량) × 배수 + 공임.',
  FIXED: '시세를 보지 않는다. 시세가 없어도 판매된다.',
  MANUAL: '시세를 보지 않는다. 관리자가 정한 값 그대로 판다.',
};

const MARKET_LINKED: ProductPriceType[] = ['MARKET_LINKED', 'MARKET_PLUS', 'MARKET_MULTIPLIER'];

const DECIMAL = /^\d{1,13}(\.\d{1,4})?$/;

/** 서버가 필드별 오류를 주면 그대로 쓴다. 프론트에서 규칙을 다시 쓰면 두 규칙이 갈라진다. */
function fieldError(error: unknown, field: string): string | null {
  if (error instanceof ApiError) return error.fieldErrors?.[field] ?? null;
  return null;
}

export type ProductFormValues = ProductInput;

/**
 * 상품 등록·수정 폼.
 *
 * <b>판매가 입력란이 없다.</b> 시세 연동 상품의 가격은 시세 × 중량 + 공임으로 서버가
 * 계산한다 (설계서 원칙 P1). 고정가(FIXED/MANUAL)일 때만 판매가를 직접 넣는다.
 *
 * <p>가격 방식에 따라 필수 필드가 달라지므로, 관계없는 입력란은 숨긴다 — 남겨 두면
 * 채워 넣고 "반영이 안 된다"고 오해한다.
 */
export function ProductForm({
  initial,
  mode,
  pending,
  error,
  onSubmit,
  onCancel,
}: {
  initial?: AdminProduct | null;
  mode: 'create' | 'edit';
  pending?: boolean;
  error?: unknown;
  onSubmit: (values: ProductFormValues) => void;
  onCancel?: () => void;
}) {
  const { data: categories } = useAdminCategories();
  const categoryOptions = useMemo(() => flattenCategories(categories ?? []), [categories]);

  const [categoryId, setCategoryId] = useState(
    initial?.categoryId == null ? '' : String(initial.categoryId),
  );
  const [name, setName] = useState(initial?.name ?? '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [thumbnailKey, setThumbnailKey] = useState(initial?.thumbnailKey ?? '');
  const [priceType, setPriceType] = useState<ProductPriceType>(
    initial?.priceType ?? 'MARKET_PLUS',
  );
  const [metalType, setMetalType] = useState<MetalType | ''>(initial?.metalType ?? 'GOLD_24K');
  const [weightG, setWeightG] = useState(initial?.weightG == null ? '' : String(initial.weightG));
  const [purity, setPurity] = useState(initial?.purity == null ? '' : String(initial.purity));
  const [laborCost, setLaborCost] = useState(String(initial?.laborCost ?? '0'));
  const [multiplier, setMultiplier] = useState(String(initial?.multiplier ?? '1'));
  const [fixedPrice, setFixedPrice] = useState(
    initial?.fixedPrice == null ? '' : String(initial.fixedPrice),
  );
  const [defaultOptionName, setDefaultOptionName] = useState('');
  const [initialStock, setInitialStock] = useState('0');

  const marketLinked = MARKET_LINKED.includes(priceType);

  const weightInvalid = marketLinked && (weightG === '' || !DECIMAL.test(weightG) || Number(weightG) <= 0);
  const fixedInvalid =
    !marketLinked && (fixedPrice === '' || !DECIMAL.test(fixedPrice) || Number(fixedPrice) <= 0);
  const laborInvalid = laborCost !== '' && !DECIMAL.test(laborCost);
  const multiplierInvalid =
    priceType === 'MARKET_MULTIPLIER' && (!DECIMAL.test(multiplier) || Number(multiplier) <= 0);
  const stockInvalid = mode === 'create' && !/^\d{1,7}$/.test(initialStock);

  const canSubmit =
    categoryId !== '' &&
    name.trim().length > 0 &&
    !weightInvalid &&
    !fixedInvalid &&
    !laborInvalid &&
    !multiplierInvalid &&
    !stockInvalid &&
    !pending;

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    onSubmit({
      categoryId: Number(categoryId),
      name: name.trim(),
      description: description.trim() === '' ? null : description,
      thumbnailKey: thumbnailKey.trim() === '' ? null : thumbnailKey.trim(),
      // 시세를 보지 않는 방식이면 금속·중량을 보내지 않는다. 남겨 보내면 화면과 실제
      // 계산 근거가 어긋난 채로 저장된다.
      metalType: marketLinked ? (metalType === '' ? null : metalType) : null,
      priceType,
      weightG: marketLinked ? weightG : null,
      purity: purity.trim() === '' ? null : purity.trim(),
      laborCost: laborCost === '' ? '0' : laborCost,
      multiplier: priceType === 'MARKET_MULTIPLIER' ? multiplier : '1',
      fixedPrice: marketLinked ? null : fixedPrice,
      ...(mode === 'create'
        ? {
            defaultOptionName: defaultOptionName.trim() === '' ? null : defaultOptionName.trim(),
            initialStock: Number(initialStock),
          }
        : {}),
    });
  };

  return (
    <form className={styles.form} onSubmit={submit}>
      {error != null && (
        <Alert tone="danger" title="저장하지 못했습니다">
          {error instanceof ApiError ? error.message : '알 수 없는 오류가 발생했습니다.'}
        </Alert>
      )}

      <div className={styles.formGrid}>
        <Field
          label="카테고리"
          htmlFor="p-category"
          required
          error={fieldError(error, 'categoryId')}
        >
          <SelectInput
            id="p-category"
            value={categoryId}
            onChange={(e) => setCategoryId(e.target.value)}
          >
            <option value="">— 선택 —</option>
            {categoryOptions.map(({ category, depth }) => (
              <option key={category.id} value={category.id}>
                {depth > 0 ? `${'　'.repeat(depth)}└ ` : ''}
                {category.name}
                {category.active ? '' : ' (숨김)'}
              </option>
            ))}
          </SelectInput>
        </Field>

        <Field label="상품명" htmlFor="p-name" required error={fieldError(error, 'name')}>
          <TextInput
            id="p-name"
            value={name}
            maxLength={200}
            onChange={(e) => setName(e.target.value)}
          />
        </Field>
      </div>

      <Field
        label="가격 방식"
        htmlFor="p-price-type"
        required
        hint={PRICE_TYPE_HINT[priceType]}
      >
        <SelectInput
          id="p-price-type"
          value={priceType}
          onChange={(e) => setPriceType(e.target.value as ProductPriceType)}
        >
          {(Object.keys(PRICE_TYPE_LABEL) as ProductPriceType[]).map((t) => (
            <option key={t} value={t}>
              {PRICE_TYPE_LABEL[t]}
            </option>
          ))}
        </SelectInput>
      </Field>

      {marketLinked ? (
        <div className={styles.formGrid}>
          <Field
            label="연동 금속"
            htmlFor="p-metal"
            required
            hint="이 금속의 현재 시세로 계산된다."
            error={fieldError(error, 'metalType')}
          >
            <SelectInput
              id="p-metal"
              value={metalType}
              onChange={(e) => setMetalType(e.target.value as MetalType)}
            >
              {METAL_ORDER.map((m) => (
                <option key={m} value={m}>
                  {METAL_LABEL[m]}
                </option>
              ))}
            </SelectInput>
          </Field>

          <Field
            label="중량 (g)"
            htmlFor="p-weight"
            required
            hint="1돈 = 3.75g"
            error={weightInvalid ? '0보다 큰 숫자를 입력해 주세요.' : fieldError(error, 'weightG')}
          >
            <DecimalInput
              id="p-weight"
              value={weightG}
              invalid={weightInvalid}
              placeholder="3.75"
              onChange={(e) => setWeightG(e.target.value)}
            />
          </Field>

          <Field
            label="공임 (원)"
            htmlFor="p-labor"
            hint="가공비. 0 이면 시세 × 중량 그대로."
            error={laborInvalid ? '숫자를 입력해 주세요.' : fieldError(error, 'laborCost')}
          >
            <MoneyInput
              id="p-labor"
              value={laborCost}
              invalid={laborInvalid}
              onValueChange={setLaborCost}
            />
          </Field>

          {priceType === 'MARKET_MULTIPLIER' && (
            <Field
              label="배수"
              htmlFor="p-multiplier"
              required
              hint="(시세 × 중량) 에 곱한다. 1.15 면 15% 가산."
              error={multiplierInvalid ? '0보다 큰 숫자를 입력해 주세요.' : null}
            >
              <DecimalInput
                id="p-multiplier"
                value={multiplier}
                invalid={multiplierInvalid}
                onChange={(e) => setMultiplier(e.target.value)}
              />
            </Field>
          )}
        </div>
      ) : (
        <div className={styles.formGrid}>
          <Field
            label="판매가 (원)"
            htmlFor="p-fixed"
            required
            hint="시세와 무관하게 이 금액으로 판다."
            error={
              fixedInvalid ? '0보다 큰 숫자를 입력해 주세요.' : fieldError(error, 'fixedPrice')
            }
          >
            <MoneyInput
              id="p-fixed"
              value={fixedPrice}
              invalid={fixedInvalid}
              onValueChange={setFixedPrice}
            />
          </Field>
        </div>
      )}

      <div className={styles.formGrid}>
        <Field
          label="순도"
          htmlFor="p-purity"
          hint="⚠️ 상세페이지 표기 전용이다. 가격 계산에 쓰이지 않는다 — 금속 종류가 이미 순도를 담고 있어 곱하면 이중 적용된다."
        >
          <DecimalInput
            id="p-purity"
            value={purity}
            placeholder="0.9999"
            onChange={(e) => setPurity(e.target.value)}
          />
        </Field>

        <Field
          label="대표 이미지 키"
          htmlFor="p-thumb"
          hint="URL 이 아니라 Object Storage 키다. 업로드 기능은 아직 없다 (STEP 10)."
        >
          <TextInput
            id="p-thumb"
            value={thumbnailKey}
            maxLength={300}
            placeholder="products/2026/goldbar-375.jpg"
            onChange={(e) => setThumbnailKey(e.target.value)}
          />
        </Field>
      </div>

      <Field label="상품 설명" htmlFor="p-desc" hint="HTML 을 넣을 수 있다.">
        <TextArea
          id="p-desc"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
      </Field>

      {mode === 'create' && (
        <>
          <Alert tone="info" title="등록 후 바로 노출되지 않습니다">
            상품은 <strong>숨김(HIDDEN)</strong> 상태로 만들어진다. 재고를 확인한 뒤 상세
            화면에서 판매중으로 바꿔야 쇼핑몰에 나온다. 재고를 담을 기본 옵션 1개도 함께
            생성된다.
          </Alert>
          <div className={styles.formGrid}>
            <Field
              label="기본 옵션 이름"
              htmlFor="p-opt-name"
              hint="비우면 중량으로 자동 생성된다. 예: 3.75g (1돈)"
            >
              <TextInput
                id="p-opt-name"
                value={defaultOptionName}
                maxLength={100}
                onChange={(e) => setDefaultOptionName(e.target.value)}
              />
            </Field>
            <Field
              label="초기 재고"
              htmlFor="p-stock"
              error={stockInvalid ? '0 이상의 정수를 입력해 주세요.' : null}
            >
              <TextInput
                id="p-stock"
                value={initialStock}
                inputMode="numeric"
                invalid={stockInvalid}
                onChange={(e) => setInitialStock(e.target.value)}
              />
            </Field>
          </div>
        </>
      )}

      <div className={styles.formActions}>
        <Button type="submit" variant="gold" disabled={!canSubmit}>
          {pending ? '저장 중…' : mode === 'create' ? '등록' : '저장'}
        </Button>
        {onCancel && (
          <Button onClick={onCancel} disabled={pending}>
            취소
          </Button>
        )}
      </div>
    </form>
  );
}
