// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 패치노트 초안 생성 결과 리포트 페이지
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

const DRAFT_PARAMS_KEY = 'patchnote_draft_params';

// ── 페이지 상태 ──────────────────────────────────────────────────────
let publicId = null;
let draftParams = null;     // sessionStorage에서 읽은 생성 파라미터

let rawContent = '';        // SSE 토큰을 누적한 원본 (source 태그 포함 가능)
let cleanedContent = '';    // done 이벤트에서 받은 정제 컨텐츠
let sourceRefs = [];        // done 이벤트의 sourceRefs 목록
let sourceList = [];        // {index, ref, type, patchType, title} 배열

let generationComplete = false;
let isSaved = false;        // 저장 완료 여부 (navigation guard)
let pendingNavigation = null;// 이탈 확인 후 실행할 함수

let activeHighlightRef = null;// 현재 강조된 출처 ref

let isOverwriteMode = false;  // 버전 덮어쓰기 모드 여부

// ── 초기화 ───────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
    const el = document.getElementById('publicId');
    if (!el) return;
    publicId = el.value;

    // sessionStorage에서 파라미터 읽기
    const raw = sessionStorage.getItem(DRAFT_PARAMS_KEY);
    if (!raw) {
        // 파라미터 없으면 피드 페이지로 리다이렉트
        window.location.replace(`/projects/${publicId}/patch-note/pending-items`);
        return;
    }

    try {
        draftParams = JSON.parse(raw);
    } catch {
        window.location.replace(`/projects/${publicId}/patch-note/pending-items`);
        return;
    }

    // SSE 스트림 시작
    startSseStream();
});

// ── SSE 스트리밍 ──────────────────────────────────────────────────────

async function startSseStream() {
    const { majorVersion, minorVersion, patchVersion, additionalPrompt } = draftParams;

    try {
        const response = await fetch(
            `/api/projects/${publicId}/patch-note/drafts/stream`,
            {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'text/event-stream',
                    'X-Requested-With': 'XMLHttpRequest',
                },
                body: JSON.stringify({
                    majorVersion,
                    minorVersion,
                    patchVersion,
                    additionalPrompt: additionalPrompt || null,
                    modelAlias: null,
                    overwrite: isOverwriteMode,
                }),
            }
        );

        if (!response.ok) {
            throw new Error(`서버 오류 (${response.status})`);
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let buffer = '';
        let currentEventName = null;

        // eslint-disable-next-line no-constant-condition
        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split('\n');
            buffer = lines.pop() ?? ''; // 마지막 미완성 줄 보관

            for (const line of lines) {
                const trimmed = line.trim();
                if (trimmed.startsWith('event:')) {
                    currentEventName = trimmed.slice(6).trim();
                } else if (trimmed.startsWith('data:')) {
                    const payload = trimmed.slice(5).trim();
                    if (payload) {
                        try {
                            const data = JSON.parse(payload);
                            handleSseEvent(currentEventName, data);
                        } catch {
                            // JSON 파싱 실패 무시
                        }
                    }
                    currentEventName = null;
                }
            }
        }
    } catch (err) {
        showErrorOverlay(err.message || '초안 생성 중 오류가 발생했습니다.');
    }
}

function handleSseEvent(eventName, data) {
    switch (eventName) {
        case 'progress':
            handleProgress(data);
            break;
        case 'sources':
            handleSources(data);
            break;
        case 'token':
            handleToken(data);
            break;
        case 'done':
            handleDone(data);
            break;
        case 'error':
            handleError(data);
            break;
        case 'context_overflow':
            handleContextOverflow(data);
            break;
        default:
            break;
    }
}

// ── SSE 이벤트 핸들러 ────────────────────────────────────────────────

function handleProgress(data) {
    const step = data.step;

    if (step === 'BUILDING_CONTEXT') {
        updateOverlay('관련 문서 검색 중...', '피드 항목과 연관된 문서를 검색하고 있습니다.', 35);
        setStepActive(1);
    } else if (step === 'GENERATING') {
        updateOverlay('AI 초안 생성 중...', 'LLM이 패치노트를 작성하고 있습니다.', 70);
        setStepActive(2);
        // 오버레이 숨기고 본문 표시
        hideOverlay();
        showStreamCursor(true);
    }
}

function handleSources(data) {
    const refs = data.refs ?? [];
    buildSourceList(refs);
    renderSourcePanel();
}

