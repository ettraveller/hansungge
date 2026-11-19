import { api } from '@/api/client';

/**
 * 푸터에 들어가는 글자들.
 *
 * <p>원천은 백엔드의 {@code FooterField} 목록이다. 칸을 더할 때는 그쪽과 여기,
 * 그리고 관리자 폼({@code FOOTER_FIELDS})을 함께 고친다.
 */
export interface FooterInfo {
  /** 상호. 로고의 「한성금거래소」는 브랜드명이고, 법적 표시가 되는 것은 이쪽이다 */
  companyName: string;
  ceo: string;
  bizNo: string;
  /** 통신판매업신고번호. 비어 있으면 화면이 그 줄을 그리지 않는다 */
  mailOrderNo: string;
  address: string;
  email: string;
  tagline: string;
  tel: string;
  hours: string;
  holiday: string;
  /**
   * 상단 띠의 글자색 ({@code #rrggbb}). 비어 있으면 기본색(흰색)이다.
   *
   * <p>푸터 값은 아니지만 띠의 대표전화가 이미 이 묶음에서 오고, 같은 화면에서
   * 같이 저장된다. 길을 하나 더 내지 않고 여기에 싣는다.
   */
  topbarColor: string;
}

export const footerApi = {
  /**
   * 관리자가 고치면 바로 보여야 한다. 푸터는 모든 화면에 있어서, 캐시가 남으면
   * "주소를 고쳤는데 안 바뀐다" 가 화면마다 반복된다.
   */
  get: () => api.get<FooterInfo>('/api/v1/footer', { cache: 'no-store' }),
};
