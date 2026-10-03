'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect } from 'react';

import * as authApi from '@/api/auth';
import { useUserAuthStore } from '@/stores/useUserAuthStore';
import type {
  AgreementCode,
  OAuthProviderType,
  SignInResult,
  VerificationChannel,
} from '@/types/user';

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
 * 로그인 결과를 세션에 넣는다.
 *
 * <p>로그인이 끝나는 길이 여러 개다 — 비밀번호, 인증번호, 소셜, 가입 완료, 소셜 연결.
 * 각자 스토어에 넣으면 한 곳만 캐시를 비우지 않는 식으로 어긋난다. 그래서 여기 한 곳에 둔다.
 */
export function useApplySignIn() {
  const signIn = useUserAuthStore((s) => s.signIn);
  const queryClient = useQueryClient();
  return useCallback(
    (result: SignInResult | null | undefined) => {
      if (result?.accessToken && result.user) {
        signIn({ accessToken: result.accessToken, user: result.user });
        void queryClient.invalidateQueries({ queryKey: userKeys.all });
      }
    },
    [signIn, queryClient],
  );
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

// =====================================================================
// 로그인
// =====================================================================

export function useSignInWithPassword() {
  const apply = useApplySignIn();
  return useMutation({
    mutationFn: ({ phone, password }: { phone: string; password: string }) =>
      authApi.signInWithPassword(phone, password),
    onSuccess: apply,
  });
}

export function useSendLoginCode() {
  return useMutation({
    mutationFn: ({ channel, target }: { channel: VerificationChannel; target: string }) =>
      authApi.sendLoginCode(channel, target),
  });
}

export function useSignInWithCode() {
  const apply = useApplySignIn();
  return useMutation({
    mutationFn: ({ channel, target, code }: { channel: VerificationChannel; target: string; code: string }) =>
      authApi.signInWithCode(channel, target, code),
    onSuccess: apply,
  });
}

export function useStartOAuth() {
  return useMutation({ mutationFn: (provider: OAuthProviderType) => authApi.startOAuth(provider) });
}

// =====================================================================
// 가입
// =====================================================================

/** 자체(휴대폰) 가입 시작 — 약관 동의. 다음은 간편인증이다. */
export function useStartSignUp() {
  return useMutation({
    mutationFn: (agreements: authApi.AgreementChoice[]) => authApi.startSignUp(agreements),
  });
}

/** 소셜 가입 — 약관 동의로 끝나고 로그인된다. */
export function useSignUpSocial() {
  const apply = useApplySignIn();
  return useMutation({
    mutationFn: ({
      signUpToken,
      agreements,
    }: {
      signUpToken: string;
      agreements: authApi.AgreementChoice[];
    }) => authApi.signUpSocial(signUpToken, agreements),
    onSuccess: apply,
  });
}

export function useStartIdentity() {
  return useMutation({ mutationFn: (signUpToken: string) => authApi.startIdentity(signUpToken) });
}

export function useSendSignUpEmailCode() {
  return useMutation({
    mutationFn: ({ signUpToken, email }: { signUpToken: string; email: string }) =>
      authApi.sendSignUpEmailCode(signUpToken, email),
  });
}

export function useCompleteSignUp() {
  const apply = useApplySignIn();
  return useMutation({ mutationFn: authApi.completeSignUp, onSuccess: apply });
}

// =====================================================================
// 로그인한 회원의 본인인증
// =====================================================================

export function useStartMemberIdentity() {
  return useMutation({ mutationFn: () => authApi.startMemberIdentity() });
}

// =====================================================================
// 비밀번호 찾기
// =====================================================================

export function useSendPasswordResetCode() {
  return useMutation({
    mutationFn: ({ channel, target }: { channel: VerificationChannel; target: string }) =>
      authApi.sendPasswordResetCode(channel, target),
  });
}

export function useVerifyPasswordResetCode() {
  return useMutation({
    mutationFn: ({ channel, target, code }: { channel: VerificationChannel; target: string; code: string }) =>
      authApi.verifyPasswordResetCode(channel, target, code),
  });
}

export function useStartPasswordReset() {
  return useMutation({ mutationFn: () => authApi.startPasswordReset() });
}

export function useResetPassword() {
  return useMutation({
    mutationFn: ({ resetToken, newPassword }: { resetToken: string; newPassword: string }) =>
      authApi.resetPassword(resetToken, newPassword),
  });
}

// =====================================================================
// 세션 종료
// =====================================================================

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

export function useChangePassword() {
  return useMutation({
    mutationFn: ({ newPassword, currentPassword }: { newPassword: string; currentPassword?: string }) =>
      authApi.changePassword(newPassword, currentPassword),
  });
}

export function useSendEmailCode() {
  return useMutation({ mutationFn: (email: string) => authApi.sendEmailCode(email) });
}

export function useChangeEmail() {
  return useMutation({
    mutationFn: ({ email, code }: { email: string; code: string }) => authApi.changeEmail(email, code),
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
