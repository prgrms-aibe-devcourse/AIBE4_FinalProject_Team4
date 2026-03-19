// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 패치노트 작성 — 피드 + 설정 패널
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

const DRAFT_PARAMS_KEY = 'patchnote_draft_params';

// ── 피드 상태 ────────────────────────────────────────────────────────
let publicId = null;
let currentSourceType = '';
let currentPatchType = '';
let currentKeyword = '';
let currentDateFrom = '';
let currentDateTo   = '';
let debounceTimer = null;
let pendingExcludeItemId = null;
let currentDetailItemId = null;

// 체크박스 상태 (초안 저장 시 COMPLETED 처리할 itemIds)
let selectedItemIds = new Set();
// 현재 PENDING 모드에서 로드된 전체 항목 데이터 (sessionStorage 저장용)
let allPendingItems = [];

// 탐색기 상태
let explorerMode = 'EXCLUDED'; // EXCLUDED | COMPLETED

// 설정 패널 값
let templateValue = '';
let additionalPromptValue = '';

// ── 초기화 ───────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
    const el = document.getElementById('publicId');
    if (!el) return;
    publicId = el.value;

    // 키워드 필터
    const keywordInput = document.getElementById('keywordInput');
    if (keywordInput) {
        keywordInput.addEventListener('input', (e) => {
            clearTimeout(debounceTimer);
            debounceTimer = setTimeout(() => {
                currentKeyword = e.target.value.trim();
                loadFeed();
            }, 350);
        });
    }

    // 소스 타입 필터
    const sourceTypeFilter = document.getElementById('sourceTypeFilter');
    if (sourceTypeFilter) {
        sourceTypeFilter.addEventListener('change', (e) => {
            currentSourceType = e.target.value;
            loadFeed();
        });
    }

    // 패치 분류 필터
    const patchTypeFilter = document.getElementById('patchTypeFilter');
    if (patchTypeFilter) {
        patchTypeFilter.addEventListener('change', (e) => {
            currentPatchType = e.target.value;
            loadFeed();
        });
    }

    // 날짜 필터
    const dateFrom = document.getElementById('dateFrom');
    if (dateFrom) {
        dateFrom.addEventListener('change', (e) => {
            currentDateFrom = e.target.value;
            loadFeed();
        });
    }

    const dateTo = document.getElementById('dateTo');
    if (dateTo) {
        dateTo.addEventListener('change', (e) => {
            currentDateTo = e.target.value;
            loadFeed();
        });
    }

    initSelectAllCheckbox();
    loadFeed();
});

// ── 피드 로드 ────────────────────────────────────────────────────────

async function loadFeed() {
    showLoading(true);

    const params = new URLSearchParams();
    params.set('mode', 'PENDING');
    if (currentSourceType) params.set('sourceType', currentSourceType);
    if (currentPatchType)  params.set('patchType', currentPatchType);
    if (currentKeyword)    params.set('keyword', currentKeyword);
    if (currentDateFrom)   params.set('dateFrom', currentDateFrom);
    if (currentDateTo)     params.set('dateTo', currentDateTo);

    try {
        const body = await callApi(
            `/api/projects/${publicId}/patch-note/pending-items?${params}`
        );

        if (body.success) {
            const items = body.data ?? [];
            allPendingItems = items;

            // 첫 로드일 때만 전체 선택 기본값 적용
            if (selectedItemIds.size === 0 && items.length > 0) {
                selectedItemIds = new Set(items.map(item => item.id));
            } else {
                // 현재 화면에 없는 항목 ID는 제거
                const visibleIds = new Set(items.map(item => item.id));
                selectedItemIds = new Set(
                    Array.from(selectedItemIds).filter(id => visibleIds.has(id))
                );
            }

            updateSelectedCount();
            renderFeed(items);
            updateSelectAllCheckboxState(items);
        } else {
            showLoading(false);
            showTopToast(body.error?.message ?? '피드를 불러오지 못했습니다.', 'danger');
        }
    } catch (err) {
        showLoading(false);
        showTopToast(err.message, 'danger');
    }
}

