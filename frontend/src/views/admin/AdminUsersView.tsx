'use client';

import { useState } from 'react';

import { Alert } from '@/components/admin/Alert';
import { Button } from '@/components/admin/Button';
import { Field, SelectInput, TextInput } from '@/components/admin/Field';
import { Modal } from '@/components/admin/Modal';
import { Pagination } from '@/components/admin/Pagination';
import { Panel } from '@/components/admin/Panel';
import { ReasonDialog } from '@/components/admin/ReasonDialog';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useAdminSession } from '@/hooks/admin/useAdminSession';
import {
  useAdminUsers,
  useChangeAdminStatus,
  useCreateAdmin,
  useResetAdminOtp,
  useResetAdminPassword,
  useUpdateAdmin,
} from '@/hooks/admin/useAdmins';
import { formatDateTime } from '@/utils/format';
import {
  ADMIN_ROLE_LABEL,
  ADMIN_STATUS_LABEL,
  type AdminRole,
  type AdminStatus,
  type AdminUser,
} from '@/types/admin';
import { ApiError } from '@/types/api';
import styles from '@/components/admin/admin.module.css';

/** 백엔드 AdminPasswordPolicy 와 같은 규칙. 서버 왕복 없이 오타를 잡아준다. */
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9]).{10,72}$/;
const PASSWORD_MESSAGE = '영문·숫자·특수문자를 모두 포함한 10자 이상이어야 합니다.';
const LOGIN_ID_PATTERN = /^[a-z0-9_]{4,60}$/;

const ROLE_HINT: Record<AdminRole, string> = {
  SUPER_ADMIN: '모든 기능 + 관리자 계정 관리',
  ADMIN: '시세 고시, 감사로그 조회',
  MANAGER: '상품·카테고리·재고 관리',
  OPERATOR: '조회 전용',
};

/**
 * 관리자 계정.
 *
 * SUPER_ADMIN 전용 화면이다. 여기서 하는 모든 행위는 사유와 함께 감사로그에 남는다.
 *
 * <p>비밀번호는 <b>조회할 수 없다</b> — 저장된 것이 BCrypt 해시라 되돌릴 방법이 없다.
 * 잊어버린 계정은 초기화만 가능하다.
 */
