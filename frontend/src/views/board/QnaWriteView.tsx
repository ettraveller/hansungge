'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { useMutation } from '@tanstack/react-query';

import { qnaApi } from '@/api/board';
import { useUserSession } from '@/hooks/useUserSession';
import { ApiError } from '@/types/api';
import { QNA_BOARD_LABEL, QNA_BOARD_PATH, type QnaBoardType } from '@/types/board';
import styles from './BoardPage.module.css';
import board from '@/components/board/board.module.css';
import form from '@/components/auth/auth.module.css';

interface Props {
  boardType: QnaBoardType;
}

/**
 * 문의 작성 (`/support/qna/write`, `/support/product-qna/write`).
 *
 * <h2>비로그인은 오류가 아니라 정상 상태다</h2>
 * 지금 이 사이트는 소셜 로그인 키가 없어 <b>아무도 로그인할 수 없다.</b> 그러니 이 화면에
 * 도달하는 사람은 대부분 비로그인이고, 빈 폼이나 오류를 보여주면 막다른 길이 된다.
 * 로그인 안내와 함께 <b>전화라는 실제로 되는 길</b>을 준다.
 *
 * <h2>마이페이지 가드와 같은 모양이다</h2>
 * {@code restored} 전에는 비로그인으로 판단하지 않는다 — 새로고침 직후 잠깐 토큰이 없는
 * 구간이 있고, 그때를 로그아웃으로 읽으면 로그인한 사람이 로그인 화면으로 튕긴다.
 */
export function QnaWriteView({ boardType }: Props) {
  const router = useRouter();
  const { isAuthenticated, restored } = useUserSession();

  const [title, setTitle] = useState('');
  const [question, setQuestion] = useState('');
  const [secret, setSecret] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const basePath = QNA_BOARD_PATH[boardType];

  const write = useMutation({
    mutationFn: () => qnaApi.write({ boardType, title, question, secret }),
    onSuccess: (created) => router.replace(`${basePath}/${created.id}`),
    onError: (e) => setFieldErrors(e instanceof ApiError ? (e.fieldErrors ?? {}) : {}),
  });

  // 제출 중에 창을 닫으면 쓰던 글이 사라진다. 최소한 경고는 한다.
  useEffect(() => {
    if (!write.isPending) return;
    const warn = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [write.isPending]);

  if (!restored) {
    return <p className={styles.loadError}>확인 중…</p>;
  }

  if (!isAuthenticated) {
    return <LoginRequired boardType={boardType} />;
  }

  return (
    <div className={styles.page}>
      <div className="wrap">
        <h1 className={form.title}>{QNA_BOARD_LABEL[boardType]} 작성</h1>

        <form
          className={styles.writeForm}
          onSubmit={(e) => {
            e.preventDefault();
            write.mutate();
          }}
        >
          <div className={form.field}>
            <label className={form.label} htmlFor="qna-title">
              제목
            </label>
            <input
              id="qna-title"
              className={form.input}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              maxLength={200}
              required
              placeholder="무엇이 궁금하신가요"
            />
            {fieldErrors.title && <span className={form.error}>{fieldErrors.title}</span>}
          </div>

          <div className={form.field}>
            <label className={form.label} htmlFor="qna-question">
              내용
            </label>
            <textarea
              id="qna-question"
              className={`${form.input} ${styles.textarea}`}
              rows={10}
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              maxLength={4000}
              required
              placeholder="주문번호나 상품명을 함께 적어 주시면 더 빠르게 확인할 수 있습니다."
            />
            {fieldErrors.question && <span className={form.error}>{fieldErrors.question}</span>}
          </div>

          <label className={styles.secretCheck}>
            <input
              type="checkbox"
              checked={secret}
              onChange={(e) => setSecret(e.target.checked)}
            />
            <span>
              비밀글로 등록
              <em>다른 분에게는 제목만 보이고, 내용과 답변은 저와 관리자만 봅니다.</em>
            </span>
          </label>

          {/* 개인정보를 게시판에 적고 나서 지우는 것보다 미리 알리는 편이 낫다 */}
          <p className={styles.writeNotice}>
            연락처·주민등록번호·계좌번호는 적지 말아 주세요. 확인이 필요하면 답변에서
            안내해 드립니다.
          </p>

          {write.isError && (
            <p className={form.error}>
              {write.error instanceof ApiError
                ? write.error.message
                : '문의를 등록하지 못했습니다. 잠시 후 다시 시도해 주세요.'}
            </p>
          )}

          <div className={board.actions}>
            <button type="submit" className={board.primaryBtn} disabled={write.isPending}>
              {write.isPending ? '등록 중…' : '등록'}
            </button>
            <Link href={basePath} className={board.ghostBtn}>
              취소
            </Link>
          </div>
        </form>
      </div>
    </div>
  );
}

/**
 * 로그인이 필요할 때.
 *
 * <p>로그인 버튼만 두지 않는다 — 지금은 눌러도 쓸 수 있는 로그인 수단이 없다.
 * 실제로 되는 길을 함께 준다.
 */
function LoginRequired({ boardType }: { boardType: QnaBoardType }) {
  const basePath = QNA_BOARD_PATH[boardType];

  return (
    <div className={styles.page}>
      <div className="wrap">
        <p className={board.empty}>문의를 남기시려면 로그인이 필요합니다.</p>

        <div className={board.actions}>
          <Link
            href={`/login?reason=required&next=${encodeURIComponent(`${basePath}/write`)}`}
            className={board.primaryBtn}
          >
            로그인
          </Link>
          <Link href="/support" className={board.ghostBtn}>
            전화로 문의하기
          </Link>
          <Link href={basePath} className={board.ghostBtn}>
            목록으로
          </Link>
        </div>
      </div>
    </div>
  );
}