function showLoading(isLoading) {
    const loadingEl = document.getElementById('feedLoading');
    const listEl    = document.getElementById('feedList');
    const emptyEl   = document.getElementById('feedEmpty');
    if (!loadingEl || !listEl || !emptyEl) return;

    if (isLoading) {
        loadingEl.classList.remove('hidden');
        listEl.classList.add('hidden');
        emptyEl.classList.add('hidden');
    } else {
        loadingEl.classList.add('hidden');
    }
}

// ── 피드 렌더링 ──────────────────────────────────────────────────────

function renderFeed(items) {
    showLoading(false);

    const listEl  = document.getElementById('feedList');
    const emptyEl = document.getElementById('feedEmpty');
    const countEl = document.getElementById('feedCount');
    if (!listEl || !emptyEl) return;

    if (countEl) {
        countEl.textContent = items.length > 0 ? `${items.length}건` : '';
    }

    if (items.length === 0) {
        listEl.classList.add('hidden');
        emptyEl.classList.remove('hidden');
        const msg = document.getElementById('feedEmptyMessage');
        if (msg) msg.textContent = '대기 중인 항목이 없습니다.';
        updateSelectAllCheckboxState([]);
        return;
    }

    emptyEl.classList.add('hidden');
    listEl.innerHTML = '';
    items.forEach(item => listEl.appendChild(buildRow(item)));
    listEl.classList.remove('hidden');
    updateSelectAllCheckboxState(items);
}

// ── 행(Row) 생성 ─────────────────────────────────────────────────────

function buildRow(item) {
    const row = document.createElement('div');
    row.className = 'grid grid-cols-12 gap-3 items-start px-4 py-4 hover:bg-surface-subtle transition-colors';
    row.dataset.itemId = item.id;

    // ── 1. 체크박스 ──
    const checkCell = document.createElement('div');
    checkCell.className = 'col-span-1 flex justify-center items-start pt-1';

    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.className = 'w-4 h-4 rounded border-divider text-docu-primary cursor-pointer';
    checkbox.checked = selectedItemIds.has(item.id);
    checkbox.setAttribute('aria-label', `${item.title} 포함 여부`);
    checkbox.addEventListener('change', (e) => {
        if (e.target.checked) {
            selectedItemIds.add(item.id);
        } else {
            selectedItemIds.delete(item.id);
        }

        updateSelectedCount();
        updateSelectAllCheckboxState(allPendingItems);
        e.stopPropagation();
    });
    checkCell.appendChild(checkbox);
    row.appendChild(checkCell);

    // ── 2. 항목 정보 (col-span-4) ──
    const infoCell = document.createElement('div');
    infoCell.className = 'col-span-4 min-w-0';

    if (item.sourceDeleted) {
        const warn = document.createElement('div');
        warn.className = 'flex items-center gap-1 mb-1';
        const warnIcon = document.createElement('span');
        warnIcon.className = 'text-xs text-amber-600 font-medium';
        warnIcon.textContent = '⚠ 원본 삭제됨';
        warn.appendChild(warnIcon);
        infoCell.appendChild(warn);
    }

    const title = document.createElement('p');
    title.className = 'text-base font-semibold text-docu-ink truncate';
    title.textContent = item.title;
    infoCell.appendChild(title);

    const summary = document.createElement('p');
    summary.className = 'text-[11px] text-docu-secondary mt-0.5 line-clamp-2';
    summary.textContent = item.summary;
    infoCell.appendChild(summary);

    row.appendChild(infoCell);

    // ── 3. 소스 타입 (col-span-2) ──
    const sourceCell = document.createElement('div');
    sourceCell.className = 'col-span-2 flex justify-center items-start pt-1';
    sourceCell.appendChild(buildSourceTypeBadge(item.sourceType));
    row.appendChild(sourceCell);

    // ── 4. 패치 분류 (col-span-2) ──
    const patchCell = document.createElement('div');
    patchCell.className = 'col-span-2 flex justify-center items-start pt-1';
    patchCell.appendChild(buildPatchTypeBadge(item.patchType));
    row.appendChild(patchCell);

    // ── 5. 생성일 (col-span-2) ──
    const dateCell = document.createElement('div');
    dateCell.className = 'col-span-2 text-xs text-docu-secondary text-center pt-1';
    dateCell.textContent = formatDate(item.sourceCreatedAt);
    row.appendChild(dateCell);

    // ── 6. 작업 버튼 (col-span-1) ──
    const actionCell = document.createElement('div');
    actionCell.className = 'col-span-1 flex justify-end items-start pt-0.5';
    const actionBtn = buildActionButton(item);
    if (actionBtn) actionCell.appendChild(actionBtn);
    row.appendChild(actionCell);

    // 행 클릭 → 상세 모달 (버튼/체크박스 클릭은 제외)
    row.style.cursor = 'pointer';
    row.addEventListener('click', (e) => {
        if (e.target.closest('button') || e.target.type === 'checkbox') return;
        openDetailModal(item.id);
    });

    return row;
}

