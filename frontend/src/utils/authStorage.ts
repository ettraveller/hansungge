import type { AgreementItem, OAuthProviderType } from '@/types/user';

/**
 * 소셜 로그인 왕복 동안만 브라우저에 남는 값들.
 *
 * <p>둘 다 sessionStorage 다. 탭을 닫으면 사라지고, 다른 탭과 공유되지 않는다 —
 * 로그인 왕복은 한 탭 안에서 끝나는 일이라 그 범위가 정확하다.
 *
 * <p>키를 이 파일에 모아 둔 이유: 문자열을 화면마다 손으로 쓰면 저장하는 쪽과 읽는 쪽이
 * 언젠가 어긋나고, 그때 증상은 "로그인이 그냥 안 됨"이라 원인을 찾기 어렵다.
 */

const STATE_KEY = 'hsg_oauth_state';
const SIGNUP_KEY = 'hsg_signup';

function safeSession(): Storage | null {
  // 시크릿 모드·저장소 차단 설정에서는 접근 자체가 예외를 던진다.
  try {
    if (typeof window === 'undefined') return null;
    return window.sessionStorage;
  } catch {
    return null;
  }
}

/**
 * 인가 요청 때 받은 state 를 보관한다.
 *
 * <p>콜백에서 이 값과 URL 의 state 를 대조하는 것이 인가코드 주입을 막는 실질적인
 * 방어선이다. 서버도 서명으로 검증하지만, 그것만으로는 "우리가 발급한 state"임은
 * 알아도 "이 브라우저가 시작한 요청"임은 알 수 없다.
 */
export const oauthState = {
  save(provider: OAuthProviderType, state: string) {
    safeSession()?.setItem(STATE_KEY, JSON.stringify({ provider, state }));
  },
  /** 한 번 쓰면 지운다. 재사용을 허용하면 대조의 의미가 없다. */
  take(provider: OAuthProviderType): string | null {
    const store = safeSession();
    const raw = store?.getItem(STATE_KEY);
    store?.removeItem(STATE_KEY);
    if (!raw) return null;
    try {
      const parsed = JSON.parse(raw) as { provider: string; state: string };
      return parsed.provider === provider ? parsed.state : null;
    } catch {
      return null;
    }
  },
};

/** 약관 동의 화면으로 넘길 가입 진행 정보. 서버 토큰(15분)이 실제 유효기간을 정한다. */
export interface PendingSignUp {
  signUpToken: string;
  agreements: AgreementItem[];
  suggestedName: string | null;
  suggestedEmail: string | null;
  /** 로그인 전에 있던 곳. 가입을 마치면 여기로 돌려보낸다. */
  next: string;
}

/**
 * 파싱 결과를 캐시한다.
 *
 * <p>{@link pendingSignUp.read} 는 {@code useSyncExternalStore} 의 스냅샷으로 쓰인다.
 * 호출할 때마다 새 객체를 만들면 React 가 "값이 바뀌었다"고 보고 무한히 다시 그린다.
 * 원문 문자열이 같으면 같은 참조를 돌려줘야 한다.
 */
let cachedRaw: string | null = null;
let cachedValue: PendingSignUp | null = null;

export const pendingSignUp = {
  save(value: PendingSignUp) {
    safeSession()?.setItem(SIGNUP_KEY, JSON.stringify(value));
  },
  read(): PendingSignUp | null {
    const raw = safeSession()?.getItem(SIGNUP_KEY) ?? null;
    if (raw === cachedRaw) return cachedValue;
    cachedRaw = raw;
    if (!raw) {
      cachedValue = null;
      return null;
    }
    try {
      cachedValue = JSON.parse(raw) as PendingSignUp;
    } catch {
      cachedValue = null;
    }
    return cachedValue;
  },
  clear() {
    safeSession()?.removeItem(SIGNUP_KEY);
    cachedRaw = null;
    cachedValue = null;
  },
  /**
   * {@code useSyncExternalStore} 용 구독.
   *
   * <p>이 값은 화면이 살아 있는 동안 바뀌지 않는다(콜백 화면에서 한 번 쓰고 넘어온다).
   * 그래서 구독은 아무것도 하지 않고, 해제 함수만 돌려준다.
   */
  subscribe(): () => void {
    return () => undefined;
  },
};

/** 로그인 후 돌아갈 곳. 열린 리다이렉트가 되지 않게 내부 경로만 허용한다. */
export function safeNext(raw: string | null | undefined): string {
  if (!raw) return '/';
  if (!raw.startsWith('/') || raw.startsWith('//')) return '/';
  if (raw.startsWith('/login') || raw.startsWith('/signup') || raw.startsWith('/auth/')) return '/';
  return raw;
}
