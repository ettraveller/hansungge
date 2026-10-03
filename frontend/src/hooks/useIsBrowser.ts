'use client';

import { useSyncExternalStore } from 'react';

/**
 * 서버 렌더에서는 false, 브라우저에서는 true.
 *
 * <p>{@code document} 가 없는 서버에서는 포털을 만들 수 없어 "지금 브라우저인가"를 알아야
 * 한다. effect 안에서 {@code setState} 하는 방식이 흔하지만, 그건 렌더를 한 번 더 돌리고
 * {@code react-hooks/set-state-in-effect} 에도 걸린다.
 *
 * <p>바뀔 일이 없는 값이라 구독은 빈 함수로 둔다 — 알려 줄 변화가 없다.
 */
const subscribeNothing = () => () => {};
const onClient = () => true;
const onServer = () => false;

export function useIsBrowser(): boolean {
  return useSyncExternalStore(subscribeNothing, onClient, onServer);
}
