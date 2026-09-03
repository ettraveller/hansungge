'use client';

import { useRouter, useSearchParams } from 'next/navigation';
import { useEffect, useState } from 'react';

import { Alert } from '@/components/admin/Alert';
import { Button } from '@/components/admin/Button';
import { Field, TextInput } from '@/components/admin/Field';
import { useAdminLogin, useAdminSession } from '@/hooks/admin/useAdminSession';
import { ApiError } from '@/types/api';
import styles from './AdminLoginView.module.css';

/** 열린 리다이렉트 방지 — 우리 관리자 경로가 아니면 대시보드로 보낸다. */
function safeNext(raw: string | null): string {
  if (!raw) return '/admin';
  if (!raw.startsWith('/admin')) return '/admin';
  if (raw.startsWith('//')) return '/admin';
  if (raw.startsWith('/admin/login')) return '/admin';
  return raw;
}

/**
 * 실패 메시지.
 *
 * 아이디가 틀렸는지 비밀번호가 틀렸는지 구분해서 보여주지 않는다 — 백엔드도 같은
 * 문구를 준다. 잠김(423)만은 구분해야 관리자가 무엇을 해야 하는지 알 수 있다.
 */
function messageOf(error: unknown): { tone: 'danger' | 'warning'; text: string } {
  if (error instanceof ApiError) {
    if (error.status === 423) return { tone: 'warning', text: error.message };
    if (error.status === 429) {
      return {
        tone: 'warning',
        text: '로그인 시도가 너무 잦습니다. 잠시 후 다시 시도해 주세요.',
      };
    }
    return { tone: 'danger', text: error.message };
  }
  return { tone: 'danger', text: '로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.' };
}

export function AdminLoginView() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const next = safeNext(searchParams.get('next'));

  const { isAuthenticated, hydrated } = useAdminSession();
  const login = useAdminLogin();

  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');

  // 이미 로그인된 상태로 로그인 화면에 오면 되돌려 보낸다.
  useEffect(() => {
    if (hydrated && isAuthenticated) router.replace(next);
  }, [hydrated, isAuthenticated, next, router]);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!loginId.trim() || !password) return;
    login.mutate(
      { loginId: loginId.trim(), password },
      { onSuccess: () => router.replace(next) },
    );
  };

  const failure = login.error ? messageOf(login.error) : null;

  return (
    <div className={styles.root}>
      <form className={styles.card} onSubmit={submit}>
        <div className={styles.brand}>
          한성금거래소
          <span className={styles.badge}>ADMIN</span>
        </div>
        <p className={styles.lead}>
          관리자 계정으로 로그인합니다. 연속 5회 실패하면 계정이 잠깁니다.
        </p>

        {failure && <Alert tone={failure.tone}>{failure.text}</Alert>}

        <Field label="아이디" htmlFor="login-id" required>
          <TextInput
            id="login-id"
            name="username"
            autoComplete="username"
            autoFocus
            value={loginId}
            onChange={(e) => setLoginId(e.target.value)}
          />
        </Field>

        <Field label="비밀번호" htmlFor="login-password" required>
          <TextInput
            id="login-password"
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </Field>

        <Button
          type="submit"
          variant="gold"
          disabled={login.isPending || !loginId.trim() || !password}
        >
          {login.isPending ? '로그인 중…' : '로그인'}
        </Button>

        <p className={styles.foot}>
          이 화면은 검색엔진에 노출되지 않으며, 모든 로그인 시도가 감사로그에 기록됩니다.
        </p>
      </form>
    </div>
  );
}
