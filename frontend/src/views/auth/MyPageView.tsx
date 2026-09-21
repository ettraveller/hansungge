'use client';

import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useEffect, useState } from 'react';

import { Notice } from '@/components/auth/Notice';
import { PasswordInput } from '@/components/ui/PasswordInput';
import {
  useAgreements,
  useAuthProviders,
  useChangeAgreement,
  useChangePassword,
  useDisconnectSocial,
  useSignOut,
  useSocialAccounts,
  useStartOAuth,
  useUserSession,
  useWithdraw,
} from '@/hooks/useUserSession';
import { useUserAuthStore } from '@/stores/useUserAuthStore';
import { oauthMode, oauthState } from '@/utils/authStorage';
import { formatDateTime, formatPhone } from '@/utils/format';
import { ApiError } from '@/types/api';
import { USER_STATUS_LABEL, type OAuthProviderType } from '@/types/user';
import { MyInquiryPanel } from './MyInquiryPanel';
import styles from '@/components/auth/auth.module.css';

/** 마이페이지 — 내 정보 · 비밀번호 · 약관 · 소셜 연동 · 탈퇴. */
export function MyPageView() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { user, restored, isAuthenticated } = useUserSession();

  useEffect(() => {
    if (restored && !isAuthenticated) {
      router.replace('/login?reason=required&next=%2Fmypage');
    }
  }, [restored, isAuthenticated, router]);

  if (!restored) {
    return <p className={styles.loading}>확인 중…</p>;
  }
  if (!isAuthenticated || !user) {
    return <p className={styles.loading}>로그인 화면으로 이동합니다…</p>;
  }

  return (
    <div className={`${styles.page} ${styles.wide}`}>
      <header className={styles.head}>
        <h1 className={styles.title}>마이페이지</h1>
        <p className={styles.lead}>{user.name ?? '고객'}님, 안녕하세요.</p>
      </header>

      {searchParams.get('linked') === '1' && (
        <div style={{ marginBottom: 16 }}>
          <Notice tone="info">소셜 계정을 연결했습니다. 다음부터 그 계정으로도 로그인됩니다.</Notice>
        </div>
      )}

      <ProfilePanel />
      <PasswordPanel />
      {/* 내가 쓴 글은 게시판이 아니라 내 계정에 속한 정보다 — 비밀글로 쓴 문의는
          목록에서 제목만 보이므로 답변이 왔는지 확인할 곳이 따로 있어야 한다 */}
      <MyInquiryPanel />
      <AgreementPanel />
      <SocialPanel />
      <AccountPanel />
    </div>
  );
}

/**
 * 내 정보 — 읽기 전용.
 *
 * <p>이름·휴대폰은 본인확인으로 확정된 값이라 여기서 고칠 수 없다. 고칠 수 있으면 "확인된 값"이
 * 아니라 "한 번 확인했다가 지금은 모르는 값"이 되고, 매입 대금의 예금주 대조가 그 위에 선다.
 * 번호를 바꿨으면 비밀번호 찾기에서 본인확인을 다시 받으면 새 번호로 맞춰진다.
 */
function ProfilePanel() {
  const { user } = useUserSession();

  return (
    <section className={styles.panel}>
      <header className={styles.panelHead}>
        <h2 className={styles.sectionTitle}>내 정보</h2>
        <p className={styles.sectionNote}>
          이름·휴대폰은 본인확인으로 확정된 값입니다. 휴대폰을 바꾸셨다면{' '}
          <Link href="/password/reset">본인확인</Link>을 다시 받아 주세요.
        </p>
      </header>
      <div className={styles.panelBody}>
        <div className={styles.rows}>
          <div className={styles.row}>
            <span className={styles.rowLabel}>이름</span>
            <span className={styles.rowValue}>{user?.name ?? '-'}</span>
          </div>
          <div className={styles.row}>
            <span className={styles.rowLabel}>휴대폰 (로그인 아이디)</span>
            <span className={styles.rowValue}>{formatPhone(user?.phone)}</span>
          </div>
          <div className={styles.row}>
            <span className={styles.rowLabel}>이메일</span>
            <span className={styles.rowValue}>{user?.email ?? '(등록하지 않음)'}</span>
          </div>
          <div className={styles.row}>
            <span className={styles.rowLabel}>상태</span>
            <span className={styles.rowValue}>{user ? USER_STATUS_LABEL[user.status] : '-'}</span>
          </div>
          <div className={styles.row}>
            <span className={styles.rowLabel}>가입일</span>
            <span className={styles.rowValue}>{formatDateTime(user?.createdAt)}</span>
          </div>
        </div>
      </div>
    </section>
  );
}

