'use client';

import Link from 'next/link';
import { useState } from 'react';

import type { RefundPolicy } from '@/api/policies';
import { DELIVERY_PERIOD_NOTICE } from '@/content/delivery';
import type { QnaSummary } from '@/types/board';
import type { ProductDetail, ProductSummary } from '@/types/product';
import { mediaUrl } from '@/utils/media';
import { formatBoardDate } from '@/utils/format';
import styles from './ProductTabs.module.css';

/**
 * 상품 상세 아래쪽 탭.
 *
 * <h2>왜 탭인가</h2>
 * 상품 설명·구매 안내·문의를 세로로 이어 붙이면 상세 이미지가 긴 상품에서 문의가
 * 화면 다섯 장 아래로 밀린다. 고객이 사기 전에 가장 자주 찾는 것이 바로 그 문의다.
 *
 * <h2>내용은 서버가 이미 채워서 넘긴다</h2>
 * 탭을 누를 때 불러오지 않는다. 초기 HTML 에 설명과 문답이 들어 있어야 검색에
 * 걸리고, 누르자마자 보여야 탭이 탭처럼 느껴진다. 목록은 앞쪽 몇 건만 담고
 * 나머지는 각자의 게시판으로 보낸다.
 */
type TabKey = 'info' | 'purchase' | 'qna' | 'related';

export function ProductTabs({
  product,
  qnas,
  qnaTotal,
  related,
  refundPolicy,
}: {
  product: ProductDetail;
  qnas: QnaSummary[];
  qnaTotal: number;
  related: ProductSummary[];
  refundPolicy: RefundPolicy | null;
}) {
  const [tab, setTab] = useState<TabKey>('info');

  const detailImages = product.detailKeys
    .map((key) => mediaUrl(key))
    .filter((url): url is string => url != null);

  const hasInfo = Boolean(product.description) || detailImages.length > 0;

  const tabs: { key: TabKey; label: string; count?: number }[] = [
    { key: 'info', label: '상품정보' },
    { key: 'purchase', label: '구매정보' },
    { key: 'qna', label: '상품문의', count: qnaTotal },
    { key: 'related', label: '관련상품', count: related.length },
  ];

  return (
    <div className={styles.root}>
      <div className={styles.tabs} role="tablist" aria-label="상품 상세 정보">
        {tabs.map((t) => (
          <button
            key={t.key}
            type="button"
            role="tab"
            id={`tab-${t.key}`}
            aria-selected={tab === t.key}
            aria-controls={`panel-${t.key}`}
            className={`${styles.tab} ${tab === t.key ? styles.tabOn : ''}`}
            onClick={() => setTab(t.key)}
          >
            {t.label}
            {t.count != null && <span className={styles.count}>({t.count})</span>}
          </button>
        ))}
      </div>

      <div
        className={styles.panel}
        role="tabpanel"
        id={`panel-${tab}`}
        aria-labelledby={`tab-${tab}`}
      >
        {tab === 'info' &&
          (hasInfo ? (
            <>
              {product.description && <p className={styles.body}>{product.description}</p>}
              {detailImages.length > 0 && (
                <div className={styles.images}>
                  {detailImages.map((url) => (
                    /* eslint-disable-next-line @next/next/no-img-element */
                    <img
                      key={url}
                      src={url}
                      // 그림 안에 글이 박혀 있어 alt 로 옮길 방법이 없다. 빈 alt 로 두면
                      // 화면 읽어주는 프로그램이 건너뛴다 — 가짜 설명을 넣는 것보다 낫다.
                      alt=""
                      loading="lazy"
                      decoding="async"
                    />
                  ))}
                </div>
              )}
            </>
          ) : (
            <p className={styles.empty}>등록된 상품 설명이 없습니다.</p>
          ))}

        {tab === 'purchase' && <PurchaseInfo product={product} refundPolicy={refundPolicy} />}

        {tab === 'qna' && <QnaTab productId={product.id} qnas={qnas} total={qnaTotal} />}

        {tab === 'related' && <RelatedTab products={related} />}
      </div>
    </div>
  );
}

