import Link from 'next/link';
import type { ReactNode } from 'react';

import type { FooterInfo } from '@/api/footer';
import type { TopLink } from '@/api/topLinks';
import { telHref } from '@/data/company';
import { mediaUrl } from '@/utils/media';
import styles from './ShopLayout.module.css';

/**
 * 헤더 위의 가는 유틸 띠 — 좌측부터 대표전화 · 아이콘 링크 · 글자 링크,
 * 오른쪽 끝에 로그인 · 회원가입 · 장바구니 (2026-11-19 헤더에서 올라왔다).
 *
 * <h2>값은 전부 관리자 설정이다</h2>
 * 대표전화는 푸터와 같은 값({@code GET /api/v1/footer})이라 한쪽에서 고치면 둘 다
 * 바뀐다. 링크는 {@code GET /api/v1/top-links} 가 준다 (V48) — 블로그·유튜브 같은
 * 아이콘 버튼과 「스마트스토어」 같은 글자 링크를 관리자 「메인 배너 & 푸터」에서
 * 걸고 내린다.
 *
 * <h2>아이콘을 앞에, 글자를 뒤에 모은다</h2>
 * 등록한 차례는 <b>같은 갈래 안에서만</b> 지킨다. 그림과 글자를 섞어 늘어놓으면
 * 높이와 간격이 들쭉날쭉해져 띠가 한 줄로 읽히지 않는다. 두 갈래 사이에는 가는
 * 금을 긋는다.
 *
 * <h2>{@code renderText} — 관리자 편집기가 꽂는다</h2>
 * 고치는 자리가 곧 손님이 보는 자리다 (푸터와 같은 방식). 그때는 링크를 누를 수
 * 없게 그린다 — 띠를 고치다 아이콘을 잘못 누른 관리자가 저장하지 않은 채로 밖으로
 * 나가게 된다.
 */
export function TopBar({
  info,
  links,
  util,
  renderText,
}: {
  info: FooterInfo;
  links: TopLink[];
  /** 띠 오른쪽 끝 — 로그인·회원가입·장바구니. 쇼핑몰은 진짜를, 편집기는 모양만 넣는다 */
  util?: ReactNode;
  renderText?: (name: keyof FooterInfo, value: string) => ReactNode;
}) {
  const editing = Boolean(renderText);

  // 아이콘 주소를 만들지 못한 것은 글자 링크로 내린다 — 깨진 그림보다 글자가 낫다.
  const withIcon = links.filter((link) => mediaUrl(link.iconKey) != null);
  const textOnly = links.filter((link) => mediaUrl(link.iconKey) == null);

  return (
    <div className={styles.topbar}>
      <div className={`wrap ${styles.topbarRow}`}>
       <div className={styles.topbarScroll}>
        <span className={styles.topbarItem}>
          대표전화{' '}
          {renderText ? (
            renderText('tel', info.tel)
          ) : (
            <a className={styles.topbarTel} href={telHref(info.tel)}>
              {info.tel}
            </a>
          )}
        </span>

        {withIcon.length > 0 && (
          <ul className={styles.topbarIcons}>
            {withIcon.map((link, index) => (
              <li key={link.id ?? `icon-${index}`}>
                <TopBarAnchor link={link} inert={editing} className={styles.topbarIcon}>
                  {/* 띠 한 줄에 스무 픽셀짜리 그림이다. next/image 로 최적화할 것이 없다. */}
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={mediaUrl(link.iconKey) ?? ''} alt={link.label} />
                </TopBarAnchor>
              </li>
            ))}
          </ul>
        )}

        {textOnly.length > 0 && (
          <ul
            className={
              withIcon.length > 0
                ? `${styles.topbarLinks} ${styles.topbarLinksDivided}`
                : styles.topbarLinks
            }
          >
            {textOnly.map((link, index) => (
              <li key={link.id ?? `text-${index}`}>
                <TopBarAnchor link={link} inert={editing} className={styles.topbarLink}>
                  {link.label}
                </TopBarAnchor>
              </li>
            ))}
          </ul>
        )}
       </div>

        {util && <div className={styles.topbarUtil}>{util}</div>}
      </div>
    </div>
  );
}

/**
 * 링크 하나.
 *
 * <p>밖으로 나가는 주소는 새 창으로 연다 — 블로그를 보러 간 손님이 장바구니를 담아
 * 둔 이 창을 잃지 않게 한다. {@code noopener} 는 열린 쪽이 이 창을 조종하지 못하게
 * 하고, {@code noreferrer} 는 어느 화면에서 넘어갔는지를 넘기지 않는다.
 */
function TopBarAnchor({
  link,
  inert,
  className,
  children,
}: {
  link: TopLink;
  /** 관리자 편집 화면. 모양은 같고 누를 수 없다 */
  inert: boolean;
  className: string;
  children: ReactNode;
}) {
  if (inert) {
    return (
      <span className={className} title={`${link.label} → ${link.href}`}>
        {children}
      </span>
    );
  }
  if (link.href.startsWith('/')) {
    return (
      <Link className={className} href={link.href} title={link.label}>
        {children}
      </Link>
    );
  }
  return (
    <a
      className={className}
      href={link.href}
      target="_blank"
      rel="noopener noreferrer"
      title={link.label}
    >
      {children}
    </a>
  );
}
