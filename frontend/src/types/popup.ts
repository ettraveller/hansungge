/** 팝업 종류. 백엔드 PopupKind 와 값이 같아야 한다 */
export type PopupKind = 'CALCULATOR' | 'CONTENT';

export const POPUP_KIND_LABEL: Record<PopupKind, string> = {
  CALCULATOR: '매입 계산기',
  CONTENT: '직접 작성',
};

/**
 * 고객 화면이 받는 팝업.
 *
 * <p>관리자용 이름과 노출 기간은 담기지 않는다 — 고객이 알 필요가 없고,
 * 내보내 봐야 운영 사정만 드러난다.
 */
export interface Popup {
  id: number;
  kind: PopupKind;
  /** CALCULATOR 면 없다 */
  title?: string | null;
  body?: string | null;
  imageKey?: string | null;
  /** 없으면 버튼을 그리지 않는다 */
  linkHref?: string | null;
  linkLabel?: string | null;
  /** 「오늘 하루 보지 않기」를 띄울지 */
  dismissible: boolean;
}

/** 관리자 목록·상세. 기간과 끄고켬까지 보인다 */
export interface AdminPopup extends Popup {
  name: string;
  startsAt?: string | null;
  endsAt?: string | null;
  active: boolean;
  sort: number;
  /** 지금 이 순간 고객 화면에 떠 있는가 — 켬/끔과 기간을 서버가 합쳐 판정한 값 */
  live: boolean;
  updatedAt?: string | null;
  updatedBy?: string | null;
}

export interface PopupInput {
  name: string;
  kind: PopupKind;
  title?: string;
  body?: string;
  imageKey?: string;
  linkHref?: string;
  linkLabel?: string;
  startsAt?: string | null;
  endsAt?: string | null;
  active: boolean;
  sort: number;
  dismissible: boolean;
}
