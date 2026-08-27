'use client';

import Link from 'next/link';
import { useMemo, useState } from 'react';

import { Alert } from '@/components/admin/Alert';
import { Button } from '@/components/admin/Button';
import { Pagination } from '@/components/admin/Pagination';
import { Panel } from '@/components/admin/Panel';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useAdminSession } from '@/hooks/admin/useAdminSession';
import { categoryPathMap, useAdminCategories, flattenCategories } from '@/hooks/admin/useCategories';
import { useAdminProducts } from '@/hooks/admin/useProducts';
import { formatDateTime, formatGram, formatKrw } from '@/utils/format';
import { METAL_LABEL, METAL_ORDER, type MetalType } from '@/types/price';
import { PRODUCT_STATUS_LABEL, type ProductStatus } from '@/types/product';
import type { AdminProduct } from '@/types/admin';
import { ApiError } from '@/types/api';
import styles from '@/components/admin/admin.module.css';

const STATUS_TONE: Record<ProductStatus, 'success' | 'warning' | 'neutral' | 'danger'> = {
  ON_SALE: 'success',
  SOLD_OUT: 'warning',
  HIDDEN: 'neutral',
  DISCONTINUED: 'danger',
};

/**
 * 상품 목록.
 *
 * 이 표에는 <b>판매가가 없다.</b> 시세 연동 상품의 가격은 조회 시점마다 계산되므로
 * 목록에 한 번 찍어 두면 그 순간부터 틀린 값이 된다. 가격은 상세 화면의
 * "가격 미리보기"에서 그때그때 계산해 보여준다.
 */
