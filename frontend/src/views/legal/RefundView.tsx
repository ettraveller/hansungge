import { SectionHead } from '@/components/ui/SectionHead';
import {
  REFUND_ARTICLES,
  REFUND_EFFECTIVE_DATE,
  type RefundArticle,
} from './refund-content';
import styles from './PrivacyView.module.css';

/**
 * 교환·반품·환불 운영규정 (`/refund`).
 *
 * <h2>서버 컴포넌트다</h2>
 * 상태가 없다. 그리고 이 규정은 <b>심사자와 검색엔진이 읽어야 하는 문서</b>라 초기
 * HTML 에 전문이 실려야 한다 — 자바스크립트로 그리면 심사 도구가 빈 페이지를 본다.
 * {@code PrivacyView} 와 같은 판단이다.
 *
 * <h2>스타일을 개인정보처리방침과 공유한다</h2>
 * 두 글이 같은 성격(조문 형식의 법적 문서)이고, 고객이 푸터에서 나란히 열어 본다.
 * 따로 만들면 글자 크기·여백이 조금씩 어긋나서 한쪽이 덜 다듬어진 것처럼 보인다.
 */
export function RefundView() {
  return (
    <div className={styles.page}>
      <div className="wrap">
        <SectionHead title="교환 · 반품 · 환불 운영규정" />

        <p className={styles.effective}>시행일 : {REFUND_EFFECTIVE_DATE}</p>

        <nav className={styles.toc} aria-label="목차">
          <ol>
            {REFUND_ARTICLES.map((a) => (
              <li key={a.title}>
                <a href={`#${slug(a.title)}`}>{a.title}</a>
              </li>
            ))}
          </ol>
        </nav>

        {REFUND_ARTICLES.map((article) => (
          <Article key={article.title} article={article} />
        ))}
      </div>
    </div>
  );
}

function Article({ article }: { article: RefundArticle }) {
  return (
    <section className={styles.section} id={slug(article.title)}>
      <h2 className={styles.heading}>{article.title}</h2>

      {article.body?.map((p, i) => (
        <p key={i} className={styles.para}>
          {p}
        </p>
      ))}

      {article.items && (
        <ol className={styles.numberedList}>
          {article.items.map((item, i) => (
            <li key={i}>
              {item}
              {/*
                항 아래에 붙는 글머리 목록 (제8조 2항의 검수 항목).
                번호를 이어 붙이지 않고 들여쓴 목록으로 둔다 — 원문이 그렇고,
                번호를 매기면 "제8조 3항"이 검수 항목을 가리키는 것으로 읽힌다.
              */}
              {article.subItems?.after === i + 1 && (
                <ul className={styles.subList}>
                  {article.subItems.list.map((sub) => (
                    <li key={sub}>{sub}</li>
                  ))}
                </ul>
              )}
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}

/** 목차 앵커. 제목이 한글이라 조문 번호만 뽑아 쓴다 — URL 에 한글이 인코딩되어 길어진다. */
function slug(title: string): string {
  const num = title.match(/^제(\d+)조/);
  return num ? `a${num[1]}` : 'refund';
}
