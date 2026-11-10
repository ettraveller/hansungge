import { PopupLayer } from '@/components/popup/PopupLayer';
import { HomeView } from '@/views/HomeView';
import { loadHomeData } from './home-data';

/**
 * 요청 시점 렌더링을 명시한다.
 *
 * 이 페이지의 내용은 <b>시세</b>다. 빌드 시점에 굳히면 그 순간의 가격이 화면에 박히고,
 * 시세가 바뀌어도 재배포 전까지 옛 값이 나간다. no-store fetch 때문에 Next.js 가 어차피
 * 동적으로 판정하지만, 의도를 코드로 남겨 두면 나중에 캐시를 붙이는 사람이 이 사실을 본다.
 */
export const dynamic = 'force-dynamic';

/**
 * 메인 (`/`).
 *
 * app/ 아래 page.tsx 는 <b>데이터 로드 + 라우팅 진입점</b>이고, 화면은 views/ 에 둔다.
 * 서버 컴포넌트라 시세·상품이 초기 HTML 에 실려 나간다 (SEO).
 *
 * (Next.js App Router 에서 src/pages 는 Pages Router 예약 디렉터리이므로
 *  화면 디렉터리 이름으로 쓸 수 없다. 그래서 views/ 를 쓴다.)
 *
 * <p>불러오는 일은 {@link loadHomeData} 에 있다 — 배너 미리보기가 같은 것을 쓴다.
 */
export default async function HomePage() {
  const data = await loadHomeData();

  return (
    <>
      {/* 팝업은 메인에만 띄운다. 모든 화면에 두면 상품을 보다 장바구니로 가는
          길목마다 알림창이 끼어든다 — 광고가 길을 막는 순간 광고가 아니라 방해다.
          내용은 관리자가 정한다(관리자 › 팝업 관리). */}
      <PopupLayer />
      <HomeView {...data} />
    </>
  );
}
