'use client';

import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { Alert } from '../Alert';
import { Modal } from '../Modal';
import { previewDraftPrice, type DraftPriceInput } from '@/api/admin/products';
import { PriceTag } from '@/components/ui/PriceTag';
import { adminKeys } from '@/hooks/admin/keys';
import { useProductImages } from '@/hooks/admin/useProducts';
import { ApiError } from '@/types/api';
import { METAL_LABEL, type MetalType } from '@/types/price';
import { formatGram, formatPricePerGram } from '@/utils/format';
import { mediaUrl } from '@/utils/media';
// ⚠️ 쇼핑몰 상세 화면의 스타일을 그대로 가져온다. 관리자용으로 비슷하게 다시 그리면
// 그쪽이 바뀔 때 이 미리보기만 낡고, "미리보기와 실제가 다르다"가 된다.
import shop from '@/views/ProductDetailView.module.css';
import styles from '../admin.module.css';

export interface PreviewInput extends DraftPriceInput {
  name: string;
  description?: string | null;
  galleryKeys: string[];
  detailKeys: string[];
}

/**
 * 등록 전 상품 페이지 미리보기.
 *
 * <h2>왜 필요한가</h2>
 * 등록 직후 상품은 {@code HIDDEN} 이라 고객 화면으로 확인할 방법이 없었다. 그래서
 * "일단 판매중으로 바꿔 보고 되돌리는" 절차를 쓰게 되는데, 그 몇 초 동안 확인되지
 * 않은 가격이 실제로 주문 가능한 상태가 된다.
 *
 * <h2>가격은 서버가 계산한다</h2>
 * 시세 × 중량 + 공임을 화면에서 계산하지 않는다. 그렇게 하면 미리보기와 실제 판매가가
 * 서로 다른 계산식을 갖게 되고, 어느 쪽이 맞는지 알 수 없다 (설계서 원칙 P1).
 *
 * <h2>실제 화면과 같은 점 · 다른 점</h2>
 * 사진·상품명·금속·중량·가격·설명·상세 이미지는 쇼핑몰과 같은 스타일로 그린다.
 * 장바구니·찜·옵션 선택은 없다 — 아직 상품이 아니라 누를 대상이 없다.
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
          고객 화면과 같은 모양으로 그린다. 장바구니 · 찜 · 옵션 선택은 빠져 있다 — 아직
          상품이 아니라 누를 대상이 없다.
        </p>
      }
    >
      {/*
        열려 있을 때만 그린다. 그래야 열 때마다 이 컴포넌트가 새로 생겨서
        가격을 다시 물어보고(그 사이 시세가 바뀌었을 수 있다) 고른 사진 번호도
        초기화된다 — effect 로 상태를 되돌리지 않아도 된다.
      */}
      {open && <PreviewBody input={input} productId={productId ?? null} />}
    </Modal>
  );
}

