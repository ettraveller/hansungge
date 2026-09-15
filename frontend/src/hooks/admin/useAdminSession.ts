'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useCallback } from 'react';

import * as authApi from '@/api/admin/auth';
import { useAdminAuthStore } from '@/stores/useAdminAuthStore';
import type { AdminLoginResponse, AdminRole } from '@/types/admin';
import { hasRole } from '@/types/admin';
import { adminKeys } from './keys';

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

/**
 * 토큰이 든 로그인 응답으로 세션을 연다.
 *
 * <p>로그인 응답을 받자마자 열지 않는 이유: 첫 등록 직후에는 복구 코드를 먼저 보여줘야 한다.
 * 세션이 열리면 로그인 화면이 곧바로 대시보드로 넘어가 코드를 볼 기회가 사라진다.
 *
 * @return 세션을 열었으면 true. 토큰이 없는 응답(아직 OTP 단계)이면 false
 */
export function useCompleteAdminSignIn() {
  const signIn = useAdminAuthStore((s) => s.signIn);
  const queryClient = useQueryClient();

  return useCallback(
    (res: AdminLoginResponse) => {
      if (!res.accessToken || !res.refreshToken || !res.admin) return false;
      signIn({ accessToken: res.accessToken, refreshToken: res.refreshToken, admin: res.admin });
      // 이전 사용자의 캐시가 남아 있으면 안 된다. 권한이 다른 계정이면 보이면 안 될 데이터다.
      queryClient.clear();
      return true;
    },
    [signIn, queryClient],
  );
}

/** 비밀번호 확인. 응답의 step 으로 다음 단계를 정한다 — 세션은 열지 않는다. */
export function useAdminLogin() {
  return useMutation({
    mutationFn: ({ loginId, password }: { loginId: string; password: string }) =>
      authApi.login(loginId, password),
  });
}

export function useOtpVerify() {
  return useMutation({
    mutationFn: ({ stepToken, code }: { stepToken: string; code: string }) =>
      authApi.verifyOtp(stepToken, code),
  });
}

export function useOtpLoginSetup() {
  return useMutation({
    mutationFn: (stepToken: string) => authApi.beginOtpSetupDuringLogin(stepToken),
  });
}

export function useOtpLoginActivate() {
  return useMutation({
    mutationFn: ({ stepToken, code }: { stepToken: string; code: string }) =>
      authApi.activateOtpDuringLogin(stepToken, code),
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

// ---------- 내 OTP ----------

export function useMyOtpStatus() {
  return useQuery({
    queryKey: adminKeys.otpStatus(),
    queryFn: authApi.myOtpStatus,
  });
}

export function useStartMyOtpSetup() {
  return useMutation({ mutationFn: authApi.beginMyOtpSetup });
}

export function useActivateMyOtp() {
  const queryClient = useQueryClient();
  const setAdmin = useAdminAuthStore((s) => s.setAdmin);

  return useMutation({
    mutationFn: (code: string) => authApi.activateMyOtp(code),
    onSuccess: async () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.otpStatus() });
      // 화면 곳곳의 "OTP 미등록" 표시가 남지 않게 내 정보도 새로 받는다.
      try {
        setAdmin(await authApi.me());
      } catch {
        // 내 정보 갱신 실패는 등록 결과와 무관하다.
      }
    },
  });
}

export function useRegenerateRecoveryCodes() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (code: string) => authApi.regenerateRecoveryCodes(code),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.otpStatus() });
    },
  });
}
