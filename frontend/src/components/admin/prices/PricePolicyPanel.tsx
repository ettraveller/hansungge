'use client';

import { useState } from 'react';

import { Alert } from '../Alert';
import { Button } from '../Button';
import { DecimalInput, Field, MoneyInput, SelectInput, TextArea } from '../Field';
import { Modal } from '../Modal';
import { Panel } from '../Panel';
import { ReasonDialog } from '../ReasonDialog';
import {
  usePolicies,
  useReapplyPolicy,
  useStartAutoQuote,
  useSwitchQuoteMode,
  useUpdatePolicy,
} from '@/hooks/admin/usePrices';
import { formatAdjustment, formatDateTime, formatPricePerGram } from '@/utils/format';
import {
  METAL_LABEL,
  METAL_ORDER,
  type MetalType,
  type PricePolicyMode,
  type PriceSide,
} from '@/types/price';
import type { PricePolicy } from '@/types/admin';
import { ApiError } from '@/types/api';
import styles from '../admin.module.css';

const MODE_LABEL: Record<PricePolicyMode, string> = {
  AUTO: '받아온 값 그대로',
  FIXED_ADJ: '금액 더하기 / 빼기',
  PERCENT_ADJ: '비율 더하기 / 빼기',
  MANUAL: '내가 정한 값',
};

const MODE_HINT: Record<PricePolicyMode, string> = {
  AUTO: '받아온 시세를 손대지 않고 그대로 고시한다.',
  FIXED_ADJ: '받아온 시세에 정한 금액을 더한다. 앞에 - 를 붙이면 뺀다. (예: 3000, -1500)',
  PERCENT_ADJ: '받아온 시세에 비율을 적용한다. -7 이면 7% 를 뺀다.',
  MANUAL: '받아온 시세와 무관하게 내가 적은 값으로 고시한다.',
};

const DECIMAL_PATTERN = /^-?\d{1,13}(\.\d{1,4})?$/;

/** 정책 표시 문구. 방식마다 의미 있는 값이 달라 한 칸에 그대로 몰아 보여주면 오해를 부른다. */
function describe(policy: PricePolicy): string {
  switch (policy.mode) {
    case 'FIXED_ADJ':
      return formatAdjustment(policy.adjAmount);
    case 'PERCENT_ADJ':
      return `${policy.adjPercent > 0 ? '+' : ''}${policy.adjPercent}%`;
    case 'MANUAL':
      return formatPricePerGram(policy.manualPrice);
    default:
      return '-';
  }
}

function sideLabelOf(side: PriceSide): string {
  return side === 'SELL' ? '판매' : '매입';
}

/**
 * 금속끼리 붙이고, 금속 안에서는 살 때를 먼저 둔다.
 *
 * <p>서버는 {@code ORDER BY metal, priceSide} 로 돌려주는데 priceSide 는 문자열이라
 * <b>BUY 가 SELL 보다 먼저</b> 온다. 고시 방식 칸을 금속의 첫 줄에 rowSpan 으로 두는
 * 이상, 그 순서에 기대면 안 된다 — 정렬이 바뀌면 표가 어긋나고, 그 사실은 화면을
 * 열어봐야만 드러난다. 여기서 못 박는다.
 */
function orderForDisplay(rows: PricePolicy[]): PricePolicy[] {
  const metalRank = new Map(METAL_ORDER.map((m, i) => [m, i]));
  return [...rows].sort((a, b) => {
    const ma = metalRank.get(a.metalType) ?? 99;
    const mb = metalRank.get(b.metalType) ?? 99;
    if (ma !== mb) return ma - mb;
    // 살 때가 먼저다. 고객이 사는 값이 이 사이트의 주력이다.
    return a.priceSide === b.priceSide ? 0 : a.priceSide === 'SELL' ? -1 : 1;
  });
}

function isFirstOfMetal(list: PricePolicy[], i: number): boolean {
  return i === 0 || list[i - 1].metalType !== list[i].metalType;
}

function metalRowCount(list: PricePolicy[], metal: MetalType): number {
  return list.filter((p) => p.metalType === metal).length;
}

