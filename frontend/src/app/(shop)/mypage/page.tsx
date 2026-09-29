import { redirect } from 'next/navigation';

/**
 * 마이페이지에 들어오면 <b>주문/배송내역</b>으로 보낸다.
 *
 * <p>여기 오는 가장 큰 이유가 "내 주문 어떻게 됐지"다. 계정 정보를 먼저 보여주면 한 번 더
 * 눌러야 하고, 정작 자주 쓰는 화면이 뒤에 숨는다. 헤더의 이름을 눌러도 이 길로 온다.
 *
 * <p>계정 관리는 왼쪽 메뉴의 제 자리({@code /mypage/account})에 있다.
 */
export default function MyPage() {
  redirect('/mypage/orders');
}