// ── 선택 항목 카운트 ──────────────────────────────────────────────────

function updateSelectedCount() {
    const display = document.getElementById('selectedCountDisplay');
    if (display) display.textContent = `${selectedItemIds.size}개`;

    const generateBtn = document.getElementById('generateBtn');
    if (generateBtn) {
        generateBtn.disabled = selectedItemIds.size === 0;
        generateBtn.classList.toggle('opacity-50', selectedItemIds.size === 0);
        generateBtn.classList.toggle('cursor-not-allowed', selectedItemIds.size === 0);
    }
}

function initSelectAllCheckbox() {
    const selectAllEl = document.getElementById('selectAllCheckbox');
    if (!selectAllEl) return;

    selectAllEl.addEventListener('change', (e) => {
        const checked = e.target.checked;

        if (checked) {
            selectedItemIds = new Set(allPendingItems.map(item => item.id));
        } else {
            selectedItemIds = new Set();
        }

        renderFeed(allPendingItems);
        updateSelectedCount();
    });
}

function updateSelectAllCheckboxState(items = allPendingItems) {
    const selectAllEl = document.getElementById('selectAllCheckbox');
    if (!selectAllEl) return;

    if (!items || items.length === 0) {
        selectAllEl.checked = false;
        selectAllEl.indeterminate = false;
        selectAllEl.disabled = true;
        return;
    }

    selectAllEl.disabled = false;

    const totalCount = items.length;
    const checkedCount = items.filter(item => selectedItemIds.has(item.id)).length;

    if (checkedCount === 0) {
        selectAllEl.checked = false;
        selectAllEl.indeterminate = false;
    } else if (checkedCount === totalCount) {
        selectAllEl.checked = true;
        selectAllEl.indeterminate = false;
    } else {
        selectAllEl.checked = false;
        selectAllEl.indeterminate = true;
    }
}

// ── 배지 빌더 ────────────────────────────────────────────────────────

function buildSourceTypeBadge(sourceType) {
    const span = document.createElement('span');
    span.className = 'badge-base';

    if (sourceType === 'DOCUMENT') {
        span.classList.add('bg-docu-primary-light', 'text-docu-primary-dark', 'border-docu-primary');
        span.textContent = '문서';
    } else if (sourceType === 'ISSUE') {
        span.classList.add('bg-docu-warning-light', 'text-docu-warning-dark', 'border-docu-warning');
        span.textContent = '이슈';
    } else {
        span.classList.add('bg-gray-100', 'text-gray-600', 'border-gray-300');
        span.textContent = sourceType ?? '-';
    }
    return span;
}

