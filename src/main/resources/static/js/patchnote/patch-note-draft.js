// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 패치노트 초안 생성 결과 리포트 페이지
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

const DRAFT_PARAMS_KEY = 'patchnote_draft_params';
const SOURCE_TAG_REGEX = /\{\{source:([^}]+)\}\}/g;

// ── 페이지 상태 ──────────────────────────────────────────────────────
let publicId = null;
let draftParams = null;

let rawContent = '';
let cleanedContent = '';
let sourceRefs = [];
let sourceList = [];

let generationComplete = false;
let isSaved = false;
let pendingNavigation = null;
let activeHighlightRef = null;
let isOverwriteMode = false;

// 스트리밍 타이프라이터 애니메이션
let typewriterDisplayed = 0;  // 현재 화면에 표시된 글자 수
let typewriterAnimId = null;  // requestAnimationFrame ID

// 완료 시 markdown 페이드 애니메이션용
let finalRenderAnimated = false;

// ── Markdown Renderer ───────────────────────────────────────────────

const markdownRenderer = (() => {
    if (typeof marked === 'undefined' || typeof DOMPurify === 'undefined') {
        return {
            renderToHtml(text) {
                return escapeHtml(stripSourceTags(text ?? ''));
            },
            enhanceHtml() {},
        };
    }

    const md = new marked.Marked({
        gfm: true,
        breaks: true,
        headerIds: false,
        mangle: false,
    });

    const renderer = {
        link({ href, title, tokens }) {
            const text = this.parser.parseInline(tokens);
            const safeHref = escapeHtml(href || '#');
            const safeTitle = title ? ` title="${escapeHtml(title)}"` : '';
            return `<a href="${safeHref}" target="_blank" rel="noopener noreferrer nofollow"${safeTitle}>${text}</a>`;
        },

        code({ text, lang }) {
            const language = (lang || '').trim();
            const langClass = language ? ` language-${escapeHtml(language)}` : '';

            return [
                '<pre class="md-pre not-prose overflow-x-auto rounded-2xl bg-slate-950 text-slate-100 px-4 py-4 shadow-sm">',
                `<code class="md-code block text-[13px] leading-6${langClass}">${escapeHtml(text)}</code>`,
                '</pre>',
            ].join('');
        },

        table(header, body) {
            return [
                '<div class="not-prose my-5 overflow-x-auto rounded-2xl border border-divider bg-white shadow-sm">',
                '<table class="w-full min-w-[520px] border-collapse text-sm">',
                `<thead>${header}</thead>`,
                `<tbody>${body}</tbody>`,
                '</table>',
                '</div>',
            ].join('');
        },

        blockquote({ tokens }) {
            return `<blockquote class="my-4">${this.parser.parse(tokens)}</blockquote>`;
        },
    };

    md.use({ renderer });

    function replaceSourceTags(text, sources = []) {
        let fallbackIndex = 0;

        return (text ?? '').replace(SOURCE_TAG_REGEX, (_, ref) => {
            const found = sources.find(source => source.ref === ref);
            const index = found ? found.index : ++fallbackIndex;

            return `<sup class="cite-ref" data-ref="${escapeHtml(ref)}" title="출처 ${index}" aria-label="출처 ${index}"><span class="cite-ref__inner">[${index}]</span></sup>`;
        });
    }

    function sanitizeHtml(html) {
        return DOMPurify.sanitize(html, {
            USE_PROFILES: { html: true },
            ADD_TAGS: ['sup', 'span'],
            ADD_ATTR: ['class', 'data-ref', 'title', 'aria-label', 'target', 'rel'],
            FORBID_TAGS: ['script', 'style', 'iframe'],
        });
    }

    function enhanceHtml(root) {
        if (!root) return;

        root.querySelectorAll('a').forEach(a => {
            a.setAttribute('target', '_blank');
            a.setAttribute('rel', 'noopener noreferrer nofollow');
            a.classList.add('break-all', 'font-medium');
        });

        root.querySelectorAll('table').forEach(table => {
            table.classList.add('w-full', 'border-collapse');
        });

        root.querySelectorAll('th').forEach(th => {
            th.classList.add(
                'bg-surface-subtle',
                'text-docu-ink',
                'font-semibold',
                'text-left',
                'px-4',
                'py-3',
                'border-b',
                'border-divider'
            );
        });

        root.querySelectorAll('td').forEach(td => {
            td.classList.add(
                'px-4',
                'py-3',
                'align-top',
                'text-docu-secondary',
                'border-b',
                'border-divider'
            );
        });

        root.querySelectorAll('sup[data-ref]').forEach(el => {
            el.classList.add(
                'citation-badge',
                'align-super',
                'ml-1'
            );
        });

        root.querySelectorAll('.cite-ref__inner').forEach(el => {
            el.classList.add(
                'font-bold',
                'text-docu-primary',
                'cursor-pointer',
                'select-none',
                'transition-colors',
                'duration-150',
                'hover:text-docu-primary/70',
                'focus:outline-none'
            );
        });

        root.querySelectorAll('ul, ol').forEach(list => {
            list.classList.add('space-y-1');
        });

        root.querySelectorAll('p').forEach(p => {
            if (!p.textContent.trim()) {
                p.remove();
            }
        });
    }

    function renderToHtml(text, sources = []) {
        const replaced = replaceSourceTags(text, sources);
        const parsed = md.parse(replaced);
        return sanitizeHtml(parsed);
    }

    return {
        renderToHtml,
        enhanceHtml,
    };
})();