function handleToken(data) {
    const content = data.content ?? '';
    rawContent += content;
    renderStreamingContent(rawContent);
    updateWordCount(rawContent);
}

function handleDone(data) {
    cleanedContent = data.cleanedContent ?? rawContent;
    sourceRefs = data.sourceRefs ?? [];

    // 완료 처리
    generationComplete = true;
    showStreamCursor(false);
    updateStreamingBadge(false);

    // 최종 컨텐츠 렌더링 (cleaned)
    renderMarkdown(cleanedContent);
    updateWordCount(cleanedContent);

    // 버튼 활성화
    enableActionButtons();

    // 생성 완료 시각
    setGeneratedAt(new Date());

    // 완료 토스트
    showTopToast('패치노트 초안 생성이 완료되었습니다.', 'success');

    // 아직 sources 이벤트가 없었다면 sourceRefs로 소스 목록 빌드
    if (sourceList.length === 0 && sourceRefs.length > 0) {
        buildSourceList(sourceRefs);
        renderSourcePanel();
    }
}

function handleError(data) {
    const message = data.message ?? '초안 생성 중 오류가 발생했습니다.';

    if (message.includes('이미 존재하는 버전')) {
        hideOverlay();
        showVersionDuplicateModal(message);
    } else {
        showErrorOverlay(message);
    }
}

function handleContextOverflow(data) {
    const currentPercent = data.currentPercent ?? 0;
    showContextOverflowModal(currentPercent);
}

// ── 오버레이 제어 ────────────────────────────────────────────────────

function updateOverlay(title, desc, progressPercent) {
    const titleEl = document.getElementById('overlayStepTitle');
    const descEl = document.getElementById('overlayStepDesc');
    const barEl = document.getElementById('overlayProgressBar');

    if (titleEl) titleEl.textContent = title;
    if (descEl) descEl.textContent = desc;
    if (barEl) {
        barEl.style.width = `${progressPercent}%`;
        barEl.setAttribute('aria-valuenow', progressPercent);
    }
}

function setStepActive(stepNum) {
    for (let i = 1; i <= 2; i++) {
        const stepEl = document.getElementById(`step${i}`);
        if (!stepEl) continue;
        const circle = stepEl.querySelector('div');
        const numEl = stepEl.querySelector('.step-num');

        if (i < stepNum) {
            // 완료
            if (circle) {
                circle.className = 'w-5 h-5 rounded-full border-2 border-emerald-500 bg-emerald-500 flex items-center justify-center';
            }
            if (numEl) numEl.textContent = '✓';
            stepEl.className = 'flex items-center gap-1.5 text-emerald-600';
        } else if (i === stepNum) {
            // 진행 중
            if (circle) {
                circle.className = 'w-5 h-5 rounded-full border-2 border-docu-primary bg-docu-primary/10 flex items-center justify-center';
            }
            if (numEl) numEl.textContent = i;
            stepEl.className = 'flex items-center gap-1.5 text-docu-primary font-medium';
        } else {
            // 미진행
            if (circle) {
                circle.className = 'w-5 h-5 rounded-full border-2 border-docu-tertiary flex items-center justify-center';
            }
            if (numEl) numEl.textContent = i;
            stepEl.className = 'flex items-center gap-1.5 text-docu-tertiary';
        }
    }
}

function hideOverlay() {
    const overlay = document.getElementById('processingOverlay');
    if (overlay) {
        overlay.style.transition = 'opacity 0.5s';
        overlay.style.opacity = '0';
        setTimeout(() => overlay.classList.add('hidden'), 500);
    }
}

function showErrorOverlay(message) {
    const processingOverlay = document.getElementById('processingOverlay');
    if (processingOverlay) processingOverlay.classList.add('hidden');

    const errorOverlay = document.getElementById('errorOverlay');
    const errorMessageEl = document.getElementById('errorMessage');

    if (errorMessageEl) errorMessageEl.textContent = message;
    if (errorOverlay) errorOverlay.classList.remove('hidden');
}

// ── 컨텍스트 초과 모달 ────────────────────────────────────────────────

