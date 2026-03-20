// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 저장된 패치노트 목록 페이지
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

// ── 페이지 상태 ──────────────────────────────────────────────────────
let publicId = null;
let allNotes = [];          // 전체 데이터 (서버에서 한 번 로드)
let filteredNotes = [];     // 필터 적용 후 데이터
let currentPage = 1;
const PAGE_SIZE = 10;

let isDeleteMode = false;   // 선택 삭제 모드
let selectedIds = new Set();// 선택된 패치노트 ID

let currentDetailId = null; // 상세 모달에서 보고 있는 ID
let pendingDeleteId = null;  // 단건 삭제 대기 ID

let debounceTimer = null;

// ── 초기화 ───────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
    const el = document.getElementById('publicId');
    if (!el) return;
    publicId = el.value;

    // 키워드 검색 debounce
    const keywordInput = document.getElementById('keywordInput');
    if (keywordInput) {
        keywordInput.addEventListener('input', () => {
            clearTimeout(debounceTimer);
            debounceTimer = setTimeout(applyFilter, 300);
        });
    }

    // 날짜 필터
    ['dateFrom', 'dateTo'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.addEventListener('change', applyFilter);
    });

    // sessionStorage 토스트 처리
    const toastMsg = sessionStorage.getItem('patchnote_list_toast');
    if (toastMsg) {
        sessionStorage.removeItem('patchnote_list_toast');
        showTopToast(toastMsg, 'success');
    }

    loadNotes();
});

// ── 데이터 로드 ──────────────────────────────────────────────────────

async function loadNotes() {
    showState('loading');

    try {
        const body = await callApi(`/api/projects/${publicId}/patch-note`);
        if (body.success) {
            allNotes = body.data ?? [];
            applyFilter();
        } else {
            showState('empty');
            showTopToast(body.error?.message ?? '목록을 불러오지 못했습니다.', 'danger');
        }
    } catch (err) {
        showState('empty');
        showTopToast(err.message, 'danger');
    }
}

// ── 초성 추출 (Java ChoseongUtil과 동일 로직) ─────────────────────────

function extractChoseong(text) {
    if (!text) return '';
    const HANGUL_BASE = 0xAC00;
    const HANGUL_END  = 0xD7A3;
    const JUNG_COUNT  = 21;
    const JONG_COUNT  = 28;
    const CHOSEONG    = ['ㄱ','ㄲ','ㄴ','ㄷ','ㄸ','ㄹ','ㅁ','ㅂ','ㅃ','ㅅ','ㅆ','ㅇ','ㅈ','ㅉ','ㅊ','ㅋ','ㅌ','ㅍ','ㅎ'];
    let result = '';
    for (const c of text) {
        const code = c.charCodeAt(0);
        if (code >= HANGUL_BASE && code <= HANGUL_END) {
            const index = Math.floor((code - HANGUL_BASE) / (JUNG_COUNT * JONG_COUNT));
            result += CHOSEONG[index];
        } else if (/[a-zA-Z0-9]/.test(c) || (code >= 0x3131 && code <= 0x3163)) {
            // 영숫자 및 한글 자모(이미 초성인 경우) 그대로 포함
            result += c.toLowerCase();
        }
    }
    return result;
}

// ── 필터링 ───────────────────────────────────────────────────────────

function applyFilter() {
    const keyword        = (document.getElementById('keywordInput')?.value ?? '').trim().toLowerCase();
    const from           = document.getElementById('dateFrom')?.value ?? '';
    const to             = document.getElementById('dateTo')?.value ?? '';
    const keywordChoseong = extractChoseong(keyword);

    filteredNotes = allNotes.filter(note => {
        // 키워드: 제목 일반 매치 | 제목 초성 매치 | 버전 매치
        if (keyword) {
            const titleLower    = note.title.toLowerCase();
            const titleChoseong = extractChoseong(note.title);
            const versionLower  = note.versionLabel.toLowerCase();

            const titleMatch   = titleLower.includes(keyword)
                              || (keywordChoseong && titleChoseong.includes(keywordChoseong));
            const versionMatch = versionLower.includes(keyword);
            if (!titleMatch && !versionMatch) return false;
        }
        // 날짜 범위
        if (from || to) {
            const noteDate = note.createdAt ? note.createdAt.substring(0, 10) : '';
            if (from && noteDate < from) return false;
            if (to && noteDate > to) return false;
        }
        return true;
    });

    currentPage = 1;
    renderPage();
}