function PreviewBody({ input, productId }: { input: PreviewInput; productId: number | null }) {
  const [photoIndex, setPhotoIndex] = useState(0);

  const pricing: DraftPriceInput = {
    priceType: input.priceType,
    metalType: input.metalType,
    weightG: input.weightG,
    purity: input.purity,
    laborCost: input.laborCost,
    multiplier: input.multiplier,
    fixedPrice: input.fixedPrice,
  };

  const {
    data: quote,
    error,
    isPending,
  } = useQuery({
    queryKey: adminKeys.productDraftPrice(pricing),
    queryFn: () => previewDraftPrice(pricing),
    // 미리보기는 항상 지금 시세여야 한다. 남겨 두면 다음에 열 때 지난 값이 비친다.
    staleTime: 0,
    gcTime: 0,
    retry: false,
  });

  const savedGallery = useProductImages(productId, 'GALLERY');
  const savedDetail = useProductImages(productId, 'DETAIL');

  const galleryKeys = productId == null ? input.galleryKeys : (savedGallery.data ?? []);
  const detailKeys = productId == null ? input.detailKeys : (savedDetail.data ?? []);

  const photos = galleryKeys
    .map((key) => mediaUrl(key))
    .filter((url): url is string => url != null);
  const detailImages = detailKeys
    .map((key) => mediaUrl(key))
    .filter((url): url is string => url != null);

  return (
    <>
      {error != null && (
        <Alert tone="danger">
          {error instanceof ApiError ? error.message : '가격을 계산하지 못했습니다.'}
        </Alert>
      )}

      <div className={shop.layout}>
        {/* ---------- 이미지 ---------- */}
        <div className={shop.gallery}>
          {photos.length > 0 ? (
            <>
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                className={shop.mainPhoto}
                // 사진을 지운 뒤에도 번호가 남아 있을 수 있다. 범위를 넘으면 마지막 장.
                src={photos[Math.min(photoIndex, photos.length - 1)]}
                alt={input.name}
                decoding="async"
              />
              {photos.length > 1 && (
                <ul className={shop.photoStrip}>
                  {photos.map((url, index) => (
                    <li key={url}>
                      <button
                        type="button"
                        className={index === photoIndex ? shop.photoPickerOn : shop.photoPicker}
                        aria-label={`${index + 1}번째 사진 보기`}
                        aria-current={index === photoIndex}
                        onClick={() => setPhotoIndex(index)}
                      >
                        {/* eslint-disable-next-line @next/next/no-img-element */}
                        <img src={url} alt="" decoding="async" />
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </>
          ) : (
            <div className={shop.mainImage} aria-hidden="true">
              <span className={shop.imageMark}>
                {input.metalType ? METAL_LABEL[input.metalType as MetalType] : '한성금거래소'}
              </span>
            </div>
          )}
        </div>

        {/* ---------- 정보 ---------- */}
        <div className={shop.info}>
          <p className={shop.meta}>
            {input.metalType && <span>{METAL_LABEL[input.metalType as MetalType]}</span>}
            {input.purity != null && input.purity !== '' && <span>순도 {input.purity}</span>}
          </p>

          <h1 className={shop.name}>{input.name.trim() === '' ? '(상품명 없음)' : input.name}</h1>

          <div className={shop.priceBox}>
            {isPending ? (
              <p className={styles.loading}>가격을 계산하는 중…</p>
            ) : (
              <>
                <PriceTag price={quote?.finalUnitPrice ?? null} size="lg" />
                {quote?.appliedMarketPrice != null && (
                  <p className={shop.basis}>
                    적용 시세 {formatPricePerGram(quote.appliedMarketPrice)}
                  </p>
                )}
                {/*
                  등록하면 HIDDEN 으로 시작하므로 status 는 NOT_FOR_SALE 로 온다. 그것을
                  "판매하지 않는 상품"으로 보여주면 틀린 안내가 된다 — 금액은 계산된
                  그대로이고 노출만 안 된 상태다. 그래서 INQUIRY_ONLY 만 알린다.
                */}
                {quote?.status === 'INQUIRY_ONLY' && (
                  <p className={shop.notice}>
                    {quote.unavailableReason ??
                      '시세가 고시되지 않아 금액이 나오지 않습니다. 시세를 먼저 고시해 주세요.'}
                  </p>
                )}
              </>
            )}
          </div>

          {input.weightG != null && input.weightG !== '' && (
            <p className={shop.specNote}>중량 {formatGram(input.weightG)}</p>
          )}
        </div>
      </div>

      {/* ---------- 설명 · 상세 이미지 ---------- */}
      {(input.description?.trim() || detailImages.length > 0) && (
        <section className={shop.description}>
          <h2 className={shop.descTitle}>상품 설명</h2>
          {input.description?.trim() && <p className={shop.descBody}>{input.description}</p>}
          {detailImages.length > 0 && (
            <div className={shop.descImages}>
              {detailImages.map((url) => (
                /* eslint-disable-next-line @next/next/no-img-element */
                <img key={url} src={url} alt="" decoding="async" />
              ))}
            </div>
          )}
        </section>
      )}
    </>
  );
}