export function AdminProductsView() {
  const { can } = useAdminSession();
  const canEdit = can('MANAGER');

  const [categoryId, setCategoryId] = useState('');
  const [keyword, setKeyword] = useState('');
  const [appliedKeyword, setAppliedKeyword] = useState('');
  const [metalType, setMetalType] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);

  const { data: categories } = useAdminCategories();
  const paths = useMemo(() => categoryPathMap(categories ?? []), [categories]);
  const categoryOptions = useMemo(() => flattenCategories(categories ?? []), [categories]);

  const search = {
    categoryId: categoryId === '' ? null : Number(categoryId),
    keyword: appliedKeyword === '' ? null : appliedKeyword,
    metalType: metalType === '' ? null : (metalType as MetalType),
    status: status === '' ? null : (status as ProductStatus),
    page,
  };
  const { data, isLoading, error } = useAdminProducts(search);
  const rows = data?.content ?? [];

  const resetTo = (fn: () => void) => {
    fn();
    setPage(0);
  };

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <div>
          <h1 className={styles.title}>상품관리</h1>
          <p className={styles.subtitle}>
            숨김·판매종료 상품까지 모두 보인다. 판매가는 시세로 계산되므로 목록에 표시하지
            않는다 — 상세에서 미리보기로 확인한다.
          </p>
        </div>
        {canEdit && (
          <div className={styles.headActions}>
            {/* 이동은 <a> 여야 한다 — 버튼으로 감싸면 새 탭 열기·주소 복사가 막힌다. */}
            <Link className={`${styles.btn} ${styles.btnGold}`} href="/admin/products/new">
              상품 등록
            </Link>
          </div>
        )}
      </header>

      {error && (
        <Alert tone="danger">
          {error instanceof ApiError ? error.message : '상품을 불러오지 못했습니다.'}
        </Alert>
      )}

      <Panel>
        <form
          className={styles.filters}
          onSubmit={(e) => {
            e.preventDefault();
            resetTo(() => setAppliedKeyword(keyword.trim()));
          }}
        >
          <div className={styles.filterField}>
            <label className={styles.label} htmlFor="f-category">
              카테고리
            </label>
            <select
              id="f-category"
              className={styles.select}
              value={categoryId}
              onChange={(e) => resetTo(() => setCategoryId(e.target.value))}
            >
              <option value="">전체</option>
              {categoryOptions.map(({ category, depth }) => (
                <option key={category.id} value={category.id}>
                  {depth > 0 ? `${'　'.repeat(depth)}└ ` : ''}
                  {category.name}
                </option>
              ))}
            </select>
          </div>

          <div className={styles.filterField}>
            <label className={styles.label} htmlFor="f-metal">
              금속
            </label>
            <select
              id="f-metal"
              className={styles.select}
              value={metalType}
              onChange={(e) => resetTo(() => setMetalType(e.target.value))}
            >
              <option value="">전체</option>
              {METAL_ORDER.map((m) => (
                <option key={m} value={m}>
                  {METAL_LABEL[m]}
                </option>
              ))}
            </select>
          </div>

          <div className={styles.filterField}>
            <label className={styles.label} htmlFor="f-status">
              판매 상태
            </label>
            <select
              id="f-status"
              className={styles.select}
              value={status}
              onChange={(e) => resetTo(() => setStatus(e.target.value))}
            >
              <option value="">전체</option>
              {(Object.keys(PRODUCT_STATUS_LABEL) as ProductStatus[]).map((s) => (
                <option key={s} value={s}>
                  {PRODUCT_STATUS_LABEL[s]}
                </option>
              ))}
            </select>
          </div>

          <div className={styles.filterField} style={{ minWidth: 200 }}>
            <label className={styles.label} htmlFor="f-keyword">
              상품명
            </label>
            <input
              id="f-keyword"
              className={styles.input}
              value={keyword}
              placeholder="부분 일치"
              onChange={(e) => setKeyword(e.target.value)}
            />
          </div>

          <Button type="submit" variant="primary">
            검색
          </Button>
          <Button
            onClick={() =>
              resetTo(() => {
                setCategoryId('');
                setMetalType('');
                setStatus('');
                setKeyword('');
                setAppliedKeyword('');
              })
            }
          >
            초기화
          </Button>
        </form>
      </Panel>

      <Panel flush>
        {isLoading ? (
          <p className={styles.loading}>불러오는 중…</p>
        ) : rows.length === 0 ? (
          <p className={styles.empty}>조건에 맞는 상품이 없습니다.</p>
        ) : (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th className={styles.num}>ID</th>
                  <th>상품명</th>
                  <th>카테고리</th>
                  <th>가격 방식</th>
                  <th>금속</th>
                  <th className={styles.num}>중량</th>
                  <th className={styles.num}>공임</th>
                  <th>상태</th>
                  <th>노출</th>
                  <th>수정</th>
                  <th aria-label="작업" />
                </tr>
              </thead>
              <tbody>
                {rows.map((product) => (
                  <ProductRow
                    key={product.id}
                    product={product}
                    categoryPath={paths.get(product.categoryId) ?? String(product.categoryId)}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}

        {data && (
          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            onChange={setPage}
          />
        )}
      </Panel>
    </div>
  );
}

function ProductRow({
  product,
  categoryPath,
}: {
  product: AdminProduct;
  categoryPath: string;
}) {
  return (
    <tr>
      <td className={`${styles.num} ${styles.muted}`}>{product.id}</td>
      <td className={styles.strong}>
        <Link href={`/admin/products/${product.id}`}>{product.name}</Link>
      </td>
      <td className={styles.muted}>{categoryPath}</td>
      <td>{product.priceTypeName}</td>
      <td>{product.metalType ? METAL_LABEL[product.metalType] : '-'}</td>
      <td className={styles.num}>{formatGram(product.weightG)}</td>
      <td className={styles.num}>{formatKrw(product.laborCost)}</td>
      <td>
        <StatusBadge tone={STATUS_TONE[product.status]}>
          {PRODUCT_STATUS_LABEL[product.status]}
        </StatusBadge>
      </td>
      <td>
        <StatusBadge tone={product.active ? 'success' : 'neutral'}>
          {product.active ? '노출' : '숨김'}
        </StatusBadge>
      </td>
      <td className={styles.muted}>{formatDateTime(product.updatedAt)}</td>
      <td>
        <Link className={`${styles.btn} ${styles.btnSm}`} href={`/admin/products/${product.id}`}>
          상세
        </Link>
      </td>
    </tr>
  );
}