// ── 렌더링 ───────────────────────────────────────────────────────────

function renderPage() {
    if (filteredNotes.length === 0) {
        showState('empty');
        document.getElementById('pagination')?.classList.add('hidden');
        return;
    }

    const totalPages = Math.ceil(filteredNotes.length / PAGE_SIZE);
    const start = (currentPage - 1) * PAGE_SIZE;
    const pageNotes = filteredNotes.slice(start, start + PAGE_SIZE);

    const listEl = document.getElementById('noteList');
    if (!listEl) return;

    listEl.innerHTML = '';
    pageNotes.forEach(note => listEl.appendChild(buildRow(note)));
    showState('list');

    renderPagination(totalPages);
    updateHeaderLayout();
}

function showState(state) {
    const loading = document.getElementById('listLoading');
    const list = document.getElementById('noteList');
    const empty = document.getElementById('listEmpty');
    if (!loading || !list || !empty) return;

    loading.classList.toggle('hidden', state !== 'loading');
    list.classList.toggle('hidden', state !== 'list');
    empty.classList.toggle('hidden', state !== 'empty');
}

// ── 행 생성 ──────────────────────────────────────────────────────────

function buildRow(note) {
    const row = document.createElement('div');
    row.dataset.noteId = note.id;

    if (isDeleteMode) {
        row.className = 'grid grid-cols-12 gap-4 items-center px-6 py-4 hover:bg-surface-subtle transition-colors';
    } else {
        row.className = 'grid grid-cols-12 gap-4 items-center px-6 py-4 hover:bg-surface-subtle transition-colors cursor-pointer';
    }

    // ── 체크박스 (삭제 모드에서만) ──
    if (isDeleteMode) {
        const checkCell = document.createElement('div');
        checkCell.className = 'col-span-1 flex justify-center';
        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.className = 'w-4 h-4 rounded border-divider text-docu-primary cursor-pointer';
        checkbox.checked = selectedIds.has(note.id);
        checkbox.setAttribute('aria-label', `${note.title} 선택`);
        checkbox.addEventListener('change', (e) => {
            if (e.target.checked) selectedIds.add(note.id);
            else selectedIds.delete(note.id);
            updateSelectAll();
        });
        checkCell.appendChild(checkbox);
        row.appendChild(checkCell);
    }

    // ── 버전 (col-span-2) ──
    const versionCell = document.createElement('div');
    versionCell.className = isDeleteMode ? 'col-span-2' : 'col-span-2';
    const versionBadge = document.createElement('span');
    versionBadge.className = 'inline-flex items-center px-2.5 py-1 rounded-full text-xs font-bold bg-docu-primary/10 text-docu-primary';
    versionBadge.textContent = note.versionLabel;
    versionCell.appendChild(versionBadge);
    row.appendChild(versionCell);

    // ── 제목 (col-span-6 or 5) ──
    const titleCell = document.createElement('div');
    titleCell.className = isDeleteMode ? 'col-span-5 min-w-0' : 'col-span-6 min-w-0';
    const titleEl = document.createElement('p');
    titleEl.className = 'text-sm font-semibold text-docu-ink truncate';
    titleEl.textContent = note.title;
    titleCell.appendChild(titleEl);
    row.appendChild(titleCell);

    // ── 저장일시 (col-span-3) ──
    const dateCell = document.createElement('div');
    dateCell.className = 'col-span-3 text-xs text-docu-secondary text-center';
    dateCell.textContent = formatDate(note.createdAt);
    row.appendChild(dateCell);

    // ── 삭제 아이콘 (col-span-1) ──
    const actionCell = document.createElement('div');
    actionCell.className = 'col-span-1 flex justify-end';
    if (!isDeleteMode) {
        const deleteBtn = document.createElement('button');
        deleteBtn.type = 'button';
        deleteBtn.className = 'text-docu-tertiary hover:text-red-500 transition-colors p-1';
        deleteBtn.setAttribute('aria-label', '삭제');
        deleteBtn.innerHTML = `<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/>
        </svg>`;
        deleteBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            openDeleteModal(note.id);
        });
        actionCell.appendChild(deleteBtn);
    }
    row.appendChild(actionCell);

    // 행 클릭 → 상세 (삭제 모드에서는 비활성)
    if (!isDeleteMode) {
        row.addEventListener('click', () => openDetailModal(note.id));
    }

    return row;
}

