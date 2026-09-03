'use client';

import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';

import { qnaApi } from '@/api/board';
import { useUserSession } from '@/hooks/useUserSession';
import { ApiError } from '@/types/api';
import { QNA_BOARD_PATH, type QnaBoardType } from '@/types/board';
import { QnaDetailView } from './QnaDetailView';
import styles from './BoardPage.module.css';
import board from '@/components/board/board.module.css';

interface Props {
  id: number;
  /** 목록으로 돌아갈 곳. 서버가 아는 값이라 props 로 받는다 */
  boardType: QnaBoardType;
}

/**
 * 서버가 못 받아 온 문의를 <b>토큰을 실어</b> 다시 받아 본다.
 *
 * <h2>왜 이 단계가 필요한가</h2>
 * 서버 컴포넌트는 Access 토큰을 가질 수 없다 — 토큰이 브라우저 메모리에만 있기 때문이다.
 * 그래서 비밀글은 서버 조회에서 404 로 온다. 그런데 그 글의 <b>작성자에게는 보여야 한다.</b>
 *
 * <p>없는 글과 남의 비밀글이 같은 404 인 것은 서버의 의도다(존재를 알리지 않는다).
 * 따라서 프론트엔드도 둘을 구분할 수 없고, 구분하려 해서도 안 된다 — 여기서는 그냥
 * 한 번 더 물어보고, 그래도 안 되면 같은 문구를 보여준다.
 */
export function QnaSecretFallback({ id, boardType }: Props) {
  const { isAuthenticated, restored } = useUserSession();

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['qna', 'detail', id],
    queryFn: () => qnaApi.detail(id),
    // 로그인하지 않았으면 물어볼 필요가 없다 — 서버가 이미 같은 답을 줬다
    enabled: restored && isAuthenticated,
    retry: false,
    staleTime: 0,
  });

  // restored 전에는 "비로그인"으로 판단하지 않는다. 새로고침 직후 잠깐 토큰이 없는데,
  // 그 순간을 로그아웃으로 읽으면 작성자에게 "볼 수 없다"가 스쳐 지나간다.
  if (!restored || (isAuthenticated && isPending)) {
    return <p className={styles.loadError}>확인 중…</p>;
  }

  if (data) {
    return <QnaDetailView qna={data} showOwnerActions />;
  }

  const status = isError && error instanceof ApiError ? error.status : null;

  return (
    <div className={styles.page}>
      <div className="wrap">
        <p className={board.empty}>
          {isAuthenticated
            ? '없는 글이거나 볼 수 없는 문의입니다.'
            : '비밀글입니다. 작성하신 분만 보실 수 있습니다.'}
        </p>

        <div className={board.actions}>
          <Link href={QNA_BOARD_PATH[boardType]} className={board.ghostBtn}>
            목록으로
          </Link>
          {!isAuthenticated && (
            <Link
              href={`/login?reason=required&next=${encodeURIComponent(`${QNA_BOARD_PATH[boardType]}/${id}`)}`}
              className={board.primaryBtn}
            >
              로그인
            </Link>
          )}
        </div>

        {status === 401 && (
          <p className={styles.loginNotice}>
            로그인이 만료되었습니다. 다시 로그인해 주세요.
          </p>
        )}
      </div>
    </div>
  );
}
