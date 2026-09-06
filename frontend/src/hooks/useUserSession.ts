'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';

import * as authApi from '@/api/auth';
import { useUserAuthStore } from '@/stores/useUserAuthStore';
import type { AgreementCode, OAuthProviderType } from '@/types/user';

export const userKeys = {
  all: ['user'] as const,
  providers: () => [...userKeys.all, 'providers'] as const,
  me: () => [...userKeys.all, 'me'] as const,
  agreements: () => [...userKeys.all, 'agreements'] as const,
  socialAccounts: () => [...userKeys.all, 'social-accounts'] as const,
};

/** 현재 로그인 상태. restored 가 false 인 동안에는 판단을 미뤄야 한다. */
export function useUserSession() {
  const user = useUserAuthStore((s) => s.user);
  const accessToken = useUserAuthStore((s) => s.accessToken);
  const restored = useUserAuthStore((s) => s.restored);

  return {
    user,
    restored,
    isAuthenticated: restored && accessToken != null && user != null,
  };
}

/**
 * 새로고침 후 세션 복구.
 *
 * <p>Access 토큰은 메모리에만 있어 새로고침하면 사라진다. HttpOnly Refresh 쿠키로
 * 다시 받아오는 것이 이 훅이다 — 앱 최상단에서 딱 한 번 실행한다.
 *
 * <p>실패는 오류가 아니다. "쿠키가 없다 = 로그인하지 않은 방문자"일 뿐이라 조용히
 * 넘긴다. 여기서 오류를 띄우면 처음 온 손님에게 매번 경고가 뜬다.
 */
export function useRestoreSession() {
  const signIn = useUserAuthStore((s) => s.signIn);
  const markRestored = useUserAuthStore((s) => s.markRestored);
  const restored = useUserAuthStore((s) => s.restored);

  useEffect(() => {
    if (restored) return;
    let cancelled = false;

    authApi
      .refreshSession()
      .then((res) => {
        if (cancelled || !res.accessToken || !res.user) return;
        signIn({ accessToken: res.accessToken, user: res.user });
      })
      .catch(() => {
        // 비로그인 방문자의 정상 경로다.
      })
      .finally(() => {
        if (!cancelled) markRestored();
      });

    return () => {
      cancelled = true;
    };
  }, [restored, signIn, markRestored]);
}

/** 로그인 화면 구성. 쓸 수 있는 제공자가 없으면 서버가 빈 배열을 준다. */
export function useAuthProviders() {
  return useQuery({
    queryKey: userKeys.providers(),
    queryFn: authApi.fetchAuthProviders,
    staleTime: 5 * 60_000,
  });
}

export function useStartOAuth() {
  return useMutation({ mutationFn: (provider: OAuthProviderType) => authApi.startOAuth(provider) });
}

export function useCompleteOAuth() {
  return useMutation({
    mutationFn: ({
      provider,
      code,
      state,
    }: {
      provider: OAuthProviderType;
      code: string;
      state: string;
    }) => authApi.completeOAuth(provider, code, state),
  });
}

export function useCompleteSignUp() {
  const signIn = useUserAuthStore((s) => s.signIn);
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: authApi.completeSignUp,
    onSuccess: (res) => {
      if (res.accessToken && res.user) {
        signIn({ accessToken: res.accessToken, user: res.user });
      }
      void queryClient.invalidateQueries({ queryKey: userKeys.all });
    },
  });
}

/* ------------------------------------------------------------------ */
/* 자체 가입 — 이메일 인증 → 본인확인 → 약관                            */
/* ------------------------------------------------------------------ */

export function useSendEmailCode() {
  return useMutation({ mutationFn: (email: string) => authApi.sendEmailCode(email) });
}

export function useConfirmEmailCode() {
  return useMutation({
    mutationFn: ({ email, code }: { email: string; code: string }) =>
      authApi.confirmEmailCode(email, code),
  });
}

export function useStartIdentity() {
  return useMutation({
    mutationFn: ({ signUpToken, returnUrl }: { signUpToken: string; returnUrl: string }) =>
      authApi.startIdentity(signUpToken, returnUrl),
  });
}

/**
 * 본인확인 결과 제출.
 *
 * <p>이미 가입한 신원이면 여기서 <b>로그인이 끝난다</b>. 그래서 성공 응답에 토큰이
 * 있으면 세션에 넣는다 — 화면이 그것을 잊으면 로그인은 됐는데 로그인 안 된 것처럼
 * 보인다.
 */
export function useCompleteIdentity() {
  const signIn = useUserAuthStore((s) => s.signIn);
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      signUpToken,
      providerToken,
      password,
    }: {
      signUpToken: string;
      providerToken: string;
      password?: string;
    }) => authApi.completeIdentity(signUpToken, providerToken, password),
    onSuccess: (result) => {
      if (result.accessToken && result.user) {
        signIn({ accessToken: result.accessToken, user: result.user });
        void queryClient.invalidateQueries({ queryKey: userKeys.all });
      }
    },
  });
}

export function useSignInWithPassword() {
  const signIn = useUserAuthStore((s) => s.signIn);
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ email, password }: { email: string; password: string }) =>
      authApi.signInWithPassword(email, password),
    onSuccess: (result) => {
      if (result.accessToken && result.user) {
        signIn({ accessToken: result.accessToken, user: result.user });
        void queryClient.invalidateQueries({ queryKey: userKeys.all });
      }
    },
  });
}

export function useSignOut() {
  const signOut = useUserAuthStore((s) => s.signOut);
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      // 서버 호출이 실패해도 로컬 세션은 반드시 정리한다.
      // 여기서 예외가 새면 "로그아웃 버튼이 안 먹는" 상태가 된다.
      try {
        await authApi.signOut();
      } catch {
        // 무시한다 — 아래에서 어차피 토큰을 버린다.
      }
    },
    onSettled: () => {
      signOut();
      queryClient.removeQueries({ queryKey: userKeys.all });
    },
  });
}

// =====================================================================
// 마이페이지
// =====================================================================

export function useMe(enabled: boolean) {
  return useQuery({ queryKey: userKeys.me(), queryFn: authApi.fetchMe, enabled });
}

export function useUpdateProfile() {
  const setUser = useUserAuthStore((s) => s.setUser);
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: authApi.updateProfile,
    onSuccess: (user) => {
      // 헤더에 이름이 걸려 있어 스토어도 함께 갱신한다.
      setUser(user);
      queryClient.setQueryData(userKeys.me(), user);
    },
  });
}

export function useAgreements(enabled: boolean) {
  return useQuery({
    queryKey: userKeys.agreements(),
    queryFn: authApi.fetchAgreements,
    enabled,
  });
}

export function useChangeAgreement() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ type, agreed }: { type: AgreementCode; agreed: boolean }) =>
      authApi.changeAgreement(type, agreed),
    onSuccess: (list) => queryClient.setQueryData(userKeys.agreements(), list),
  });
}

export function useSocialAccounts(enabled: boolean) {
  return useQuery({
    queryKey: userKeys.socialAccounts(),
    queryFn: authApi.fetchSocialAccounts,
    enabled,
  });
}

export function useDisconnectSocial() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (provider: OAuthProviderType) => authApi.disconnectSocial(provider),
    onSuccess: (list) => queryClient.setQueryData(userKeys.socialAccounts(), list),
  });
}

export function useWithdraw() {
  const signOut = useUserAuthStore((s) => s.signOut);
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: authApi.withdraw,
    onSuccess: () => {
      signOut();
      queryClient.removeQueries({ queryKey: userKeys.all });
    },
  });
}
