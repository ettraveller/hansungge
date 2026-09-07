'use client';

import { useState } from 'react';

import { Alert } from '@/components/admin/Alert';
import { Button } from '@/components/admin/Button';
import { Pagination } from '@/components/admin/Pagination';
import { Panel } from '@/components/admin/Panel';
import { StatusBadge, type StatusTone } from '@/components/ui/StatusBadge';
import { useAdminSession } from '@/hooks/admin/useAdminSession';
import { useMemberCounts, useMembers } from '@/hooks/admin/useMembers';
import { ApiError } from '@/types/api';
import {
  MEMBER_STATUS_LABEL,
  MEMBER_STATUS_ORDER,
  type AdminMemberSummary,
  type MemberStatus,
} from '@/types/member';
import { formatDateTime } from '@/utils/format';
import { MemberDetailDialog } from './members/MemberDetailDialog';
import { MemberStatusDialog } from './members/MemberStatusDialog';
import { MemberUnlockDialog } from './members/MemberUnlockDialog';
import styles from '@/components/admin/admin.module.css';

type StatusFilter = MemberStatus | 'ALL';

const STATUS_TONE: Record<MemberStatus, StatusTone> = {
  ACTIVE: 'success',
  BLOCKED: 'danger',
  DORMANT: 'warning',
  WITHDRAWN: 'neutral',
};

/**
 * 회원 관리.
 *
 * <h2>목록은 가려져 있다</h2>
 * 이메일·이름·휴대폰이 <b>서버에서 가려져</b> 온다. 목록은 한 번에 20줄이고 검색으로
 * 훑을 수 있어서, 여기서 전부 드러내면 회원 데이터베이스를 페이지 단위로 내려받는
 * 창구가 된다.
 *
 * <p>전체 값은 상세에서 본다. 그리고 <b>그 조회는 감사로그에 남는다</b> — 개인정보를
 * 볼 수 있게 하는 것과 아무도 모르게 보도록 두는 것은 다른 문제다.
 *
 * <h2>잠금과 이용정지는 다른 버튼이다</h2>
 * <ul>
 *   <li><b>잠금</b> — 연속 로그인 실패로 자동으로 걸리고, 30분 뒤 스스로 풀린다.
 *       상태는 그대로 「정상」이다. 「잠금 해제」는 그 30분을 기다리지 않게 해 줄 뿐이다</li>
 *   <li><b>이용정지</b> — 관리자가 걸고, 스스로 풀리지 않는다</li>
 * </ul>
 * 한 버튼으로 묶으면 잠긴 고객을 풀어 주려다 정지시키는 일이 생긴다.
 */