function showContextOverflowModal(currentPercent) {
    const percentEl = document.getElementById('contextOverflowPercent');
    const barEl = document.getElementById('contextOverflowBar');

    if (percentEl) percentEl.textContent = `${currentPercent}%`;
    if (barEl) {
        // 100% 초과일 수 있으므로 시각적으로 100%로 고정
        barEl.style.width = '100%';
    }

    const modal = document.getElementById('contextOverflowModal');
    if (modal) modal.classList.remove('hidden');
}

function closeContextOverflowModal() {
    const modal = document.getElementById('contextOverflowModal');
    if (modal) modal.classList.add('hidden');
    showTopToast('참조 문서가 자동으로 요약되어 생성됩니다.', 'info');
}

// ── 다시 생성하기 ─────────────────────────────────────────────────────

/**
 * 상태를 초기화하고 SSE 스트리밍을 재시작한다.
 * 에러 오버레이의 "다시 생성하기" 버튼과 연결된다.
 */
function retryGeneration() {
    // 상태 초기화
    rawContent = '';
    cleanedContent = '';
    sourceRefs = [];
    sourceList = [];
    generationComplete = false;
    activeHighlightRef = null;

    // 컨텐츠 영역 초기화
    const draftContentEl = document.getElementById('draftContent');
    if (draftContentEl) draftContentEl.innerHTML = '';

    // 출처 패널 초기화
    const sourceListEl = document.getElementById('sourceList');
    if (sourceListEl) sourceListEl.innerHTML = '';
    const sourceEmptyEl = document.getElementById('sourceEmpty');
    if (sourceEmptyEl) sourceEmptyEl.textContent = '';
    const countBadge = document.getElementById('sourceCountBadge');
    if (countBadge) countBadge.textContent = '0건';

    // 단어 수 배지 초기화
    updateWordCount('');

    // 버튼 비활성화
    ['copyBtn', 'saveBtn'].forEach(id => {
        const btn = document.getElementById(id);
        if (btn) btn.disabled = true;
    });

    // 스트리밍 배지 초기화
    updateStreamingBadge(true);

    // 에러 오버레이 숨기기
    const errorOverlay = document.getElementById('errorOverlay');
    if (errorOverlay) errorOverlay.classList.add('hidden');

    // 처리 오버레이 복원
    const processingOverlay = document.getElementById('processingOverlay');
    if (processingOverlay) {
        processingOverlay.style.transition = '';
        processingOverlay.style.opacity = '1';
        processingOverlay.classList.remove('hidden');
    }

    // 진행 단계 초기화
    setStepActive(0);

    // SSE 재시작
    startSseStream();
}

/**
 * 버전 중복 모달에서 "새 버전으로 덮어쓰기" 버튼 클릭 시 호출.
 * isOverwriteMode를 true로 설정하고 재생성한다.
 */
function overwriteAndRegenerate() {
    isOverwriteMode = true;
    closeVersionDuplicateModal();
    retryGeneration();
}

// ── 컨텐츠 렌더링 ────────────────────────────────────────────────────

/**
 * 스트리밍 중 rawContent를 렌더링한다.
 * {{source:REF}} 패턴을 번호 배지로 치환 후 마크다운 변환.
 */
function renderStreamingContent(text) {
    const draftContentEl = document.getElementById('draftContent');
    if (!draftContentEl) return;

    let processed = text;
    let citationIndex = 0;

    // {{source:REF}} → 클릭 가능한 번호 배지
    processed = processed.replace(/\{\{source:([^}]+)\}\}/g, (match, ref) => {
        citationIndex++;
        const idx = citationIndex;
        return `<sup class="citation-badge cursor-pointer select-none inline-flex items-center justify-center w-4 h-4 rounded-full bg-docu-primary/10 text-docu-primary text-[10px] font-bold align-super hover:bg-docu-primary/20 transition-colors" data-ref="${ref}" title="${ref}" onclick="highlightSource('${ref}')">[${idx}]</sup>`;
    });

    if (typeof marked !== 'undefined' && typeof DOMPurify !== 'undefined') {
        // DOMPurify 설정: citation 배지의 onclick 허용
        const clean = DOMPurify.sanitize(marked.parse(processed), {
            ADD_ATTR: ['onclick', 'data-ref'],
        });
        draftContentEl.innerHTML = clean;
    } else {
        draftContentEl.textContent = text;
    }
}

/**
 * 최종 cleanedContent를 마크다운으로 렌더링한다.
 */