function buildPatchTypeBadge(patchType) {
    const span = document.createElement('span');
    span.className = 'badge-base';

    const config = {
        NEW:         { cls: ['bg-docu-success-light', 'text-docu-success-dark', 'border-docu-success'],   label: '신규기능' },
        CHANGE:      { cls: ['bg-docu-primary-light', 'text-docu-primary-dark', 'border-docu-primary'],   label: '변경사항' },
        FIX:         { cls: ['bg-docu-danger-light',  'text-docu-danger',       'border-docu-danger'],    label: '버그수정' },
        MAINTENANCE: { cls: ['bg-gray-100',           'text-gray-600',          'border-gray-300'],       label: '유지보수' },
    }[patchType];

    if (config) {
        span.classList.add(...config.cls);
        span.textContent = config.label;
    } else {
        span.classList.add('bg-gray-100', 'text-gray-600', 'border-gray-300');
        span.textContent = patchType ?? '-';
    }
    return span;
}

// ── 액션 버튼 빌더 ───────────────────────────────────────────────────

function buildActionButton(item) {
    if (item.status === 'PENDING') {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'inline-flex items-center justify-center px-2.5 py-1 text-xs font-semibold '
                      + 'border-2 border-docu-ink bg-surface-base text-docu-ink rounded-docu-btn '
                      + 'shadow-docu-sm hover:bg-white transition-colors whitespace-nowrap';
        btn.textContent = '제외';
        btn.addEventListener('click', () => openExcludeModal(item.id));
        return btn;
    }

    return null;
}

// ── 날짜 포맷 ────────────────────────────────────────────────────────

function formatDate(isoString) {
    if (!isoString) return '-';
    try {
        return new Date(isoString).toLocaleDateString('ko-KR', {
            year: 'numeric', month: '2-digit', day: '2-digit',
        });
    } catch {
        return '-';
    }
}

// ── 상세 모달 ────────────────────────────────────────────────────────

async function openDetailModal(itemId) {
    currentDetailItemId = itemId;

    const modal = document.getElementById('detailModal');
    if (!modal) return;
    resetDetailModal();
    modal.classList.remove('hidden');

    try {
        const body = await callApi(
            `/api/projects/${publicId}/patch-note/pending-items/${itemId}`
        );

        if (body.success) {
            renderDetailModal(body.data);
        } else {
            closeDetailModal();
            showTopToast(body.error?.message ?? '상세 정보를 불러오지 못했습니다.', 'danger');
        }
    } catch (err) {
        closeDetailModal();
        showTopToast(err.message, 'danger');
    }
}

function resetDetailModal() {
    const titleEl = document.getElementById('detailModalTitle');
    if (titleEl) titleEl.textContent = '불러오는 중...';

    const summaryEl = document.getElementById('detailSummary');
    if (summaryEl) summaryEl.textContent = '';

    const badgesEl = document.getElementById('detailBadges');
    if (badgesEl) badgesEl.innerHTML = '';

    ['detailDeletedBanner', 'detailExcludeBtn', 'detailRestoreBtn'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.classList.add('hidden');
    });
}

function renderDetailModal(detail) {
    const titleEl = document.getElementById('detailModalTitle');
    if (titleEl) titleEl.textContent = detail.title;

    const badgesEl = document.getElementById('detailBadges');
    if (badgesEl) {
        badgesEl.innerHTML = '';
        badgesEl.appendChild(buildSourceTypeBadge(detail.sourceType));
        badgesEl.appendChild(buildPatchTypeBadge(detail.patchType));
    }

    const deletedBanner = document.getElementById('detailDeletedBanner');
    if (deletedBanner) {
        deletedBanner.classList.toggle('hidden', !detail.sourceDeleted);
    }

    const summaryEl = document.getElementById('detailSummary');
    if (summaryEl) summaryEl.textContent = detail.summary;

    setDetailText('detailSourceType', { DOCUMENT: '문서', ISSUE: '이슈' }[detail.sourceType] ?? detail.sourceType);
    setDetailText('detailPatchType', detail.patchType);
    setDetailText('detailStatus', { PENDING: '대기 중', EXCLUDED: '제외됨', COMPLETED: '완료됨' }[detail.status] ?? detail.status);
    setDetailText('detailCreatedAt', formatDate(detail.sourceCreatedAt));

    const linkEl = document.getElementById('detailSourceLink');
    const linkDisabledEl = document.getElementById('detailSourceLinkDisabled');
    if (detail.sourceLink) {
        if (linkEl) { linkEl.href = detail.sourceLink; linkEl.classList.remove('hidden'); }
        if (linkDisabledEl) linkDisabledEl.classList.add('hidden');
    } else {
        if (linkEl) linkEl.classList.add('hidden');
        if (linkDisabledEl) linkDisabledEl.classList.remove('hidden');
    }

    const excludeBtn = document.getElementById('detailExcludeBtn');
    const restoreBtn = document.getElementById('detailRestoreBtn');
    if (excludeBtn) excludeBtn.classList.toggle('hidden', detail.status !== 'PENDING');
    if (restoreBtn) restoreBtn.classList.toggle('hidden', detail.status !== 'EXCLUDED');
}

