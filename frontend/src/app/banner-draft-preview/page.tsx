import type { Metadata } from 'next';

import { DraftHero } from './DraftHero';

export const metadata: Metadata = {
  title: '꾸민 배너 미리보기',
  // 손님이 찾아올 화면이 아니다. 색인되면 메인과 거의 같은 화면이 하나 더 검색에 뜬다.
  robots: { index: false, follow: false },
};

/**
 * 꾸민 배너 미리보기 (`/banner-draft-preview`).
 *
 * <p>관리자 화면의 모달이 iframe 으로 연다. 담아 둔 것({@code sessionStorage})이 없으면
 * 빈 화면이다 — 주소만 알아도 볼 것이 없다는 뜻이라, 공개 경로여도 새어 나갈 것이 없다.
 *
 * <p>사진 배너 미리보기({@code /banner-preview})와 달리 머리띠·시세표·푸터를 함께
 * 그리지 않는다. 여기서 확인하는 것은 <b>배너 한 장 안의 배치</b>이고, 그것은 저장하기
 * 전에 봐야 하는 유일한 것이다. 저장한 뒤의 전체 화면은 메인에서 그대로 보인다.
 */
export default function BannerDraftPreviewPage() {
  return (
    <main id="main">
      <DraftHero />
    </main>
  );
}