export function AdminMembersView() {
  const { can } = useAdminSession();
  const canViewDetail = can('MANAGER');
  const canUnlock = can('MANAGER');
  const canChangeStatus = can('ADMIN');

  const [keyword, setKeyword] = useState('');
  const [applied, setApplied] = useState('');
  const [status, setStatus] = useState<StatusFilter>('ALL');
  const [page, setPage] = useState(0);

  const [viewing, setViewing] = useState<number | null>(null);
  const [unlocking, setUnlocking] = useState<AdminMemberSummary | null>(null);
  const [changing, setChanging] = useState<AdminMemberSummary | null>(null);

  const { data, isLoading, error } = useMembers({
    keyword: applied || undefined,
    status: status === 'ALL' ? undefined : status,
    page,
  });
  const { data: counts } = useMemberCounts();

  const rows = data?.content ?? [];

  /** 필터를 바꾸면 첫 페이지로 돌아간다 — 3페이지에서 필터를 바꾸면 빈 화면이 나온다. */
  const changeFilter = (next: () => void) => {
    next();
    setPage(0);
  };

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <div>
          <h1 className={styles.title}>회원 관리</h1>
          <p className={styles.subtitle}>
            회원을 찾고, 로그인 잠금을 풀거나 이용을 정지합니다. 회원 정보는 고칠 수
            없습니다 — 이름·생년월일·휴대폰은 본인확인으로만 채워지는 값입니다.
          </p>
        </div>
      </header>

      {!canViewDetail && (
        <Alert tone="info">
          상세 조회와 검색은 MANAGER 이상이 할 수 있습니다. 목록의 값은 가려져 있습니다.
        </Alert>
      )}

      {counts && (
        <div className={styles.summaryRow}>
          {MEMBER_STATUS_ORDER.map((s) => (
            <button
              key={s}
              type="button"
              className={`${styles.summaryCard} ${status === s ? styles.summaryCardOn : ''}`}
              aria-pressed={status === s}
              onClick={() => changeFilter(() => setStatus(status === s ? 'ALL' : s))}
            >
              <span className={styles.summaryLabel}>{MEMBER_STATUS_LABEL[s]}</span>
              <strong className={`${styles.summaryValue} tabular`}>{counts[s] ?? 0}</strong>
            </button>
          ))}
        </div>
      )}

      <Panel
        title="회원 목록"
        note="이메일·이름·휴대폰은 가려서 보여줍니다. 전체 값은 상세에서 확인하며, 그 조회는 감사로그에 남습니다."
        actions={
          <form
            className={styles.filters}
            onSubmit={(e) => {
              e.preventDefault();
              changeFilter(() => setApplied(keyword));
            }}
          >
            {/*
              검색은 가리기 전 원본을 대상으로 하므로 서버가 MANAGER 를 요구한다.
              OPERATOR 에게 입력칸을 보여주면 눌렀을 때만 403 을 알게 된다 — 쓸 수 없는
              것은 그리지 않는다.
            */}
            {canViewDetail && (
              <>
                <input
                  className={styles.input}
                  value={keyword}
                  onChange={(e) => setKeyword(e.target.value)}
                  placeholder="이메일 · 이름 · 휴대폰"
                  aria-label="회원 검색"
                />
                <Button small type="submit">
                  검색
                </Button>
              </>
            )}
            {applied && (
              <Button
                small
                type="button"
                onClick={() =>
                  changeFilter(() => {
                    setKeyword('');
                    setApplied('');
                  })
                }
              >
                초기화
              </Button>
            )}
            <select
              className={styles.select}
              value={status}
              onChange={(e) => changeFilter(() => setStatus(e.target.value as StatusFilter))}
              aria-label="회원 상태"
            >
              <option value="ALL">전체 상태</option>
              {MEMBER_STATUS_ORDER.map((s) => (
                <option key={s} value={s}>
                  {MEMBER_STATUS_LABEL[s]}
                </option>
              ))}
            </select>
          </form>
        }
        flush
      >
        {error ? (
          <Alert tone="danger" title="회원을 불러오지 못했습니다">
            {error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요.'}
          </Alert>
        ) : isLoading ? (
          <p className={styles.empty}>불러오는 중…</p>
        ) : rows.length === 0 ? (
          <p className={styles.empty}>
            {applied || status !== 'ALL'
              ? '조건에 맞는 회원이 없습니다.'
              : '가입한 회원이 없습니다.'}
          </p>
        ) : (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th scope="col">회원</th>
                  <th scope="col">휴대폰</th>
                  <th scope="col">로그인 수단</th>
                  <th scope="col">상태</th>
                  <th scope="col">가입</th>
                  <th scope="col">작업</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((member) => (
                  <tr
                    key={member.id}
                    className={member.status === 'WITHDRAWN' ? styles.muted : undefined}
                  >
                    <td data-label="회원" data-card-title>
                      <span className={styles.wrapText}>{member.name}</span>
                      <span className={styles.cellSub}>{member.email}</span>
                    </td>
                    <td data-label="휴대폰" className="tabular">
                      {member.phone}
                    </td>
                    <td data-label="로그인 수단">
                      {loginMethods(member)}
                      {!member.identityVerified && (
                        <span className={styles.cellSub}>본인확인 전</span>
                      )}
                    </td>
                    <td data-label="상태">
                      <StatusBadge tone={STATUS_TONE[member.status]}>
                        {member.statusLabel}
                      </StatusBadge>
                      {/*
                        잠금은 상태와 별개다. 「정상」인데 잠긴 회원이 있고, 그 사람이
                        지금 전화를 걸어 오는 사람이다. 상태 배지 하나로는 안 보인다.
                      */}
                      {member.locked && (
                        <span className={styles.cellSub}>
                          잠김 · 실패 {member.loginFailCount}회
                        </span>
                      )}
                    </td>
                    <td data-label="가입" className="tabular">
                      {formatDateTime(member.createdAt)}
                    </td>
                    <td data-label="작업" data-card-actions>
                      <div className={styles.rowActions}>
                        {canViewDetail && (
                          <Button small onClick={() => setViewing(member.id)}>
                            상세
                          </Button>
                        )}
                        {canUnlock && member.locked && (
                          <Button small onClick={() => setUnlocking(member)}>
                            잠금 해제
                          </Button>
                        )}
                        {canChangeStatus && member.status !== 'WITHDRAWN' && (
                          <Button
                            small
                            variant={member.status === 'BLOCKED' ? undefined : 'danger'}
                            onClick={() => setChanging(member)}
                          >
                            {member.status === 'BLOCKED' ? '정지 해제' : '상태 변경'}
                          </Button>
                        )}
                      </div>
                    </td>
                  </tr>
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

      {viewing != null && (
        <MemberDetailDialog id={viewing} onClose={() => setViewing(null)} />
      )}
      {unlocking && (
        <MemberUnlockDialog member={unlocking} onClose={() => setUnlocking(null)} />
      )}
      {changing && (
        <MemberStatusDialog member={changing} onClose={() => setChanging(null)} />
      )}
    </div>
  );
}

/**
 * 로그인 수단.
 *
 * <p>비밀번호가 없는 회원이 정상적으로 존재한다 — 소셜로만 가입한 사람이다. 이 칸이
 * 비어 보이면 "비밀번호를 초기화해 달라"는 요청에 잘못 응대하게 된다.
 */
function loginMethods(member: AdminMemberSummary): string {
  const methods: string[] = [];
  if (member.hasPassword) methods.push('비밀번호');
  // 소셜 연동 목록은 상세에서만 온다. 목록에서는 "비밀번호가 있나"만 판단한다
  if (methods.length === 0) return '소셜';
  return methods.join(' · ');
}

/** 다른 화면에서 다시 쓰지 않는다 — 목록 표의 어휘라 여기 둔다. */
export { STATUS_TONE as MEMBER_STATUS_TONE };
