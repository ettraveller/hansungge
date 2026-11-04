import Link from 'next/link';

import { ALL_BRANCHES } from '@/data/branches';
import { SectionHead } from '@/components/ui/SectionHead';
import styles from './SupportView.module.css';

/** 고객센터에서 갈 수 있는 곳. 링크가 늘면 여기에만 추가한다. */
const DESKS = [
  {
    href: '/support/qna',
    title: '문의게시판',
    desc: '주문·배송·매입 등 무엇이든 물어보세요. 답변은 게시판에 남습니다.',
  },
  {
    href: '/support/product-qna',
    title: '제품문의',
    desc: '상품의 규격·재고·각인 등 제품에 관한 질문을 남기실 수 있습니다.',
  },
  {
    href: '/notice',
    title: '공지사항',
    desc: '배송 일정, 지점 운영, 시세 고시에 관한 안내입니다.',
  },
  {
    href: '/event',
    title: '이벤트',
    desc: '진행 중인 이벤트를 확인하고 참여하실 수 있습니다.',
  },
];

/**
 * 고객센터 (`/support`).
 *
 * <h2>이 페이지가 필요한 이유</h2>
 * GNB 「고객센터」, 모바일 드로어의 「고객센터 전체」, 로그인 화면의 안내 문구까지
 * <b>세 곳이 이 주소를 가리키는데 라우트가 없어 404</b>였다. 특히 로그인 화면의 그
 * 문구는 <i>"소셜 로그인 준비 중입니다 — 주문·문의는 고객센터로 도와드리겠습니다"</i>
 * 라, 로그인이 막힌 사람을 막다른 곳으로 보내고 있었다.
 *
 * <p>그래서 이 페이지의 첫 번째 일은 <b>전화번호를 보여주는 것</b>이다. 게시판은 로그인이
 * 필요하지만 전화는 아니다.
 */
export function SupportView() {
  // 지점 연락처는 지점 데이터가 원천이다. 여기에 다시 적으면 지점이 이전할 때
  // 한쪽만 고쳐지고, 그 차이는 누가 전화를 걸어 보기 전까지 드러나지 않는다.
  /**
   * 전화 문의에 내걸 지점.
   *
   * <p>그냥 첫 지점을 쓰면 안 된다. 지점 목록의 차례는 지역순이고, 맨 앞에 오는
   * 지점이 <b>아직 번호가 안 나온 새 지점</b>일 수 있다 — 그러면 고객센터의
   * 대표번호 자리가 빈 채로 뜬다. 번호가 있는 첫 지점을 고른다.
   */
  const branch = ALL_BRANCHES.find((b) => b.tel) ?? ALL_BRANCHES[0];

  return (
    <div className={styles.page}>
      <div className="wrap">
        <SectionHead as="h1" title="고객센터" desc="궁금한 점을 가장 빠른 방법으로 도와드립니다" />

        {/* ---------- 전화 ---------- */}
        <section className={styles.call} aria-labelledby="support-call">
          <div>
            <h2 id="support-call" className={styles.callLabel}>
              전화 문의
            </h2>
            <p className={`${styles.callNumber} tabular`}>{branch.tel ?? '준비 중'}</p>
            {branch.hours && <p className={styles.callHours}>{branch.hours}</p>}
          </div>
          {branch.tel && (
            <a className={styles.callBtn} href={`tel:${branch.tel.replace(/[^0-9]/g, '')}`}>
              전화 걸기
            </a>
          )}
        </section>

        {/* ---------- 바로가기 ---------- */}
        <section className={styles.desks} aria-label="고객센터 메뉴">
          {DESKS.map((desk) => (
            <Link key={desk.href} href={desk.href} className={styles.desk}>
              <span className={styles.deskTitle}>{desk.title}</span>
              <span className={styles.deskDesc}>{desk.desc}</span>
              <span className={styles.deskGo} aria-hidden="true">
                바로가기 →
              </span>
            </Link>
          ))}
        </section>

        <p className={styles.foot}>
          매입 절차가 궁금하시면 <Link href="/purchase-guide">매입절차 안내</Link>를, 방문을
          계획 중이시면 <Link href="/store">지점찾기</Link>를 확인해 보세요.
        </p>
      </div>
    </div>
  );
}
