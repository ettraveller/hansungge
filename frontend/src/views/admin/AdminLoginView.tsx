'use client';

import { useRouter, useSearchParams } from 'next/navigation';
import { useEffect, useState } from 'react';

import { Alert } from '@/components/admin/Alert';
import { Button } from '@/components/admin/Button';
import { Field, PasswordInput, TextInput } from '@/components/admin/Field';
import { isCompleteOtp, OtpCodeInput } from '@/components/admin/otp/OtpCodeInput';
import { OtpSetupStep } from '@/components/admin/otp/OtpSetupStep';
import { RecoveryCodesList } from '@/components/admin/otp/RecoveryCodesList';
import otpStyles from '@/components/admin/otp/otp.module.css';
import {
  useAdminLogin,
  useAdminSession,
  useCompleteAdminSignIn,
  useOtpLoginActivate,
  useOtpLoginSetup,
  useOtpVerify,
} from '@/hooks/admin/useAdminSession';
import type { AdminLoginResponse, AdminOtpSetup } from '@/types/admin';
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

type Notice = { tone: 'danger' | 'warning' | 'info'; text: string };

/**
 * 실패 메시지.
 *
 * 아이디가 틀렸는지 비밀번호가 틀렸는지 구분해서 보여주지 않는다 — 백엔드도 같은
 * 문구를 준다. 잠김만은 구분해야 관리자가 무엇을 해야 하는지 알 수 있다.
 */
function messageOf(error: unknown): Notice {
  if (error instanceof ApiError) {
    if (error.code === 'ADMIN_ACCOUNT_LOCKED') {
      return { tone: 'warning', text: `${error.message} 최고관리자에게 잠금 해제를 요청하세요.` };
    }
    if (error.status === 429) {
      return { tone: 'warning', text: '시도가 너무 잦습니다. 잠시 후 다시 시도해 주세요.' };
    }
    return { tone: 'danger', text: error.message };
  }
  return { tone: 'danger', text: '로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.' };
}

/** 이 오류가 나면 2단계를 이어갈 수 없다 — 처음 화면으로 돌려보낸다. */
function endsLogin(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.code === 'ADMIN_OTP_STEP_EXPIRED' || error.code === 'ADMIN_ACCOUNT_LOCKED')
  );
}

/**
 * 로그인 단계.
 *
 * password → otp (등록한 관리자)
 *          → setup → codes (처음 로그인하는 관리자)
 * otp 에서 복구 코드로 들어오면 recovered 를 한 번 거친다.
 */
type Stage =
  | { kind: 'password' }
  | { kind: 'otp'; stepToken: string }
  | { kind: 'setup'; stepToken: string; setup: AdminOtpSetup | null }
  | { kind: 'codes'; codes: string[]; login: AdminLoginResponse }
  | { kind: 'recovered'; remaining: number; login: AdminLoginResponse };

