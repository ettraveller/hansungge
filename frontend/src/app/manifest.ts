import type { MetadataRoute } from 'next';

/**
 * 웹 매니페스트 — 안드로이드에서 "홈 화면에 추가"할 때 쓰인다.
 *
 * <p>이게 없으면 안드로이드가 <b>화면을 축소한 그림</b>을 아이콘으로 쓴다. 아이폰은
 * {@code app/apple-icon.png} 를 따로 보므로 그쪽은 파일 이름만으로 해결된다.
 *
 * <p>{@code display: 'browser'} 다 — 주소창을 숨기지 않는다. 결제창이 뜨고 외부 인증으로
 * 오가는 사이트라, 주소를 확인할 수 없으면 고객이 지금 어디에 카드번호를 넣는지 알 수 없다.
 */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: '한성금거래소',
    short_name: '한성금거래소',
    description: '금·은·귀금속 온라인 쇼핑몰. 매일 고시하는 시세로 골드바·주얼리를 판매합니다.',
    start_url: '/',
    display: 'browser',
    lang: 'ko',
    // 배경·테마는 토큰의 종이색·먹과 같은 값이다. 여기만 다르면 앱을 열 때 한 번 번쩍인다.
    background_color: '#f5f2eb',
    theme_color: '#1c1a17',
    icons: [
      { src: '/icon-192.png', sizes: '192x192', type: 'image/png' },
      { src: '/icon-512.png', sizes: '512x512', type: 'image/png' },
    ],
  };
}
