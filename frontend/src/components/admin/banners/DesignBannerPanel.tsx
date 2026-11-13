'use client';

import type { AdminHeroBanner } from '@/api/admin/banners';
import { HeroSlider } from '@/components/home/HeroSlider';
import { Panel } from '../Panel';
import styles from '../admin.module.css';

/**
 * 화면에서 꾸민 배너들.
 *
 * <h2>지금은 보여주기만 한다</h2>
 * 고치는 화면은 다음 단계다. 그래도 <b>지금 무엇이 나가고 있는지</b>는 보여야 한다 —
 * 보이지 않으면 관리자 화면은 비어 있는데 메인에는 배너가 돌아가는 상태가 되고,
 * 어디서 고치는 것인지 찾을 길이 없다.
 *
 * <p>글로 적지 않고 손님 화면과 <b>같은 컴포넌트</b>를 줄여서 그린다. 글로 적어 두면
 * 배너가 바뀌어도 그 글은 그대로 남아, 관리자 화면이 거짓말을 하게 된다.
 *
 * <p>줄이는 데 {@code transform} 을 쓴다 — 폭을 줄이면 그 폭이 미디어 쿼리를 건드려
 * 휴대폰 모양이 나오고, 정작 보려던 "메인에서 보이는 모양" 이 아니게 된다.
 */
export function DesignBannerPanel({ banners }: { banners: AdminHeroBanner[] }) {
  if (banners.length === 0) {
    return null;
  }

  return (
    <Panel
      title="꾸민 배너"
      note={`지금 메인에서 돌아가고 있는 ${banners.length}장이다. 사진 배너보다 먼저 나간다.`}
    >
      <p className={styles.hint}>
        배경 위에 글자와 버튼을 쌓아 만든 배너다. 글자를 그 자리에서 고치는 화면은 다음에
        붙인다 — 지금은 무엇이 나가고 있는지만 보여준다.
      </p>

      <div className={styles.heroThumb}>
        <div className={styles.heroThumbInner}>
          <HeroSlider
            slides={[]}
            banners={banners.map((b) => ({
              id: b.id,
              design: b.content,
              alt: b.altText,
            }))}
          />
        </div>
      </div>
    </Panel>
  );
}
