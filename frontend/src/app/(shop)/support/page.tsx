import type { Metadata } from 'next';

import { SupportView } from '@/views/board/SupportView';

export const metadata: Metadata = {
  title: '고객센터',
  description:
    '한성금거래소 고객센터. 전화 문의, 문의게시판, 제품문의, 공지사항, 이벤트를 한곳에서 안내합니다.',
};

/**
 * 고객센터 (`/support`).
 *
 * <p>GNB 「고객센터」와 모바일 드로어의 「고객센터 전체」, 그리고 로그인 화면의 안내
 * 문구까지 세 곳이 이 주소를 가리키는데 라우트가 없어 404 였다.
 *
 * <p>내용이 전부 편집 콘텐츠라 이 페이지가 서버에서 가져올 것은 없다.
 */
export default function SupportPage() {
  return <SupportView />;
}