// ── 테이블 헤더 레이아웃 업데이트 ────────────────────────────────────

function updateHeaderLayout() {
    const checkboxHeader = document.getElementById('headerCheckbox');
    const versionHeader = document.getElementById('headerVersion');
    const titleHeader = document.getElementById('headerTitle');

    if (!checkboxHeader || !versionHeader || !titleHeader) return;

    if (isDeleteMode) {
        checkboxHeader.classList.remove('hidden');
        titleHeader.className = 'col-span-5';
    } else {
        checkboxHeader.classList.add('hidden');
        titleHeader.className = 'col-span-6';
    }
}

// ── 페이지네이션 ──────────────────────────────────────────────────────

function renderPagination(totalPages) {
    const paginationEl = document.getElementById('pagination');
    const infoEl = document.getElementById('paginationInfo');
    const pageNumbersEl = document.getElementById('pageNumbers');
    const prevBtn = document.getElementById('prevPageBtn');
    const nextBtn = document.getElementById('nextPageBtn');

    if (!paginationEl) return;

    if (totalPages <= 1) {
        paginationEl.classList.add('hidden');
        return;
    }

    paginationEl.classList.remove('hidden');

    const start = (currentPage - 1) * PAGE_SIZE + 1;
    const end = Math.min(currentPage * PAGE_SIZE, filteredNotes.length);
    if (infoEl) infoEl.textContent = `${start}-${end} / ${filteredNotes.length}건`;

    if (prevBtn) prevBtn.disabled = currentPage <= 1;
    if (nextBtn) nextBtn.disabled = currentPage >= totalPages;

    if (pageNumbersEl) {
        pageNumbersEl.innerHTML = '';
        const maxVisible = 5;
        let startPage = Math.max(1, currentPage - Math.floor(maxVisible / 2));
        let endPage = Math.min(totalPages, startPage + maxVisible - 1);
        if (endPage - startPage < maxVisible - 1) {
            startPage = Math.max(1, endPage - maxVisible + 1);
        }

        for (let p = startPage; p <= endPage; p++) {
            const btn = document.createElement('button');
            btn.textContent = p;
            btn.className = p === currentPage
                ? 'px-3 py-1.5 text-xs rounded border border-docu-primary text-docu-primary bg-docu-primary/5 font-medium'
                : 'px-3 py-1.5 text-xs rounded border border-divider text-docu-secondary hover:border-docu-primary hover:text-docu-primary transition-colors';
            btn.addEventListener('click', () => changePage(p));
            pageNumbersEl.appendChild(btn);
        }
    }
}