function setDetailText(elementId, text) {
    const el = document.getElementById(elementId);
    if (el) el.textContent = text ?? '-';
}

function closeDetailModal() {
    currentDetailItemId = null;
    const modal = document.getElementById('detailModal');
    if (modal) modal.classList.add('hidden');
}

function detailExclude() {
    if (!currentDetailItemId) return;
    closeDetailModal();
    openExcludeModal(currentDetailItemId);
}

async function detailRestore() {
    const itemId = currentDetailItemId;
    closeDetailModal();
    if (!itemId) return;
    await restoreItem(itemId);
}

// ── 제외 액션 ────────────────────────────────────────────────────────

function openExcludeModal(itemId) {
    pendingExcludeItemId = itemId;
    const modal = document.getElementById('excludeModal');
    if (modal) modal.classList.remove('hidden');
}

function closeExcludeModal() {
    pendingExcludeItemId = null;
    const modal = document.getElementById('excludeModal');
    if (modal) modal.classList.add('hidden');
}

async function confirmExclude() {
    const itemId = pendingExcludeItemId;
    closeExcludeModal();
    if (!itemId) return;

    try {
        const body = await callApi(
            `/api/projects/${publicId}/patch-note/pending-items/${itemId}/exclude`,
            { method: 'PATCH' }
        );

        if (body.success) {
            showTopToast(body.message ?? '패치노트 피드에서 제외되었습니다.', 'success');
            loadFeed();
        } else {
            showTopToast(body.error?.message ?? '제외 처리에 실패했습니다.', 'danger');
        }
    } catch (err) {
        showTopToast(err.message, 'danger');
    }
}

// ── 복원 액션 ────────────────────────────────────────────────────────

async function restoreItem(itemId) {
    try {
        const body = await callApi(
            `/api/projects/${publicId}/patch-note/pending-items/${itemId}/restore`,
            { method: 'PATCH' }
        );

        if (body.success) {
            showTopToast(body.message ?? '패치노트 피드로 복원되었습니다.', 'success');
            loadFeed();
        } else {
            showTopToast(body.error?.message ?? '복원에 실패했습니다.', 'danger');
        }
    } catch (err) {
        showTopToast(err.message, 'danger');
    }
}

// ── 출처 링크 생성 ───────────────────────────────────────────────────

function buildItemSourceLink(item) {
    if (item.sourceDeleted) return null;
    if (item.sourceType === 'DOCUMENT') return `/projects/${publicId}/documents/${item.sourceId}`;
    if (item.sourceType === 'ISSUE') return `/projects/${publicId}/issues/${item.sourceId}/analysis`;
    return null;
}

// ── 초안 생성 ─────────────────────────────────────────────────────────

let pendingDraftParams = null; // 버전 중복 모달 표시 중 파라미터 임시 보관

