'use client';

import { useState } from 'react';

import { Alert } from '@/components/admin/Alert';
import { Button } from '@/components/admin/Button';
import { CheckboxRow } from '@/components/admin/Field';
import styles from './otp.module.css';

/**
 * 복구 코드 — 이 화면을 닫으면 다시 볼 수 없다.
 *
 * <p>옮겨 적었다고 체크해야 넘어간다. 휴대폰을 잃었을 때 이 코드마저 없으면 최고관리자가
 * 초기화해 줄 때까지 시세를 고칠 수 없다.
 */
export function RecoveryCodesList({
  codes,
  doneLabel,
  onDone,
}: {
  codes: string[];
  doneLabel: string;
  onDone: () => void;
}) {
  const [saved, setSaved] = useState(false);
  const [copied, setCopied] = useState<'done' | 'failed' | null>(null);

  const copyAll = async () => {
    try {
      await navigator.clipboard.writeText(codes.join('\n'));
      setCopied('done');
    } catch {
      setCopied('failed');
    }
  };

  return (
    <div className={styles.stack}>
      <Alert tone="warning" title="복구 코드를 안전한 곳에 적어 두세요">
        휴대폰을 잃어버리거나 바꿨을 때 6자리 대신 넣는 코드입니다. 코드마다 한 번만 쓸 수
        있고, <strong>이 화면을 닫으면 다시 볼 수 없습니다.</strong>
      </Alert>

      <ul className={styles.codes} aria-label="복구 코드">
        {codes.map((code) => (
          <li key={code}>{code}</li>
        ))}
      </ul>

      <div className={styles.actions}>
        <Button type="button" onClick={copyAll}>
          {copied === 'done' ? '복사했습니다' : '전체 복사'}
        </Button>
      </div>
      {copied === 'failed' && (
        <p className={styles.caption}>복사하지 못했습니다. 화면을 보고 직접 옮겨 적어 주세요.</p>
      )}

      <CheckboxRow
        label="복구 코드를 휴대폰이 아닌 곳에 옮겨 적었습니다"
        checked={saved}
        onChange={(e) => setSaved(e.target.checked)}
      />

      <Button type="button" variant="gold" disabled={!saved} onClick={onDone}>
        {doneLabel}
      </Button>
    </div>
  );
}
