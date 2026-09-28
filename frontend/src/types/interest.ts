/** 마이페이지 요약. 숫자는 전부 서버가 센 값이다 — 화면이 한 장씩 세면 총계가 틀린다. */
export interface MyPageSummary {
  name?: string | null;
  orderCount: number;
  awaitingPayment: number;
  preparing: number;
  shipping: number;
  delivered: number;
  cancelled: number;
  wishlistCount: number;
}
