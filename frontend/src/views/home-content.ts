import type { Slide } from '@/components/home/HeroSlider';

/**
 * 메인 화면의 <b>편집 콘텐츠</b>.
 *
 * <p>배너는 원래 관리자가 운영하는 데이터지만 배너 관리 도메인이 아직 없다(STEP 15~16).
 * 화면 코드에 섞어 두면 나중에 API 로 바꿀 때 어디를 고쳐야 하는지 찾기 어려워, 한 파일에
 * 모아 <b>교체 지점을 한눈에</b> 보이게 한다.
 *
 * <p>백엔드가 붙으면 이 파일을 지우고 호출부에서 API 를 부르면 된다 — 컴포넌트는 그대로다.
 *
 * <p>공지·뉴스 목업(`NOTICES`, `NEWS`)은 여기 있었지만 <b>지웠다.</b> 공지는 실제
 * 게시판 API(`/api/v1/notices`)로 바뀌었고, 뉴스센터는 만들지 않기로 했다 —
 * 이 파일이 존재하는 목적이 바로 그 교체였다.
 */

/** ⚠️ 임시 콘텐츠. 배너 관리 기능이 생기면 API 로 대체한다. */
export const HERO_SLIDES: Slide[] = [
  {
    eyebrow: 'HANSUNG GOLD EXCHANGE',
    title: '오늘의 정확한 시세로',
    highlight: '안심하고 거래하세요',
    desc: '당사가 고시한 시세를 그대로 적용합니다. 최종 금액은 주문하시는 시각의 고시 시세로 확정됩니다.',
    // 바로 아래가 시세표라 "시세 확인"으로 보내면 이미 도착한 곳을 다시 가리킨다.
    // 배너의 역할은 다음 행동을 만드는 것이다.
    href: '/shop?categoryId=1',
    cta: '골드바 보러가기',
    image: '/hero.jpg',
  },
  {
    eyebrow: 'PURE GOLD 999.9',
    title: '순도 99.99% 골드바',
    highlight: '보증서와 함께',
    desc: '1돈부터 10돈까지. 모든 제품에 품질보증서를 발급해 재매입 시에도 가치를 인정받습니다.',
    href: '/shop?categoryId=1',
    cta: '골드바 보러가기',
  },
  {
    eyebrow: 'BUY-BACK SERVICE',
    title: '보유하신 귀금속',
    highlight: '최고가 매입',
    desc: '돌반지·목걸이 등 보유하신 귀금속을 당일 고시 시세로 매입해 드립니다.',
    href: '/purchase-guide',
    cta: '매입 절차 안내',
  },
];
