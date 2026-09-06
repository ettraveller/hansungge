'use client';

import { Fragment, useState } from 'react';

import { Alert } from '@/components/admin/Alert';
import { Button } from '@/components/admin/Button';
import { Pagination } from '@/components/admin/Pagination';
import { Panel } from '@/components/admin/Panel';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useAuditLogs } from '@/hooks/admin/useAuditLogs';
import { formatDateTime } from '@/utils/format';
import type { ActorType, AuditLog } from '@/types/admin';
import { ApiError } from '@/types/api';
import styles from '@/components/admin/admin.module.css';

/**
 * 알려진 행위 목록. 검색에 정확 일치로 쓰이므로 오타가 나면 결과가 0건이다 —
 * 자유 입력 대신 목록에서 고르게 한다. 백엔드 AuditActions 와 값이 같아야 한다.
 */
const ACTION_GROUPS: { label: string; actions: [string, string][] }[] = [
  {
    label: '인증',
    actions: [
      ['ADMIN_LOGIN', '로그인 성공'],
      ['ADMIN_LOGIN_FAILED', '로그인 실패'],
      ['ADMIN_LOGIN_BLOCKED', '로그인 차단'],
      ['ADMIN_ACCOUNT_AUTO_LOCKED', '계정 자동 잠김'],
      ['ADMIN_LOGOUT', '로그아웃'],
      ['ADMIN_TOKEN_REFRESH', '토큰 재발급'],
    ],
  },
  {
    label: '관리자 계정',
    actions: [
      ['ADMIN_CREATE', '계정 생성'],
      ['ADMIN_UPDATE', '계정 수정'],
      ['ADMIN_STATUS_CHANGE', '상태 변경'],
      ['ADMIN_PASSWORD_CHANGE', '비밀번호 변경'],
      ['ADMIN_PASSWORD_RESET', '비밀번호 초기화'],
    ],
  },
  {
    label: '시세',
    actions: [
      ['PRICE_APPLY', '시세 고시'],
      ['PRICE_APPLY_HELD', '시세 보류(승인 대기)'],
      ['PRICE_POLICY_UPDATE', '보정 정책 변경'],
      ['PRICE_PENDING_APPROVE', '승인'],
      ['PRICE_PENDING_REJECT', '반려'],
    ],
  },
  {
    label: '상품 · 카테고리',
    actions: [
      ['PRODUCT_CREATE', '상품 등록'],
      ['PRODUCT_UPDATE', '상품 수정'],
      ['PRODUCT_STATUS_CHANGE', '상품 상태 변경'],
      ['PRODUCT_OPTION_CREATE', '옵션 추가'],
      ['PRODUCT_OPTION_UPDATE', '옵션 수정'],
      ['PRODUCT_STOCK_ADJUST', '재고 조정'],
      ['PRODUCT_IMAGE_CHANGE', '이미지 변경'],
      ['CATEGORY_CREATE', '카테고리 등록'],
      ['CATEGORY_UPDATE', '카테고리 수정'],
      ['CATEGORY_DELETE', '카테고리 삭제'],
    ],
  },
];

/** 실패·차단 계열은 눈에 띄어야 한다. 정상 로그 사이에 섞이면 놓친다. */
function toneOf(action: string): 'danger' | 'warning' | 'neutral' {
  if (action.endsWith('_FAILED') || action.endsWith('_BLOCKED')) return 'danger';
  if (action.includes('LOCKED') || action.includes('HELD') || action.includes('REJECT')) {
    return 'warning';
  }
  return 'neutral';
}

/** 날짜 입력(YYYY-MM-DD)을 KST 기준 ISO 로 바꾼다. 서버는 [from, to) 로 해석한다. */
function toIso(date: string, endOfDay: boolean): string | null {
  if (!date) return null;
  return endOfDay ? `${date}T23:59:59+09:00` : `${date}T00:00:00+09:00`;
}

/**
 * 감사로그.
 *
 * 이 표는 <b>지워지지 않는 기록</b>이다. "누가 언제 무엇을 어떤 값에서 어떤 값으로
 * 바꿨는가"가 전부 남는다. 비밀번호·토큰 같은 값은 백엔드가 저장 시점에 마스킹한다.
 */
