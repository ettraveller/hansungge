import type { Metadata } from 'next';
import { notFound, unstable_rethrow } from 'next/navigation';
import { productApi } from '@/api/products';
import { load } from '@/api/server';
import { ApiError } from '@/types/api';
import { ProductDetailView } from '@/views/ProductDetailView';

interface PageProps {
  params: Promise<{ id: string }>;
}

/**
 * 요청 시점 렌더링을 명시한다.
 *
 * 상세 화면의 가격은 <b>현재 시세</b>로 계산된 값이다. 빌드 시점에 굳히면 그 순간의
 * 가격이 화면에 박히고, 시세가 바뀌어도 재배포 전까지 옛 값이 나간다.
 */
export const dynamic = 'force-dynamic';

/**
 * 상품 상세 (`/shop/product/{id}`).
 *
 * <p>Next.js 16 에서 {@code params} 는 Promise 라 반드시 await 해야 한다.
 *
 * <p>백엔드는 숨김·판매종료 상품에 <b>403 이 아니라 404</b> 를 준다. 여기서도 그대로
 * {@code notFound()} 로 넘겨 "여기 뭔가 있다"는 신호를 만들지 않는다.
 */
export default async function ProductDetailPage({ params }: PageProps) {
  const id = toProductId((await params).id);
  if (id == null) {
    notFound();
  }

  const product = await load(() => productApi.detail(id), `상품 상세 ${id}`);

  if (!product.data) {
    // 존재하지 않는 상품과 일시 장애를 구분한다. 후자를 404 로 처리하면
    // 검색엔진이 정상 상품 페이지를 색인에서 지워버린다.
    if (product.error?.startsWith('PRODUCT_NOT_FOUND')) {
      notFound();
    }
    throw new Error(`상품 정보를 불러올 수 없습니다: ${product.error}`);
  }

  return <ProductDetailView product={product.data} />;
}

/** 검색엔진·SNS 공유용 메타데이터. 상품명이 title 에 들어가야 검색 결과에 뜬다. */
export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const id = toProductId((await params).id);
  if (id == null) {
    return { title: '상품' };
  }

  try {
    const product = await productApi.detail(id);
    return {
      title: product.name,
      description:
        product.description?.slice(0, 150) ??
        `${product.name} — 한성금거래소 고시 시세 기준 판매가.`,
      openGraph: { title: product.name, type: 'website' },
    };
  } catch (e) {
    // Next.js 내부 오류(동적 렌더링 신호 등)는 삼키지 않는다. 반드시 첫 줄이어야 한다.
    unstable_rethrow(e);

    // 메타데이터 생성 실패가 페이지 자체를 막아서는 안 된다.
    if (!(e instanceof ApiError)) {
      console.error(`[SSR] 상품 ${id} 메타데이터 생성 실패`, e);
    }
    return { title: '상품' };
  }
}

function toProductId(raw: string): number | null {
  const n = Number(raw);
  return Number.isInteger(n) && n > 0 ? n : null;
}
