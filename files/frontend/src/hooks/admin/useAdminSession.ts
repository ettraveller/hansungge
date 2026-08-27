'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';

import * as authApi from '@/api/admin/auth';
import { useAdminAuthStore } from '@/stores/useAdminAuthStore';
import type { AdminRole } from '@/types/admin';
import { hasRole } from '@/types/admin';

/** 현재 로그인 상태. hydrated 가 false 인 동안에는 판단을 미뤄야 한다. */
export function useAdminSession() {
  const admin = useAdminAuthStore((s) => s.admin);
  const accessToken = useAdminAuthStore((s) => s.accessToken);
  const hydrated = useAdminAuthStore((s) => s.hydrated);

  return {
    admin,
    hydrated,
    isAuthenticated: hydrated && accessToken != null && admin != null,
    /** 화면 노출용 판정. 실제 차단은 백엔드가 한다. */
    can: (minimum: AdminRole) => hasRole(admin?.role, minimum),
  };
}

export function useAdminLogin() {
  const signIn = useAdminAuthStore((s) => s.signIn);
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ loginId, password }: { loginId: string; password: string }) =>
      authApi.login(loginId, password),
    onSuccess: (res) => {
      signIn({ accessToken: res.accessToken, refreshToken: res.refreshToken, admin: res.admin });
      // 이전 사용자의 캐시가 남아 있으면 안 된다. 권한이 다른 계정이면 보이면 안 될 데이터다.
      queryClient.clear();
    },
  });
}

export function useAdminLogout() {
  const signOut = useAdminAuthStore((s) => s.signOut);
  const queryClient = useQueryClient();
  const router = useRouter();

  return useMutation({
    mutationFn: async () => {
      // 서버 호출이 실패해도(이미 만료 등) 로컬 세션은 반드시 정리한다.
      // 여기서 예외가 새면 "로그아웃 버튼이 안 먹는" 상태가 된다.
      try {
        await authApi.logout();
      } catch {
        // 무시한다 — 아래에서 어차피 토큰을 버린다.
      }
    },
    onSettled: () => {
      signOut();
      queryClient.clear();
      router.replace('/admin/login');
    },
  });
}

export function useChangeMyPassword() {
  return useMutation({
    mutationFn: ({
      currentPassword,
      newPassword,
    }: {
      currentPassword: string;
      newPassword: string;
    }) => authApi.changeMyPassword(currentPassword, newPassword),
  });
}