function renderMarkdown(text) {
    const draftContentEl = document.getElementById('draftContent');
    if (!draftContentEl) return;

    if (typeof marked !== 'undefined' && typeof DOMPurify !== 'undefined') {
        draftContentEl.innerHTML = DOMPurify.sanitize(marked.parse(text ?? ''));
    } else {
        draftContentEl.textContent = text ?? '';
    }
}

// ── 출처 패널 ────────────────────────────────────────────────────────

function buildSourceList(refs) {
    sourceList = refs.map((ref, index) => {
        const pendingItems = draftParams?.pendingItems ?? [];
        const matched = findPendingItemByRef(ref, pendingItems);

        return {
            index: index + 1,
            ref,
            type: ref.startsWith('ISSUE-') ? 'ISSUE' : 'DOCUMENT',
            patchType: matched?.patchType ?? null,
            title: matched?.title ?? ref,
            sourceId: matched?.sourceId ?? null,
        };
    });
}

function findPendingItemByRef(ref, pendingItems) {
    if (ref.startsWith('ISSUE-')) {
        const sourceId = parseInt(ref.slice(6), 10);
        return pendingItems.find(item => item.sourceType === 'ISSUE' && item.sourceId === sourceId) ?? null;
    }
    if (ref.startsWith('DOC-')) {
        const sourceId = parseInt(ref.slice(4), 10);
        return pendingItems.find(item => item.sourceType === 'DOCUMENT' && item.sourceId === sourceId) ?? null;
    }
    return null;
}

function renderSourcePanel() {
    const sourceListEl = document.getElementById('sourceList');
    const sourceEmptyEl = document.getElementById('sourceEmpty');
    const countBadge = document.getElementById('sourceCountBadge');

    if (countBadge) countBadge.textContent = `${sourceList.length}건`;

    if (sourceList.length === 0) {
        if (sourceListEl) sourceListEl.innerHTML = '';
        if (sourceEmptyEl) sourceEmptyEl.textContent = '참조된 출처가 없습니다.';
        return;
    }

    if (sourceEmptyEl) sourceEmptyEl.textContent = '';

    if (!sourceListEl) return;
    sourceListEl.innerHTML = '';

    sourceList.forEach(source => {
        const item = document.createElement('div');
        item.id = `source-item-${source.ref}`;
        item.className = 'px-5 py-3 hover:bg-surface-subtle transition-colors cursor-pointer group';
        item.setAttribute('role', 'button');
        item.setAttribute('tabindex', '0');
        item.setAttribute('aria-label', `출처 ${source.index}: ${source.title}`);

        item.addEventListener('click', () => highlightSource(source.ref));
        item.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' || e.key === ' ') highlightSource(source.ref);
        });

        const row = document.createElement('div');
        row.className = 'flex items-start gap-3';

        // 번호 배지
        const numBadge = document.createElement('span');
        numBadge.className = 'flex-shrink-0 w-5 h-5 rounded-full bg-docu-primary/10 text-docu-primary text-[10px] font-bold flex items-center justify-center mt-0.5';
        numBadge.textContent = source.index;
        row.appendChild(numBadge);

        const info = document.createElement('div');
        info.className = 'flex-1 min-w-0';

        // 배지 행
        const badges = document.createElement('div');
        badges.className = 'flex items-center gap-1.5 mb-1';
        badges.appendChild(buildSourceTypeBadge(source.type));
        if (source.patchType) badges.appendChild(buildPatchTypeBadge(source.patchType));
        info.appendChild(badges);

        // 제목
        const titleEl = document.createElement('p');
        titleEl.className = 'text-xs text-docu-secondary line-clamp-2 group-hover:text-docu-ink transition-colors';
        titleEl.textContent = source.title;
        info.appendChild(titleEl);

        row.appendChild(info);
        item.appendChild(row);
        sourceListEl.appendChild(item);
    });
}

// ── 출처 강조 ────────────────────────────────────────────────────────

function highlightSource(ref) {
    // 이전 강조 해제
    if (activeHighlightRef) {
        const prev = document.getElementById(`source-item-${activeHighlightRef}`);
        if (prev) prev.classList.remove('bg-docu-primary/5', 'ring-1', 'ring-docu-primary/30');
    }

    // 새 강조
    activeHighlightRef = ref;
    const target = document.getElementById(`source-item-${ref}`);
    if (target) {
        target.classList.add('bg-docu-primary/5', 'ring-1', 'ring-docu-primary/30');
        target.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }
}

