'use client';

import { useState } from 'react';

import { Alert } from '@/components/admin/Alert';
import { Button } from '@/components/admin/Button';
import { Field, PasswordInput } from '@/components/admin/Field';
import { MyOtpPanel } from '@/components/admin/otp/MyOtpPanel';
import { Panel } from '@/components/admin/Panel';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useAdminSession, useChangeMyPassword } from '@/hooks/admin/useAdminSession';
import { formatDateTime } from '@/utils/format';
import { ADMIN_ROLE_LABEL, ADMIN_STATUS_LABEL } from '@/types/admin';
import { ApiError } from '@/types/api';
import styles from '@/components/admin/admin.module.css';

const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9]).{10,72}$/;
const PASSWORD_MESSAGE = '영문·숫자·특수문자를 모두 포함한 10자 이상이어야 합니다.';

/**
 * 내 계정.
 *
 * 비밀번호를 바꾸면 <b>내 기존 토큰이 전부 무효가 된다</b> — 다른 기기에 남은 세션까지
 * 끊긴다. 그래서 성공 직후 이 탭도 곧 로그아웃된다는 사실을 미리 알린다.
 */
export function AdminAccountView() {
  const { admin } = useAdminSession();
  const change = useChangeMyPassword();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const newInvalid = newPassword !== '' && !PASSWORD_PATTERN.test(newPassword);
  const mismatch = confirmPassword !== '' && newPassword !== confirmPassword;
  const sameAsCurrent = newPassword !== '' && newPassword === currentPassword;

  const canSubmit =
    currentPassword.length > 0 &&
    PASSWORD_PATTERN.test(newPassword) &&
    newPassword === confirmPassword &&
    !sameAsCurrent &&
    !change.isPending;

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <div>
          <h1 className={styles.title}>내 계정</h1>
          <p className={styles.subtitle}>계정 정보 확인, 2단계 인증, 비밀번호 변경.</p>
        </div>
      </header>

      <Panel title="계정 정보">
        {!admin ? (
          <p className={styles.loading}>불러오는 중…</p>
        ) : (
          <div className={styles.cards}>
            <div className={styles.stat}>
              <span className={styles.statLabel}>아이디</span>
              <span className={styles.statValueSm}>{admin.loginId}</span>
            </div>
            <div className={styles.stat}>
              <span className={styles.statLabel}>이름</span>
              <span className={styles.statValueSm}>{admin.name}</span>
            </div>
            <div className={styles.stat}>
              <span className={styles.statLabel}>권한</span>
              <span className={styles.statValueSm}>{ADMIN_ROLE_LABEL[admin.role]}</span>
            </div>
            <div className={styles.stat}>
              <span className={styles.statLabel}>상태</span>
              <span className={styles.statValueSm}>
                <StatusBadge tone={admin.status === 'ACTIVE' ? 'success' : 'warning'}>
                  {ADMIN_STATUS_LABEL[admin.status]}
                </StatusBadge>
              </span>
            </div>
            <div className={styles.stat}>
              <span className={styles.statLabel}>마지막 로그인</span>
              <span className={styles.statValueSm}>{formatDateTime(admin.lastLoginAt)}</span>
            </div>
            <div className={styles.stat}>
              <span className={styles.statLabel}>비밀번호 변경</span>
              <span className={styles.statValueSm}>
                {formatDateTime(admin.passwordChangedAt)}
              </span>
            </div>
          </div>
        )}
      </Panel>

      <MyOtpPanel />

      <Panel title="비밀번호 변경" note="변경하면 내 기존 토큰이 전부 무효가 된다.">
        <form
          className={styles.form}
          onSubmit={(e) => {
            e.preventDefault();
            if (!canSubmit) return;
            change.mutate(
              { currentPassword, newPassword },
              {
                onSuccess: () => {
                  setCurrentPassword('');
                  setNewPassword('');
                  setConfirmPassword('');
                },
              },
            );
          }}
        >
          {change.error && (
            <Alert tone="danger">
              {change.error instanceof ApiError
                ? change.error.message
                : '비밀번호를 바꾸지 못했습니다.'}
            </Alert>
          )}

          {change.isSuccess && (
            <Alert tone="success" title="비밀번호가 바뀌었습니다">
              다른 기기에 남아 있던 세션도 모두 끊겼다. 이 창도 다음 요청부터 다시 로그인을
              요구할 수 있다.
            </Alert>
          )}

          <div className={styles.formGrid}>
            <Field label="현재 비밀번호" htmlFor="cur-pw" required>
              <PasswordInput
                id="cur-pw"
                autoComplete="current-password"
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
              />
            </Field>

            <Field
              label="새 비밀번호"
              htmlFor="new-pw"
              required
              hint={PASSWORD_MESSAGE}
              error={
                newInvalid
                  ? PASSWORD_MESSAGE
                  : sameAsCurrent
                    ? '현재 비밀번호와 다른 값을 입력해 주세요.'
                    : null
              }
            >
              <PasswordInput
                id="new-pw"
                autoComplete="new-password"
                invalid={newInvalid || sameAsCurrent}
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
              />
            </Field>

            <Field
              label="새 비밀번호 확인"
              htmlFor="new-pw2"
              required
              error={mismatch ? '입력한 두 값이 다릅니다.' : null}
            >
              <PasswordInput
                id="new-pw2"
                autoComplete="new-password"
                invalid={mismatch}
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
              />
            </Field>
          </div>

          <div className={styles.formActions}>
            <Button type="submit" variant="primary" disabled={!canSubmit}>
              {change.isPending ? '변경 중…' : '비밀번호 변경'}
            </Button>
          </div>
        </form>
      </Panel>
    </div>
  );
}
