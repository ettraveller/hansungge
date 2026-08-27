import type { BoardPost } from '@/components/home/BoardList';
import type { Slide } from '@/components/home/HeroSlider';
import type { Region } from '@/components/home/StoreLocator';

/**
 * 메인 화면의 <b>편집 콘텐츠</b>.
 *
 * <p>배너·지점·게시글은 원래 관리자가 운영하는 데이터지만, 해당 도메인(배너 관리·지점 관리·
 * 게시판)이 아직 없다(STEP 15~16). 화면 코드에 섞어 두면 나중에 API 로 바꿀 때 어디를
 * 고쳐야 하는지 찾기 어려워, 한 파일에 모아 <b>교체 지점을 한눈에</b> 보이게 한다.
 *
 * <p>백엔드가 붙으면 이 파일을 지우고 호출부에서 API 를 부르면 된다 — 컴포넌트는 그대로다.
 */

/** ⚠️ 임시 콘텐츠. 배너 관리 기능이 생기면 API 로 대체한다. */
export const HERO_SLIDES: Slide[] = [
  {
    eyebrow: 'HANSUNG GOLD EXCHANGE',
    title: '오늘의 정확한 시세로',
    highlight: '안심하고 거래하세요',
    desc: '고시 시세를 그대로 반영한 판매가. 주문 시점에 서버가 다시 계산해 확정합니다.',
    href: '/price',
    cta: '오늘의 금 시세 확인',
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
    desc: '돌반지·목걸이 등 귀금속을 투명한 고시 시세로 매입해 드립니다.',
    href: '/purchase-guide',
    cta: '매입 절차 안내',
  },
];

/** ⚠️ 임시 콘텐츠. 지점 관리 기능이 생기면 API 로 대체한다. */
export const REGIONS: Region[] = [
  {
    name: '서울',
    branches: [
      { name: '종로 본점', address: '서울특별시 종로구 돈화문로 00, 0층 (묘동)', tel: '02-000-0000' },
      { name: '강남점', address: '서울특별시 강남구 테헤란로 000, 0층', tel: '02-000-0001' },
      { name: '영등포점', address: '서울특별시 영등포구 영중로 00, 0층', tel: '02-000-0002' },
    ],
  },
  {
    name: '경기 · 인천',
    branches: [
      { name: '수원점', address: '경기도 수원시 팔달구 정조로 000', tel: '031-000-0000' },
      { name: '인천점', address: '인천광역시 미추홀구 주안로 000', tel: '032-000-0000' },
    ],
  },
  {
    name: '부산 · 경남',
    branches: [
      { name: '부산 서면점', address: '부산광역시 부산진구 중앙대로 000', tel: '051-000-0000' },
    ],
  },
  {
    name: '대구 · 경북',
    branches: [
      { name: '대구 중앙점', address: '대구광역시 중구 중앙대로 000', tel: '053-000-0000' },
    ],
  },
];

/** ⚠️ 임시 콘텐츠. 게시판(STEP 15~16)이 생기면 API 로 대체한다. */
export const NEWS: BoardPost[] = [
  { id: 1, title: '국제 금 시세 동향과 국내 고시가 반영 안내', date: '2026-08-26', fresh: true },
  { id: 2, title: '추석 연휴 배송 및 시세 고시 일정 안내', date: '2026-08-20' },
  { id: 3, title: '골드바 신규 규격(5돈) 출시', date: '2026-08-12' },
  { id: 4, title: '순금 주얼리 라인 개편 안내', date: '2026-08-01' },
];

/** ⚠️ 임시 콘텐츠. 게시판(STEP 15~16)이 생기면 API 로 대체한다. */
export const NOTICES: BoardPost[] = [
  { id: 1, title: '온라인 주문 시 시세 적용 기준 변경 안내', date: '2026-08-25', fresh: true },
  { id: 2, title: '개인정보처리방침 개정 안내', date: '2026-08-18' },
  { id: 3, title: '시스템 정기 점검 안내 (매월 첫째 주 화요일)', date: '2026-08-04' },
  { id: 4, title: '매입 접수 운영시간 변경 안내', date: '2026-07-28' },
];

/** 중간 띠배너. 레퍼런스의 시세 아래 배너 자리다. */
export const MID_BANNER = {
  eyebrow: 'SAFE DELIVERY',
  title: '오후 2시 이전 결제 시 당일 출고',
  desc: '보증서와 함께 안전 포장되어 배송됩니다.',
  href: '/guide',
  cta: '이용안내 보기',
};
