'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';

import { Notice } from '@/components/auth/Notice';
import {
  useAgreements,
  useChangeAgreement,
  useDisconnectSocial,
  useSignOut,
  useSocialAccounts,
  useUpdateProfile,
  useUserSession,
  useWithdraw,
} from '@/hooks/useUserSession';
import { formatDateTime } from '@/utils/format';
import { ApiError } from '@/types/api';
import { USER_STATUS_LABEL, type OAuthProviderType } from '@/types/user';
import { MyInquiryPanel } from './MyInquiryPanel';
import styles from '@/components/auth/auth.module.css';

/** 마이페이지 — 내 정보 · 약관 · 소셜 연동 · 탈퇴. */
export function MyPageView() {
  const router = useRouter();
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

      <ProfilePanel />
      {/* 내가 쓴 글은 게시판이 아니라 내 계정에 속한 정보다 — 비밀글로 쓴 문의는
          목록에서 제목만 보이므로 답변이 왔는지 확인할 곳이 따로 있어야 한다 */}
      <MyInquiryPanel />
      <AgreementPanel />
      <SocialPanel />
      <AccountPanel />
    </div>
  );
}

function ProfilePanel() {
  const { user } = useUserSession();
  const update = useUpdateProfile();
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(user?.name ?? '');
  const [phone, setPhone] = useState(user?.phone ?? '');

  const error = update.error;
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : undefined;

  return (
    <section className={styles.panel}>
      <header className={styles.panelHead}>
        <h2 className={styles.sectionTitle}>내 정보</h2>
      </header>
      <div className={styles.panelBody}>
        {error && (
          <div style={{ marginBottom: 14 }}>
            <Notice tone="error">
              {fieldErrors?.phone ??
                (error instanceof ApiError ? error.message : '저장하지 못했습니다.')}
            </Notice>
          </div>
        )}

        {editing ? (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              update.mutate(
                { name: name.trim(), phone: phone.trim() },
                { onSuccess: () => setEditing(false) },
              );
            }}
          >
            <div className={styles.field}>
              <label className={styles.label} htmlFor="me-name">
                이름
              </label>
              <input
                id="me-name"
                className={styles.input}
                value={name}
                maxLength={100}
                onChange={(e) => setName(e.target.value)}
              />
            </div>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="me-phone">
                연락처
              </label>
              <input
                id="me-phone"
                className={styles.input}
                value={phone}
                placeholder="010-1234-5678"
                onChange={(e) => setPhone(e.target.value)}
              />
              <span className={styles.hint}>주문·배송 안내에 사용됩니다. 비워 둘 수 있습니다.</span>
            </div>
            <div className={styles.actions}>
              <button
                type="submit"
                className={styles.ghost}
                disabled={update.isPending || name.trim() === ''}
              >
                {update.isPending ? '저장 중…' : '저장'}
              </button>
              <button
                type="button"
                className={styles.ghost}
                disabled={update.isPending}
                onClick={() => {
                  setEditing(false);
                  setName(user?.name ?? '');
                  setPhone(user?.phone ?? '');
                  update.reset();
                }}
              >
                취소
              </button>
            </div>
          </form>
        ) : (
          <>
            <div className={styles.rows}>
              <div className={styles.row}>
                <span className={styles.rowLabel}>이름</span>
                <span className={styles.rowValue}>{user?.name ?? '-'}</span>
              </div>
              <div className={styles.row}>
                <span className={styles.rowLabel}>이메일</span>
                <span className={styles.rowValue}>{user?.email ?? '(제공되지 않음)'}</span>
              </div>
              <div className={styles.row}>
                <span className={styles.rowLabel}>연락처</span>
                <span className={styles.rowValue}>{user?.phone ?? '-'}</span>
              </div>
              <div className={styles.row}>
                <span className={styles.rowLabel}>상태</span>
                <span className={styles.rowValue}>
                  {user ? USER_STATUS_LABEL[user.status] : '-'}
                </span>
              </div>
              <div className={styles.row}>
                <span className={styles.rowLabel}>가입일</span>
                <span className={styles.rowValue}>{formatDateTime(user?.createdAt)}</span>
              </div>
            </div>
            <div className={styles.actions} style={{ marginTop: 16 }}>
              <button type="button" className={styles.ghost} onClick={() => setEditing(true)}>
                수정
              </button>
            </div>
          </>
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
              {change.error instanceof ApiError
                ? change.error.message
                : '변경하지 못했습니다.'}
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
                    onChange={(e) =>
                      change.mutate({ type: agreement.type, agreed: e.target.checked })
                    }
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

function SocialPanel() {
  const { isAuthenticated } = useUserSession();
  const { data, isLoading } = useSocialAccounts(isAuthenticated);
  const disconnect = useDisconnectSocial();

  const accounts = data ?? [];

  return (
    <section className={styles.panel}>
      <header className={styles.panelHead}>
        <h2 className={styles.sectionTitle}>연결된 소셜 계정</h2>
        <p className={styles.sectionNote}>
          마지막 하나는 해제할 수 없습니다 — 로그인할 방법이 사라집니다.
        </p>
      </header>
      <div className={styles.panelBody}>
        {disconnect.error && (
          <div style={{ marginBottom: 14 }}>
            <Notice tone="error">
              {disconnect.error instanceof ApiError
                ? disconnect.error.message
                : '해제하지 못했습니다.'}
            </Notice>
          </div>
        )}

        {isLoading ? (
          <p className={styles.loading}>불러오는 중…</p>
        ) : accounts.length === 0 ? (
          <p className={styles.loading}>연결된 계정이 없습니다.</p>
        ) : (
          accounts.map((account) => (
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
                disabled={disconnect.isPending || accounts.length <= 1}
                title={accounts.length <= 1 ? '마지막 연동은 해제할 수 없습니다.' : undefined}
                onClick={() => disconnect.mutate(account.provider as OAuthProviderType)}
              >
                해제
              </button>
            </div>
          ))
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
                다만 <strong>주문·결제 내역은 삭제되지 않습니다</strong> — 전자상거래법상
                거래 기록은 일정 기간 보관해야 합니다.
              </Notice>
              {withdraw.error && (
                <div style={{ marginTop: 12 }}>
                  <Notice tone="error">
                    {withdraw.error instanceof ApiError
                      ? withdraw.error.message
                      : '처리하지 못했습니다.'}
                  </Notice>
                </div>
              )}
              <div className={styles.actions} style={{ marginTop: 14 }}>
                <button
                  type="button"
                  className={`${styles.ghost} ${styles.danger}`}
                  disabled={withdraw.isPending}
                  onClick={() =>
                    withdraw.mutate(undefined, { onSuccess: () => router.replace('/') })
                  }
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
