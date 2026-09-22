import type { IdentityResult, IdentityStartForm, PasswordResetGrant, User } from '@/types/user';

/**
 * 본인인증(간편인증) 왕복.
 *
 * <p>본인확인은 <b>우리 화면을 떠났다가 돌아오는</b> 흐름이다. 이니시스 창 → 이니시스가
 * 결과를 백엔드 수신 주소로 POST → 백엔드가 {@code /auth/identity?providerToken=…} 로 보낸다.
 * 그 사이 React 상태는 전부 사라지므로, 돌아왔을 때 "무엇을 하던 중이었는지"를
 * sessionStorage 에 남긴다.
 *
 * <p>localStorage 가 아닌 이유는 탭을 닫으면 사라져야 하기 때문이다 — 공용 PC 에서
 * 가입하다 만 사람의 진행 상태가 남으면 안 된다.
 *
 * <p><b>비밀번호는 여기 담지 않는다.</b> sessionStorage 는 같은 출처의 스크립트가 전부 읽는다.
 */

const PENDING_KEY = 'hsg_identity';
const OUTCOME_KEY = 'hsg_identity_outcome';

export interface PendingIdentity {
  /** 본인확인 시작 때 받은 진행 토큰. 결과 제출 때 그대로 보낸다 */
  token: string;
  /**
   * 'signup' = 자체 가입, 'reset' = 비밀번호 찾기,
   * 'verify' = 로그인한 회원의 본인인증(마이페이지 — 매입·정산계좌 앞, 소셜 회원의 번호 등록)
   */
  flow: 'signup' | 'reset' | 'verify';
  /** 끝난 뒤 보낼 곳 */
  next: string;
}

/** 결과를 받은 뒤 — 새로고침해도 같은 본인인증을 다시 제출하지 않도록 남겨 둔다. */
export type IdentityOutcome =
  | { flow: 'signup'; providerToken: string; next: string; result: IdentityResult }
  | { flow: 'reset'; providerToken: string; next: string; grant: PasswordResetGrant }
  | { flow: 'verify'; providerToken: string; next: string; user: User };

function storage(): Storage | null {
  try {
    return typeof window === 'undefined' ? null : window.sessionStorage;
  } catch {
    // 시크릿 모드나 저장소 차단. 여기서 던지면 화면이 통째로 죽는다.
    return null;
  }
}

function readJson<T>(key: string): T | null {
  const raw = storage()?.getItem(key);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

export const pendingIdentity = {
  save(value: PendingIdentity) {
    storage()?.setItem(PENDING_KEY, JSON.stringify(value));
  },
  read(): PendingIdentity | null {
    return readJson<PendingIdentity>(PENDING_KEY);
  },
  clear() {
    storage()?.removeItem(PENDING_KEY);
  },
};

/**
 * 결과 보관.
 *
 * <p>같은 본인확인 결과를 두 번 제출하면 서버가 "이미 사용된 본인확인"으로 거부한다.
 * 결과 화면(비밀번호 입력 등)에서 새로고침하면 그 일이 생기므로, 받은 결과를 여기 두고
 * 같은 providerToken 이면 다시 제출하지 않고 이것을 쓴다.
 */
export const identityOutcome = {
  save(value: IdentityOutcome) {
    storage()?.setItem(OUTCOME_KEY, JSON.stringify(value));
  },
  readFor(providerToken: string): IdentityOutcome | null {
    const value = readJson<IdentityOutcome>(OUTCOME_KEY);
    return value && value.providerToken === providerToken ? value : null;
  },
  clear() {
    storage()?.removeItem(OUTCOME_KEY);
  },
};

/**
 * 서버가 준 폼을 그대로 전송해 본인확인 창으로 간다.
 *
 * <p>값을 해석하지 않고 hidden 필드로 옮겨 담기만 한다. KG이니시스는 인증창을 폼 POST 로 열고
 * (상점아이디·거래번호·해시), 개발용 목도 같은 모양이라 화면에 공급자별 분기가 없다.
 *
 * <p>팝업이 아니라 <b>페이지 전환</b>으로 연다. 이니시스 규격은 팝업이 기본이지만, 휴대폰
 * 브라우저·앱 안 웹뷰에서는 팝업이 막히고 규격도 페이지 전환을 허용한다. 결과는 어차피
 * 백엔드를 거쳐 {@code /auth/identity} 로 돌아온다.
 */
export function submitIdentityForm(form: IdentityStartForm): void {
  const element = document.createElement('form');
  element.method = form.method;
  element.action = form.action;
  element.acceptCharset = 'UTF-8';
  element.style.display = 'none';
  Object.entries(form.fields).forEach(([name, value]) => {
    const input = document.createElement('input');
    input.type = 'hidden';
    input.name = name;
    input.value = value;
    element.appendChild(input);
  });
  document.body.appendChild(element);
  element.submit();
}
