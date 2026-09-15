'use client';

import { useState } from 'react';

import { Alert } from '@/components/admin/Alert';
import { Button } from '@/components/admin/Button';
import { Field } from '@/components/admin/Field';
import { Panel } from '@/components/admin/Panel';
import { StatusBadge } from '@/components/ui/StatusBadge';
import {
  useActivateMyOtp,
  useMyOtpStatus,
  useRegenerateRecoveryCodes,
  useStartMyOtpSetup,
} from '@/hooks/admin/useAdminSession';
import { ApiError } from '@/types/api';
import { formatDateTime } from '@/utils/format';
import adminStyles from '@/components/admin/admin.module.css';
import { isCompleteOtp, OtpCodeInput } from './OtpCodeInput';
import { OtpSetupStep } from './OtpSetupStep';
import { RecoveryCodesList } from './RecoveryCodesList';
import styles from './otp.module.css';

/** 남은 복구 코드가 이만큼 이하면 새로 받으라고 알린다 */
const LOW_RECOVERY_CODES = 3;

function messageOf(error: unknown, fallback: string): string | null {
  if (!error) return null;
  return error instanceof ApiError ? error.message : fallback;
}

/** 내 계정 — 2단계 인증 상태, 등록, 복구 코드 재발급. */
export function MyOtpPanel() {
  const status = useMyOtpStatus();
  const start = useStartMyOtpSetup();
  const activate = useActivateMyOtp();
  const regenerate = useRegenerateRecoveryCodes();

  const [codes, setCodes] = useState<string[] | null>(null);
  const [regenerating, setRegenerating] = useState(false);
  const [regenCode, setRegenCode] = useState('');

  const data = status.data;

  const body = (() => {
    if (codes) {
      return <RecoveryCodesList codes={codes} doneLabel="닫기" onDone={() => setCodes(null)} />;
    }
    if (status.isLoading) {
      return <p className={adminStyles.loading}>불러오는 중…</p>;
    }
    if (!data) {
      return (
        <Alert tone="danger">
          {messageOf(status.error, '2단계 인증 상태를 불러오지 못했습니다.')}
        </Alert>
      );
    }

    if (!data.enabled) {
      if (start.data) {
        return (
          <OtpSetupStep
            setup={start.data}
            pending={activate.isPending}
            error={messageOf(activate.error, '등록하지 못했습니다.')}
            onCancel={() => {
              start.reset();
              activate.reset();
            }}
            onSubmit={(code) =>
              activate.mutate(code, {
                onSuccess: (res) => {
                  start.reset();
                  setCodes(res.recoveryCodes);
                },
              })
            }
          />
        );
      }
      return (
        <div className={styles.stack}>
          <Alert tone={data.required ? 'warning' : 'info'}>
            아직 인증 앱을 등록하지 않았습니다. 등록하면 로그인할 때 비밀번호 다음에 휴대폰의
            6자리를 입력합니다.
          </Alert>
          {start.error && (
            <Alert tone="danger">{messageOf(start.error, '등록을 시작하지 못했습니다.')}</Alert>
          )}
          <div>
            <Button variant="gold" disabled={start.isPending} onClick={() => start.mutate()}>
              {start.isPending ? '준비 중…' : '인증 앱 등록'}
            </Button>
          </div>
        </div>
      );
    }

    const low = data.recoveryCodesRemaining <= LOW_RECOVERY_CODES;

    return (
      <div className={styles.stack}>
        <div className={adminStyles.cards}>
          <div className={adminStyles.stat}>
            <span className={adminStyles.statLabel}>상태</span>
            <span className={adminStyles.statValueSm}>
              <StatusBadge tone="success">사용 중</StatusBadge>
            </span>
          </div>
          <div className={adminStyles.stat}>
            <span className={adminStyles.statLabel}>등록일</span>
            <span className={adminStyles.statValueSm}>{formatDateTime(data.enabledAt)}</span>
          </div>
          <div className={adminStyles.stat}>
            <span className={adminStyles.statLabel}>남은 복구 코드</span>
            <span className={adminStyles.statValueSm}>
              <StatusBadge tone={low ? 'warning' : 'neutral'}>
                {data.recoveryCodesRemaining}개
              </StatusBadge>
            </span>
          </div>
        </div>

        {low && (
          <Alert tone="warning">
            복구 코드가 얼마 남지 않았습니다. 휴대폰이 곁에 있을 때 새로 받아 두세요.
          </Alert>
        )}

        {regenerating ? (
          <form
            className={styles.stack}
            onSubmit={(e) => {
              e.preventDefault();
              if (!isCompleteOtp(regenCode) || regenerate.isPending) return;
              regenerate.mutate(regenCode, {
                onSuccess: (res) => {
                  setRegenerating(false);
                  setRegenCode('');
                  setCodes(res.recoveryCodes);
                },
              });
            }}
          >
            {regenerate.error && (
              <Alert tone="danger">
                {messageOf(regenerate.error, '복구 코드를 새로 받지 못했습니다.')}
              </Alert>
            )}
            <Field
              label="지금 인증 앱의 6자리"
              htmlFor="otp-regen-code"
              required
              hint="새 코드를 받으면 지금 가진 복구 코드는 모두 쓸 수 없게 됩니다."
            >
              <OtpCodeInput
                id="otp-regen-code"
                value={regenCode}
                onChange={setRegenCode}
                autoFocus
                disabled={regenerate.isPending}
              />
            </Field>
            <div className={styles.actions}>
              <Button
                type="button"
                disabled={regenerate.isPending}
                onClick={() => {
                  setRegenerating(false);
                  setRegenCode('');
                  regenerate.reset();
                }}
              >
                취소
              </Button>
              <Button
                type="submit"
                variant="primary"
                disabled={regenerate.isPending || !isCompleteOtp(regenCode)}
              >
                {regenerate.isPending ? '확인 중…' : '새 복구 코드 받기'}
              </Button>
            </div>
          </form>
        ) : (
          <div>
            <Button onClick={() => setRegenerating(true)}>복구 코드 새로 받기</Button>
          </div>
        )}

        <p className={adminStyles.hint}>
          휴대폰을 바꾸거나 잃어버렸다면 최고관리자에게 OTP 초기화를 요청하세요. 초기화한 뒤
          다음 로그인에서 새 휴대폰으로 다시 등록합니다.
        </p>
      </div>
    );
  })();

  return (
    <Panel title="2단계 인증 (OTP)" note="비밀번호가 새어 나가도 휴대폰이 없으면 로그인할 수 없습니다.">
      {body}
    </Panel>
  );
}