/**
 * 비밀번호 — 소셜로만 가입한 회원은 새로 정하고, 있으면 현재 비밀번호를 확인한 뒤 바꾼다.
 *
 * <p>정해 두면 휴대폰번호로도 로그인된다.
 */
function PasswordPanel() {
  const { user } = useUserSession();
  const setUser = useUserAuthStore((s) => s.setUser);
  const change = useChangePassword();
  const hasPassword = Boolean(user?.hasPassword);

  const [open, setOpen] = useState(false);
  const [current, setCurrent] = useState('');
  const [password, setPassword] = useState('');
  const [passwordAgain, setPasswordAgain] = useState('');
  const [done, setDone] = useState(false);

  const mismatch = passwordAgain !== '' && password !== passwordAgain;
  const canSubmit =
    password !== '' && password === passwordAgain && (!hasPassword || current !== '') && !change.isPending;

  const reset = () => {
    setOpen(false);
    setCurrent('');
    setPassword('');
    setPasswordAgain('');
    change.reset();
  };

  return (
    <section className={styles.panel}>
      <header className={styles.panelHead}>
        <h2 className={styles.sectionTitle}>비밀번호</h2>
        <p className={styles.sectionNote}>
          {hasPassword
            ? '휴대폰번호와 비밀번호로 로그인할 수 있습니다.'
            : '소셜 계정으로 가입하셔서 비밀번호가 없습니다. 정해 두면 휴대폰번호로도 로그인됩니다.'}
        </p>
      </header>
      <div className={styles.panelBody}>
        {done && !open && (
          <div style={{ marginBottom: 14 }}>
            <Notice tone="info">비밀번호를 저장했습니다.</Notice>
          </div>
        )}

        {open ? (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (!canSubmit) return;
              change.mutate(
                { newPassword: password, currentPassword: hasPassword ? current : undefined },
                {
                  onSuccess: () => {
                    if (user) setUser({ ...user, hasPassword: true });
                    setDone(true);
                    reset();
                  },
                },
              );
            }}
          >
            {change.error && (
              <div style={{ marginBottom: 14 }}>
                <Notice tone="error">
                  {change.error instanceof ApiError ? change.error.message : '저장하지 못했습니다.'}
                </Notice>
              </div>
            )}
            {hasPassword && (
              <div className={styles.field}>
                <label className={styles.label} htmlFor="me-current">
                  현재 비밀번호
                </label>
                <PasswordInput
                  id="me-current"
                  inputClassName={styles.input}
                  value={current}
                  onChange={(e) => setCurrent(e.target.value)}
                  autoComplete="current-password"
                  required
                />
                <span className={styles.hint}>
                  기억나지 않으면 <Link href="/password/reset">비밀번호 찾기</Link>를 이용해 주세요.
                </span>
              </div>
            )}
            <div className={styles.field}>
              <label className={styles.label} htmlFor="me-password">
                새 비밀번호
              </label>
              <PasswordInput
                id="me-password"
                inputClassName={styles.input}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="new-password"
                required
              />
              <span className={styles.hint}>영문·숫자·특수문자를 모두 포함한 10자 이상.</span>
            </div>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="me-password2">
                새 비밀번호 확인
              </label>
              <PasswordInput
                id="me-password2"
                inputClassName={styles.input}
                value={passwordAgain}
                onChange={(e) => setPasswordAgain(e.target.value)}
                autoComplete="new-password"
                required
              />
              {mismatch && <span className={styles.error}>비밀번호가 서로 다릅니다.</span>}
            </div>
            <div className={styles.actions}>
              <button type="submit" className={styles.ghost} disabled={!canSubmit}>
                {change.isPending ? '저장 중…' : '저장'}
              </button>
              <button type="button" className={styles.ghost} disabled={change.isPending} onClick={reset}>
                취소
              </button>
            </div>
          </form>
        ) : (
          <div className={styles.actions}>
            <button
              type="button"
              className={styles.ghost}
              onClick={() => {
                setDone(false);
                setOpen(true);
              }}
            >
              {hasPassword ? '비밀번호 바꾸기' : '비밀번호 정하기'}
            </button>
          </div>
        )}
      </div>
    </section>
  );
}