async function startGeneration() {
    const titleInput = document.getElementById('draftTitleInput');
    const majorInput = document.getElementById('versionMajor');
    const minorInput = document.getElementById('versionMinor');
    const patchInput = document.getElementById('versionPatch');

    const title = titleInput?.value.trim() ?? '';
    const majorVersion = parseInt(majorInput?.value ?? '0', 10) || 0;
    const minorVersion = parseInt(minorInput?.value ?? '0', 10) || 0;
    const patchVersion = parseInt(patchInput?.value ?? '0', 10) || 0;

    let hasError = false;

    // 제목 검증
    const titleError = document.getElementById('draftTitleError');
    if (!title) {
        if (titleError) { titleError.textContent = '패치노트 제목을 입력해 주세요.'; titleError.classList.remove('hidden'); }
        if (titleInput) titleInput.classList.add('border-red-400');
        hasError = true;
    } else {
        if (titleError) titleError.classList.add('hidden');
        if (titleInput) titleInput.classList.remove('border-red-400');
    }

    // 버전 검증
    const versionError = document.getElementById('versionError');
    if (isNaN(majorVersion) || majorVersion < 0) {
        if (versionError) { versionError.textContent = '버전 값을 올바르게 입력해 주세요.'; versionError.classList.remove('hidden'); }
        hasError = true;
    } else {
        if (versionError) versionError.classList.add('hidden');
    }

    if (hasError) return;

    // 템플릿 + 추가 프롬프트 결합
    let combinedPrompt = '';
    if (templateValue) {
        combinedPrompt += `다음 템플릿 형식을 따라 패치노트를 작성해 주세요:\n${templateValue}`;
    }
    if (additionalPromptValue) {
        if (combinedPrompt) combinedPrompt += '\n\n';
        combinedPrompt += additionalPromptValue;
    }

    const params = {
        publicId,
        title,
        versionString: `v${majorVersion}.${minorVersion}.${patchVersion}`,
        majorVersion,
        minorVersion,
        patchVersion,
        additionalPrompt: combinedPrompt || null,
        selectedItemIds: Array.from(selectedItemIds),
        pendingItems: allPendingItems.map(item => ({
            id: item.id,
            sourceId: item.sourceId,
            sourceType: item.sourceType,
            title: item.title,
            patchType: item.patchType,
            sourceLink: buildItemSourceLink(item),
        })),
        overwrite: false,
    };

    // 버전 중복 사전 체크 (초안 페이지 이동 전)
    try {
        const body = await callApi(`/api/projects/${publicId}/patch-note`);
        if (body.success) {
            const notes = body.data ?? [];
            const versionLabel = `v${majorVersion}.${minorVersion}.${patchVersion}`;
            const alreadyExists = notes.some(note => note.versionLabel === versionLabel);
            if (alreadyExists) {
                pendingDraftParams = params;
                showVersionExistsModal(versionLabel);
                return;
            }
        }
    } catch {
        // 체크 실패 시 그냥 진행 (서버에서 재검증)
    }

    navigateToDraft(params);
}

function navigateToDraft(params) {
    sessionStorage.setItem(DRAFT_PARAMS_KEY, JSON.stringify(params));
    window.location.href = `/projects/${publicId}/patch-note/draft`;
}

// ── 버전 존재 확인 모달 ───────────────────────────────────────────────

function showVersionExistsModal(versionLabel) {
    const msgEl = document.getElementById('versionExistsMessage');
    if (msgEl) {
        msgEl.textContent = `${versionLabel} 버전이 이미 존재합니다. 기존 버전을 삭제하고 새 초안을 생성하시겠습니까?`;
    }
    const modal = document.getElementById('versionExistsModal');
    if (modal) modal.classList.remove('hidden');
}

function closeVersionExistsModal() {
    pendingDraftParams = null;
    const modal = document.getElementById('versionExistsModal');
    if (modal) modal.classList.add('hidden');
}

function goToPatchNoteList() {
    closeVersionExistsModal();
    window.location.href = `/projects/${publicId}/patch-note`;
}

function proceedWithOverwrite() {
    if (!pendingDraftParams) return;
    const params = { ...pendingDraftParams, overwrite: true };
    closeVersionExistsModal();
    navigateToDraft(params);
}

