'use client';

/**
 * 「오늘 하루 보지 않기」 기록 — 이 브라우저에만 남는다.
 *
 * <h2>왜 서버가 아닌가</h2>
 * 팝업은 로그인 전에도 뜬다. 그 사람에게는 서버에 기록할 자리가 없다.
 *
 * <h2>왜 외부 저장소(useSyncExternalStore)로 두나</h2>
 * localStorage 는 React 바깥의 값이다. effect 에서 읽어 setState 하면 첫 칠 뒤
 * 한 번 더 그려지고, 서버가 그린 결과와 어긋난다. React 에게 "이건 바깥 값"이라고
 * 알려 주면 서버에서는 {@code null}(아직 모름), 브라우저에서는 실제 값을 쓴다.
 *
 * <h2>못 읽어도 막지 않는다</h2>
 * 사생활 보호 창이나 저장소를 막아 둔 브라우저에서는 읽기·쓰기가 모두 실패한다.
 * 그때는 조용히 넘어가고 팝업이 다시 뜬다 — 안 뜨는 것보다 낫다.
 */

const KEY = 'hsg.popup.hidden';

/**
 * 스냅샷을 캐시해 둔다.
 *
 * <p>{@code useSyncExternalStore} 는 매번 같은 참조를 받아야 한다. 호출할 때마다 새
 * 배열을 만들면 React 가 "바뀌었다"고 보고 무한히 다시 그린다.
 */
let cache: number[] = [];
let cacheRaw: string | null = null;
const listeners = new Set<() => void>();

export function subscribeHidden(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** 오늘 하루 숨긴 팝업 id 들. 브라우저에서만 뜻이 있다. */
export function getHiddenSnapshot(): number[] {
  let raw: string | null = null;
  try {
    raw = window.localStorage.getItem(KEY);
  } catch {
    return cache;
  }
  // 저장소가 그대로면 지난 배열을 그대로 돌려준다 (위 주석 — 참조가 같아야 한다)
  if (raw === cacheRaw) return cache;

  cacheRaw = raw;
  cache = parse(raw);
  return cache;
}

/** 서버에는 저장소가 없다. null 은 "아직 모른다"는 뜻이고, 그동안 팝업을 띄우지 않는다. */
export function getHiddenServerSnapshot(): number[] | null {
  return null;
}

/**
 * 오늘 자정까지 숨긴다.
 *
 * <p>「24시간」이 아니라 <b>자정까지</b>다. 밤 11시에 누른 사람에게 다음 날 밤 11시까지
 * 숨기면 이틀을 안 보는 셈이 된다. 「오늘 하루」라고 적었으면 오늘까지다.
 */
export function hidePopupToday(id: number): void {
  try {
    const parsed = parseMap(window.localStorage.getItem(KEY));
    const midnight = new Date();
    midnight.setHours(24, 0, 0, 0);

    const now = Date.now();
    const next: Record<string, number> = { [String(id)]: midnight.getTime() };
    // 지난 기록은 버린다. 안 그러면 이 칸이 영영 자란다.
    for (const [key, until] of Object.entries(parsed)) {
      if (until > now) next[key] = until;
    }
    window.localStorage.setItem(KEY, JSON.stringify(next));
  } catch {
    // 저장소를 막아 둔 브라우저다. 다음에 또 뜨는 것뿐이다.
  }
  for (const listener of listeners) listener();
}

function parse(raw: string | null): number[] {
  const now = Date.now();
  return Object.entries(parseMap(raw))
    .filter(([, until]) => until > now)
    .map(([id]) => Number(id))
    .filter((id) => Number.isFinite(id));
}

function parseMap(raw: string | null): Record<string, number> {
  if (!raw) return {};
  try {
    const parsed: unknown = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? (parsed as Record<string, number>) : {};
  } catch {
    // 손으로 고쳤거나 옛 형식이다. 버리고 처음부터 쌓는다.
    return {};
  }
}
