/**
 * 레이아웃에서 쓰는 아이콘.
 *
 * <p>이모지(🔑 🛒 ♡)를 쓰지 않는 이유 — 기기·OS 마다 모양과 크기가 제각각이라 정렬이
 * 어긋나고, 색을 맞출 수 없으며, 스크린리더가 "열쇠"처럼 엉뚱하게 읽는다.
 *
 * <p>선 굵기는 <b>1.5 하나로 통일</b>한다. 섞이면 아이콘들이 들쭉날쭉해 보인다.
 */

interface IconProps {
  size?: number;
  className?: string;
}

function Svg({ size = 20, className, children }: IconProps & { children: React.ReactNode }) {
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {children}
    </svg>
  );
}

export function SearchIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <circle cx="11" cy="11" r="7" />
      <path d="M20 20l-3.5-3.5" />
    </Svg>
  );
}

export function CartIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M6 8h12l-1 11H7L6 8z" />
      <path d="M9 8V6a3 3 0 0 1 6 0v2" />
    </Svg>
  );
}

export function UserIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <circle cx="12" cy="8" r="3.4" />
      <path d="M5 20c0-3.6 3.1-6 7-6s7 2.4 7 6" />
    </Svg>
  );
}

export function HomeIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M4 11l8-6 8 6" />
      <path d="M6 10v9h12v-9" />
    </Svg>
  );
}

/** 시세 — 로켓·화살표 같은 뻔한 은유 대신 추이 선 그 자체를 쓴다 */
export function ChartIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M4 16l5-5 4 4 7-8" />
    </Svg>
  );
}

export function MenuIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M4 7h16" />
      <path d="M4 12h16" />
      <path d="M4 18h10" />
    </Svg>
  );
}