// ── 배지 빌더 ────────────────────────────────────────────────────────

function buildSourceTypeBadge(type) {
    const span = document.createElement('span');
    span.className = 'inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium';

    if (type === 'ISSUE') {
        span.classList.add('bg-orange-100', 'text-orange-700');
        span.textContent = '이슈';
    } else {
        span.classList.add('bg-indigo-100', 'text-indigo-700');
        span.textContent = '문서';
    }
    return span;
}

function buildPatchTypeBadge(patchType) {
    const span = document.createElement('span');
    span.className = 'inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium';

    const config = {
        NEW:         { cls: ['bg-emerald-100', 'text-emerald-700'], label: '신규' },
        CHANGE:      { cls: ['bg-blue-100',    'text-blue-700'],    label: '변경' },
        FIX:         { cls: ['bg-red-100',     'text-red-700'],     label: '수정' },
        MAINTENANCE: { cls: ['bg-slate-100',   'text-slate-600'],   label: '유지보수' },
    }[patchType];

    if (config) {
        span.classList.add(...config.cls);
        span.textContent = config.label;
    }
    return span;
}

// ── UI 헬퍼 ──────────────────────────────────────────────────────────

function showStreamCursor(visible) {
    const cursor = document.getElementById('streamCursor');
    if (cursor) cursor.classList.toggle('hidden', !visible);
}

function updateStreamingBadge(isStreaming) {
    const badge = document.getElementById('streamingBadge');
    if (!badge) return;

    if (isStreaming) {
        badge.innerHTML = `<span class="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span> 생성 중`;
        badge.className = 'inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-emerald-100 text-emerald-700';
    } else {
        badge.textContent = '생성 완료';
        badge.className = 'inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-surface-subtle text-docu-secondary';
    }
}

function updateWordCount(text) {
    const badge = document.getElementById('wordCountBadge');
    if (!badge) return;
    const charCount = (text ?? '').replace(/\s+/g, '').length;
    badge.textContent = `${charCount.toLocaleString('ko-KR')}자`;
}

function setGeneratedAt(date) {
    const badge = document.getElementById('generatedAtBadge');
    if (!badge) return;
    badge.textContent = `생성 완료: ${date.toLocaleString('ko-KR', {
        year: 'numeric', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit',
    })}`;
}

function enableActionButtons() {
    ['copyBtn', 'saveBtn'].forEach(id => {
        const btn = document.getElementById(id);
        if (btn) btn.disabled = false;
    });
}

// ── 복사 ─────────────────────────────────────────────────────────────

function copyDraftContent() {
    if (!cleanedContent) return;
    navigator.clipboard.writeText(cleanedContent).then(() => {
        showTopToast('클립보드에 복사되었습니다.', 'success');
    }).catch(() => {
        showTopToast('복사에 실패했습니다.', 'danger');
    });
}

// ── 저장 ─────────────────────────────────────────────────────────────

function openSaveConfirmModal() {
    if (!generationComplete) return;

    const metaEl = document.getElementById('saveConfirmMeta');
    if (metaEl && draftParams) {
        const title = draftParams.title || '(제목 없음)';
        const version = draftParams.versionString || '';
        const count = (draftParams.selectedItemIds ?? []).length;

        let overwriteNote = '';
        if (isOverwriteMode) {
            overwriteNote = `
                <p class="mt-2 flex items-center gap-1.5 text-amber-700 bg-amber-50 border border-amber-200 rounded px-3 py-2 text-xs">
                    <svg class="w-3.5 h-3.5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20" aria-hidden="true">
                        <path fill-rule="evenodd" d="M8.485 2.495c.673-1.167 2.357-1.167 3.03 0l6.28 10.875c.673 1.167-.17 2.625-1.516 2.625H3.72c-1.347 0-2.189-1.458-1.515-2.625L8.485 2.495zM10 5a.75.75 0 01.75.75v3.5a.75.75 0 01-1.5 0v-3.5A.75.75 0 0110 5zm0 9a1 1 0 100-2 1 1 0 000 2z" clip-rule="evenodd"/>
                    </svg>
                    기존 ${escapeHtml(version)} 버전이 삭제되고 새 초안으로 대체됩니다.
                </p>
            `;
        }

        metaEl.innerHTML = `
            <p><span class="font-semibold text-docu-ink">제목:</span> ${escapeHtml(title)}</p>
            <p><span class="font-semibold text-docu-ink">버전:</span> ${escapeHtml(version)}</p>
            <p><span class="font-semibold text-docu-ink">완료 처리 항목:</span> ${count}개</p>
            ${overwriteNote}
        `;
    }

    const modal = document.getElementById('saveConfirmModal');
    if (modal) modal.classList.remove('hidden');
}

