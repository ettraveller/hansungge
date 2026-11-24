import Link from 'next/link';

import { groupFaqs, type Faq } from '@/api/faqs';
import { SectionHead } from '@/components/ui/SectionHead';
import styles from './FaqView.module.css';

/**
 * 자주 묻는 질문 (`/support/faq`).
 *
 * <h2>서버에서 그리고, 접고 펴는 것은 브라우저가 한다</h2>
 * {@code details}/{@code summary} 를 쓴다. 자바스크립트로 접으면 답이 초기 HTML 에서
 * 빠져 검색엔진이 읽지 못한다 — 이 화면은 "무통장입금 되나요" 같은 검색으로 들어오는
 * 자리다. 키보드로도 그대로 열린다.
 *
 * <h2>답은 글자 그대로</h2>
 * HTML 로 꽂지 않는다. 줄바꿈만 CSS({@code white-space: pre-line})로 살린다.
 */
export function FaqView({ faqs }: { faqs: Faq[] | null }) {
  const groups = groupFaqs(faqs ?? []);

  return (
    <div className={styles.page}>
      <div className="wrap">
        <SectionHead as="h1" title="자주 묻는 질문" desc="많이 받는 질문과 답을 모았습니다" />

        {faqs === null ? (
          <p className={styles.empty}>질문을 불러올 수 없습니다. 잠시 후 다시 시도해 주세요.</p>
        ) : groups.length === 0 ? (
          <p className={styles.empty}>
            아직 등록된 질문이 없습니다. 궁금한 점은{' '}
            <Link href="/support/qna/write">문의게시판</Link>에 남겨 주세요.
          </p>
        ) : (
          <>
            {/* 묶음이 여럿이면 위에서 바로 건너뛰게 한다 */}
            {groups.length > 1 && (
              <nav className={styles.toc} aria-label="질문 묶음">
                {groups.map((group, index) => (
                  <a key={group.category} href={`#faq-${index}`}>
                    {group.category}
                  </a>
                ))}
              </nav>
            )}

            {groups.map((group, index) => (
              <section key={group.category} id={`faq-${index}`} className={styles.group}>
                <h2 className={styles.groupTitle}>{group.category}</h2>
                <div className={styles.items}>
                  {group.items.map((faq, i) => (
                    <details key={faq.id ?? `${index}-${i}`} className={styles.item}>
                      <summary className={styles.question}>
                        <span className={styles.mark} aria-hidden="true">
                          Q
                        </span>
                        {faq.question}
                      </summary>
                      <p className={styles.answer}>{faq.answer}</p>
                    </details>
                  ))}
                </div>
              </section>
            ))}

            <p className={styles.foot}>
              찾는 답이 없으면 <Link href="/support/qna/write">문의게시판</Link>에 남겨 주세요.
            </p>
          </>
        )}
      </div>
    </div>
  );
}
