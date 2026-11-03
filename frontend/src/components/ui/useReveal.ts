'use client';

import { useEffect, useRef } from 'react';

/**
 * 화면에 들어오면 클래스를 하나 붙인다. 그 뒤는 CSS 가 한다.
 *
 * <h2>상태를 React 에 두지 않는다</h2>
 * 순전히 보이는 문제라 다시 그릴 이유가 없다. 한 화면에 열 개가 있으면 열 번의
 * 리렌더가 스크롤 중에 몰린다 — 정작 부드러워야 할 순간에.
 *
 * <h2>한 번만 올린다</h2>
 * 올라온 뒤 관찰을 끊는다. 다시 올라갔다 내려올 때마다 글이 사라졌다 나타나면
 * 읽던 자리를 잃는다.
 *
 * <h2>숨기는 일은 하지 않는다</h2>
 * 이 훅은 <b>보이게만</b> 한다. 숨기는 규칙은 CSS 쪽 {@code @media (scripting: enabled)}
 * 안에 있다 — 그래야 JS 가 막힌 날 본문이 사라지지 않는다.
 *
 * @param onClass 보일 때 붙일 클래스 (CSS 모듈의 `styles.on`)
 */
export function useReveal<T extends HTMLElement>(onClass: string) {
  const ref = useRef<T>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;

    const show = () => el.classList.add(onClass);

    // 아주 오래된 브라우저. 숨겨 둔 채로 두지 않는다.
    if (typeof IntersectionObserver === 'undefined') {
      show();
      return;
    }

    const rect = el.getBoundingClientRect();

    /*
     * 화면 안이거나 <b>이미 지나친 곳</b>이면 지금 올린다.
     *
     * 윗쪽까지 포함하는 이유 — 앵커 링크(#quote), 뒤로가기의 스크롤 복원,
     * Ctrl+End 처럼 중간을 건너뛰고 도착하는 길이 있다. 그때 지나친 블록은
     * 관찰자가 "들어왔다"고 알려 줄 기회를 영영 얻지 못한다.
     *
     * 관찰자도 첫 호출에서 알려 주지만 그것은 한 프레임 뒤라, 첫 화면에 있는
     * 제목이 한 번 깜빡인 뒤 올라온다.
     *
     * ⚠️ 크기가 0 인 것은 제외한다. 숨겨진 탭 안의 요소({@code display:none})는
     * 사각형이 전부 0 이라 "맨 위에 있다"로 읽혀, 아직 보이지도 않는데 다 올라와
     * 버린다. 그러면 탭을 눌러 들어갔을 때 아무것도 움직이지 않는다. 관찰자는
     * 그 요소가 실제로 그려지는 순간 알려 주므로, 그때까지 맡겨 둔다.
     */
    if ((rect.width > 0 || rect.height > 0) && rect.top < window.innerHeight) {
      show();
      return;
    }

    const io = new IntersectionObserver(
      (entries) => {
        if (!entries[0].isIntersecting) return;
        show();
        io.disconnect();
      },
      {
        /*
         * 아래는 12% 당기고, <b>위는 끝까지 연다.</b>
         *
         * 아래 -12% — 가장자리에 닿자마자 올리면 화면 밖에서 이미 다 올라와
         * 버려 움직임이 보이지 않는다. 조금 들어온 뒤에 시작한다.
         *
         * 위 100000px — 한 번에 건너뛰는 길을 위한 것이다. 맨 아래로 단번에
         * 내리면(Ctrl+End, 뒤로가기의 스크롤 복원) 중간 블록은 "아래에 있음"
         * 에서 "위에 있음"으로 바뀌는데, 둘 다 교차하지 <b>않는</b> 상태라
         * 관찰자가 아무것도 알려 주지 않는다 — 콜백이 영영 안 돌고 그 블록은
         * 투명한 채로 남는다. 위를 열어 두면 지나친 순간이 곧 교차가 된다.
         */
        rootMargin: '100000px 0px -12% 0px',
      },
    );
    io.observe(el);
    return () => io.disconnect();
  }, [onClass]);

  return ref;
}
