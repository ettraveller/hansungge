import { create } from 'zustand';
import { persist } from 'zustand/middleware';

/**
 * 클라이언트 전역 상태 — 장바구니 (STEP 12 에서 완성).
 *
 * 중요: 스토어는 <b>금액을 계산하지 않는다.</b> 담은 시점 가격(priceAtAdd)은 서버가 준
 * 값을 그대로 보관만 하며, 합계·최종 결제금액은 항상 서버가 다시 계산한다 (설계서 §13.6).
 * 그래서 여기에 total 계산 함수를 두지 않는다.
 */
export interface CartLine {
  productId: number;
  optionId: number;
  productName: string;
  optionName: string | null;
  quantity: number;
  /** 담은 시점 서버 계산가. 표시·변동 비교용이며 결제 금액이 아니다. */
  priceAtAdd: string;
}

interface CartState {
  lines: CartLine[];
  add: (line: CartLine) => void;
  remove: (productId: number, optionId: number) => void;
  setQuantity: (productId: number, optionId: number, quantity: number) => void;
  clear: () => void;
}

export const useCartStore = create<CartState>()(
  persist(
    (set) => ({
      lines: [],
      add: (line) =>
        set((state) => {
          const idx = state.lines.findIndex(
            (l) => l.productId === line.productId && l.optionId === line.optionId,
          );
          if (idx === -1) return { lines: [...state.lines, line] };
          const next = [...state.lines];
          next[idx] = { ...next[idx], quantity: next[idx].quantity + line.quantity };
          return { lines: next };
        }),
      remove: (productId, optionId) =>
        set((state) => ({
          lines: state.lines.filter(
            (l) => !(l.productId === productId && l.optionId === optionId),
          ),
        })),
      setQuantity: (productId, optionId, quantity) =>
        set((state) => ({
          lines: state.lines.map((l) =>
            l.productId === productId && l.optionId === optionId ? { ...l, quantity } : l,
          ),
        })),
      clear: () => set({ lines: [] }),
    }),
    { name: 'hanseongge-cart' },
  ),
);