// ── 초기화 ───────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
    const el = document.getElementById('publicId');
    if (!el) return;
    publicId = el.value;

    const raw = sessionStorage.getItem(DRAFT_PARAMS_KEY);
    if (!raw) {
        window.location.replace(`/projects/${publicId}/patch-note/pending-items`);
        return;
    }

    try {
        draftParams = JSON.parse(raw);
    } catch {
        window.location.replace(`/projects/${publicId}/patch-note/pending-items`);
        return;
    }

    // 피드 페이지에서 덮어쓰기 선택 시 overwrite 모드 초기화
    if (draftParams.overwrite === true) {
        isOverwriteMode = true;
    }

    const draftContentEl = document.getElementById('draftContent');
    if (draftContentEl) {
        draftContentEl.addEventListener('click', (e) => {
            const badge = e.target.closest('[data-ref]');
            if (!badge) return;
            const ref = badge.dataset.ref;
            if (ref) highlightSource(ref);
        });

        draftContentEl.addEventListener('keydown', (e) => {
            const badge = e.target.closest('[data-ref]');
            if (!badge) return;
            if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                const ref = badge.dataset.ref;
                if (ref) highlightSource(ref);
            }
        });
    }

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

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split('\n');
            buffer = lines.pop() ?? '';

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
                            // ignore invalid json
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
    scheduleTypewriter();
}

// 타이프라이터: 스트리밍 중 plain text를 글자 단위로 점진적 노출
function scheduleTypewriter() {
    if (typewriterAnimId !== null) return;
    typewriterAnimId = requestAnimationFrame(runTypewriter);
}

function runTypewriter() {
    typewriterAnimId = null;
    const plainText = stripSourceTags(rawContent);
    const target = plainText.length;
    if (typewriterDisplayed >= target) return;

    // 누적된 글자가 많을수록 더 빠르게 따라잡기
    const gap = target - typewriterDisplayed;
    const step = Math.max(1, Math.min(gap, Math.ceil(gap / 4)));
    typewriterDisplayed = Math.min(target, typewriterDisplayed + step);

    const draftContentEl = document.getElementById('draftContent');
    if (draftContentEl) {
        draftContentEl.textContent = plainText.slice(0, typewriterDisplayed);
    }

    if (typewriterDisplayed < target) {
        typewriterAnimId = requestAnimationFrame(runTypewriter);
    }
}