function AgreementPanel() {
  const { isAuthenticated } = useUserSession();
  const { data, isLoading } = useAgreements(isAuthenticated);
  const change = useChangeAgreement();

  return (
    <section className={styles.panel}>
      <header className={styles.panelHead}>
        <h2 className={styles.sectionTitle}>약관 동의</h2>
        <p className={styles.sectionNote}>
          선택 항목은 언제든 바꿀 수 있습니다. 바꾼 기록은 지워지지 않고 새로 쌓입니다.
        </p>
      </header>
      <div className={styles.panelBody}>
        {change.error && (
          <div style={{ marginBottom: 14 }}>
            <Notice tone="error">
              {change.error instanceof ApiError ? change.error.message : '변경하지 못했습니다.'}
            </Notice>
          </div>
        )}

        {isLoading ? (
          <p className={styles.loading}>불러오는 중…</p>
        ) : (
          (data ?? []).map((agreement) => (
            <div key={agreement.type} className={styles.socialRow}>
              <div>
                <span className={agreement.required ? styles.tagRequired : styles.tagOptional}>
                  [{agreement.required ? '필수' : '선택'}]
                </span>
                {agreement.name}
                <span className={styles.socialMeta}>
                  버전 {agreement.version} ·{' '}
                  {agreement.agreedAt
                    ? `${formatDateTime(agreement.agreedAt)} ${agreement.agreed ? '동의' : '철회'}`
                    : '기록 없음'}
                </span>
              </div>
              {agreement.required ? (
                <span className={styles.socialMeta}>동의함 (철회 불가)</span>
              ) : (
                <label className={styles.rowLabel} style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                  <input
                    type="checkbox"
                    checked={agreement.agreed}
                    disabled={change.isPending}
                    onChange={(e) => change.mutate({ type: agreement.type, agreed: e.target.checked })}
                  />
                  동의
                </label>
              )}
            </div>
          ))
        )}
      </div>
    </section>
  );
}

/**
 * 연결된 소셜 계정 — 해제와 <b>연결하기</b>.
 *
 * <p>연결은 로그인한 상태에서 본인이 한다. 이메일이 같다는 이유로 서버가 알아서 합치지 않는다.
 * 소셜 창을 다녀오는 동안 "연결하려던 것"임을 남겨 두면 콜백 화면이 로그인 대신 연결로 처리한다.
 */
