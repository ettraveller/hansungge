'use client';

import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useEffect, useState } from 'react';

import { Notice } from '@/components/auth/Notice';
import { MyPageShell } from '@/layouts/MyPageShell';
import { PasswordRules, isValidPassword } from '@/components/auth/PasswordRules';
import { PasswordInput } from '@/components/ui/PasswordInput';
import {
  useAgreements,
  useAuthProviders,
  useChangeAgreement,
  useChangePassword,
  useDisconnectSocial,
  useSignOut,
  useSocialAccounts,
  useStartMemberIdentity,
  useStartOAuth,
  useUserSession,
  useWithdraw,
} from '@/hooks/useUserSession';
import { useUserAuthStore } from '@/stores/useUserAuthStore';
import { oauthMode, oauthState } from '@/utils/authStorage';
import { pendingIdentity, submitIdentityForm } from '@/utils/identityReturn';
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
      router.replace('/login?reason=required&next=%2Fmypage%2Faccount');
    }
  }, [restored, isAuthenticated, router]);

  if (!restored) {
    return <p className={styles.loading}>확인 중…</p>;
  }
  if (!isAuthenticated || !user) {
    return <p className={styles.loading}>로그인 화면으로 이동합니다…</p>;
  }

  return (
    // 다른 마이페이지 화면과 같은 껍데기를 쓴다. 여기만 메뉴가 없으면 계정 관리에 들어간
    // 순간 왼쪽이 사라져, 고객은 뒤로가기 말고는 돌아갈 길을 찾지 못한다.
    //
    // 요약 줄은 감춘다 — 이 화면 자체가 회원 정보라 바로 아래 내용과 겹친다.
    <MyPageShell title="계정 관리" lead={`${user.name ?? '고객'}님, 안녕하세요.`} showSummary={false}>
      {searchParams.get('linked') === '1' && (
        <div style={{ marginBottom: 16 }}>
          <Notice tone="info">소셜 계정을 연결했습니다. 다음부터 그 계정으로도 로그인됩니다.</Notice>
        </div>
      )}
      {searchParams.get('verified') === '1' && (
        <div style={{ marginBottom: 16 }}>
          <Notice tone="info">본인인증을 마쳤습니다. 이름·휴대폰번호가 인증된 값으로 바뀌었습니다.</Notice>
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
    </MyPageShell>
  );
}

/**
 * 내 정보 — 읽기 전용, 그리고 본인인증.
 *
 * <p>이름·휴대폰은 본인인증으로 확정된 값이라 여기서 고칠 수 없다. 고칠 수 있으면 "확인된 값"이
 * 아니라 "한 번 확인했다가 지금은 모르는 값"이 되고, 매입 대금의 예금주 대조가 그 위에 선다.
 *
 * <p>소셜 가입자는 본인인증을 받지 않았다(2026-09-21 개편). 여기서 받으면 실명·휴대폰이 채워지고
 * 휴대폰 로그인도 쓸 수 있다. 매입 신청·정산계좌 등록 전에도 이 본인인증을 요구한다.
 */
function ProfilePanel() {
  const { user } = useUserSession();
  const { data: config } = useAuthProviders();
  const start = useStartMemberIdentity();
  const verified = Boolean(user?.identityVerified);
  // 본인인증 공급자가 준비됐을 때만 버튼을 그린다 — 눌러야만 실패를 아는 버튼을 만들지 않는다.
  const canVerify = Boolean(config?.signUpAvailable);

  const verify = () =>
    start.mutate(undefined, {
      onSuccess: (res) => {
        pendingIdentity.save({ token: res.token, flow: 'verify', next: '/mypage?verified=1' });
        submitIdentityForm(res.form);
      },
    });

  return (
    <section className={styles.panel}>
      <header className={styles.panelHead}>
        <h2 className={styles.sectionTitle}>내 정보</h2>
        <p className={styles.sectionNote}>
          {verified
            ? '이름·휴대폰은 본인인증으로 확정된 값입니다. 휴대폰이나 이름이 바뀌었다면 본인인증을 다시 받아 주세요.'
            : '아직 본인인증을 받지 않았습니다. 본인인증을 하면 이름·휴대폰번호가 등록되고 휴대폰번호로도 로그인할 수 있습니다. 매입 신청 전에는 꼭 필요합니다.'}
        </p>
      </header>
      <div className={styles.panelBody}>
        <div className={styles.rows}>
          <div className={styles.row}>
            <span className={styles.rowLabel}>본인인증</span>
            <span className={styles.rowValue}>{verified ? '완료' : '받지 않음'}</span>
          </div>
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

        {start.error && (
          <div style={{ marginTop: 14 }}>
            <Notice tone="error">
              {start.error instanceof ApiError ? start.error.message : '본인인증을 시작하지 못했습니다.'}
            </Notice>
          </div>
        )}
        {canVerify && (
          <div className={styles.actions} style={{ marginTop: 14 }}>
            <button type="button" className={styles.ghost} disabled={start.isPending} onClick={verify}>
              {start.isPending ? '본인인증으로 이동 중…' : verified ? '본인인증 다시 받기' : '본인인증하기'}
            </button>
          </div>
        )}
      </div>
    </section>
  );
}

/**
 * 비밀번호 — 소셜로만 가입한 회원은 새로 정하고, 있으면 현재 비밀번호를 확인한 뒤 바꾼다.
 *
 * <p>휴대폰 로그인에는 비밀번호와 <b>본인인증으로 등록된 휴대폰번호</b>가 함께 있어야 한다.
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
    isValidPassword(password) &&
    password === passwordAgain &&
    (!hasPassword || current !== '') &&
    !change.isPending;

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
          {user?.canPasswordLogin
            ? '휴대폰번호와 비밀번호로 로그인할 수 있습니다.'
            : hasPassword
              ? '비밀번호는 정해 두었지만 휴대폰번호가 없어 아직 휴대폰으로 로그인할 수 없습니다. 위에서 본인인증을 하면 번호가 등록됩니다.'
              : '소셜 계정으로 가입하셔서 비밀번호가 없습니다. 본인인증으로 휴대폰번호를 등록하고 비밀번호를 정하면 휴대폰번호로도 로그인됩니다.'}
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
                    if (user) setUser({ ...user, hasPassword: true, canPasswordLogin: Boolean(user.phone) });
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
                aria-describedby="me-password-rules"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="new-password"
                required
              />
              <PasswordRules value={password} id="me-password-rules" />
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
  const lastOne = accounts.length <= 1 && !user?.canPasswordLogin;

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
          휴대폰번호·비밀번호로 로그인할 수 없으면 마지막 하나는 해제할 수 없습니다 — 로그인할 방법이 사라집니다.
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
                  title={lastOne ? '휴대폰번호·비밀번호 로그인이 없어 마지막 연동은 해제할 수 없습니다.' : undefined}
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