// ── 제외된 항목 탐색기 ────────────────────────────────────────────────

function openExcludedExplorer() {
    explorerMode = 'EXCLUDED';
    const modal = document.getElementById('explorerModal');
    if (modal) modal.classList.remove('hidden');
    loadExplorerItems();
}

function closeExcludedExplorer() {
    const modal = document.getElementById('explorerModal');
    if (modal) modal.classList.add('hidden');
}

function switchExplorerMode(mode) {
    explorerMode = mode;

    document.querySelectorAll('.explorer-tab').forEach(btn => {
        const isActive = btn.id === `explorer-tab-${mode}`;
        btn.classList.toggle('border-docu-primary', isActive);
        btn.classList.toggle('text-docu-primary', isActive);
        btn.classList.toggle('border-transparent', !isActive);
        btn.classList.toggle('text-docu-secondary', !isActive);
        btn.setAttribute('aria-current', isActive ? 'page' : 'false');
    });

    loadExplorerItems();
}

async function loadExplorerItems() {
    const loadingEl = document.getElementById('explorerLoading');
    const listEl    = document.getElementById('explorerList');
    const emptyEl   = document.getElementById('explorerEmpty');
    const countEl   = document.getElementById('explorerCount');

    if (loadingEl) loadingEl.classList.remove('hidden');
    if (listEl)    listEl.classList.add('hidden');
    if (emptyEl)   emptyEl.classList.add('hidden');

    try {
        const params = new URLSearchParams({ mode: explorerMode });
        const body = await callApi(
            `/api/projects/${publicId}/patch-note/pending-items?${params}`
        );

        if (loadingEl) loadingEl.classList.add('hidden');

        if (body.success) {
            // 클라이언트 사이드 필터: 현재 탭의 상태만 표시
            const items = (body.data ?? []).filter(item => item.status === explorerMode);
            if (countEl) countEl.textContent = items.length > 0 ? `${items.length}건` : '';

            if (items.length === 0) {
                if (emptyEl) emptyEl.classList.remove('hidden');
                return;
            }

            if (listEl) {
                listEl.innerHTML = '';
                items.forEach(item => listEl.appendChild(buildExplorerRow(item)));
                listEl.classList.remove('hidden');
            }
        } else {
            if (emptyEl) emptyEl.classList.remove('hidden');
            showTopToast(body.error?.message ?? '목록을 불러오지 못했습니다.', 'danger');
        }
    } catch (err) {
        if (loadingEl) loadingEl.classList.add('hidden');
        if (emptyEl) emptyEl.classList.remove('hidden');
        showTopToast(err.message, 'danger');
    }
}