function handleDone(data) {
    // 타이프라이터 중단
    if (typewriterAnimId !== null) {
        cancelAnimationFrame(typewriterAnimId);
        typewriterAnimId = null;
    }
    typewriterDisplayed = 0;

    cleanedContent = data.cleanedContent ?? rawContent;
    sourceRefs = data.sourceRefs ?? [];

    // 실제 사용된 ref 기준으로 sourceList 재구성 (정확한 인덱스·링크 보장)
    if (sourceRefs.length > 0) {
        buildSourceList(sourceRefs);
        renderSourcePanel();
    } else if (sourceList.length === 0) {
        renderSourcePanel();
    }

    updateWordCount(stripSourceTags(cleanedContent));
    renderFinalContent(cleanedContent);

    generationComplete = true;
    showStreamCursor(false);
    updateStreamingBadge(false);
    enableActionButtons();
    setGeneratedAt(new Date());
    showTopToast('패치노트 초안 생성이 완료되었습니다.', 'success');
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
            if (circle) {
                circle.className = 'w-5 h-5 rounded-full border-2 border-emerald-500 bg-emerald-500 flex items-center justify-center';
            }
            if (numEl) numEl.textContent = '✓';
            stepEl.className = 'flex items-center gap-1.5 text-emerald-600';
        } else if (i === stepNum) {
            if (circle) {
                circle.className = 'w-5 h-5 rounded-full border-2 border-docu-primary bg-docu-primary/10 flex items-center justify-center';
            }
            if (numEl) numEl.textContent = i;
            stepEl.className = 'flex items-center gap-1.5 text-docu-primary font-medium';
        } else {
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

function retryGeneration() {
    rawContent = '';
    cleanedContent = '';
    sourceRefs = [];
    sourceList = [];
    generationComplete = false;
    activeHighlightRef = null;
    finalRenderAnimated = false;

    // 타이프라이터 상태 초기화
    typewriterDisplayed = 0;
    if (typewriterAnimId !== null) {
        cancelAnimationFrame(typewriterAnimId);
        typewriterAnimId = null;
    }

    const draftContentEl = document.getElementById('draftContent');
    if (draftContentEl) {
        draftContentEl.innerHTML = '';
        draftContentEl.classList.remove('opacity-0', 'opacity-100', 'transition-opacity', 'duration-300', 'ease-out');
    }

    const sourceListEl = document.getElementById('sourceList');
    if (sourceListEl) sourceListEl.innerHTML = '';

    const sourceEmptyEl = document.getElementById('sourceEmpty');
    if (sourceEmptyEl) sourceEmptyEl.textContent = '출처 정보를 수집 중입니다...';

    const countBadge = document.getElementById('sourceCountBadge');
    if (countBadge) countBadge.textContent = '0건';

    updateWordCount('');

    ['copyBtn', 'saveBtn'].forEach(id => {
        const btn = document.getElementById(id);
        if (btn) btn.disabled = true;
    });

    updateStreamingBadge(true);

    const errorOverlay = document.getElementById('errorOverlay');
    if (errorOverlay) errorOverlay.classList.add('hidden');

    const processingOverlay = document.getElementById('processingOverlay');
    if (processingOverlay) {
        processingOverlay.style.transition = '';
        processingOverlay.style.opacity = '1';
        processingOverlay.classList.remove('hidden');
    }

    setStepActive(0);
    startSseStream();
}

function overwriteAndRegenerate() {
    isOverwriteMode = true;
    closeVersionDuplicateModal();
    retryGeneration();
}

// ── 렌더링 ───────────────────────────────────────────────────────────

function renderMarkdownToElement(el, text, sources = []) {
    if (!el) return;

    if (typeof marked === 'undefined' || typeof DOMPurify === 'undefined') {
        el.textContent = stripSourceTags(text ?? '');
        return;
    }

    el.innerHTML = markdownRenderer.renderToHtml(text, sources);
    markdownRenderer.enhanceHtml(el);

    el.querySelectorAll('[data-ref]').forEach(node => {
        node.setAttribute('tabindex', '0');
        node.setAttribute('role', 'button');
    });
}

function renderFinalContent(text) {
    const draftContentEl = document.getElementById('draftContent');
    if (!draftContentEl) return;

    renderMarkdownToElement(draftContentEl, text, sourceList);

    if (!finalRenderAnimated) {
        finalRenderAnimated = true;
        draftContentEl.classList.add('opacity-0');
        requestAnimationFrame(() => {
            draftContentEl.classList.add('transition-opacity', 'duration-300', 'ease-out');
            draftContentEl.classList.remove('opacity-0');
            draftContentEl.classList.add('opacity-100');
        });
    }
}

function stripSourceTags(text) {
    if (!text) return '';
    return text
        .replace(SOURCE_TAG_REGEX, '')
        .replace(/[ \t]{2,}/g, ' ')
        .replace(/\n{3,}/g, '\n\n')
        .trim();
}

// plain text 복사용
function markdownToPlainText(text) {
    const withoutSources = stripSourceTags(text ?? '');

    if (typeof marked === 'undefined' || typeof DOMPurify === 'undefined') {
        return withoutSources;
    }

    const html = markdownRenderer.renderToHtml(withoutSources, []);
    const temp = document.createElement('div');
    temp.innerHTML = html;

    temp.querySelectorAll('sup[data-ref]').forEach(el => el.remove());

    return (temp.textContent || temp.innerText || '')
        .replace(/\n{3,}/g, '\n\n')
        .trim();
}

// ── 출처 패널 ────────────────────────────────────────────────────────

function buildSourceList(refs) {
    sourceList = refs.map((ref, index) => {
        const pendingItems = draftParams?.pendingItems ?? [];
        const matched = findPendingItemByRef(ref, pendingItems);

        // sourceLink: pendingItems에서 찾거나, ref 형식(ISSUE-/DOC-)에서 직접 구성
        let sourceLink = matched?.sourceLink ?? null;
        if (!sourceLink) {
            if (ref.startsWith('ISSUE-')) {
                const sid = parseInt(ref.slice(6), 10);
                if (sid) sourceLink = `/projects/${publicId}/issues/${sid}/analysis`;
            } else if (ref.startsWith('DOC-')) {
                const sid = parseInt(ref.slice(4), 10);
                if (sid) sourceLink = `/projects/${publicId}/documents/${sid}`;
            }
        }

        return {
            index: index + 1,
            ref,
            type: ref.startsWith('ISSUE-') ? 'ISSUE' : 'DOCUMENT',
            patchType: matched?.patchType ?? null,
            title: matched?.title ?? ref,
            sourceId: matched?.sourceId ?? null,
            sourceLink,
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

        item.addEventListener('click', () => {
            highlightSource(source.ref);
            if (source.sourceLink) {
                window.open(source.sourceLink, '_blank', 'noopener,noreferrer');
            }
        });

        item.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                highlightSource(source.ref);
                if (source.sourceLink) {
                    window.open(source.sourceLink, '_blank', 'noopener,noreferrer');
                }
            }
        });

        const row = document.createElement('div');
        row.className = 'flex items-start gap-3';

        const numBadge = document.createElement('span');
        numBadge.className = 'flex-shrink-0 inline-flex items-center justify-center min-w-[1.75rem] h-7 px-2 rounded-full border border-docu-primary/15 bg-gradient-to-b from-docu-primary/10 to-docu-primary/5 text-docu-primary text-[11px] font-semibold shadow-sm mt-0.5';
        numBadge.textContent = `[${source.index}]`;
        row.appendChild(numBadge);

        const info = document.createElement('div');
        info.className = 'flex-1 min-w-0';

        const badges = document.createElement('div');
        badges.className = 'flex items-center gap-1.5 mb-1';
        badges.appendChild(buildSourceTypeBadge(source.type));
        if (source.patchType) badges.appendChild(buildPatchTypeBadge(source.patchType));
        info.appendChild(badges);

        const titleEl = document.createElement('p');
        titleEl.className = 'text-xs text-docu-secondary line-clamp-2 group-hover:text-docu-ink transition-colors';
        titleEl.textContent = source.title;
        info.appendChild(titleEl);

        if (source.sourceLink) {
            const linkHint = document.createElement('p');
            linkHint.className = 'text-[10px] text-docu-primary mt-0.5 flex items-center gap-0.5';
            linkHint.innerHTML = '<svg class="w-2.5 h-2.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"/></svg>원본 보기';
            info.appendChild(linkHint);
        }

        row.appendChild(info);
        item.appendChild(row);
        sourceListEl.appendChild(item);
    });
}