export function AdminUsersView() {
  const { admin, can } = useAdminSession();
  const allowed = can('SUPER_ADMIN');

  const [page, setPage] = useState(0);
  const { data, isLoading, error } = useAdminUsers(page, allowed);

  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<AdminUser | null>(null);
  const [statusTarget, setStatusTarget] = useState<{ user: AdminUser; next: AdminStatus } | null>(
    null,
  );
  const [resetTarget, setResetTarget] = useState<AdminUser | null>(null);
  const [otpResetTarget, setOtpResetTarget] = useState<AdminUser | null>(null);

  if (!allowed) {
    return (
      <div className={styles.page}>
        <Alert tone="warning" title="권한이 없습니다">
          관리자 계정 관리는 최고관리자(SUPER_ADMIN)만 할 수 있습니다.
        </Alert>
      </div>
    );
  }

  const rows = data?.content ?? [];

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <div>
          <h1 className={styles.title}>관리자 계정</h1>
          <p className={styles.subtitle}>
            비밀번호나 OTP 를 연속 5회 틀리면 계정이 자동으로 잠긴다. 잠금 해제는 상태를
            정상으로 바꾸거나 비밀번호를 초기화하면 된다. 휴대폰을 잃어버린 관리자는 OTP 를
            초기화한다.
          </p>
        </div>
        <div className={styles.headActions}>
          <Button variant="gold" onClick={() => setCreating(true)}>
            계정 생성
          </Button>
        </div>
      </header>

      {error && (
        <Alert tone="danger">
          {error instanceof ApiError ? error.message : '계정을 불러오지 못했습니다.'}
        </Alert>
      )}

      <Panel flush>
        {isLoading ? (
          <p className={styles.loading}>불러오는 중…</p>
        ) : rows.length === 0 ? (
          <p className={styles.empty}>계정이 없습니다.</p>
        ) : (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th className={styles.num}>ID</th>
                  <th>아이디</th>
                  <th>이름</th>
                  <th>권한</th>
                  <th>상태</th>
                  <th>OTP</th>
                  <th className={styles.num}>실패</th>
                  <th>마지막 로그인</th>
                  <th>비밀번호 변경</th>
                  <th aria-label="작업" />
                </tr>
              </thead>
              <tbody>
                {rows.map((user) => {
                  const isSelf = user.id === admin?.id;
                  return (
                    <tr key={user.id}>
                      <td data-label="ID" className={`${styles.num} ${styles.muted}`}>{user.id}</td>
                      <td data-label="아이디" data-card-title className={styles.strong}>
                        {user.loginId}
                        {isSelf && <span className={styles.muted}> (본인)</span>}
                      </td>
                      <td data-label="이름">{user.name}</td>
                      <td data-label="권한">{ADMIN_ROLE_LABEL[user.role]}</td>
                      <td data-label="상태">
                        <StatusBadge
                          tone={
                            user.status === 'ACTIVE'
                              ? 'success'
                              : user.status === 'LOCKED'
                                ? 'warning'
                                : 'danger'
                          }
                        >
                          {ADMIN_STATUS_LABEL[user.status]}
                        </StatusBadge>
                      </td>
                      <td data-label="OTP">
                        <StatusBadge tone={user.otpEnabled ? 'success' : 'neutral'}>
                          {user.otpEnabled ? '등록' : '미등록'}
                        </StatusBadge>
                      </td>
                      <td data-label="실패" className={styles.num}>{user.loginFailCount}</td>
                      <td data-label="마지막 로그인" className={styles.muted}>{formatDateTime(user.lastLoginAt)}</td>
                      <td data-label="비밀번호 변경" className={styles.muted}>{formatDateTime(user.passwordChangedAt)}</td>
                      <td data-label="작업" data-card-actions>
                        <div className={styles.rowActions}>
                          <Button small onClick={() => setEditing(user)}>
                            수정
                          </Button>
                          {user.status === 'ACTIVE' ? (
                            <Button
                              small
                              variant="danger"
                              disabled={isSelf}
                              title={isSelf ? '본인 계정은 정지할 수 없습니다.' : undefined}
                              onClick={() => setStatusTarget({ user, next: 'DISABLED' })}
                            >
                              정지
                            </Button>
                          ) : (
                            <Button
                              small
                              onClick={() => setStatusTarget({ user, next: 'ACTIVE' })}
                            >
                              잠금 해제
                            </Button>
                          )}
                          <Button small onClick={() => setResetTarget(user)}>
                            비번 초기화
                          </Button>
                          <Button
                            small
                            disabled={isSelf || !user.otpEnabled}
                            title={
                              isSelf
                                ? '본인 OTP 는 초기화할 수 없습니다.'
                                : !user.otpEnabled
                                  ? '아직 인증 앱을 등록하지 않은 계정입니다.'
                                  : undefined
                            }
                            onClick={() => setOtpResetTarget(user)}
                          >
                            OTP 초기화
                          </Button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {data && (
          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            onChange={setPage}
          />
        )}
      </Panel>

      {creating && <CreateDialog onClose={() => setCreating(false)} />}
      {editing && (
        <EditDialog key={editing.id} user={editing} onClose={() => setEditing(null)} />
      )}
      {statusTarget && (
        <StatusDialog
          key={`${statusTarget.user.id}-${statusTarget.next}`}
          user={statusTarget.user}
          next={statusTarget.next}
          onClose={() => setStatusTarget(null)}
        />
      )}
      {resetTarget && (
        <ResetDialog key={resetTarget.id} user={resetTarget} onClose={() => setResetTarget(null)} />
      )}
      {otpResetTarget && (
        <OtpResetDialog
          key={otpResetTarget.id}
          user={otpResetTarget}
          onClose={() => setOtpResetTarget(null)}
        />
      )}
    </div>
  );
}

function RoleSelect({
  id,
  value,
  onChange,
}: {
  id: string;
  value: AdminRole;
  onChange: (role: AdminRole) => void;
}) {
  return (
    <Field label="권한 등급" htmlFor={id} required hint={ROLE_HINT[value]}>
      <SelectInput id={id} value={value} onChange={(e) => onChange(e.target.value as AdminRole)}>
        {(['OPERATOR', 'MANAGER', 'ADMIN', 'SUPER_ADMIN'] as AdminRole[]).map((r) => (
          <option key={r} value={r}>
            {ADMIN_ROLE_LABEL[r]}
          </option>
        ))}
      </SelectInput>
    </Field>
  );
}

function CreateDialog({ onClose }: { onClose: () => void }) {
  const create = useCreateAdmin();
  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [role, setRole] = useState<AdminRole>('OPERATOR');

  const loginIdInvalid = loginId !== '' && !LOGIN_ID_PATTERN.test(loginId);
  const passwordInvalid = password !== '' && !PASSWORD_PATTERN.test(password);
  const canSubmit =
    LOGIN_ID_PATTERN.test(loginId) &&
    PASSWORD_PATTERN.test(password) &&
    name.trim().length > 0 &&
    !create.isPending;

  return (
    <Modal
      open
      title="관리자 계정 생성"
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose} disabled={create.isPending}>
            취소
          </Button>
          <Button
            variant="primary"
            disabled={!canSubmit}
            onClick={() =>
              create.mutate(
                { loginId, password, name: name.trim(), role },
                { onSuccess: onClose },
              )
            }
          >
            {create.isPending ? '생성 중…' : '생성'}
          </Button>
        </>
      }
    >
      <div className={styles.form}>
        {create.error && (
          <Alert tone="danger">
            {create.error instanceof ApiError
              ? create.error.message
              : '계정을 만들지 못했습니다.'}
          </Alert>
        )}

        <Alert tone="info">
          초기 비밀번호는 <strong>이 화면에서만 보인다.</strong> 저장되는 것은 해시라
          나중에 다시 확인할 수 없다 — 본인에게 안전한 경로로 전달할 것.
        </Alert>

        <Field
          label="로그인 아이디"
          htmlFor="new-login-id"
          required
          hint="영문 소문자·숫자·밑줄, 4~60자"
          error={loginIdInvalid ? '영문 소문자·숫자·밑줄만, 4자 이상이어야 합니다.' : null}
        >
          <TextInput
            id="new-login-id"
            value={loginId}
            invalid={loginIdInvalid}
            autoComplete="off"
            onChange={(e) => setLoginId(e.target.value)}
          />
        </Field>

        <Field label="이름" htmlFor="new-name" required>
          <TextInput
            id="new-name"
            value={name}
            maxLength={100}
            onChange={(e) => setName(e.target.value)}
          />
        </Field>

        <Field
          label="초기 비밀번호"
          htmlFor="new-password"
          required
          hint={PASSWORD_MESSAGE}
          error={passwordInvalid ? PASSWORD_MESSAGE : null}
        >
          <TextInput
            id="new-password"
            type="text"
            value={password}
            invalid={passwordInvalid}
            autoComplete="new-password"
            onChange={(e) => setPassword(e.target.value)}
          />
        </Field>

        <RoleSelect id="new-role" value={role} onChange={setRole} />
      </div>
    </Modal>
  );
}

function EditDialog({ user, onClose }: { user: AdminUser; onClose: () => void }) {
  const update = useUpdateAdmin();
  const { admin } = useAdminSession();
  const [name, setName] = useState(user.name);
  const [role, setRole] = useState<AdminRole>(user.role);

  const isSelf = user.id === admin?.id;
  const canSubmit = name.trim().length > 0 && !update.isPending;

  return (
    <Modal
      open
      title={`계정 수정 · ${user.loginId}`}
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose} disabled={update.isPending}>
            취소
          </Button>
          <Button
            variant="primary"
            disabled={!canSubmit}
            onClick={() =>
              update.mutate({ id: user.id, name: name.trim(), role }, { onSuccess: onClose })
            }
          >
            {update.isPending ? '저장 중…' : '저장'}
          </Button>
        </>
      }
    >
      <div className={styles.form}>
        {update.error && (
          <Alert tone="danger">
            {update.error instanceof ApiError
              ? update.error.message
              : '계정을 수정하지 못했습니다.'}
          </Alert>
        )}

        {isSelf && (
          <Alert tone="warning">
            본인 계정의 권한은 바꿀 수 없다 — 실수로 자신을 강등해 아무도 계정을 관리할 수
            없게 되는 상황을 막기 위해서다.
          </Alert>
        )}

        <Field label="이름" htmlFor="edit-name" required>
          <TextInput
            id="edit-name"
            value={name}
            maxLength={100}
            onChange={(e) => setName(e.target.value)}
          />
        </Field>

        <RoleSelect id="edit-role" value={role} onChange={setRole} />
      </div>
    </Modal>
  );
}

function StatusDialog({
  user,
  next,
  onClose,
}: {
  user: AdminUser;
  next: AdminStatus;
  onClose: () => void;
}) {
  const change = useChangeAdminStatus();
  const disabling = next === 'DISABLED';

  return (
    <ReasonDialog
      open
      danger={disabling}
      title={disabling ? `계정 정지 · ${user.loginId}` : `잠금 해제 · ${user.loginId}`}
      confirmLabel={disabling ? '정지' : '해제'}
      placeholder={disabling ? '예: 퇴사 처리' : '예: 본인 확인 후 잠금 해제'}
      pending={change.isPending}
      error={change.error instanceof ApiError ? change.error.message : null}
      description={
        disabling ? (
          <>
            <strong>{user.name}</strong> 계정을 정지한다. 즉시 로그인할 수 없게 되고 이미
            발급된 토큰도 무효가 된다.
          </>
        ) : (
          <>
            <strong>{user.name}</strong> 계정을 정상으로 되돌린다. 로그인 실패 횟수도 함께
            초기화된다.
          </>
        )
      }
      onConfirm={(reason) =>
        change.mutate({ id: user.id, status: next, reason }, { onSuccess: onClose })
      }
      onClose={onClose}
    />
  );
}

function OtpResetDialog({ user, onClose }: { user: AdminUser; onClose: () => void }) {
  const reset = useResetAdminOtp();

  return (
    <ReasonDialog
      open
      danger
      title={`OTP 초기화 · ${user.loginId}`}
      confirmLabel="초기화"
      placeholder="예: 휴대폰 분실 — 본인과 통화로 확인"
      pending={reset.isPending}
      error={reset.error instanceof ApiError ? reset.error.message : null}
      description={
        <>
          휴대폰을 잃어버리거나 바꾼 관리자를 위한 조치입니다. <strong>{user.name}</strong>{' '}
          계정의 인증 앱 등록과 복구 코드를 지우고, 열려 있던 로그인도 모두 끊습니다. 잠긴
          계정이면 잠금도 풀립니다. 대상은 다음 로그인에서 새 휴대폰으로 다시 등록합니다.
          <br />
          <strong>본인 확인을 한 뒤에만 진행하세요</strong> — 비밀번호를 훔친 사람이 요청했다면
          2단계 인증이 무력해집니다.
        </>
      }
      onConfirm={(reason) => reset.mutate({ id: user.id, reason }, { onSuccess: onClose })}
      onClose={onClose}
    />
  );
}

function ResetDialog({ user, onClose }: { user: AdminUser; onClose: () => void }) {
  const reset = useResetAdminPassword();
  const [newPassword, setNewPassword] = useState('');

  const invalid = newPassword !== '' && !PASSWORD_PATTERN.test(newPassword);

  return (
    <ReasonDialog
      open
      title={`비밀번호 초기화 · ${user.loginId}`}
      confirmLabel="초기화"
      placeholder="예: 본인 확인 후 초기화 요청 접수"
      pending={reset.isPending}
      error={reset.error instanceof ApiError ? reset.error.message : null}
      description={
        <>
          새 비밀번호를 정해 준다. 잠긴 계정이면 잠금도 함께 풀리고, 대상의 기존 토큰은
          전부 무효가 된다. 이 값은 지금 이후로 다시 볼 수 없다.
          <div style={{ marginTop: 10 }}>
            <TextInput
              value={newPassword}
              invalid={invalid}
              autoComplete="new-password"
              placeholder="영문·숫자·특수문자 포함 10자 이상"
              aria-label="새 비밀번호"
              onChange={(e) => setNewPassword(e.target.value)}
            />
          </div>
          {invalid && <p className={styles.error}>{PASSWORD_MESSAGE}</p>}
        </>
      }
      onConfirm={(reason) => {
        if (!PASSWORD_PATTERN.test(newPassword)) return;
        reset.mutate({ id: user.id, newPassword, reason }, { onSuccess: onClose });
      }}
      onClose={onClose}
    />
  );
}
