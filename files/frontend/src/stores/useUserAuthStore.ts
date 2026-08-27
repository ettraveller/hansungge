import { create } from 'zustand';

import type { User } from '@/types/user';

/**
 * 회원 인증 상태.
 *
 * <h3>Access 토큰을 어디에도 저장하지 않는다</h3>
 * localStorage 도 sessionStorage 도 쓰지 않고 <b>메모리에만</b> 둔다. 새로고침하면
 * 사라지지만, 그때는 HttpOnly Refresh 쿠키로 다시 받아온다 ({@code /api/v1/auth/refresh}).
 *
 * 그것이 이 구조의 요점이다 — 장기 세션(14일)은 스크립트가 읽을 수 없는 쿠키에 있고,
 * 스크립트가 만질 수 있는 값은 30분짜리 Access 토큰뿐이다. 둘 다 저장소에 두면
 * HttpOnly 쿠키를 쓴 의미가 사라진다.
 *
 * <h3>그래서 관리자 스토어와 다르다</h3>
 * 관리자는 토큰을 sessionStorage 에 둔다. 관리자 API 는 쿠키를 쓰지 않기 때문이고,
 * /admin 은 운영에서 IP 허용목록 뒤에 있다. 회원 쪽은 불특정 다수가 쓰는 화면이라
 * 저장소에 남기지 않는 편을 택했다.
 */
interface UserAuthState {
  accessToken: string | null;
  user: User | null;
  /**
   * 최초 세션 복구(refresh 시도)가 끝났는가.
   *
   * false 인 동안에는 "로그인 안 됨"으로 판단하면 안 된다 — 판단하면 새로고침할 때마다
   * 로그인 화면이 한 번 번쩍이고, 보호된 화면이 잠깐 빈 채로 그려진다.
   */
  restored: boolean;

  signIn: (payload: { accessToken: string; user: User }) => void;
  setAccessToken: (accessToken: string) => void;
  setUser: (user: User) => void;
  signOut: () => void;
  markRestored: () => void;
}

export const useUserAuthStore = create<UserAuthState>((set) => ({
  accessToken: null,
  user: null,
  restored: false,

  signIn: ({ accessToken, user }) => set({ accessToken, user }),
  setAccessToken: (accessToken) => set({ accessToken }),
  setUser: (user) => set({ user }),
  signOut: () => set({ accessToken: null, user: null }),
  markRestored: () => set({ restored: true }),
}));

/** 컴포넌트 밖(API 클라이언트)에서 토큰을 다룬다. */
export const userAuth = {
  accessToken: () => useUserAuthStore.getState().accessToken,
  setAccessToken: (token: string) => useUserAuthStore.getState().setAccessToken(token),
  signOut: () => useUserAuthStore.getState().signOut(),
};
