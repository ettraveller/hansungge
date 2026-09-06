'use client';

import { useState } from 'react';

import { Alert } from '../Alert';
import { Button } from '../Button';
import { DecimalInput, Field, MoneyInput, TextInput } from '../Field';
import { Modal } from '../Modal';
import { Panel } from '../Panel';
import { ReasonDialog } from '../ReasonDialog';
import { StatusBadge } from '@/components/ui/StatusBadge';
import {
  useAddOption,
  useAdjustStock,
  useProductOptions,
  useUpdateOption,
} from '@/hooks/admin/useProducts';
import { formatGram, formatKrw } from '@/utils/format';
import type { AdminProductOption } from '@/types/admin';
import type { ProductPriceType } from '@/types/price';
import { ApiError } from '@/types/api';
import styles from '../admin.module.css';

const DECIMAL = /^\d{1,13}(\.\d{1,4})?$/;

/**
 * 옵션 · 재고.
 *
 * 재고는 옵션에만 있다 — 상품 자체에는 없다. 그래서 상품을 만들면 기본 옵션 1개가
 * 자동으로 생긴다.
 *
 * <p>재고 조정은 <b>증감이 아니라 절대 수량</b>이다. 요청이 중복 도착해도 두 번 깎이지
 * 않는다. 화면 문구도 "실사 결과 수량"으로 적어 그 의미를 흐리지 않는다.
 */
