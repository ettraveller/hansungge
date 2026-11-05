'use client';

import { useQuery } from '@tanstack/react-query';

import { Alert } from '../Alert';
import { Modal } from '../Modal';
import {
  previewDraftDetail,
  type DraftDetailInput,
  type DraftPriceInput,
} from '@/api/admin/products';
import { adminKeys } from '@/hooks/admin/keys';
import { useProductImages } from '@/hooks/admin/useProducts';
import { ApiError } from '@/types/api';
// ⚠️ 고객이 보는 바로 그 컴포넌트다. 관리자용으로 비슷하게 다시 그리지 않는다 —
// 그러면 쇼핑몰 쪽이 바뀔 때 미리보기만 낡아서 "미리보기와 실제가 다르다"가 된다.
import { ProductDetailView } from '@/views/ProductDetailView';
import styles from '../admin.module.css';

export interface PreviewInput {
  name: string;
  description?: string | null;
  galleryKeys: string[];
  detailKeys: string[];
  /** 주문제작인가. 미리보기의 구분·청약철회 안내가 이 값으로 갈린다 */
  madeToOrder: boolean;
  /** 과세 상품인가 (V38). 미리보기의 「부가세 포함」 표시가 갈린다 */
  vatTaxable: boolean;
  /** 배송기간 문구 (V41). 비우면 미리보기도 기본 문구로 보인다 */
  deliveryPeriod?: string | null;
  /** 택배사 (V41). 비우면 「우체국택배」 */
  courier?: string | null;
  pricing: DraftPriceInput;
}

/**
 * 등록 전 상품 페이지 미리보기.
 *
 * <h2>왜 필요한가</h2>
 * 등록 직후 상품은 {@code HIDDEN} 이라 고객 화면으로 확인할 방법이 없었다. 그래서
 * 가격을 보려면 "일단 판매중으로 바꿔 보고 되돌리는" 절차를 쓰게 되는데, 그 몇 초
 * 동안 확인되지 않은 가격이 실제로 주문 가능한 상태가 된다.
 *
 * <h2>고객 화면을 그대로 띄운다</h2>
 * 서버가 <b>공개 상세와 같은 모양</b>의 응답을 주고({@code POST /detail-preview}),
 * 그것을 {@link ProductDetailView} 에 그대로 넘긴다. 가격·옵션·가격 내역까지 고객이
 * 보는 것과 같은 코드로 그려진다.
 *
 * <p>{@code preview} 로 넘기면 장바구니·찜·최근본상품만 꺼진다 — 아직 상품이 아니라서
 * 그것들이 가리킬 id 가 없다. 버튼은 비활성 상태로 남는다. 빼 버리면 그 아래 여백과
 * 배치가 달라져서, 정작 확인하려던 "고객이 보는 모양"이 아니게 된다.
 *
 * <h2>판매중으로 가정해서 그린다</h2>
 * 등록 직후 상품은 HIDDEN 이고, 공개 응답은 판매하지 않는 상품의 금액을 가린다. 그대로
 * 두면 미리보기에 "시세문의"만 떠서 정작 확인하려던 가격이 안 보인다. 그래서 서버가
 * <b>판매중을 가정해</b> 계산하고, 그 전제를 창 아래에 적어 둔다.
 */
export function ProductPreviewModal({
  open,
  onClose,
  input,
  productId,
}: {
  open: boolean;
  onClose: () => void;
  input: PreviewInput;
  /**
   * 수정 화면이면 그 상품 id.
   *
   * <p>등록 화면에서는 폼이 이미지 키를 들고 있지만, 수정 화면에서는 아래 패널이
   * 들고 있어 폼의 목록이 비어 있다. 그대로 그리면 사진이 하나도 없는 미리보기가
   * 나와서 "사진이 안 들어갔다"로 읽힌다 — 저장된 목록을 받아 쓴다.
   */
  productId?: number | null;
}) {
  return (
    <Modal
      open={open}
      title="상품 페이지 미리보기"
      width="wide"
      onClose={onClose}
      footer={
        <p className={styles.hint}>
          <strong>판매를 시작하면</strong> 고객에게 이렇게 보인다. 등록 직후에는 숨김
          상태라 아직 노출되지 않는다. 장바구니 · 찜은 눌리지 않는다 — 아직 상품이 아니라
          담을 대상이 없다.
        </p>
      }
    >
      {/*
        열려 있을 때만 그린다. 그래야 열 때마다 새로 생겨서 가격을 다시 물어본다 —
        그 사이 시세가 바뀌었을 수 있다. effect 로 상태를 되돌리지 않아도 된다.
      */}
      {open && <PreviewBody input={input} productId={productId ?? null} />}
    </Modal>
  );
}

function PreviewBody({ input, productId }: { input: PreviewInput; productId: number | null }) {
  const savedGallery = useProductImages(productId, 'GALLERY');
  const savedDetail = useProductImages(productId, 'DETAIL');

  const galleryKeys = productId == null ? input.galleryKeys : (savedGallery.data ?? []);
  const detailKeys = productId == null ? input.detailKeys : (savedDetail.data ?? []);

  const body: DraftDetailInput = {
    name: input.name,
    description: input.description,
    galleryKeys,
    detailKeys,
    madeToOrder: input.madeToOrder,
    vatTaxable: input.vatTaxable,
    deliveryPeriod: input.deliveryPeriod,
    courier: input.courier,
    pricing: input.pricing,
  };

  const {
    data: product,
    error,
    isPending,
  } = useQuery({
    queryKey: adminKeys.productDraftPreview(body),
    queryFn: () => previewDraftDetail(body),
    // 미리보기는 항상 지금 시세여야 한다. 남겨 두면 다음에 열 때 지난 값이 비친다.
    staleTime: 0,
    gcTime: 0,
    retry: false,
  });

  if (error != null) {
    return (
      <Alert tone="danger" title="미리보기를 만들지 못했습니다">
        {error instanceof ApiError ? error.message : '알 수 없는 오류가 발생했습니다.'}
      </Alert>
    );
  }

  if (isPending || !product) {
    return <p className={styles.loading}>불러오는 중…</p>;
  }

  return (
    <div className={styles.previewFrame}>
      <ProductDetailView product={product} preview />
    </div>
  );
}
