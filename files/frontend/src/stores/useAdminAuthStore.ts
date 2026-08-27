import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

import type { AdminRole, AdminUser } from '@/types/admin';
import { hasRole } from '@/types/admin';

/** SSR 용 빈 저장소. 아무것도 남기지 않지만 persist 가 저장소를 포기하지는 않게 한다. */
const memoryStorage: Storage = {
  length: 0,
  clear: () => undefined,
  getItem: () => null,
  key: () => null,
  removeItem: () => undefined,
  setItem: () => undefined,
};

/**
 * 관리자 인증 상태.
 *
 * <h3>토큰을 왜 sessionStorage 에 두는가</h3>
 * 백엔드는 토큰을 응답 본문으로 내려준다 — 쿠키가 아니다. 관리자 화면이 SPA 라
 * 쿠키로 실으면 CSRF 방어를 따로 붙여야 하기 때문이다. 그래서 보관은 클라이언트 몫이다.
 *
 * <p>localStorage 가 아니라 <b>sessionStorage</b> 를 쓴다. 탭을 닫으면 사라지므로
 * 공용 PC 에 세션이 남지 않는다. 대신 XSS 가 발생하면 토큰을 읽히는 것은 여전하다 —
 * 그 위험은 (1) 관리자 화면에 외부 스크립트를 넣지 않는 것, (2) 운영에서 /admin 을
 * IP 허용목록 뒤에 두는 것, (3) 토큰 세대(token_version)로 즉시 무효화할 수 있게 한
 * 것으로 막는다.
 *
 * <p>이 스토어는 <b>권한을 판정하지 않는다.</b> {@link canAccess} 는 메뉴를 감추기 위한
 * 것일 뿐이고, 실제 차단은 백엔드 {@code @PreAuthorize} 가 한다.
 */
interface AdminAuthState {
  accessToken: string | null;
  refreshToken: string | null;
  admin: AdminUser | null;
  /** sessionStorage 복구가 끝났는가. false 동안에는 "로그인 안 됨"으로 판단하면 안 된다. */
  hydrated: boolean;

  signIn: (payload: { accessToken: string; refreshToken: string; admin: AdminUser }) => void;
  /** 재발급된 Access 토큰만 교체한다 (Refresh 토큰은 회전하지 않는다). */
  setAccessToken: (accessToken: string) => void;
  setAdmin: (admin: AdminUser) => void;
  signOut: () => void;
  markHydrated: () => void;
}

export const useAdminAuthStore = create<AdminAuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      admin: null,
      hydrated: false,

      signIn: ({ accessToken, refreshToken, admin }) => set({ accessToken, refreshToken, admin }),
      setAccessToken: (accessToken) => set({ accessToken }),
      setAdmin: (admin) => set({ admin }),
      signOut: () => set({ accessToken: null, refreshToken: null, admin: null }),
      markHydrated: () => set({ hydrated: true }),
    }),
    {
      name: 'hanseongge-admin-auth',
      // 서버 렌더 중에는 sessionStorage 가 없다. 접근하면 persist 가 저장소를 통째로
      // 포기하고, 그러면 브라우저에서도 복구가 일어나지 않는다.
      storage: createJSONStorage(() =>
        typeof window === 'undefined' ? memoryStorage : window.sessionStorage,
      ),
      // 생성 시점에 자동 복구하지 않는다. 자동 복구는 create() 가 끝나기 전에 실행되어
      // 그 안에서 useAdminAuthStore 를 참조할 수 없다 — hydrated 를 켜지 못해 화면이
      // "세션을 확인하는 중"에서 멈춘다. 아래에서 스토어가 완성된 뒤 직접 호출한다.
      skipHydration: true,
      partialize: (state) => ({
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        admin: state.admin,
      }),
    },
  ),
);

if (typeof window !== 'undefined') {
  // 복구가 성공하든(저장값 있음) 비어 있든 실패하든 hydrated 는 반드시 켜져야 한다.
  // 켜지지 않으면 로그인 화면으로도, 관리자 화면으로도 넘어가지 못한다.
  void Promise.resolve(useAdminAuthStore.persist.rehydrate()).finally(() => {
    useAdminAuthStore.getState().markHydrated();
  });
}

/** 컴포넌트 밖(API 클라이언트 등)에서 토큰을 읽는다. */
export const adminAuth = {
  accessToken: () => useAdminAuthStore.getState().accessToken,
  refreshToken: () => useAdminAuthStore.getState().refreshToken,
  setAccessToken: (token: string) => useAdminAuthStore.getState().setAccessToken(token),
  signOut: () => useAdminAuthStore.getState().signOut(),
};

/**
 * 화면 노출용 권한 판정.
 *
 * ⚠️ 보안 경계가 아니다 — 메뉴를 숨길 뿐이다. 이것만 믿고 백엔드 권한을 생략하면 안 된다.
 */
export function canAccess(admin: AdminUser | null, minimum: AdminRole): boolean {
  return hasRole(admin?.role, minimum);
}
