import type { Metadata } from 'next';
import { StoreView } from '@/views/StoreView';

export const metadata: Metadata = {
  title: '지점안내',
  description:
    '한성금거래소 지점 안내. 가까운 지점에서 금·귀금속 실물 확인과 매입 상담을 받으실 수 있습니다.',
};

/**
 * 지점찾기 (`/store`).
 *
 * <p>지점 데이터가 아직 코드 상수라 서버에서 가져올 것이 없다. 그래서 이 페이지는
 * 정적으로 미리 만들어 둔다 — 시세처럼 매 요청 바뀌는 값이 없다.
 *
 * <p>지점 관리 기능(STEP 15~16)이 붙어 API 로 바뀌면 여기서 데이터를 불러
 * {@code StoreView} 에 props 로 넘기고, 그때 렌더링 방식을 다시 정하면 된다.
 */
export default function StorePage() {
  return <StoreView />;
}