function SocialPanel() {
  const { user, isAuthenticated } = useUserSession();
  const { data, isLoading } = useSocialAccounts(isAuthenticated);
  const { data: config } = useAuthProviders();
  const disconnect = useDisconnectSocial();
  const startOAuth = useStartOAuth();

  const accounts = data ?? [];
  const linked = new Set(accounts.map((a) => a.provider));
  const linkable = (config?.providers ?? []).filter((p) => !linked.has(p.code));
  // 비밀번호가 없으면 마지막 소셜이 유일한 로그인 수단이다.
  const lastOne = accounts.length <= 1 && !user?.hasPassword;

  const connect = (provider: OAuthProviderType) =>
    startOAuth.mutate(provider, {
      onSuccess: (res) => {
        oauthState.save(provider, res.state);
        oauthMode.setLink();
        window.location.assign(res.authorizeUrl);
      },
    });

  const failure = disconnect.error ?? startOAuth.error;

  return (
    <section className={styles.panel}>
      <header className={styles.panelHead}>
        <h2 className={styles.sectionTitle}>연결된 소셜 계정</h2>
        <p className={styles.sectionNote}>
          비밀번호가 없으면 마지막 하나는 해제할 수 없습니다 — 로그인할 방법이 사라집니다.
        </p>
      </header>
      <div className={styles.panelBody}>
        {failure && (
          <div style={{ marginBottom: 14 }}>
            <Notice tone="error">
              {failure instanceof ApiError ? failure.message : '처리하지 못했습니다.'}
            </Notice>
          </div>
        )}

        {isLoading ? (
          <p className={styles.loading}>불러오는 중…</p>
        ) : (
          <>
            {accounts.length === 0 && linkable.length === 0 && (
              <p className={styles.loading}>연결된 계정이 없습니다.</p>
            )}
            {accounts.map((account) => (
              <div key={account.provider} className={styles.socialRow}>
                <div>
                  <strong>{account.providerName}</strong>
                  <span className={styles.socialMeta}>
                    {account.email ?? '(이메일 미제공)'} · {formatDateTime(account.connectedAt)} 연결
                  </span>
                </div>
                <button
                  type="button"
                  className={`${styles.ghost} ${styles.danger}`}
                  disabled={disconnect.isPending || lastOne}
                  title={lastOne ? '비밀번호가 없어 마지막 연동은 해제할 수 없습니다.' : undefined}
                  onClick={() => disconnect.mutate(account.provider as OAuthProviderType)}
                >
                  해제
                </button>
              </div>
            ))}
            {linkable.map((provider) => (
              <div key={provider.code} className={styles.socialRow}>
                <div>
                  <strong>{provider.name}</strong>
                  <span className={styles.socialMeta}>연결되지 않음</span>
                </div>
                <button
                  type="button"
                  className={styles.ghost}
                  disabled={startOAuth.isPending}
                  onClick={() => connect(provider.code)}
                >
                  연결하기
                </button>
              </div>
            ))}
          </>
        )}
      </div>
    </section>
  );
}

function AccountPanel() {
  const router = useRouter();
  const signOut = useSignOut();
  const withdraw = useWithdraw();
  const [confirming, setConfirming] = useState(false);

  return (
    <section className={styles.panel}>
      <header className={styles.panelHead}>
        <h2 className={styles.sectionTitle}>계정</h2>
      </header>
      <div className={styles.panelBody}>
        <div className={styles.actions}>
          <button
            type="button"
            className={styles.ghost}
            disabled={signOut.isPending}
            onClick={() => signOut.mutate(undefined, { onSuccess: () => router.replace('/') })}
          >
            {signOut.isPending ? '로그아웃 중…' : '로그아웃'}
          </button>
          <Link className={styles.ghost} href="/mypage/orders">
            주문 내역
          </Link>
        </div>

        <div style={{ marginTop: 22, paddingTop: 18, borderTop: '1px solid var(--line)' }}>
          {confirming ? (
            <>
              <Notice tone="warn" title="정말 탈퇴하시겠습니까?">
                모든 기기에서 로그아웃되고 다시 로그인할 수 없습니다.
                <br />
                다만 <strong>주문·결제 내역은 삭제되지 않습니다</strong> — 전자상거래법상 거래
                기록은 일정 기간 보관해야 합니다.
              </Notice>
              {withdraw.error && (
                <div style={{ marginTop: 12 }}>
                  <Notice tone="error">
                    {withdraw.error instanceof ApiError ? withdraw.error.message : '처리하지 못했습니다.'}
                  </Notice>
                </div>
              )}
              <div className={styles.actions} style={{ marginTop: 14 }}>
                <button
                  type="button"
                  className={`${styles.ghost} ${styles.danger}`}
                  disabled={withdraw.isPending}
                  onClick={() => withdraw.mutate(undefined, { onSuccess: () => router.replace('/') })}
                >
                  {withdraw.isPending ? '처리 중…' : '탈퇴합니다'}
                </button>
                <button
                  type="button"
                  className={styles.ghost}
                  disabled={withdraw.isPending}
                  onClick={() => setConfirming(false)}
                >
                  취소
                </button>
              </div>
            </>
          ) : (
            <button
              type="button"
              className={`${styles.ghost} ${styles.danger}`}
              onClick={() => setConfirming(true)}
            >
              회원 탈퇴
            </button>
          )}
        </div>
      </div>
    </section>
  );
}
