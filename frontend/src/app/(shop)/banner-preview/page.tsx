import type { Metadata } from 'next';

import type { HeroBanner } from '@/api/banners';
// ⚠️ 고객이 보는 바로 그 화면이다. 관리자용으로 비슷하게 다시 그리지 않는다 —
// 그러면 쇼핑몰 쪽이 바뀔 때 미리보기만 낡아서 "미리보기와 실제가 다르다"가 된다.
import { HomeView } from '@/views/HomeView';
import { loadHomeData } from '../home-data';

export const metadata: Metadata = {
  title: '배너 미리보기',
  // 손님이 찾아올 화면이 아니다. 색인되면 메인과 거의 같은 페이지가 하나 더 검색에 뜬다.
  robots: { index: false, follow: false },
};

export const dynamic = 'force-dynamic';

/**
 * 받아들일 이미지 키 모양.
 *
 * <p>이 주소는 공개 경로이고 값은 쿼리에서 온다. {@code mediaUrl} 은 {@code http} 로
 * 시작하는 값을 <b>그대로 쓰므로</b>, 걸러내지 않으면 남의 서버 이미지를 우리 도메인
 * 화면에 띄우는 주소가 만들어진다. {@code :} 와 맨 앞 {@code /} 가 막히면 그 길이 닫힌다.
 */
const KEY_PATTERN = /^[A-Za-z0-9][A-Za-z0-9/_.-]*$/;

/** 쉼표로 묶인 키 목록. 모양이 아닌 것은 빈 칸으로 둔다 — 자리를 당기면 짝이 어긋난다. */
function parseKeys(value: string | undefined): string[] {
  if (!value) return [];
  return value
    .split(',')
    .map((key) => (KEY_PATTERN.test(key) && !key.includes('..') ? key : ''));
}

/**
 * 배너 미리보기 (`/banner-preview`).
 *
 * <h2>메인을 그대로 띄우고 배너만 갈아 끼운다</h2>
 * 배너 조각만 따로 그리면 <b>확인하고 싶은 것을 확인할 수 없다.</b> 배너는 그 아래
 * 시세표와 맞닿는 자리에 놓이고, 사진의 아래쪽 문구가 시세표 위에 어떻게 걸리는지가
 * 실제로 보는 그림이다. 머리띠·시세·상품·푸터까지 함께 있어야 그게 보인다.
 *
 * <p>그래서 {@link loadHomeData} 를 메인과 똑같이 부르고 배너만 바꾼다. 메인이 바뀌면
 * 미리보기도 같이 바뀐다 — 두 벌로 갈라지지 않는다.
 *
 * <h2>왜 따로 한 페이지인가</h2>
 * 관리자 화면의 모달 안에서 히어로를 바로 그리면 <b>휴대폰용을 확인할 수 없다.</b>
 * 가로용·휴대폰용을 가르는 것이 창 폭을 보는 미디어 쿼리이고, 그 창은 모달이 아니라
 * 관리자가 쓰는 브라우저 창이다. 데스크톱에서 열면 영원히 가로용만 보인다.
 *
 * <p>iframe 은 제 폭이 곧 제 뷰포트다. 390px 짜리 iframe 안에서는 미디어 쿼리·{@code dvh}
 * ·히어로 높이가 전부 휴대폰 값으로 풀린다 — CSS 를 한 줄도 고치지 않고 실제와 같은
 * 그림을 얻는다.
 *
 * <h2>저장하기 전의 것을 본다</h2>
 * 그래서 배너만 DB 가 아니라 쿼리로 받는다. 저장한 뒤에만 볼 수 있으면 "일단 저장하고
 * 확인하고 되돌리는" 절차가 되고, 그 사이 확인되지 않은 배너가 메인에 걸린다.
 *
 * <p>팝업({@code PopupLayer})은 띄우지 않는다. 지금 확인하려는 것이 배너인데 그 위를
 * 알림창이 덮는다.
 */
export default async function BannerPreviewPage({
  searchParams,
}: {
  searchParams: Promise<{ w?: string; m?: string }>;
}) {
  const [params, data] = await Promise.all([searchParams, loadHomeData()]);

  const wideKeys = parseKeys(params.w);
  const mobileKeys = parseKeys(params.m);

  const banners: HeroBanner[] = wideKeys
    .map((imageKey, index) => ({
      id: index,
      imageKey,
      mobileImageKey: mobileKeys[index] || null,
      linkHref: null,
      altText: `${index + 1}번 배너`,
      sort: index,
      active: true,
    }))
    // 거른 뒤에 짝을 맞춘다 — 먼저 걸러내면 휴대폰용이 한 칸씩 당겨져 엉뚱한 배너에 붙는다.
    .filter((banner) => banner.imageKey !== '');

  // 배너가 없으면 HomeView 가 기본 배너로 돌아간다 — 메인에서 일어나는 일과 같다.
  return <HomeView {...data} banners={banners} />;
}