function closeSaveConfirmModal() {
    const modal = document.getElementById('saveConfirmModal');
    if (modal) modal.classList.add('hidden');
}

async function savePatchNote() {
    const saveBtn = document.getElementById('saveConfirmBtn');
    if (saveBtn) { saveBtn.disabled = true; saveBtn.textContent = '저장 중...'; }

    const { title, majorVersion, minorVersion, patchVersion, selectedItemIds } = draftParams ?? {};

    const requestBody = {
        title: title || '',
        content: cleanedContent,
        majorVersion: majorVersion ?? 0,
        minorVersion: minorVersion ?? 0,
        patchVersion: patchVersion ?? 0,
        itemIds: selectedItemIds ?? [],
        overwrite: isOverwriteMode,
    };

    try {
        const body = await callApi(
            `/api/projects/${publicId}/patch-note`,
            {
                method: 'POST',
                body: JSON.stringify(requestBody),
            }
        );

        closeSaveConfirmModal();

        if (body.success) {
            isSaved = true;
            sessionStorage.removeItem(DRAFT_PARAMS_KEY);
            sessionStorage.setItem('patchnote_list_toast', '패치노트가 저장되었습니다.');
            window.location.href = `/projects/${publicId}/patch-note`;
        } else {
            if (saveBtn) { saveBtn.disabled = false; saveBtn.textContent = '저장'; }
            showTopToast(body.error?.message ?? '저장에 실패했습니다.', 'danger');
        }
    } catch (err) {
        closeSaveConfirmModal();
        if (saveBtn) { saveBtn.disabled = false; saveBtn.textContent = '저장'; }
        showTopToast(err.message, 'danger');
    }
}

// ── 버전 중복 모달 ────────────────────────────────────────────────────

function showVersionDuplicateModal(message) {
    const msgEl = document.getElementById('versionDuplicateMessage');
    if (msgEl) msgEl.textContent = message;
    const modal = document.getElementById('versionDuplicateModal');
    if (modal) modal.classList.remove('hidden');
}

function closeVersionDuplicateModal() {
    const modal = document.getElementById('versionDuplicateModal');
    if (modal) modal.classList.add('hidden');
}

// ── 네비게이션 ────────────────────────────────────────────────────────

function goToList() {
    navigateTo(`/projects/${publicId}/patch-note`);
}

function goToPendingItems() {
    navigateTo(`/projects/${publicId}/patch-note/pending-items`);
}

function handleBackNavigation() {
    navigateTo(`/projects/${publicId}/patch-note`);
}

/**
 * 저장 전 이탈 가드 처리.
 * 생성 완료 후 저장 전인 경우 확인 모달을 표시한다.
 */
function navigateTo(url) {
    if (generationComplete && !isSaved) {
        pendingNavigation = () => { window.location.href = url; };
        openLeaveConfirmModal();
    } else {
        window.location.href = url;
    }
}

function openLeaveConfirmModal() {
    const modal = document.getElementById('leaveConfirmModal');
    if (!modal) return;
    modal.classList.remove('hidden');

    const confirmBtn = document.getElementById('leaveConfirmBtn');
    if (confirmBtn) {
        confirmBtn.onclick = () => {
            closeLeaveConfirmModal();
            sessionStorage.removeItem(DRAFT_PARAMS_KEY);
            if (pendingNavigation) pendingNavigation();
        };
    }
}

function closeLeaveConfirmModal() {
    pendingNavigation = null;
    const modal = document.getElementById('leaveConfirmModal');
    if (modal) modal.classList.add('hidden');
}

// 브라우저 이탈 가드 (새로고침, 탭 닫기 등)
window.addEventListener('beforeunload', (e) => {
    if (generationComplete && !isSaved) {
        e.preventDefault();
        e.returnValue = '생성된 초안이 저장되지 않았습니다. 페이지를 이동하시겠습니까?';
    }
});

// ── 유틸 ─────────────────────────────────────────────────────────────

function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}