function changePage(page) {
    const totalPages = Math.ceil(filteredNotes.length / PAGE_SIZE);
    if (page < 1 || page > totalPages) return;
    currentPage = page;
    renderPage();
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

// ── 삭제 모드 ────────────────────────────────────────────────────────

function toggleDeleteMode() {
    if (!isDeleteMode) {
        // 삭제 모드 진입
        isDeleteMode = true;
        selectedIds.clear();

        const btn = document.getElementById('deleteModeBtn');
        const cancelBtn = document.getElementById('deleteCancelBtn');
        if (btn) {
            btn.textContent = '선택 삭제';
            btn.className = 'btn-danger';
            btn.setAttribute('aria-pressed', 'true');
        }
        if (cancelBtn) cancelBtn.classList.remove('hidden');

        renderPage();
    } else {
        // 삭제 모드에서 삭제 실행
        if (selectedIds.size === 0) {
            showTopToast('삭제할 항목을 선택해 주세요.', 'warning');
            return;
        }
        openBulkDeleteModal();
    }
}

function cancelDeleteMode() {
    isDeleteMode = false;
    selectedIds.clear();

    const btn = document.getElementById('deleteModeBtn');
    const cancelBtn = document.getElementById('deleteCancelBtn');
    if (btn) {
        btn.textContent = '선택 삭제';
        btn.className = 'btn-secondary';
        btn.setAttribute('aria-pressed', 'false');
    }
    if (cancelBtn) cancelBtn.classList.add('hidden');

    const selectAll = document.getElementById('selectAllCheckbox');
    if (selectAll) selectAll.checked = false;

    renderPage();
}

function toggleSelectAll(checked) {
    const start = (currentPage - 1) * PAGE_SIZE;
    const pageNotes = filteredNotes.slice(start, start + PAGE_SIZE);

    pageNotes.forEach(note => {
        if (checked) selectedIds.add(note.id);
        else selectedIds.delete(note.id);
    });

    // 현재 페이지 체크박스 업데이트
    document.querySelectorAll('#noteList input[type="checkbox"]').forEach(cb => {
        cb.checked = checked;
    });
}

function updateSelectAll() {
    const start = (currentPage - 1) * PAGE_SIZE;
    const pageNotes = filteredNotes.slice(start, start + PAGE_SIZE);
    const allChecked = pageNotes.length > 0 && pageNotes.every(n => selectedIds.has(n.id));

    const selectAll = document.getElementById('selectAllCheckbox');
    if (selectAll) selectAll.checked = allChecked;
}

// ── 상세 모달 ────────────────────────────────────────────────────────

async function openDetailModal(noteId) {
    currentDetailId = noteId;
    const modal = document.getElementById('detailModal');
    if (!modal) return;

    // 초기화
    const titleEl = document.getElementById('detailModalTitle');
    const versionBadge = document.getElementById('detailVersionBadge');
    const createdAtEl = document.getElementById('detailCreatedAt');
    const loadingEl = document.getElementById('detailLoading');
    const contentEl = document.getElementById('detailContent');

    if (titleEl) titleEl.textContent = '불러오는 중...';
    if (versionBadge) versionBadge.textContent = '';
    if (createdAtEl) createdAtEl.textContent = '';
    if (loadingEl) loadingEl.classList.remove('hidden');
    if (contentEl) { contentEl.innerHTML = ''; contentEl.classList.add('hidden'); }

    modal.classList.remove('hidden');

    try {
        const body = await callApi(`/api/projects/${publicId}/patch-note/${noteId}`);
        if (body.success) {
            renderDetailModal(body.data);
        } else {
            closeDetailModal();
            showTopToast(body.error?.message ?? '패치노트를 불러오지 못했습니다.', 'danger');
        }
    } catch (err) {
        closeDetailModal();
        showTopToast(err.message, 'danger');
    }
}

function renderDetailModal(detail) {
    const titleEl = document.getElementById('detailModalTitle');
    const versionBadge = document.getElementById('detailVersionBadge');
    const statusBadge = document.getElementById('detailStatusBadge');
    const createdAtEl = document.getElementById('detailCreatedAt');
    const loadingEl = document.getElementById('detailLoading');
    const contentEl = document.getElementById('detailContent');

    if (titleEl) titleEl.textContent = detail.title;
    if (versionBadge) versionBadge.textContent = detail.versionLabel;
    if (statusBadge) statusBadge.textContent = detail.status === 'DRAFT' ? 'DRAFT' : detail.status;
    if (createdAtEl) createdAtEl.textContent = `저장일시: ${formatDateTime(detail.createdAt)}`;

    if (loadingEl) loadingEl.classList.add('hidden');
    if (contentEl) {
        // Markdown rendering (marked + DOMPurify)
        const cleanContent = stripSourceTags(detail.content ?? '');
        if (typeof marked !== 'undefined' && typeof DOMPurify !== 'undefined') {
            contentEl.innerHTML = DOMPurify.sanitize(marked.parse(cleanContent));
        } else {
            // Fallback: pre-formatted text
            const pre = document.createElement('pre');
            pre.className = 'text-sm text-docu-secondary whitespace-pre-wrap';
            pre.textContent = cleanContent;
            contentEl.appendChild(pre);
        }
        contentEl.classList.remove('hidden');
    }
}

function closeDetailModal() {
    currentDetailId = null;
    const modal = document.getElementById('detailModal');
    if (modal) modal.classList.add('hidden');
}

function copyDetailContent() {
    const contentEl = document.getElementById('detailContent');
    if (!contentEl) return;
    const text = contentEl.innerText || contentEl.textContent;
    navigator.clipboard.writeText(text).then(() => {
        showTopToast('클립보드에 복사되었습니다.', 'success');
    }).catch(() => {
        showTopToast('복사에 실패했습니다.', 'danger');
    });
}

// ── 단건 삭제 ────────────────────────────────────────────────────────

function openDeleteModal(noteId) {
    pendingDeleteId = noteId;
    const modal = document.getElementById('deleteModal');
    if (modal) modal.classList.remove('hidden');
}

function closeDeleteModal() {
    pendingDeleteId = null;
    const modal = document.getElementById('deleteModal');
    if (modal) modal.classList.add('hidden');
}

async function confirmDelete() {
    const noteId = pendingDeleteId;
    closeDeleteModal();
    closeDetailModal();
    if (!noteId) return;

    try {
        const body = await callApi(
            `/api/projects/${publicId}/patch-note/${noteId}`,
            { method: 'DELETE' }
        );
        if (body.success) {
            showTopToast('패치노트가 삭제되었습니다.', 'success');
            allNotes = allNotes.filter(n => n.id !== noteId);
            applyFilter();
        } else {
            showTopToast(body.error?.message ?? '삭제에 실패했습니다.', 'danger');
        }
    } catch (err) {
        showTopToast(err.message, 'danger');
    }
}

// ── 일괄 삭제 ────────────────────────────────────────────────────────

function openBulkDeleteModal() {
    const descEl = document.getElementById('bulkDeleteDesc');
    if (descEl) descEl.textContent = `선택한 ${selectedIds.size}개의 패치노트를 삭제하시겠습니까? 삭제 후에는 복구할 수 없습니다.`;
    const modal = document.getElementById('bulkDeleteModal');
    if (modal) modal.classList.remove('hidden');
}

function closeBulkDeleteModal() {
    const modal = document.getElementById('bulkDeleteModal');
    if (modal) modal.classList.add('hidden');
}

async function confirmBulkDelete() {
    closeBulkDeleteModal();
    const ids = Array.from(selectedIds);
    if (ids.length === 0) return;

    let successCount = 0;
    let failCount = 0;

    for (const noteId of ids) {
        try {
            const body = await callApi(
                `/api/projects/${publicId}/patch-note/${noteId}`,
                { method: 'DELETE' }
            );
            if (body.success) {
                allNotes = allNotes.filter(n => n.id !== noteId);
                successCount++;
            } else {
                failCount++;
            }
        } catch {
            failCount++;
        }
    }

    cancelDeleteMode();
    applyFilter();

    if (failCount === 0) {
        showTopToast(`${successCount}개의 패치노트가 삭제되었습니다.`, 'success');
    } else {
        showTopToast(`${successCount}개 삭제 완료, ${failCount}개 실패.`, 'warning');
    }
}

// ── 날짜 포맷 ────────────────────────────────────────────────────────

function formatDate(isoString) {
    if (!isoString) return '-';
    try {
        return new Date(isoString).toLocaleDateString('ko-KR', {
            year: 'numeric', month: '2-digit', day: '2-digit',
        });
    } catch { return '-'; }
}

function formatDateTime(isoString) {
    if (!isoString) return '-';
    try {
        return new Date(isoString).toLocaleString('ko-KR', {
            year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit',
        });
    } catch { return '-'; }
}

// ── 소스 태그 제거 유틸 ───────────────────────────────────────────────

/**
 * {{source:REF}} 태그를 제거하고 정제된 텍스트를 반환한다.
 */
function stripSourceTags(text) {
    if (!text) return '';
    return text.replace(/\{\{source:[^}]+\}\}/g, '').replace(/  +/g, ' ').trim();
}