export function ProductOptionsPanel({
  productId,
  priceType,
  canEdit,
}: {
  productId: number;
  priceType: ProductPriceType;
  canEdit: boolean;
}) {
  const { data, isLoading, error } = useProductOptions(productId);
  const [editing, setEditing] = useState<AdminProductOption | 'new' | null>(null);
  const [stockTarget, setStockTarget] = useState<AdminProductOption | null>(null);

  const rows = data ?? [];
  const usesAdditionalLabor = priceType === 'MARKET_PLUS';

  return (
    <Panel
      title="옵션 · 재고"
      note={
        usesAdditionalLabor
          ? '이 상품은 옵션 추가공임이 가격에 반영된다.'
          : '추가공임은 MARKET_PLUS 방식에서만 가격에 반영된다 — 이 상품에서는 무시된다.'
      }
      actions={
        canEdit ? (
          <Button small variant="gold" onClick={() => setEditing('new')}>
            옵션 추가
          </Button>
        ) : undefined
      }
      flush
    >
      {error && (
        <div className={styles.panelBody}>
          <Alert tone="danger">
            {error instanceof ApiError ? error.message : '옵션을 불러오지 못했습니다.'}
          </Alert>
        </div>
      )}

      {isLoading ? (
        <p className={styles.loading}>불러오는 중…</p>
      ) : rows.length === 0 ? (
        <p className={styles.empty}>옵션이 없습니다.</p>
      ) : (
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th className={styles.num}>ID</th>
                <th>옵션명</th>
                <th className={styles.num}>중량</th>
                <th className={styles.num}>추가공임</th>
                <th className={styles.num}>재고</th>
                <th>판매</th>
                <th className={styles.num}>정렬</th>
                {canEdit && <th aria-label="작업" />}
              </tr>
            </thead>
            <tbody>
              {rows.map((option) => (
                <tr key={option.id}>
                  <td data-label="ID" className={`${styles.num} ${styles.muted}`}>{option.id}</td>
                  <td data-label="옵션명" data-card-title className={styles.strong}>{option.name}</td>
                  <td data-label="중량" className={styles.num}>
                    {option.weightG == null ? (
                      <span className={styles.muted}>상품 중량</span>
                    ) : (
                      formatGram(option.weightG)
                    )}
                  </td>
                  <td data-label="추가공임" className={styles.num}>
                    {usesAdditionalLabor ? (
                      formatKrw(option.additionalLaborCost)
                    ) : (
                      <span className={styles.muted}>{formatKrw(option.additionalLaborCost)}</span>
                    )}
                  </td>
                  <td data-label="재고" className={`${styles.num} ${styles.strong}`}>{option.stock ?? '-'}</td>
                  <td data-label="판매">
                    <StatusBadge
                      tone={!option.active ? 'neutral' : option.inStock ? 'success' : 'warning'}
                    >
                      {!option.active ? '비활성' : option.inStock ? '판매 가능' : '품절'}
                    </StatusBadge>
                  </td>
                  <td data-label="정렬" className={styles.num}>{option.sort}</td>
                  {canEdit && (
                    <td data-label="작업" data-card-actions>
                      <div className={styles.rowActions}>
                        <Button small onClick={() => setStockTarget(option)}>
                          재고
                        </Button>
                        <Button small onClick={() => setEditing(option)}>
                          수정
                        </Button>
                      </div>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {editing && (
        <OptionDialog
          key={editing === 'new' ? 'new' : editing.id}
          productId={productId}
          option={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
        />
      )}

      {stockTarget && (
        <StockDialog
          key={stockTarget.id}
          productId={productId}
          option={stockTarget}
          onClose={() => setStockTarget(null)}
        />
      )}
    </Panel>
  );
}

function OptionDialog({
  productId,
  option,
  onClose,
}: {
  productId: number;
  option: AdminProductOption | null;
  onClose: () => void;
}) {
  const add = useAddOption();
  const update = useUpdateOption();
  const mutation = option ? update : add;

  const [name, setName] = useState(option?.name ?? '');
  const [weightG, setWeightG] = useState(option?.weightG == null ? '' : String(option.weightG));
  const [additionalLaborCost, setAdditionalLaborCost] = useState(
    String(option?.additionalLaborCost ?? '0'),
  );
  const [stock, setStock] = useState('0');
  const [sort, setSort] = useState(String(option?.sort ?? 0));
  const [active, setActive] = useState(option?.active ?? true);

  const weightInvalid = weightG !== '' && (!DECIMAL.test(weightG) || Number(weightG) <= 0);
  const laborInvalid = additionalLaborCost !== '' && !DECIMAL.test(additionalLaborCost);
  const sortInvalid = !/^-?\d{1,6}$/.test(sort);
  const stockInvalid = option == null && !/^\d{1,7}$/.test(stock);

  const canSubmit =
    name.trim().length > 0 &&
    !weightInvalid &&
    !laborInvalid &&
    !sortInvalid &&
    !stockInvalid &&
    !mutation.isPending;

  const submit = () => {
    if (!canSubmit) return;
    const input = {
      name: name.trim(),
      weightG: weightG === '' ? null : weightG,
      additionalLaborCost: additionalLaborCost === '' ? '0' : additionalLaborCost,
      sort: Number(sort),
      active,
    };
    if (option) {
      update.mutate({ productId, optionId: option.id, input }, { onSuccess: onClose });
    } else {
      add.mutate({ productId, input: { ...input, stock: Number(stock) } }, { onSuccess: onClose });
    }
  };

  return (
    <Modal
      open
      title={option ? `옵션 수정 · ${option.name}` : '옵션 추가'}
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose} disabled={mutation.isPending}>
            취소
          </Button>
          <Button variant="primary" disabled={!canSubmit} onClick={submit}>
            {mutation.isPending ? '저장 중…' : '저장'}
          </Button>
        </>
      }
    >
      <div className={styles.form}>
        {mutation.error && (
          <Alert tone="danger">
            {mutation.error instanceof ApiError
              ? mutation.error.message
              : '옵션을 저장하지 못했습니다.'}
          </Alert>
        )}

        {option && (
          <Alert tone="info">
            재고 수량은 이 창에서 바꾸지 않는다. 사유가 남는{' '}
            <strong>재고 조정</strong> 으로만 바꿀 수 있다.
          </Alert>
        )}

        <Field label="옵션명" htmlFor="opt-name" required>
          <TextInput
            id="opt-name"
            value={name}
            maxLength={100}
            autoFocus
            onChange={(e) => setName(e.target.value)}
          />
        </Field>

        <div className={styles.formGrid}>
          <Field
            label="중량 (g)"
            htmlFor="opt-weight"
            hint="비우면 상품 중량을 쓴다."
            error={weightInvalid ? '0보다 큰 숫자를 입력해 주세요.' : null}
          >
            <DecimalInput
              id="opt-weight"
              value={weightG}
              invalid={weightInvalid}
              onChange={(e) => setWeightG(e.target.value)}
            />
          </Field>

          <Field
            label="추가공임 (원)"
            htmlFor="opt-labor"
            hint="MARKET_PLUS 방식에서만 가격에 더해진다."
            error={laborInvalid ? '숫자를 입력해 주세요.' : null}
          >
            <MoneyInput
              id="opt-labor"
              value={additionalLaborCost}
              invalid={laborInvalid}
              onValueChange={setAdditionalLaborCost}
            />
          </Field>

          {option == null && (
            <Field
              label="초기 재고"
              htmlFor="opt-stock"
              error={stockInvalid ? '0 이상의 정수를 입력해 주세요.' : null}
            >
              <TextInput
                id="opt-stock"
                value={stock}
                inputMode="numeric"
                invalid={stockInvalid}
                onChange={(e) => setStock(e.target.value)}
              />
            </Field>
          )}

          <Field
            label="정렬 순서"
            htmlFor="opt-sort"
            required
            error={sortInvalid ? '정수를 입력해 주세요.' : null}
          >
            <TextInput
              id="opt-sort"
              value={sort}
              inputMode="numeric"
              invalid={sortInvalid}
              onChange={(e) => setSort(e.target.value)}
            />
          </Field>
        </div>

        <Field label="활성" hint="마지막 활성 옵션은 끌 수 없다 — 재고가 존재할 곳이 사라진다.">
          <label className={styles.checkRow}>
            <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
            판매에 사용
          </label>
        </Field>
      </div>
    </Modal>
  );
}

function StockDialog({
  productId,
  option,
  onClose,
}: {
  productId: number;
  option: AdminProductOption;
  onClose: () => void;
}) {
  const adjust = useAdjustStock();
  const [stock, setStock] = useState(String(option.stock ?? 0));

  const invalid = !/^\d{1,7}$/.test(stock);

  return (
    <ReasonDialog
      open
      title={`재고 조정 · ${option.name}`}
      confirmLabel="조정"
      placeholder="예: 2026-08-27 실사 반영"
      pending={adjust.isPending}
      error={adjust.error instanceof ApiError ? adjust.error.message : null}
      description={
        <>
          현재 재고 <strong>{option.stock ?? 0}</strong> → 조정 후{' '}
          <strong>{invalid ? '?' : stock}</strong>
          <br />
          증감이 아니라 <strong>실사 결과 수량</strong>을 그대로 넣는다. 같은 요청이 두 번
          들어가도 재고가 두 번 깎이지 않는다.
          <div style={{ marginTop: 10, maxWidth: 180 }}>
            <TextInput
              value={stock}
              inputMode="numeric"
              invalid={invalid}
              aria-label="조정 후 재고 수량"
              onChange={(e) => setStock(e.target.value)}
            />
          </div>
          {invalid && <p className={styles.error}>0 이상의 정수를 입력해 주세요.</p>}
        </>
      }
      onConfirm={(reason) => {
        if (invalid) return;
        adjust.mutate(
          { productId, optionId: option.id, stock: Number(stock), reason },
          { onSuccess: onClose },
        );
      }}
      onClose={onClose}
    />
  );
}
