/**
 * 본인확인을 마치고 돌아올 자리.
 *
 * <p>본인확인은 <b>우리 화면을 떠났다가 돌아오는</b> 흐름이다. 그 사이 React 상태는
 * 전부 사라지므로, 돌아왔을 때 "무엇을 하던 중이었는지"를 알 방법이 필요하다.
 *
 * <p>소셜 로그인의 {@code hsg_oauth_state}·{@code hsg_signup} 과 같은 방식으로
 * sessionStorage 에 둔다. localStorage 가 아닌 이유는 탭을 닫으면 사라져야 하기
 * 때문이다 — 공용 PC 에서 가입하다 만 사람의 진행 상태가 남으면 안 된다.
 *
 * <p><b>비밀번호는 여기 담지 않는다.</b> sessionStorage 는 같은 출처의 스크립트가
 * 전부 읽을 수 있다. 비밀번호는 폼이 들고 있다가 사라지면 다시 입력받는다.
 */

const KEY = 'hsg_identity';

export interface PendingIdentity {
  /** 이메일 인증 또는 소셜 콜백에서 받은 가입 진행 토큰 */
  signUpToken: string;
  /** 'email' = 자체 가입, 'social' = 소셜 가입. 돌아온 뒤 갈 곳이 다르다 */
  origin: 'email' | 'social';
  /** 가입을 마친 뒤 보낼 곳 */
  next: string;
}

function storage(): Storage | null {
  try {
    return window.sessionStorage;
  } catch {
    // 시크릿 모드나 저장소 차단. 여기서 던지면 화면이 통째로 죽는다.
    return null;
  }
}

export const pendingIdentity = {
  save(value: PendingIdentity) {
    storage()?.setItem(KEY, JSON.stringify(value));
  },

  /** 읽고 지운다. 한 번 쓰면 끝나는 값이라 남겨 둘 이유가 없다. */
  take(): PendingIdentity | null {
    const raw = storage()?.getItem(KEY);
    if (!raw) return null;
    storage()?.removeItem(KEY);
    try {
      return JSON.parse(raw) as PendingIdentity;
    } catch {
      return null;
    }
  },

  clear() {
    storage()?.removeItem(KEY);
  },
};

/**
 * 본인확인 창이 돌아올 우리 쪽 주소.
 *
 * <p>서버가 CORS 허용 목록으로 거르므로 아무 주소나 쓸 수 없다. 절대 주소여야 하는
 * 이유는 공급자 창이 우리 출처 밖에서 열리기 때문이다.
 */
export function identityReturnUrl(): string {
  return `${window.location.origin}/auth/identity`;
}