/**
 * 자동고시 설정.
 *
 * ⚠️ 정책을 바꿔도 <b>이미 발행된 시세는 바뀌지 않는다.</b> 다음 고시부터 반영된다 —
 * append-only 이력이 과거 시점의 판매가를 그대로 증명해야 하기 때문이다.
 */
export function PricePolicyPanel({ canEdit }: { canEdit: boolean }) {
  const { data, isLoading, error } = usePolicies();
  const [editing, setEditing] = useState<PricePolicy | null>(null);
  // 전환 대상과 "무엇으로 바꿀지". 사유를 받아야 하므로 한 번에 실행하지 않는다.
  // 전환 대상은 금속이다. 살 때만 자동으로 두는 상태를 만들 수 없다.
  const [switching, setSwitching] = useState<{ metalType: MetalType; to: boolean } | null>(null);
  const switchMode = useSwitchQuoteMode();
  // 보정값을 바꾼 뒤 "왜 안 바뀌지"가 되지 않도록, 지금 값에 바로 적용하는 길을 둔다.
  const [reapplying, setReapplying] = useState<MetalType | null>(null);
  const reapply = useReapplyPolicy();

  return (
    <Panel
      title="자동고시 설정"
      note={
        '자동으로 낼지 손으로 낼지, 그리고 받아온 값에 얼마를 더하거나 뺄지를 여기서 정한다. ' +
        '자동으로 켜 두면 받아온 값에 아래 설정을 적용해 바로 고시한다. ' +
        '설정을 바꿔도 이미 나간 시세는 그대로다 — 새 보정은 다음 고시부터 적용된다. ' +
        '지금 값에 바로 반영하려면 금속별 [지금 적용] 을 쓴다.'
      }
      flush
    >
      {error && (
        <div className={styles.panelBody}>
          <Alert tone="danger">
            {error instanceof ApiError ? error.message : '정책을 불러오지 못했습니다.'}
          </Alert>
        </div>
      )}

      {isLoading ? (
        <p className={styles.loading}>불러오는 중…</p>
      ) : !data || data.length === 0 ? (
        <p className={styles.empty}>등록된 정책이 없습니다.</p>
      ) : (
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>금속</th>
                <th>구분</th>
                <th>고시 방식</th>
                <th>계산 방식</th>
                <th className={styles.num}>더하거나 뺄 값</th>
                <th>변경자</th>
                <th>변경 시각</th>
                {canEdit && <th aria-label="작업" />}
              </tr>
            </thead>
            <tbody>
              {orderForDisplay(data).map((policy, i, list) => (
                <tr key={policy.id}>
                  <td data-label="금속" data-card-title className={styles.strong}>{METAL_LABEL[policy.metalType]}</td>
                  <td data-label="구분">{sideLabelOf(policy.priceSide)}</td>
                  {/*
                    고시 방식은 금속 단위다. 같은 금속의 두 줄에 각각 버튼을 두면
                    한쪽만 바꾸는 조작이라고 읽힌다 — 실제로는 두 줄이 함께 바뀐다.
                    그래서 첫 줄(판매)에만 두고 두 줄에 걸쳐 놓는다.
                  */}
                  {isFirstOfMetal(list, i) && (
                    <td data-label="고시 방식" rowSpan={metalRowCount(list, policy.metalType)}>
                      <QuoteModeCell
                        policy={policy}
                        canEdit={canEdit}
                        onSwitch={(to) => setSwitching({ metalType: policy.metalType, to })}
                        onReapply={() => setReapplying(policy.metalType)}
                      />
                    </td>
                  )}
                  <td data-label="계산 방식">{MODE_LABEL[policy.mode]}</td>
                  <td data-label="더하거나 뺄 값" className={styles.num}>{describe(policy)}</td>
                  <td data-label="변경자" className={styles.muted}>{policy.updatedBy ?? '-'}</td>
                  <td data-label="변경 시각" className={styles.muted}>{formatDateTime(policy.updatedAt)}</td>
                  {canEdit && (
                    <td data-label="작업" data-card-actions>
                      <Button small onClick={() => setEditing(policy)}>
                        값 변경
                      </Button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {editing && (
        // key 를 줘서 대상이 바뀌면 폼 상태가 그 정책 기준으로 다시 만들어지게 한다.
        // useEffect 로 되돌리는 것보다 확실하고, 이전 값이 남아 잘못 저장될 여지가 없다.
        <PolicyEditDialog
          key={editing.id}
          policy={editing}
          onClose={() => setEditing(null)}
        />
      )}

      {/*
        전환에도 사유를 받는다. 자동에서 수동으로 내린 기록이 없으면, 나중에
        "왜 그날 시세가 멈춰 있었는가"를 아무도 설명하지 못한다.

        조건부로 마운트한다 — open 을 false 로 걸어두면 닫힌 동안에도 이전 대상의
        상태가 남아, 다음에 열 때 옛 값이 잠깐 비친다.
      */}
      {/*
        자동으로 켤 때는 보정값을 함께 받는다. 값을 정하지 않은 채 켜지면 보정 0 으로
        수집값이 그대로 판매가가 되고, 마진 없이 파는 상태는 판 뒤에야 드러난다.

        끄는 쪽은 사유만 받는다 — 사고 대응 중에 값을 함께 요구하면 급한 손이
        엉뚱한 값을 밀어 넣는다.
      */}
      {switching?.to && (
        <AutoQuoteStartDialog
          key={`${switching.metalType}-on`}
          metalType={switching.metalType}
          policies={data ?? []}
          onClose={() => setSwitching(null)}
        />
      )}

      {reapplying && (
        <ReasonDialog
          key={`${reapplying}-reapply`}
          open
          title="바뀐 보정값을 지금 시세에 적용"
          description={`${METAL_LABEL[reapplying]} — 가장 최근에 받은 원본 시세에 지금 보정값을 적용해 새로 고시합니다. 지난 이력은 그대로 남고 새 행이 쌓입니다. 원본이 오래되었으면 거절되며, 그때는 새로 고시해야 합니다.`}
          confirmLabel="지금 적용"
          placeholder="예: 마진 조정분 즉시 반영"
          pending={reapply.isPending}
          error={
            reapply.error instanceof ApiError
              ? reapply.error.message
              : reapply.error
                ? '적용하지 못했습니다.'
                : null
          }
          onConfirm={(reason) =>
            reapply.mutate(
              { metalType: reapplying, reason },
              { onSuccess: () => setReapplying(null) },
            )
          }
          onClose={() => setReapplying(null)}
        />
      )}

      {switching && !switching.to && (
        <ReasonDialog
          key={`${switching.metalType}-off`}
          open
          title="수동고시로 전환"
          description={`${METAL_LABEL[switching.metalType]} — 수집은 계속되지만 발행은 멈춥니다. 시세는 관리자가 직접 고시할 때까지 지금 값 그대로입니다.`}
          confirmLabel="수동고시로 전환"
          danger
          placeholder="예: 수집값 이상 확인, 수동 전환"
          pending={switchMode.isPending}
          error={
            switchMode.error instanceof ApiError
              ? switchMode.error.message
              : switchMode.error
                ? '고시 방식을 바꾸지 못했습니다.'
                : null
          }
          onConfirm={(reason) =>
            switchMode.mutate(
              {
                metalType: switching.metalType,
                autoQuote: false,
                changeReason: reason,
              },
              { onSuccess: () => setSwitching(null) },
            )
          }
          onClose={() => setSwitching(null)}
        />
      )}
    </Panel>
  );
}

/**
 * 지금 자동인지 수동인지, 그리고 반대로 바꾸는 버튼.
 *
 * <p>토글 스위치를 쓰지 않는다. 스위치는 눌리는 순간 값이 바뀐다고 읽히는데
 * 여기서는 사유를 받는 창이 먼저 뜬다 — 눌렀는데 안 바뀐 것처럼 보인다.
 * 현재 상태를 글로 적고, 바꾸는 것은 버튼으로 둔다.
 */
function QuoteModeCell({
  policy,
  canEdit,
  onSwitch,
  onReapply,
}: {
  policy: PricePolicy;
  canEdit: boolean;
  onSwitch: (to: boolean) => void;
  onReapply: () => void;
}) {
  return (
    <div className={styles.quoteMode}>
      <span className={policy.autoQuote ? styles.badgeAuto : styles.badgeManual}>
        {policy.autoQuote ? '자동' : '수동'}
      </span>
      {canEdit && (
        <>
          <Button small onClick={() => onSwitch(!policy.autoQuote)}>
            {policy.autoQuote ? '수동으로' : '자동으로'}
          </Button>
          {/*
            보정값을 바꿔도 이미 나간 시세는 그대로다. 다음 고시가 언제 올지 모르는
            동안 "고쳐도 아무 일도 안 일어난다"로 보이므로, 지금 값에 바로 적용하는
            길을 같은 자리에 둔다.
          */}
          <Button small onClick={onReapply}>
            지금 적용
          </Button>
        </>
      )}
    </div>
  );
}

function PolicyEditDialog({ policy, onClose }: { policy: PricePolicy; onClose: () => void }) {
  const update = useUpdatePolicy();

  const [mode, setMode] = useState<PricePolicyMode>(policy.mode);
  const [adjAmount, setAdjAmount] = useState(String(policy.adjAmount ?? '0'));
  const [adjPercent, setAdjPercent] = useState(String(policy.adjPercent ?? '0'));
  const [manualPrice, setManualPrice] = useState(
    policy.manualPrice == null ? '' : String(policy.manualPrice),
  );
  const [changeReason, setChangeReason] = useState('');

  const value =
    mode === 'FIXED_ADJ' ? adjAmount : mode === 'PERCENT_ADJ' ? adjPercent : manualPrice;
  const needsValue = mode !== 'AUTO';
  const valueInvalid = needsValue && (value === '' || !DECIMAL_PATTERN.test(value));
  // 지정가는 0 이하가 될 수 없다 — 어느 경로로도 가격을 0/NULL 로 만들지 않는다 (원칙 P6).
  const manualInvalid = mode === 'MANUAL' && !valueInvalid && Number(manualPrice) <= 0;

  const canSubmit =
    !valueInvalid && !manualInvalid && changeReason.trim().length > 0 && !update.isPending;

  const submit = () => {
    if (!canSubmit) return;
    update.mutate(
      {
        metalType: policy.metalType,
        priceSide: policy.priceSide,
        mode,
        adjAmount: mode === 'FIXED_ADJ' ? adjAmount : null,
        adjPercent: mode === 'PERCENT_ADJ' ? adjPercent : null,
        manualPrice: mode === 'MANUAL' ? manualPrice : null,
        changeReason: changeReason.trim(),
      },
      { onSuccess: onClose },
    );
  };

  return (
    <Modal
      open
      title={`${METAL_LABEL[policy.metalType as MetalType]} ${sideLabelOf(policy.priceSide)} · 자동고시 값 설정`}
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose} disabled={update.isPending}>
            취소
          </Button>
          <Button variant="primary" disabled={!canSubmit} onClick={submit}>
            {update.isPending ? '저장 중…' : '저장'}
          </Button>
        </>
      }
    >
      <div className={styles.form}>
        {update.error && (
          <Alert tone="danger">
            {update.error instanceof ApiError
              ? update.error.message
              : '정책을 저장하지 못했습니다.'}
          </Alert>
        )}

        <Alert tone="info">
          저장해도 현재 고시된 시세는 바뀌지 않는다. 다음 고시부터 적용된다.
        </Alert>

        <Field label="계산 방식" htmlFor="policy-mode" required hint={MODE_HINT[mode]}>
          <SelectInput
            id="policy-mode"
            value={mode}
            onChange={(e) => setMode(e.target.value as PricePolicyMode)}
          >
            {(Object.keys(MODE_LABEL) as PricePolicyMode[]).map((m) => (
              <option key={m} value={m}>
                {MODE_LABEL[m]}
              </option>
            ))}
          </SelectInput>
        </Field>

        {mode === 'FIXED_ADJ' && (
          <Field
            label="더하거나 뺄 금액 (원 / 1g)"
            htmlFor="policy-adj-amount"
            required
            hint="받아온 시세에 이 금액을 더한다. 앞에 - 를 붙이면 뺀다. 예: 3000, -1500"
            error={valueInvalid ? '숫자를 입력해 주세요.' : null}
          >
            <MoneyInput
              id="policy-adj-amount"
              value={adjAmount}
              invalid={valueInvalid}
              onValueChange={setAdjAmount}
            />
          </Field>
        )}

        {mode === 'PERCENT_ADJ' && (
          <Field
            label="더하거나 뺄 비율 (%)"
            htmlFor="policy-adj-percent"
            required
            hint="받아온 시세에 이 비율을 적용한다. -7 이면 7% 를 뺀다."
            error={valueInvalid ? '숫자를 입력해 주세요.' : null}
          >
            <DecimalInput
              id="policy-adj-percent"
              value={adjPercent}
              invalid={valueInvalid}
              onChange={(e) => setAdjPercent(e.target.value)}
            />
          </Field>
        )}

        {mode === 'MANUAL' && (
          <Field
            label="지정가 (원/1g)"
            htmlFor="policy-manual"
            required
            hint="원본 시세를 무시하고 이 값을 쓴다."
            error={
              valueInvalid
                ? '숫자를 입력해 주세요.'
                : manualInvalid
                  ? '지정가는 0보다 커야 합니다.'
                  : null
            }
          >
            <MoneyInput
              id="policy-manual"
              value={manualPrice}
              invalid={valueInvalid || manualInvalid}
              onValueChange={setManualPrice}
            />
          </Field>
        )}

        <Field label="변경 사유" htmlFor="policy-reason" required hint="감사로그에 남는다.">
          <TextArea
            id="policy-reason"
            value={changeReason}
            maxLength={300}
            placeholder="예: 판매 마진 정책 조정"
            onChange={(e) => setChangeReason(e.target.value)}
          />
        </Field>
      </div>
    </Modal>
  );
}

/**
 * 자동고시 시작 — 보정값을 함께 받는다.
 *
 * <p>"자동으로 켠다"와 "얼마를 더한다"를 따로 두면, 값을 정하지 않은 채 자동이
 * 켜진다. 그러면 보정 0 으로 수집값이 그대로 판매가가 되고, 마진 없이 파는 상태는
 * 판 뒤에야 드러난다. 켜는 순간 반드시 답해야 하는 질문이므로 건너뛸 수 없게 둔다.
 *
 * <p>지금 설정된 값을 기본으로 채운다. 대개는 그 값을 그대로 쓰고, 바꿀 때만 고친다.
 */
function AutoQuoteStartDialog({
  metalType,
  policies,
  onClose,
}: {
  metalType: MetalType;
  policies: PricePolicy[];
  onClose: () => void;
}) {
  const start = useStartAutoQuote();

  const sellPolicy = policies.find((p) => p.metalType === metalType && p.priceSide === 'SELL');
  const buyPolicy = policies.find((p) => p.metalType === metalType && p.priceSide === 'BUY');

  // 자동고시에 쓸 수 없는 모드(지정가)로 설정돼 있으면 금액 보정을 기본으로 잡는다.
  const initialMode: PricePolicyMode =
    sellPolicy && AUTO_MODES.includes(sellPolicy.mode) ? sellPolicy.mode : 'FIXED_ADJ';

  const [mode, setMode] = useState<PricePolicyMode>(initialMode);
  const [sellAdj, setSellAdj] = useState(() => initialAdj(sellPolicy, initialMode));
  const [buyAdj, setBuyAdj] = useState(() => initialAdj(buyPolicy, initialMode));
  const [reason, setReason] = useState('');

  const needsValue = mode !== 'AUTO';
  const sellInvalid = needsValue && !DECIMAL_PATTERN.test(sellAdj);
  const buyInvalid = needsValue && !DECIMAL_PATTERN.test(buyAdj);
  const canSubmit = !sellInvalid && !buyInvalid && reason.trim().length > 0 && !start.isPending;

  const unit = mode === 'PERCENT_ADJ' ? '%' : '원 / 1g';

  return (
    <Modal
      open
      title={`${METAL_LABEL[metalType]} 자동고시 시작`}
      onClose={onClose}
    >
      <form
        className={styles.form}
        onSubmit={(e) => {
          e.preventDefault();
          if (!canSubmit) return;
          start.mutate(
            {
              metalType,
              mode,
              sellAdj: needsValue ? sellAdj : '0',
              buyAdj: needsValue ? buyAdj : '0',
              changeReason: reason.trim(),
            },
            { onSuccess: onClose },
          );
        }}
      >
        {start.error && (
          <Alert tone="danger" title="자동고시를 시작하지 못했습니다">
            {start.error instanceof ApiError ? start.error.message : '알 수 없는 오류입니다.'}
          </Alert>
        )}

        <Alert tone="info" title="켜면 이렇게 동작합니다">
          수집한 값에 아래 보정을 적용해 <strong>살 때와 팔 때가 함께</strong> 발행됩니다.
          한쪽이라도 변동률이 허용치를 넘으면 양쪽 모두 발행하지 않고 승인 대기로
          넘어갑니다.
        </Alert>

        <Field label="계산 방식" htmlFor="auto-mode" required hint={MODE_HINT[mode]}>
          <SelectInput
            id="auto-mode"
            value={mode}
            onChange={(e) => setMode(e.target.value as PricePolicyMode)}
          >
            {AUTO_MODES.map((m) => (
              <option key={m} value={m}>
                {MODE_LABEL[m]}
              </option>
            ))}
          </SelectInput>
        </Field>

        {needsValue && (
          <div className={styles.formGrid}>
            <Field
              label={`내가 살 때 보정 (${unit})`}
              htmlFor="auto-sell-adj"
              required
              hint="앞에 - 를 붙이면 뺀다."
              error={sellInvalid ? '숫자를 입력해 주세요.' : null}
            >
              {mode === 'PERCENT_ADJ' ? (
                <DecimalInput
                  id="auto-sell-adj"
                  value={sellAdj}
                  invalid={sellInvalid}
                  onChange={(e) => setSellAdj(e.target.value)}
                />
              ) : (
                <MoneyInput
                  id="auto-sell-adj"
                  value={sellAdj}
                  invalid={sellInvalid}
                  onValueChange={setSellAdj}
                />
              )}
            </Field>

            <Field
              label={`내가 팔 때 보정 (${unit})`}
              htmlFor="auto-buy-adj"
              required
              hint="매입가는 보통 빼는 쪽이다."
              error={buyInvalid ? '숫자를 입력해 주세요.' : null}
            >
              {mode === 'PERCENT_ADJ' ? (
                <DecimalInput
                  id="auto-buy-adj"
                  value={buyAdj}
                  invalid={buyInvalid}
                  onChange={(e) => setBuyAdj(e.target.value)}
                />
              ) : (
                <MoneyInput
                  id="auto-buy-adj"
                  value={buyAdj}
                  invalid={buyInvalid}
                  onValueChange={setBuyAdj}
                />
              )}
            </Field>
          </div>
        )}

        <Field
          label="시작 사유"
          htmlFor="auto-reason"
          required
          hint="정책 이력과 감사로그에 그대로 남는다."
        >
          <TextArea
            id="auto-reason"
            value={reason}
            placeholder="예: 오전 마진 정책 반영, 자동고시 시작"
            maxLength={300}
            onChange={(e) => setReason(e.target.value)}
          />
        </Field>

        <div className={styles.formActions}>
          <Button type="submit" variant="gold" disabled={!canSubmit}>
            {start.isPending ? '시작하는 중…' : '자동고시 시작'}
          </Button>
          <Button type="button" onClick={onClose}>
            취소
          </Button>
        </div>
      </form>
    </Modal>
  );
}

/** 자동고시에 쓸 수 있는 계산 방식. 지정가는 수집값을 무시하는 셈이라 뺀다. */
const AUTO_MODES: PricePolicyMode[] = ['FIXED_ADJ', 'PERCENT_ADJ', 'AUTO'];

/** 지금 설정된 값을 문자열로. 모드가 다르면 빈 값에서 시작한다. */
function initialAdj(policy: PricePolicy | undefined, mode: PricePolicyMode): string {
  if (!policy) return '0';
  if (mode === 'FIXED_ADJ') return String(policy.adjAmount ?? '0');
  if (mode === 'PERCENT_ADJ') return String(policy.adjPercent ?? '0');
  return '0';
}
