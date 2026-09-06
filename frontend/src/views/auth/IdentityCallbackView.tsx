'use client';

import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';

import { Notice } from '@/components/auth/Notice';
import { useUserAuthStore } from '@/stores/useUserAuthStore';
import * as authApi from '@/api/auth';
import { pendingIdentity } from '@/utils/identityReturn';
import { pendingSignUp } from '@/utils/authStorage';
import { ApiError } from '@/types/api';
import styles from '@/components/auth/auth.module.css';

/**
 * 본인확인을 마치고 돌아오는 자리 (`/auth/identity`).
 *
 * <h2>왜 useQuery 인가</h2>
 * 화면에 들어온 것 자체가 트리거다. 버튼을 누를 것이 없다. 공급자 토큰을 queryKey 에
 * 넣으면 <b>같은 결과로 두 번 제출되지 않는다</b> — 소셜 콜백 화면이 같은 이유로 같은
 * 방식을 쓴다. 두 번 제출되면 두 번째는 "이미 사용된 본인확인"으로 실패하고, 사용자는
 * 성공했는데 실패 화면을 본다.
 *
 * <h2>여기서 끝날 수도 있다</h2>
 * 이미 가입한 신원이면 서버가 <b>그 자리에서 로그인시킨다</b>. 약관 화면으로 보내지
 * 않는다 — 이미 회원인 사람에게 약관을 다시 받을 이유가 없다.
 */
export function IdentityCallbackView() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const signIn = useUserAuthStore((s) => s.signIn);

  const providerToken = searchParams.get('providerToken');

  const { isError, error } = useQuery({
    queryKey: ['identity-callback', providerToken],
    retry: false,
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    queryFn: async () => {
      const pending = pendingIdentity.take();
      if (!pending) {
        throw new CallbackError(
          '진행 중인 가입 정보가 없습니다',
          '창을 새로 열었거나 시간이 오래 지났습니다. 처음부터 다시 진행해 주세요.',
        );
      }
      if (!providerToken) {
        throw new CallbackError(
          '본인확인 결과를 받지 못했습니다',
          '본인확인 창에서 취소하셨거나 인증이 완료되지 않았습니다.',
        );
      }

      const result = await authApi.completeIdentity(pending.signUpToken, providerToken);

      // 이미 가입한 신원 — 서버가 로그인 수단만 붙이고 들여보냈다.
      if (result.accessToken && result.user) {
        signIn({ accessToken: result.accessToken, user: result.user });
        router.replace(pending.next);
        return result;
      }

      // 처음 보는 신원 — 약관 화면으로 넘긴다.
      pendingSignUp.save({
        signUpToken: result.signUpToken ?? '',
        agreements: result.agreements ?? [],
        suggestedName: result.suggestedName ?? null,
        suggestedEmail: result.suggestedEmail ?? null,
        next: pending.next,
      });
      router.replace('/signup');
      return result;
    },
  });

  if (isError) {
    const title = error instanceof CallbackError ? error.title : '본인확인을 마치지 못했습니다';
    const detail =
      error instanceof CallbackError
        ? error.detail
        : error instanceof ApiError
          ? error.message
          : '잠시 후 다시 시도해 주세요.';

    return (
      <div className={styles.page}>
        <div className={styles.card}>
          <Notice tone="error" title={title}>
            {detail}
          </Notice>
          <p className={styles.foot}>
            <Link href="/signup/email">회원가입 다시 시작</Link>
            {' · '}
            <Link href="/login">로그인</Link>
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <div className={styles.card}>
        <p className={styles.loading}>본인확인 결과를 확인하는 중…</p>
      </div>
    </div>
  );
}

/** 제목과 설명을 나눠 담는다. 한 줄 메시지로는 무엇을 해야 하는지 전할 수 없다. */
class CallbackError extends Error {
  readonly title: string;
  readonly detail: string;

  constructor(title: string, detail: string) {
    super(title);
    this.title = title;
    this.detail = detail;
  }
}
