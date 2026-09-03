'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';

import { eventApi } from '@/api/board';
import { useUserSession } from '@/hooks/useUserSession';
import { ApiError } from '@/types/api';
import type { EventStatus } from '@/types/board';
import styles from './BoardPage.module.css';
import board from '@/components/board/board.module.css';
import form from '@/components/auth/auth.module.css';

interface Props {
  eventId: number;
  status: EventStatus;
  alreadyEntered: boolean;
}

/**
 * 이벤트 응모 폼.
 *
 * <h2>기간 밖에서는 폼을 그리지 않는다</h2>
 * 서버도 거절하지만, 눌러 보고 나서 거절당하는 것과 처음부터 없는 것은 다르다.
 * <b>서버 검증을 없애지는 않는다</b> — 종료 직전에 열어 둔 화면에서 누르거나 API 를
 * 직접 부르면 그대로 들어오기 때문이다. 화면은 안내이고 판단은 서버가 한다.
 */
export function EventEntryForm({ eventId, status, alreadyEntered }: Props) {
  const router = useRouter();
  const { isAuthenticated, restored } = useUserSession();

  const [content, setContent] = useState('');
  const [secret, setSecret] = useState(true);

  const enter = useMutation({
    mutationFn: () => eventApi.enter(eventId, content, secret),
    onSuccess: () => {
      setContent('');
      // 응모 목록은 서버가 그린다. 새로 받아야 방금 쓴 글이 보인다
      router.refresh();
    },
  });

  if (status === 'UPCOMING') {
    return <p className={styles.loginNotice}>아직 응모 기간이 아닙니다.</p>;
  }

  if (status === 'CLOSED') {
    return <p className={styles.loginNotice}>종료된 이벤트입니다.</p>;
  }

  if (!restored) {
    return null;
  }

  if (!isAuthenticated) {
    return (
      <p className={styles.loginNotice}>
        응모하시려면 로그인이 필요합니다.
        <Link href={`/login?reason=required&next=${encodeURIComponent(`/event/${eventId}`)}`}>
          로그인
        </Link>
      </p>
    );
  }

  if (alreadyEntered) {
    return <p className={styles.loginNotice}>이미 응모하셨습니다. 결과를 기다려 주세요.</p>;
  }

  return (
    <form
      className={styles.entryForm}
      onSubmit={(e) => {
        e.preventDefault();
        enter.mutate();
      }}
    >
      <div className={form.field}>
        <label className="sr-only" htmlFor="event-entry">
          응모 내용
        </label>
        <textarea
          id="event-entry"
          className={`${form.input} ${styles.entryTextarea}`}
          rows={3}
          value={content}
          onChange={(e) => setContent(e.target.value)}
          maxLength={2000}
          required
          placeholder="응모 내용을 입력해 주세요."
        />
      </div>

      <div className={styles.entryFormFoot}>
        <label className={styles.secretInline}>
          <input type="checkbox" checked={secret} onChange={(e) => setSecret(e.target.checked)} />
          비밀글
        </label>

        <button type="submit" className={board.primaryBtn} disabled={enter.isPending}>
          {enter.isPending ? '등록 중…' : '응모하기'}
        </button>
      </div>

      {enter.isError && (
        <p className={form.error}>
          {enter.error instanceof ApiError
            ? enter.error.message
            : '응모하지 못했습니다. 잠시 후 다시 시도해 주세요.'}
        </p>
      )}
    </form>
  );
}