export function AdminAuditLogsView() {
  const [actor, setActor] = useState('');
  const [actorType, setActorType] = useState('');
  const [action, setAction] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [applied, setApplied] = useState({});
  const [page, setPage] = useState(0);
  const [expanded, setExpanded] = useState<number | null>(null);

  const search = { ...applied, page };
  const { data, isLoading, error } = useAuditLogs(search);
  const rows = data?.content ?? [];

  const apply = () => {
    setApplied({
      actor: actor.trim() === '' ? null : actor.trim(),
      actorType: actorType === '' ? null : (actorType as ActorType),
      action: action === '' ? null : action,
      from: toIso(from, false),
      to: toIso(to, true),
    });
    setPage(0);
  };

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <div>
          <h1 className={styles.title}>감사로그</h1>
          <p className={styles.subtitle}>
            관리자 행위 기록. 수정도 삭제도 되지 않으며 최신순으로 고정 정렬된다.
            비밀번호·토큰 값은 저장 시점에 마스킹된다.
          </p>
        </div>
      </header>

      {error && (
        <Alert tone="danger">
          {error instanceof ApiError ? error.message : '감사로그를 불러오지 못했습니다.'}
        </Alert>
      )}

      <Panel>
        <form
          className={styles.filters}
          onSubmit={(e) => {
            e.preventDefault();
            apply();
          }}
        >
          <div className={styles.filterField} style={{ minWidth: 160 }}>
            <label className={styles.label} htmlFor="a-actor">
              행위자
            </label>
            <input
              id="a-actor"
              className={styles.input}
              value={actor}
              placeholder="부분 일치"
              onChange={(e) => setActor(e.target.value)}
            />
          </div>

          <div className={styles.filterField}>
            <label className={styles.label} htmlFor="a-actor-type">
              주체
            </label>
            <select
              id="a-actor-type"
              className={styles.select}
              value={actorType}
              onChange={(e) => setActorType(e.target.value)}
            >
              <option value="">전체</option>
              <option value="ADMIN">관리자</option>
              <option value="USER">회원</option>
              <option value="SYSTEM">시스템</option>
            </select>
          </div>

          <div className={styles.filterField} style={{ minWidth: 200 }}>
            <label className={styles.label} htmlFor="a-action">
              행위
            </label>
            <select
              id="a-action"
              className={styles.select}
              value={action}
              onChange={(e) => setAction(e.target.value)}
            >
              <option value="">전체</option>
              {ACTION_GROUPS.map((group) => (
                <optgroup key={group.label} label={group.label}>
                  {group.actions.map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </optgroup>
              ))}
            </select>
          </div>

          <div className={styles.filterField}>
            <label className={styles.label} htmlFor="a-from">
              시작일
            </label>
            <input
              id="a-from"
              type="date"
              className={styles.input}
              value={from}
              onChange={(e) => setFrom(e.target.value)}
            />
          </div>

          <div className={styles.filterField}>
            <label className={styles.label} htmlFor="a-to">
              종료일
            </label>
            <input
              id="a-to"
              type="date"
              className={styles.input}
              value={to}
              onChange={(e) => setTo(e.target.value)}
            />
          </div>

          <Button type="submit" variant="primary">
            검색
          </Button>
          <Button
            onClick={() => {
              setActor('');
              setActorType('');
              setAction('');
              setFrom('');
              setTo('');
              setApplied({});
              setPage(0);
            }}
          >
            초기화
          </Button>
        </form>
      </Panel>

      <Panel flush>
        {isLoading ? (
          <p className={styles.loading}>불러오는 중…</p>
        ) : rows.length === 0 ? (
          <p className={styles.empty}>조건에 맞는 기록이 없습니다.</p>
        ) : (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>시각</th>
                  <th>행위자</th>
                  <th>행위</th>
                  <th>대상</th>
                  <th>IP</th>
                  <th aria-label="변경 내용" />
                </tr>
              </thead>
              <tbody>
                {rows.map((log) => (
                  <Fragment key={log.id}>
                    <tr>
                      <td data-label="시각" className={styles.muted}>{formatDateTime(log.createdAt)}</td>
                      <td data-label="행위자" className={styles.strong}>
                        {log.actor}
                        <span className={styles.muted}> · {log.actorType}</span>
                      </td>
                      <td data-label="행위" data-card-title>
                        <StatusBadge tone={toneOf(log.action)}>{log.action}</StatusBadge>
                      </td>
                      <td data-label="대상" className={styles.muted}>
                        {log.targetType ? `${log.targetType} #${log.targetId ?? '-'}` : '-'}
                      </td>
                      <td data-label="IP" className={styles.muted}>{log.ip ?? '-'}</td>
                      <td data-label="변경 내용" data-card-actions>
                        {log.beforeValue || log.afterValue ? (
                          <Button
                            small
                            onClick={() => setExpanded(expanded === log.id ? null : log.id)}
                            aria-expanded={expanded === log.id}
                          >
                            {expanded === log.id ? '접기' : '변경 내용'}
                          </Button>
                        ) : (
                          <span className={styles.muted}>-</span>
                        )}
                      </td>
                    </tr>
                    {expanded === log.id && (
                      <tr>
                        <td colSpan={6} style={{ background: 'var(--light-gray)' }}>
                          <ChangeDetail log={log} />
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {data && (
          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            onChange={setPage}
          />
        )}
      </Panel>
    </div>
  );
}

/** JSONB 값은 서버가 준 문자열이다. 보기 좋게 들여쓰되, 파싱에 실패하면 원문을 그대로 둔다. */
function pretty(raw: string | null | undefined): string {
  if (!raw) return '-';
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}

function ChangeDetail({ log }: { log: AuditLog }) {
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 12, padding: '10px 0' }}>
      <div>
        <p className={styles.statLabel}>변경 전</p>
        <pre className={styles.jsonBlock}>{pretty(log.beforeValue)}</pre>
      </div>
      <div>
        <p className={styles.statLabel}>변경 후</p>
        <pre className={styles.jsonBlock}>{pretty(log.afterValue)}</pre>
      </div>
      {log.userAgent && (
        <div style={{ gridColumn: '1 / -1' }}>
          <p className={styles.statLabel}>User-Agent</p>
          <p className={styles.hint}>{log.userAgent}</p>
        </div>
      )}
    </div>
  );
}