function buildExplorerRow(item) {
    const row = document.createElement('div');
    row.className = 'flex items-center gap-4 px-6 py-4 hover:bg-surface-subtle transition-colors';

    // 정보 영역
    const info = document.createElement('div');
    info.className = 'flex-1 min-w-0';

    const badgeRow = document.createElement('div');
    badgeRow.className = 'flex items-center gap-1.5 mb-1';
    badgeRow.appendChild(buildSourceTypeBadge(item.sourceType));
    badgeRow.appendChild(buildPatchTypeBadge(item.patchType));

    const dateSpan = document.createElement('span');
    dateSpan.className = 'text-xs text-docu-secondary';
    dateSpan.textContent = formatDate(item.sourceCreatedAt);
    badgeRow.appendChild(dateSpan);

    info.appendChild(badgeRow);

    const title = document.createElement('p');
    title.className = 'text-sm font-medium text-docu-ink truncate';
    title.textContent = item.title;
    info.appendChild(title);

    row.appendChild(info);

    // 피드에 추가 / 복원 버튼 (EXCLUDED만)
    if (item.status === 'EXCLUDED') {
        const addBtn = document.createElement('button');
        addBtn.type = 'button';
        addBtn.className = 'flex-shrink-0 inline-flex items-center justify-center px-3 py-1.5 text-xs font-semibold '
                         + 'border-2 border-docu-ink bg-surface-base text-docu-ink rounded-docu-btn '
                         + 'shadow-docu-sm hover:bg-white transition-colors whitespace-nowrap';
        addBtn.textContent = '피드에 추가';
        addBtn.addEventListener('click', async () => {
            addBtn.disabled = true;
            addBtn.textContent = '처리 중...';

            try {
                const body = await callApi(
                    `/api/projects/${publicId}/patch-note/pending-items/${item.id}/restore`,
                    { method: 'PATCH' }
                );

                if (body.success) {
                    row.style.transition = 'opacity 0.3s';
                    row.style.opacity = '0';
                    setTimeout(() => {
                        row.remove();
                        const countEl = document.getElementById('explorerCount');
                        if (countEl) {
                            const remaining = document.getElementById('explorerList')?.children.length ?? 0;
                            countEl.textContent = remaining > 0 ? `${remaining}건` : '';
                        }
                    }, 300);

                    loadFeed();
                    showTopToast('피드에 추가되었습니다.', 'success');
                } else {
                    addBtn.disabled = false;
                    addBtn.textContent = '피드에 추가';
                    showTopToast(body.error?.message ?? '추가에 실패했습니다.', 'danger');
                }
            } catch (err) {
                addBtn.disabled = false;
                addBtn.textContent = '피드에 추가';
                showTopToast(err.message, 'danger');
            }
        });

        row.appendChild(addBtn);
    }

    return row;
}

// ── 템플릿 모달 ───────────────────────────────────────────────────────

function openTemplateModal() {
    const modal = document.getElementById('templateModal');
    if (!modal) return;
    const input = document.getElementById('templateModalInput');
    if (input) input.value = templateValue;
    modal.classList.remove('hidden');
    if (input) setTimeout(() => input.focus(), 50);
}

function closeTemplateModal() {
    const modal = document.getElementById('templateModal');
    if (modal) modal.classList.add('hidden');
}

function confirmTemplateInput() {
    const input = document.getElementById('templateModalInput');
    if (input) {
        templateValue = input.value.trim();
        updateTemplateTrigger();
    }
    closeTemplateModal();
}

function updateTemplateTrigger() {
    const preview = document.getElementById('templatePreview');
    if (!preview) return;
    if (templateValue) {
        preview.textContent = templateValue;
        preview.classList.remove('text-docu-secondary');
        preview.classList.add('text-docu-ink');
    } else {
        preview.textContent = '패치노트 작성 양식이 있다면 클릭해서 입력하세요';
        preview.classList.remove('text-docu-ink');
        preview.classList.add('text-docu-secondary');
    }
}

// ── 추가 프롬프트 모달 ────────────────────────────────────────────────

function openAdditionalPromptModal() {
    const modal = document.getElementById('additionalPromptModal');
    if (!modal) return;
    const input = document.getElementById('additionalPromptModalInput');
    if (input) input.value = additionalPromptValue;
    modal.classList.remove('hidden');
    if (input) setTimeout(() => input.focus(), 50);
}

function closeAdditionalPromptModal() {
    const modal = document.getElementById('additionalPromptModal');
    if (modal) modal.classList.add('hidden');
}

function confirmAdditionalPromptInput() {
    const input = document.getElementById('additionalPromptModalInput');
    if (input) {
        additionalPromptValue = input.value.trim();
        updateAdditionalPromptTrigger();
    }
    closeAdditionalPromptModal();
}

function updateAdditionalPromptTrigger() {
    const preview = document.getElementById('additionalPromptPreview');
    if (!preview) return;
    if (additionalPromptValue) {
        preview.textContent = additionalPromptValue;
        preview.classList.remove('text-docu-secondary');
        preview.classList.add('text-docu-ink');
    } else {
        preview.textContent = 'AI에게 추가로 전달할 지침을 클릭해서 입력하세요';
        preview.classList.remove('text-docu-ink');
        preview.classList.add('text-docu-secondary');
    }
}
