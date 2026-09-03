import type { Metadata } from 'next';

import { PurchaseGuideView } from '@/views/PurchaseGuideView';

export const metadata: Metadata = {
  title: '매입절차',
  description:
    '한성금거래소 매입 절차 안내. 금·은·세공품을 당일 고시 시세로 매입해 드립니다. 매장 방문부터 대금 지급까지 다섯 단계와 준비물을 안내합니다.',
};

/**
 * 매입절차 (`/purchase-guide`).
 *
 * <p>GNB 「매입절차」와 메인 히어로 배너가 이미 이 주소를 가리키고 있었다.
 *
 * <p>내용이 전부 편집 콘텐츠라 이 페이지가 서버에서 가져올 것은 없다. 그래서
 * {@code dynamic} 을 쓰지 않는다.
 *
 * <p>다만 빌드 결과에는 {@code ƒ}(요청 시 렌더링)로 잡힌다 — 공용 {@code ShopLayout}
 * 이 GNB 카테고리를 서버에서 불러오기 때문에 {@code (shop)} 그룹 전체가 그렇다.
 * 이 페이지의 선택이 아니다.
 *
 * <p>매입 시세를 이 페이지에 싣지 않는 것은 의도적이다. 같은 값을 화면마다 다른 모양으로
 * 보여주면 어느 쪽이 맞는지 고객이 판단해야 한다. 시세는 `/price` 가 맡고 여기서는
 * 링크로 보낸다 — 덕분에 이 페이지가 정적으로 남는다.
 *
 * <p>콘텐츠 관리 기능(STEP 15~16)이 붙어 API 로 바뀌면 여기서 데이터를 불러
 * {@code PurchaseGuideView} 에 props 로 넘기고, 그때 렌더링 방식을 다시 정하면 된다.
 */
export default function PurchaseGuidePage() {
  return <PurchaseGuideView />;
}
