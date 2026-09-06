'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState, useSyncExternalStore } from 'react';

import { AgreementChecklist } from '@/components/auth/AgreementChecklist';
import { Notice } from '@/components/auth/Notice';
import { useCompleteSignUp } from '@/hooks/useUserSession';
import { pendingSignUp, type PendingSignUp } from '@/utils/authStorage';
import { ApiError } from '@/types/api';
import type { AgreementCode } from '@/types/user';
import styles from '@/components/auth/auth.module.css';

/**
 * 약관 동의 후 가입 완료.
 *
 * <p>이 화면에 도달했다는 것은 <b>소셜 인증은 끝났지만 아직 회원이 아니라는</b> 뜻이다.
 * 서버는 계정을 만들지 않은 채 신원을 담은 짧은 토큰(15분)만 발급해 두었고,
 * 여기서 필수 약관에 동의하는 순간 회원·소셜연동·동의이력이 한 번에 생성된다.
 *
 * <p>순서가 반대였다면 — 계정을 먼저 만들고 동의를 나중에 받았다면 — 이 화면에서
 * 이탈한 사람의 개인정보를 동의 없이 보관하게 된다.
 */
export function SignUpView() {
  const router = useRouter();
  const signUp = useCompleteSignUp();

  /**
   * sessionStorage 는 브라우저에만 있다. effect 로 읽어 상태에 넣으면 렌더가 한 번 더
   * 도는 데다, "서버가 그린 화면"과 "복구된 화면"을 구분할 수 없다.
   *
   * <p>{@code useSyncExternalStore} 는 이 상황을 위한 도구다. 서버·하이드레이션
   * 단계에서는 {@code undefined}(= 아직 모름)를, 그 뒤에는 실제 값을 돌려주므로
   * "불러오는 중 → 있음/없음" 순서가 자연스럽게 나온다.
   */
  const pending = useSyncExternalStore<PendingSignUp | null | undefined>(
    pendingSignUp.subscribe,
    pendingSignUp.read,
    () => undefined,
  );

  const [checked, setChecked] = useState<Record<string, boolean>>({});
  /**
   * 이름은 고를 수 없다.
   *
   * <p>본인확인기관이 준 실명이라 회원이 바꿀 수 있으면 안 된다 — 바꿀 수 있으면
   * "확인된 이름"이 아니라 "한 번 확인했다가 지금은 모르는 이름"이 되고, 주문서와
   * 매입 접수가 그 값 위에 선다. 보여주기만 한다.
   */
  const name = pending?.suggestedName ?? '';

  if (pending === undefined) {
    return (
      <div className={styles.page}>
        <p className={styles.loading}>불러오는 중…</p>
      </div>
    );
  }

  // 새로고침·직접 접근·토큰 만료 — 어느 쪽이든 처음부터 다시 하는 수밖에 없다.
  if (pending === null) {
    return (
      <div className={styles.page}>
        <header className={styles.head}>
          <h1 className={styles.title}>회원가입</h1>
        </header>
        <div className={styles.card}>
          <Notice tone="warn" title="진행 중인 가입 정보가 없습니다">
            소셜 로그인을 먼저 거쳐야 약관 동의 화면으로 올 수 있습니다.
          </Notice>
          <div style={{ marginTop: 18 }}>
            <Link
              className={styles.primary}
              href="/login"
              style={{ display: 'block', textAlign: 'center', lineHeight: '48px' }}
            >
              로그인 화면으로
            </Link>
          </div>
        </div>
      </div>
    );
  }

  const missingRequired = pending.agreements
    .filter((a) => a.required && !checked[a.code])
    .map((a) => a.name);
  const canSubmit = missingRequired.length === 0 && !signUp.isPending;

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    signUp.mutate(
      {
        signUpToken: pending.signUpToken,
        // 체크하지 않은 선택 약관도 false 로 함께 보낸다 —
        // "묻지 않은 것"과 "거절한 것"은 나중에 구분되어야 한다.
        agreements: pending.agreements.map((a) => ({
          type: a.code as AgreementCode,
          agreed: Boolean(checked[a.code]),
        })),
      },
      {
        onSuccess: () => {
          pendingSignUp.clear();
          router.replace(pending.next);
        },
      },
    );
  };

  const error = signUp.error;
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : undefined;

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <h1 className={styles.title}>회원가입</h1>
        <p className={styles.lead}>
          약관에 동의하시면 가입이 완료됩니다.
          <br />
          동의 전까지는 어떤 정보도 저장되지 않습니다.
        </p>
      </header>

      <form className={styles.card} onSubmit={submit}>
        {error && (
          <div style={{ marginBottom: 16 }}>
            <Notice tone="error" title="가입하지 못했습니다">
              {error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요.'}
              {fieldErrors && (
                <ul style={{ marginTop: 6, paddingLeft: 16 }}>
                  {Object.entries(fieldErrors).map(([key, message]) => (
                    <li key={key}>{message}</li>
                  ))}
                </ul>
              )}
            </Notice>
          </div>
        )}

        {pending.suggestedEmail && (
          <div className={styles.field}>
            <span className={styles.label}>연결된 계정</span>
            <input className={styles.input} value={pending.suggestedEmail} disabled readOnly />
            <span className={styles.hint}>소셜 계정에서 받아온 이메일입니다.</span>
          </div>
        )}

        <div className={styles.field}>
          <label className={styles.label} htmlFor="signup-name">
            이름
          </label>
          <input id="signup-name" className={styles.input} value={name} disabled readOnly />
          <span className={styles.hint}>본인확인으로 확인된 이름입니다. 바꿀 수 없습니다.</span>
        </div>

        <h2 className={styles.sectionTitle}>약관 동의</h2>
        <p className={styles.sectionNote}>
          필수 항목에 모두 동의해야 가입할 수 있습니다. 선택 항목은 나중에 마이페이지에서
          바꿀 수 있습니다.
        </p>

        <AgreementChecklist
          agreements={pending.agreements}
          checked={checked}
          disabled={signUp.isPending}
          onChange={setChecked}
        />

        <div style={{ marginTop: 20 }}>
          <button type="submit" className={styles.primary} disabled={!canSubmit}>
            {signUp.isPending ? '가입 처리 중…' : '동의하고 가입하기'}
          </button>
          {missingRequired.length > 0 && (
            <p className={styles.error} style={{ marginTop: 8 }}>
              필수 항목이 남았습니다: {missingRequired.join(', ')}
            </p>
          )}
        </div>

        <p className={styles.foot}>
          만 14세 미만은 가입할 수 없습니다.
          <br />
          가입을 원하지 않으시면 이 화면을 닫으면 됩니다 — 저장된 정보가 없습니다.
        </p>
      </form>
    </div>
  );
}