export function AdminLoginView() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const next = safeNext(searchParams.get('next'));

  const { isAuthenticated, hydrated } = useAdminSession();
  const completeSignIn = useCompleteAdminSignIn();
  const login = useAdminLogin();
  const verify = useOtpVerify();
  const startSetup = useOtpLoginSetup();
  const activate = useOtpLoginActivate();

  const [stage, setStage] = useState<Stage>({ kind: 'password' });
  const [notice, setNotice] = useState<Notice | null>(null);

  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [useRecovery, setUseRecovery] = useState(false);

  // 이미 로그인된 상태로 로그인 화면에 오면 되돌려 보낸다.
  useEffect(() => {
    if (hydrated && isAuthenticated) router.replace(next);
  }, [hydrated, isAuthenticated, next, router]);

  const finish = (res: AdminLoginResponse) => {
    if (completeSignIn(res)) router.replace(next);
  };

  const restart = (why: Notice | null) => {
    login.reset();
    verify.reset();
    startSetup.reset();
    activate.reset();
    setCode('');
    setUseRecovery(false);
    setStage({ kind: 'password' });
    setNotice(why);
  };

  /** 2단계 도중의 실패. 이어갈 수 없는 오류면 처음으로 돌린다. */
  const onStepError = (error: unknown) => {
    if (endsLogin(error)) restart(messageOf(error));
  };

  const submitPassword = (e: React.FormEvent) => {
    e.preventDefault();
    if (!loginId.trim() || !password) return;
    setNotice(null);
    login.mutate(
      { loginId: loginId.trim(), password },
      {
        onSuccess: (res) => {
          setPassword('');
          if (res.step === 'OTP_REQUIRED' && res.stepToken) {
            setStage({ kind: 'otp', stepToken: res.stepToken });
          } else if (res.step === 'OTP_SETUP_REQUIRED' && res.stepToken) {
            setStage({ kind: 'setup', stepToken: res.stepToken, setup: null });
          } else {
            finish(res);
          }
        },
      },
    );
  };

  const submitCode = (stepToken: string, value = code) => {
    if (!isCompleteOtp(value, useRecovery) || verify.isPending) return;
    verify.mutate(
      { stepToken, code: value },
      {
        onSuccess: (res) => {
          if (res.recoveryCodesRemaining != null) {
            setStage({ kind: 'recovered', remaining: res.recoveryCodesRemaining, login: res });
          } else {
            finish(res);
          }
        },
        onError: (error) => {
          setCode('');
          onStepError(error);
        },
      },
    );
  };

  const passwordFailure = login.error ? messageOf(login.error) : null;

  return (
    <div className={styles.root}>
      <div className={styles.card}>
        <div className={styles.brand}>
          한성금거래소
          <span className={styles.badge}>ADMIN</span>
        </div>

        {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}

        {stage.kind === 'password' && (
          <form className={styles.form} onSubmit={submitPassword}>
            <p className={styles.lead}>
              관리자 계정으로 로그인합니다. 비밀번호 다음에 휴대폰 인증 앱의 6자리를 입력합니다.
            </p>

            {passwordFailure && <Alert tone={passwordFailure.tone}>{passwordFailure.text}</Alert>}

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
              <PasswordInput
                id="login-password"
                name="password"
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
              {login.isPending ? '확인 중…' : '다음'}
            </Button>
          </form>
        )}

        {stage.kind === 'otp' && (
          <form
            className={styles.form}
            onSubmit={(e) => {
              e.preventDefault();
              submitCode(stage.stepToken);
            }}
          >
            <div>
              <h1 className={otpStyles.heading}>
                {useRecovery ? '복구 코드 입력' : '인증 코드 입력'}
              </h1>
              <p className={otpStyles.lead}>
                {useRecovery
                  ? '등록할 때 받아 적은 복구 코드 중 아직 쓰지 않은 것 하나를 입력하세요.'
                  : '휴대폰 인증 앱에 표시된 6자리를 입력하세요.'}
              </p>
            </div>

            {verify.error && !endsLogin(verify.error) && (
              <Alert tone="danger">{messageOf(verify.error).text}</Alert>
            )}

            <OtpCodeInput
              id="login-otp"
              key={useRecovery ? 'recovery' : 'totp'}
              value={code}
              recovery={useRecovery}
              autoFocus
              disabled={verify.isPending}
              onChange={setCode}
              onComplete={(value) => submitCode(stage.stepToken, value)}
            />

            <Button
              type="submit"
              variant="gold"
              disabled={verify.isPending || !isCompleteOtp(code, useRecovery)}
            >
              {verify.isPending ? '확인 중…' : '로그인'}
            </Button>

            <button
              type="button"
              className={otpStyles.linkButton}
              onClick={() => {
                setUseRecovery((v) => !v);
                setCode('');
                verify.reset();
              }}
            >
              {useRecovery ? '인증 앱의 6자리로 입력' : '휴대폰을 쓸 수 없나요? 복구 코드로 입력'}
            </button>

            <div className={styles.stepFoot}>
              <span>연속 5회 틀리면 계정이 잠깁니다.</span>
              <button type="button" className={otpStyles.linkButton} onClick={() => restart(null)}>
                처음으로
              </button>
            </div>
          </form>
        )}

        {stage.kind === 'setup' && (
          <div className={styles.form}>
            <div>
              <h1 className={otpStyles.heading}>인증 앱 등록</h1>
              <p className={otpStyles.lead}>
                관리자 화면은 비밀번호와 휴대폰 인증 앱으로 로그인합니다. 처음 한 번만 등록하면
                다음부터는 6자리만 입력하면 됩니다.
              </p>
            </div>

            {stage.setup ? (
              <OtpSetupStep
                setup={stage.setup}
                pending={activate.isPending}
                error={
                  activate.error && !endsLogin(activate.error)
                    ? messageOf(activate.error).text
                    : null
                }
                cancelLabel="처음으로"
                onCancel={() => restart(null)}
                onSubmit={(value) =>
                  activate.mutate(
                    { stepToken: stage.stepToken, code: value },
                    {
                      onSuccess: (res) => {
                        if (res.login) {
                          setStage({ kind: 'codes', codes: res.recoveryCodes, login: res.login });
                        } else {
                          restart({ tone: 'info', text: '등록했습니다. 다시 로그인해 주세요.' });
                        }
                      },
                      onError: onStepError,
                    },
                  )
                }
              />
            ) : (
              <>
                <ol className={otpStyles.steps}>
                  <li>
                    휴대폰에 <strong>Google Authenticator</strong> 또는{' '}
                    <strong>Microsoft Authenticator</strong> 앱을 설치합니다.
                  </li>
                  <li>아래 버튼을 눌러 QR 코드를 받습니다.</li>
                  <li>앱에서 QR 을 찍고, 앱에 뜬 6자리를 입력합니다.</li>
                </ol>

                {startSetup.error && !endsLogin(startSetup.error) && (
                  <Alert tone="danger">{messageOf(startSetup.error).text}</Alert>
                )}

                <Button
                  variant="gold"
                  disabled={startSetup.isPending}
                  onClick={() =>
                    startSetup.mutate(stage.stepToken, {
                      onSuccess: (setup) =>
                        setStage({ kind: 'setup', stepToken: stage.stepToken, setup }),
                      onError: onStepError,
                    })
                  }
                >
                  {startSetup.isPending ? '준비 중…' : 'QR 코드 받기'}
                </Button>

                <div className={styles.stepFoot}>
                  <span>15분 안에 등록을 마쳐 주세요.</span>
                  <button
                    type="button"
                    className={otpStyles.linkButton}
                    onClick={() => restart(null)}
                  >
                    처음으로
                  </button>
                </div>
              </>
            )}
          </div>
        )}

        {stage.kind === 'codes' && (
          <div className={styles.form}>
            <div>
              <h1 className={otpStyles.heading}>등록했습니다</h1>
              <p className={otpStyles.lead}>
                마지막으로 복구 코드를 적어 두면 로그인이 끝납니다.
              </p>
            </div>
            <RecoveryCodesList
              codes={stage.codes}
              doneLabel="관리자 화면으로"
              onDone={() => finish(stage.login)}
            />
          </div>
        )}

        {stage.kind === 'recovered' && (
          <div className={styles.form}>
            <Alert tone="warning" title="복구 코드로 로그인했습니다">
              쓴 코드는 다시 쓸 수 없습니다. 남은 복구 코드는{' '}
              <strong>{stage.remaining}개</strong>입니다.
              <br />
              휴대폰을 잃어버렸다면 최고관리자에게 OTP 초기화를 요청하세요. 휴대폰이 있다면 내
              계정에서 복구 코드를 새로 받을 수 있습니다.
            </Alert>
            <Button variant="gold" onClick={() => finish(stage.login)}>
              관리자 화면으로
            </Button>
          </div>
        )}

        <p className={styles.foot}>
          이 화면은 검색엔진에 노출되지 않으며, 모든 로그인 시도가 감사로그에 기록됩니다.
        </p>
      </div>
    </div>
  );
}
