import Link from 'next/link';

import { SectionHead } from '@/components/ui/SectionHead';
import { GUIDE_SECTIONS, type GuideSection } from './guide-content';
import styles from './GuideView.module.css';

/**
 * 이용안내 (`/guide`).
 *
 * <h2>이 주소가 이미 쓰이고 있었다</h2>
 * 푸터가 진작 {@code /guide} 를 링크하고 있어서 <b>운영 사이트에서 404 가 나고 있었다.</b>
 * 개인정보처리방침({@code /privacy})이 겪은 것과 같은 일이다.
 *
 * <h2>서버 컴포넌트다</h2>
 * 상태가 없다. 그리고 이 글은 <b>심사자와 검색엔진이 읽어야 하는 문서</b>다 — KG이니시스
 * 통합인증 심사에 「상품·서비스 설명」과 「환불 정책」을 볼 수 있는 주소로 제출한다.
 * 자바스크립트로 그리면 검수 도구가 빈 페이지를 본다.
 */
export function GuideView() {
  return (
    <div className={styles.page}>
      <div className="wrap">
        <SectionHead title="이용안내" />

        <p className={styles.lead}>
          주문 전에 확인하실 것과 배송·교환·반품·A/S 기준을 적었습니다. 개인정보의 수집·이용은{' '}
          <Link href="/privacy">개인정보처리방침</Link>에서 보실 수 있습니다.
        </p>

        <nav className={styles.toc} aria-label="목차">
          {GUIDE_SECTIONS.map((s) => (
            <a key={s.title} href={`#${slug(s.title)}`}>
              {s.title}
            </a>
          ))}
        </nav>

        {GUIDE_SECTIONS.map((section) => (
          <Section key={section.title} section={section} />
        ))}

        <p className={styles.foot}>
          궁금한 점은 <Link href="/support/qna/write">1:1 문의</Link>로 남겨 주세요. 상품에 대한
          질문은 <Link href="/support/product-qna">상품 문의</Link>에서도 받습니다.
        </p>
      </div>
    </div>
  );
}

function Section({ section }: { section: GuideSection }) {
  return (
    <section className={styles.section} id={slug(section.title)}>
      <h2 className={styles.heading}>{section.title}</h2>

      {section.lead && <p className={styles.sectionLead}>{section.lead}</p>}

      <ul className={styles.list}>
        {section.items.map((item, i) => (
          // 본문에만 <b> 를 쓴다. 내용은 저장소 안의 고정 문자열이고 사용자 입력이 아니다.
          <li key={i} dangerouslySetInnerHTML={{ __html: item }} />
        ))}
      </ul>

      {section.callout && <p className={styles.callout}>{section.callout}</p>}
    </section>
  );
}

/** 목차 앵커. 제목이 한글이라 번호만 뽑아 쓴다 — URL 에 한글이 인코딩되어 길어진다. */
function slug(title: string): string {
  const num = title.match(/^\d+/);
  return num ? `g${num[0]}` : 'guide';
}
