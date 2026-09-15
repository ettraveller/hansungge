'use client';

import Image from 'next/image';
import { toDataURL } from 'qrcode';
import { useEffect, useState } from 'react';

import { Alert } from '@/components/admin/Alert';
import { Button } from '@/components/admin/Button';
import { Field } from '@/components/admin/Field';
import type { AdminOtpSetup } from '@/types/admin';
import { isCompleteOtp, OtpCodeInput } from './OtpCodeInput';
import styles from './otp.module.css';

/**
 * 인증 앱 등록 — QR · 키 · 확인 6자리.
 *
 * <p>QR 은 브라우저에서 그린다. 키가 담긴 주소를 외부 QR 서비스로 보내면 그 서비스가 키를 본다.
 *
 * <p>관리 화면을 <b>그 휴대폰으로</b> 열었다면 QR 을 찍을 수 없다. 그래서 "앱에서 바로 열기"
 * 링크와 직접 입력용 키를 함께 보여준다.
 */
export function OtpSetupStep({
  setup,
  pending,
  error,
  onSubmit,
  onCancel,
  cancelLabel = '취소',
}: {
  setup: AdminOtpSetup;
  pending: boolean;
  error: string | null;
  onSubmit: (code: string) => void;
  onCancel?: () => void;
  cancelLabel?: string;
}) {
  const [code, setCode] = useState('');
  const qr = useQrDataUrl(setup.otpauthUri);
  const [copied, setCopied] = useState<'done' | 'failed' | null>(null);

  const submit = (value = code) => {
    if (!isCompleteOtp(value) || pending) return;
    onSubmit(value);
  };

  const copySecret = async () => {
    try {
      await navigator.clipboard.writeText(setup.secret.replace(/\s/g, ''));
      setCopied('done');
    } catch {
      setCopied('failed');
    }
  };

  return (
    <form
      className={styles.stack}
      onSubmit={(e) => {
        e.preventDefault();
        submit();
      }}
    >
      <div className={styles.qrBox}>
        {qr ? (
          <Image
            src={qr}
            alt="인증 앱 등록용 QR 코드"
            width={184}
            height={184}
            unoptimized
            className={styles.qr}
          />
        ) : (
          <div className={styles.qrPlaceholder} aria-hidden="true" />
        )}
        <p className={styles.caption}>
          인증 앱에서 <strong>QR 코드 스캔</strong>을 눌러 찍어 주세요.
          <br />
          앱에는 “{setup.issuer} ({setup.accountName})”로 표시됩니다.
        </p>
      </div>

      <Field
        label="QR 을 찍을 수 없을 때"
        hint={
          copied === 'done'
            ? '키를 복사했습니다. 인증 앱에서 “설정 키 입력”을 골라 붙여 넣으세요.'
            : copied === 'failed'
              ? '복사하지 못했습니다. 키를 보고 직접 입력해 주세요.'
              : '이 휴대폰으로 보고 있다면 “앱에서 열기”를 누르거나, 키를 복사해 인증 앱의 “설정 키 입력”에 넣으세요.'
        }
      >
        <div className={styles.secretRow}>
          <code className={styles.secret}>{setup.secret}</code>
          <Button type="button" small onClick={copySecret}>
            키 복사
          </Button>
          <a className={styles.linkButton} href={setup.otpauthUri}>
            앱에서 열기
          </a>
        </div>
      </Field>

      {error && <Alert tone="danger">{error}</Alert>}

      <Field
        label="앱에 뜬 6자리"
        htmlFor="otp-setup-code"
        required
        hint="등록이 제대로 됐는지 확인합니다. 숫자는 30초마다 바뀝니다."
      >
        <OtpCodeInput
          id="otp-setup-code"
          value={code}
          onChange={setCode}
          onComplete={(value) => submit(value)}
          disabled={pending}
        />
      </Field>

      <div className={styles.actions}>
        {onCancel && (
          <Button type="button" onClick={onCancel} disabled={pending}>
            {cancelLabel}
          </Button>
        )}
        <Button type="submit" variant="gold" disabled={pending || !isCompleteOtp(code)}>
          {pending ? '확인 중…' : '등록 완료'}
        </Button>
      </div>
    </form>
  );
}

function useQrDataUrl(uri: string): string | null {
  const [qr, setQr] = useState<{ uri: string; src: string } | null>(null);

  useEffect(() => {
    let alive = true;
    toDataURL(uri, { errorCorrectionLevel: 'M', margin: 1, width: 368 })
      .then((src) => {
        if (alive) setQr({ uri, src });
      })
      .catch(() => {
        // QR 을 못 그려도 키 직접 입력으로 등록할 수 있다.
      });
    return () => {
      alive = false;
    };
  }, [uri]);

  return qr?.uri === uri ? qr.src : null;
}
