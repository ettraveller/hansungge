import type { Metadata } from 'next';

import { footerApi } from '@/api/footer';
import { load } from '@/api/server';
import { FOOTER_DEFAULTS } from '@/data/company';
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
 * <p>대표전화·운영시간은 <b>푸터와 같은 설정값</b>을 쓴다. 코드에 따로 박으면 관리자가
 * 푸터에서 번호를 바꾼 날 같은 화면 안에서 두 번호가 어긋난다. 조회가 실패하면
 * 기본값으로 그린다 — 전화번호가 사라지는 것이 이 화면에서 가장 나쁜 결과다.
 */
export default async function SupportPage() {
  const footer = await load(() => footerApi.get(), '고객센터 대표전화');
  const info = footer.data ?? FOOTER_DEFAULTS;

  return <SupportView tel={info.tel} hours={info.hours} holiday={info.holiday} />;
}
