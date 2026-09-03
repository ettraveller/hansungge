'use client';

import { useState } from 'react';

import { Button } from './Button';
import { Field, TextArea } from './Field';
import { Modal } from './Modal';
import { Alert } from './Alert';

/**
 * 사유를 받아 실행하는 확인 대화상자.
 *
 * 관리자 행위 대부분(상태 변경·재고 조정·시세 승인·비밀번호 초기화)은 백엔드가
 * 사유를 필수로 받고 감사로그에 남긴다. 그 입력을 화면마다 새로 만들면 어딘가는
 * 빠뜨리고 400 을 맞는다 — 한 곳에 모아 둔다.
 */
export function ReasonDialog({
  open,
  title,
  description,
  confirmLabel = '확인',
  danger,
  placeholder = '예: 2026-08-27 실사 반영',
  maxLength = 200,
  pending,
  error,
  onConfirm,
  onClose,
}: {
  open: boolean;
  title: React.ReactNode;
  description?: React.ReactNode;
  confirmLabel?: string;
  danger?: boolean;
  placeholder?: string;
  /** 백엔드 @Size 와 맞춘다. 넘으면 서버가 400 을 준다. */
  maxLength?: number;
  pending?: boolean;
  error?: string | null;
  onConfirm: (reason: string) => void;
  onClose: () => void;
}) {
  // 본문을 열려 있을 때만 마운트한다. 닫으면 통째로 사라지므로 다음에 열 때
  // 사유 입력이 자연히 비어 있다 — effect 로 값을 지우면 이전 사유가 한 프레임 남는다.
  return open ? (
    <ReasonForm
      title={title}
      description={description}
      confirmLabel={confirmLabel}
      danger={danger}
      placeholder={placeholder}
      maxLength={maxLength}
      pending={pending}
      error={error}
      onConfirm={onConfirm}
      onClose={onClose}
    />
  ) : null;
}

function ReasonForm({
  title,
  description,
  confirmLabel,
  danger,
  placeholder,
  maxLength,
  pending,
  error,
  onConfirm,
  onClose,
}: {
  title: React.ReactNode;
  description?: React.ReactNode;
  confirmLabel: string;
  danger?: boolean;
  placeholder: string;
  maxLength: number;
  pending?: boolean;
  error?: string | null;
  onConfirm: (reason: string) => void;
  onClose: () => void;
}) {
  const [reason, setReason] = useState('');

  const tooLong = reason.length > maxLength;
  const canSubmit = reason.trim().length > 0 && !tooLong && !pending;

  return (
    <Modal
      open
      title={title}
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose} disabled={pending}>
            취소
          </Button>
          <Button
            variant={danger ? 'danger' : 'primary'}
            disabled={!canSubmit}
            onClick={() => onConfirm(reason.trim())}
          >
            {pending ? '처리 중…' : confirmLabel}
          </Button>
        </>
      }
    >
      {description && (
        <div style={{ marginBottom: 14 }}>
          <Alert tone={danger ? 'warning' : 'info'}>{description}</Alert>
        </div>
      )}
      {error && (
        <div style={{ marginBottom: 14 }}>
          <Alert tone="danger">{error}</Alert>
        </div>
      )}
      <Field
        label="사유"
        htmlFor="reason-dialog-input"
        required
        hint={`감사로그에 그대로 남는다. ${reason.length}/${maxLength}자`}
        error={tooLong ? `${maxLength}자를 넘을 수 없습니다.` : null}
      >
        <TextArea
          id="reason-dialog-input"
          value={reason}
          invalid={tooLong}
          placeholder={placeholder}
          onChange={(e) => setReason(e.target.value)}
        />
      </Field>
    </Modal>
  );
}