/** 배송·청약철회·환불규정. 사는 사람이 결제 전에 알아야 하는 것만 모은다. */
function PurchaseInfo({
  product,
  refundPolicy,
}: {
  product: ProductDetail;
  refundPolicy: RefundPolicy | null;
}) {
  return (
    <div className={styles.infoList}>
      <div className={styles.infoItem}>
        <span className={styles.infoLabel}>배송기간</span>
        {/* 같은 페이지의 배송 안내 박스(DeliveryTerms)와 한 문장이어야 한다. 전에는
            여기만 content/delivery.ts 를 읽지 않고 제 문장으로 「영업일 기준 2~6주」라고
            적고 있었다 — 박스는 「최대 6주」인데 여기는 "빠르면 2주"로 읽혀, 한 화면이
            두 기간을 말했다. 그런 경우 고객에게 유리한 쪽이 적용된다. */}
        <span className={styles.infoBody}>
          {DELIVERY_PERIOD_NOTICE}
          {product.madeToOrder && ' 주문제작 상품은 제작 착수 후 단순 변심으로 취소하실 수 없습니다.'}
        </span>
      </div>

      <div className={styles.infoItem}>
        <span className={styles.infoLabel}>배송비</span>
        <span className={styles.infoBody}>
          주문서에서 확인하실 수 있습니다. 금·은 현물은 보험이 붙는 안전 배송으로 보냅니다.
        </span>
      </div>

      <div className={styles.infoItem}>
        <span className={styles.infoLabel}>청약철회</span>
        <span className={styles.infoBody}>
          금·은 현물은 국제 시세에 따라 값이 계속 바뀌는 상품이라, 전자상거래법 제17조
          제2항에 따라 <strong>단순 변심에 의한 청약철회가 제한</strong>됩니다. 다만 상품에
          하자가 있거나 주문과 다른 물건이 온 경우에는 교환·반품하실 수 있습니다.
        </span>
      </div>

      <div className={styles.infoItem}>
        <span className={styles.infoLabel}>교환·반품·환불</span>
        <div>
          {refundPolicy ? (
            <div className={styles.policyBox}>
              <p className={styles.infoBody}>{refundPolicy.content}</p>
            </div>
          ) : (
            <p className={styles.infoBody}>
              규정을 불러오지 못했습니다. <Link href="/guide">이용안내</Link>에서 확인해 주세요.
            </p>
          )}
        </div>
      </div>
    </div>
  );
}

function QnaTab({
  productId,
  qnas,
  total,
}: {
  productId: number;
  qnas: QnaSummary[];
  total: number;
}) {
  return (
    <>
      {qnas.length === 0 ? (
        <p className={styles.empty}>등록된 문의가 없습니다. 궁금한 점을 남겨 주세요.</p>
      ) : (
        <div className={styles.qnaList}>
          {qnas.map((qna) => (
            <Link
              key={qna.id}
              href={`/support/product-qna/${qna.id}`}
              className={styles.qnaRow}
            >
              <span
                className={`${styles.badge} ${qna.status === 'ANSWERED' ? styles.badgeDone : ''}`}
              >
                {qna.status === 'ANSWERED' ? '답변완료' : '답변대기'}
              </span>
              <span className={styles.qnaTitle}>
                {qna.secret ? '🔒 비밀글입니다' : qna.title}
              </span>
              <span className={styles.qnaMeta}>
                {qna.authorName} · {formatBoardDate(qna.createdAt)}
              </span>
            </Link>
          ))}
        </div>
      )}

      <div className={styles.actions}>
        <Link
          className={styles.action}
          href={`/support/product-qna/write?productId=${productId}`}
        >
          문의하기
        </Link>
        {total > qnas.length && (
          <Link className={styles.action} href="/support/product-qna">
            문의 전체보기
          </Link>
        )}
      </div>
    </>
  );
}

function RelatedTab({ products }: { products: ProductSummary[] }) {
  if (products.length === 0) {
    return <p className={styles.empty}>같은 분류의 다른 상품이 없습니다.</p>;
  }

  return (
    <div className={styles.related}>
      {products.map((item) => {
        const thumb = mediaUrl(item.thumbnailKey);
        return (
          <Link key={item.id} href={`/shop/product/${item.id}`} className={styles.card}>
            {thumb ? (
              /* eslint-disable-next-line @next/next/no-img-element */
              <img className={styles.thumb} src={thumb} alt="" loading="lazy" decoding="async" />
            ) : (
              <div className={styles.thumb} aria-hidden="true" />
            )}
            <p className={styles.cardName}>{item.name}</p>
            <p className={styles.cardPrice}>{item.priceText}</p>
          </Link>
        );
      })}
    </div>
  );
}