// ── 출처 강조 ────────────────────────────────────────────────────────

function highlightSource(ref) {
    if (activeHighlightRef) {
        const prev = document.getElementById(`source-item-${activeHighlightRef}`);
        if (prev) prev.classList.remove('bg-docu-primary/5', 'ring-1', 'ring-docu-primary/30');
    }

    const prevBadge = document.querySelector(`sup[data-ref="${cssEscape(activeHighlightRef)}"] .cite-ref__inner`);
    if (prevBadge) {
        prevBadge.classList.remove('ring-2', 'ring-docu-primary/25', 'bg-docu-primary/15', 'scale-[1.03]');
    }

    activeHighlightRef = ref;

    const target = document.getElementById(`source-item-${ref}`);
    if (target) {
        target.classList.add('bg-docu-primary/5', 'ring-1', 'ring-docu-primary/30');
        target.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }

    const currentBadge = document.querySelector(`sup[data-ref="${cssEscape(ref)}"] .cite-ref__inner`);
    if (currentBadge) {
        currentBadge.classList.add('ring-2', 'ring-docu-primary/25', 'bg-docu-primary/15', 'scale-[1.03]');
        currentBadge.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' });
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
        CHANGE:      { cls: ['bg-blue-100', 'text-blue-700'], label: '변경' },
        FIX:         { cls: ['bg-red-100', 'text-red-700'], label: '수정' },
        MAINTENANCE: { cls: ['bg-slate-100', 'text-slate-600'], label: '유지보수' },
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
        badge.innerHTML = '<span class="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span> 생성 중';
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
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
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

    const textToCopy = markdownToPlainText(cleanedContent);

    navigator.clipboard.writeText(textToCopy)
        .then(() => {
            showTopToast('텍스트가 클립보드에 복사되었습니다.', 'success');
        })
        .catch(() => {
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
    if (saveBtn) {
        saveBtn.disabled = true;
        saveBtn.textContent = '저장 중...';
    }

    const { title, majorVersion, minorVersion, patchVersion, selectedItemIds } = draftParams ?? {};

    const requestBody = {
        title: title || '',
        content: stripSourceTags(cleanedContent),
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
            if (saveBtn) {
                saveBtn.disabled = false;
                saveBtn.textContent = '저장';
            }
            showTopToast(body.error?.message ?? '저장에 실패했습니다.', 'danger');
        }
    } catch (err) {
        closeSaveConfirmModal();
        if (saveBtn) {
            saveBtn.disabled = false;
            saveBtn.textContent = '저장';
        }
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

function navigateTo(url) {
    if (generationComplete && !isSaved) {
        pendingNavigation = () => {
            window.location.href = url;
        };
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
            const nav = pendingNavigation;
            closeLeaveConfirmModal();
            sessionStorage.removeItem(DRAFT_PARAMS_KEY);
            if (nav) nav();
        };
    }
}

function closeLeaveConfirmModal() {
    pendingNavigation = null;
    const modal = document.getElementById('leaveConfirmModal');
    if (modal) modal.classList.add('hidden');
}

window.addEventListener('beforeunload', (e) => {
    if (generationComplete && !isSaved) {
        e.preventDefault();
        e.returnValue = '생성된 초안이 저장되지 않았습니다. 페이지를 이동하시겠습니까?';
    }
});

// ── 유틸 ─────────────────────────────────────────────────────────────

function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str ?? '';
    return div.innerHTML;
}

function cssEscape(value) {
    if (!value) return '';
    if (window.CSS && typeof window.CSS.escape === 'function') {
        return window.CSS.escape(value);
    }
    return String(value).replace(/["\\]/g, '\\$&');
}
