import type { Category } from '@/types/product';

export interface NavChild {
  href: string;
  label: string;
}

export interface NavItem {
  href: string;
  label: string;
  children: NavChild[];
}

/**
 * 상품 카테고리 앞뒤로 붙는 고정 메뉴.
 *
 * <p>카테고리는 관리자가 바꾸는 데이터라 서버에서 받아 오지만, 이 메뉴들은 <b>화면 자체</b>라
 * 코드에 둔다. 카테고리 테이블에 "매입절차" 같은 행을 만들어 넣으면 상품이 0개인 카테고리가
 * 목록에 섞이고, 그걸 걸러내는 예외 처리가 여기저기 생긴다.
 */
const LEADING: NavItem[] = [
  { href: '/price', label: '시세조회', children: [] },
];

const TRAILING: NavItem[] = [
  { href: '/purchase-guide', label: '매입절차', children: [] },
  { href: '/store', label: '지점안내', children: [] },
  {
    href: '/support',
    label: '고객센터',
    children: [
      { href: '/support/qna', label: '문의게시판' },
      { href: '/support/product-qna', label: '제품문의' },
      { href: '/event', label: '이벤트' },
      { href: '/notice', label: '공지사항' },
      // 뉴스센터(`/news`)는 뺐다. 만들지 않기로 했고, 남겨 두면 영구히 404 인 메뉴가 된다.
      // 백엔드 news 테이블은 그대로 있으므로 나중에 필요해지면 한 줄 되돌리면 된다.
    ],
  },
];

/**
 * 카테고리 트리 → GNB 항목.
 *
 * <p>하위 카테고리가 드롭다운이 된다. 서버 데이터로 만들기 때문에 관리자가 카테고리를
 * 추가하면 <b>배포 없이</b> 메뉴에 나타난다.
 *
 * <p>백엔드가 null 필드를 생략하므로({@code non_null}) 최상위 판정은
 * {@code parentId == null} — 느슨한 비교여야 {@code undefined} 도 잡힌다.
 */
export function buildNav(categories: Category[] | null): NavItem[] {
  if (!categories || categories.length === 0) {
    return [...LEADING, ...TRAILING];
  }

  const sorted = [...categories].sort((a, b) => a.sort - b.sort || a.id - b.id);
  const roots = sorted.filter((c) => c.parentId == null);

  const categoryItems: NavItem[] = roots.map((root) => ({
    href: `/shop?categoryId=${root.id}`,
    label: root.name,
    children: sorted
      .filter((c) => c.parentId === root.id)
      .map((child) => ({
        href: `/shop?categoryId=${child.id}`,
        label: child.name,
      })),
  }));

  return [...LEADING, ...categoryItems, ...TRAILING];
}
