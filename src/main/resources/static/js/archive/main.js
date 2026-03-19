const projectId = document.getElementById('projectId').value;
let currentPage = 0;
const pageSize = 10;

document.addEventListener('DOMContentLoaded', () => {
    loadGroups(currentPage);

    document.getElementById('btnNewDocument').addEventListener('click', () => {
        resetUploadModal();
        openModal('uploadModal');
    });

    // 드래그앤드롭 설정
    setupDropZone('uploadDropZone', 'uploadFile');
    setupDropZone('newVersionDropZone', 'newVersionFile');
    setupDropZone('editDropZone', 'editFile');

    // 이벤트 위임 (XSS 방지: 인라인 onclick 대신 data-action 사용)
    const groupList = document.getElementById('groupList');

    groupList.addEventListener('click', (e) => {
        const target = e.target.closest('[data-action]');
        if (!target || target.disabled) return;

        const action = target.dataset.action;
        const groupId = Number(target.dataset.groupId);
        const documentId = Number(target.dataset.documentId);

        switch (action) {
            case 'startEditGroupName':
                startEditGroupName(groupId, target.dataset.groupName);
                break;
            case 'submitGroupName':
                submitGroupName(groupId);
                break;
            case 'cancelEditGroupName':
                cancelEditGroupName(groupId, target.dataset.groupName);
                break;
            case 'toggleCategoryDropdown':
                toggleCategoryDropdown(groupId, target);
                break;
            case 'submitCategory':
                submitCategory(groupId, target.dataset.category);
                break;
            case 'openAddVersion':
                openAddVersion(groupId, target.dataset.latestVersion);
                break;
            case 'toggleDocuments':
                toggleDocuments(groupId);
                break;
            case 'downloadDocument':
                downloadDocument(documentId);
                break;
            case 'openEditDocument':
                openEditDocument(
                    documentId, groupId,
                    target.dataset.groupName, target.dataset.category,
                    target.dataset.version, target.dataset.isProcessed === 'true'
                );
                break;
            case 'openDeleteModal':
                openDeleteModal(documentId, groupId, target.dataset.docName, target.dataset.version);
                break;
            case 'retryEmbedding':
                retryEmbedding(documentId);
                break;
        }
    });

    groupList.addEventListener('keydown', (e) => {
        const input = e.target.closest('input[data-action-enter]');
        if (!input) return;

        if (e.key === 'Enter') {
            submitGroupName(Number(input.dataset.groupId));
        } else if (e.key === 'Escape') {
            cancelEditGroupName(Number(input.dataset.groupId), input.dataset.groupName);
        }
    });
});

// ==================== 새 파일 업로드 ====================

function resetUploadModal() {
    document.getElementById('uploadGroupName').value = '';
    document.getElementById('uploadCategory').value = '';
    document.querySelectorAll('#uploadModal .category-chip').forEach(btn => {
        btn.classList.remove(...CHIP_ACTIVE);
        btn.classList.add(...CHIP_INACTIVE);
    });
    document.getElementById('uploadMajor').value = '1';
    document.getElementById('uploadMinor').value = '0';
    document.getElementById('uploadPatch').value = '0';
    document.getElementById('uploadFile').value = '';
    document.getElementById('uploadFileInfo').classList.add('hidden');
    document.getElementById('uploadDropZone').classList.remove('hidden');
    document.getElementById('uploadIsProcessed').checked = false;
    hideModalError('uploadError');
}

function onUploadFileSelected(input) {
    if (input.files.length > 0) {
        document.getElementById('uploadFileName').textContent = input.files[0].name;
        document.getElementById('uploadFileInfo').classList.remove('hidden');
        document.getElementById('uploadDropZone').classList.add('hidden');
    }
}

function clearUploadFile() {
    document.getElementById('uploadFile').value = '';
    document.getElementById('uploadFileInfo').classList.add('hidden');
    document.getElementById('uploadDropZone').classList.remove('hidden');
}

async function submitUpload() {
    hideModalError('uploadError');

    const groupName = document.getElementById('uploadGroupName').value.trim();
    const category = document.getElementById('uploadCategory').value.trim();
    const majorVersion = parseInt(document.getElementById('uploadMajor').value);
    const minorVersion = parseInt(document.getElementById('uploadMinor').value);
    const patchVersion = parseInt(document.getElementById('uploadPatch').value);
    const isProcessed = document.getElementById('uploadIsProcessed').checked;
    const fileInput = document.getElementById('uploadFile');

    if (!groupName) { showModalError('uploadError', '그룹명을 입력해주세요.'); return; }
    if (!category) { showModalError('uploadError', '카테고리를 선택하거나 입력해주세요.'); return; }
    if (!fileInput.files.length) { showModalError('uploadError', '파일을 선택해주세요.'); return; }

    const requestData = { groupName, category, majorVersion, minorVersion, patchVersion, isProcessed };
    const formData = new FormData();
    formData.append('request', new Blob([JSON.stringify(requestData)], { type: 'application/json' }));
    formData.append('file', fileInput.files[0]);

    const btn = document.querySelector('#uploadModal button[onclick="submitUpload()"]');
    startLoading(btn);
    try {
        const result = await callApi(`/api/projects/${projectId}/documents`, {
            method: 'POST',
            body: formData
        });
        if (result.success) {
            const uploadedDocId = result.data.documentId;
            if (result.data.embeddingStatus !== 'NONE') {
                subscribeEmbeddingStatus(uploadedDocId);
            }
            await completeLoading(btn);
            closeModal('uploadModal');
            loadGroups(currentPage);
            return;
        } else {
            showModalError('uploadError', result.error?.message || '업로드에 실패했습니다.');
        }
    } catch (e) {
        showModalError('uploadError', '업로드에 실패했습니다.');
        console.error(e);
    }
    stopLoading(btn);
}

// ==================== 새 버전 업로드 ====================

function openAddVersion(groupId, latestVersion) {
    hideModalError('newVersionError');
    document.getElementById('newVersionGroupId').value = groupId;
    document.getElementById('newVersionFile').value = '';
    document.getElementById('newVersionFileInfo').classList.add('hidden');
    document.getElementById('newVersionDropZone').classList.remove('hidden');
    document.getElementById('newVersionIsProcessed').checked = false;

    // 최신 버전 파싱 → 기본값 세팅
    const vParts = (latestVersion || 'v1.0.0').replace('v', '').split('.');
    document.getElementById('newVersionMajor').value = vParts[0] || '1';
    document.getElementById('newVersionMinor').value = vParts[1] || '0';
    document.getElementById('newVersionPatch').value = vParts[2] || '0';
    document.getElementById('newVersionLatestHint').textContent = `(최신버전: ${latestVersion || 'v1.0.0'})`;

    // 그룹 행에서 그룹명/카테고리 추출
    const groupRow = document.querySelector(`[data-group-id="${groupId}"]`);
    if (groupRow) {
        const groupName = groupRow.querySelector('.font-semibold')?.textContent || '';
        const category = groupRow.querySelector('.bg-surface-muted')?.textContent || '';
        document.getElementById('newVersionGroupName').value = groupName.trim();
        document.getElementById('newVersionCategory').value = category.trim();
    }

    openModal('newVersionModal');
}

function onNewVersionFileSelected(input) {
    if (input.files.length > 0) {
        document.getElementById('newVersionFileName').textContent = input.files[0].name;
        document.getElementById('newVersionFileInfo').classList.remove('hidden');
        document.getElementById('newVersionDropZone').classList.add('hidden');
    }
}

function clearNewVersionFile() {
    document.getElementById('newVersionFile').value = '';
    document.getElementById('newVersionFileInfo').classList.add('hidden');
    document.getElementById('newVersionDropZone').classList.remove('hidden');
}

async function submitNewVersion() {
    hideModalError('newVersionError');

    const groupId = document.getElementById('newVersionGroupId').value;
    const majorVersion = parseInt(document.getElementById('newVersionMajor').value);
    const minorVersion = parseInt(document.getElementById('newVersionMinor').value);
    const patchVersion = parseInt(document.getElementById('newVersionPatch').value);
    const isProcessed = document.getElementById('newVersionIsProcessed').checked;
    const fileInput = document.getElementById('newVersionFile');

    if (!fileInput.files.length) { showModalError('newVersionError', '파일을 선택해주세요.'); return; }

    const requestData = { majorVersion, minorVersion, patchVersion, isProcessed };
    const formData = new FormData();
    formData.append('request', new Blob([JSON.stringify(requestData)], { type: 'application/json' }));
    formData.append('file', fileInput.files[0]);

    const btn = document.querySelector('#newVersionModal button[onclick="submitNewVersion()"]');
    startLoading(btn);
    try {
        const result = await callApi(`/api/projects/${projectId}/groups/${groupId}/documents`, {
            method: 'POST',
            body: formData
        });
        if (result.success) {
            const uploadedDocId = result.data.documentId;
            if (result.data.embeddingStatus !== 'NONE') {
                subscribeEmbeddingStatus(uploadedDocId);
            }
            await completeLoading(btn);
            closeModal('newVersionModal');
            loadGroups(currentPage);
            return;
        } else {
            showModalError('newVersionError', result.error?.message || '업로드에 실패했습니다.');
        }
    } catch (e) {
        showModalError('newVersionError', '업로드에 실패했습니다.');
        console.error(e);
    }
    stopLoading(btn);
}

// ==================== 파일 수정 ====================

function openEditDocument(documentId, groupId, groupName, category, version, isProcessed) {
    hideModalError('editError');
    document.getElementById('editDocumentId').value = documentId;
    document.getElementById('editGroupId').value = groupId;
    document.getElementById('editGroupName').value = groupName || '';
    document.getElementById('editCategory').value = category || '';

    const currentVersion = version || 'v1.0.0';
    const vParts = currentVersion.replace('v', '').split('.');
    document.getElementById('editMajor').value = vParts[0] || '1';
    document.getElementById('editMinor').value = vParts[1] || '0';
    document.getElementById('editPatch').value = vParts[2] || '0';
    document.getElementById('editVersionHint').textContent = `(현재버전: ${currentVersion})`;

    document.getElementById('editIsProcessed').checked = isProcessed || false;
    document.getElementById('editFileToggle').checked = false;
    document.getElementById('editFileArea').classList.add('hidden');
    document.getElementById('editFile').value = '';
    document.getElementById('editFileInfo').classList.add('hidden');
    document.getElementById('editDropZone').classList.remove('hidden');

    openModal('editModal');
}

async function submitEdit() {
    hideModalError('editError');

    const documentId = document.getElementById('editDocumentId').value;
    const majorVersion = parseInt(document.getElementById('editMajor').value);
    const minorVersion = parseInt(document.getElementById('editMinor').value);
    const patchVersion = parseInt(document.getElementById('editPatch').value);
    const isProcessed = document.getElementById('editIsProcessed').checked;
    const fileInput = document.getElementById('editFile');
    const fileEnabled = document.getElementById('editFileToggle').checked;

    const requestData = { majorVersion, minorVersion, patchVersion, isProcessed };
    const formData = new FormData();
    formData.append('request', new Blob([JSON.stringify(requestData)], { type: 'application/json' }));
    if (fileEnabled && fileInput.files.length > 0) {
        formData.append('file', fileInput.files[0]);
    }

    const btn = document.querySelector('#editModal button[onclick="submitEdit()"]');
    startLoading(btn);
    try {
        const result = await callApi(`/api/projects/${projectId}/documents/${documentId}`, {
            method: 'PATCH',
            body: formData
        });
        if (result.success) {
            await completeLoading(btn);
            closeModal('editModal');
            loadGroups(currentPage);
            return;
        } else {
            showModalError('editError', result.error?.message || '수정에 실패했습니다.');
        }
    } catch (e) {
        showModalError('editError', '수정에 실패했습니다.');
        console.error(e);
    }
    stopLoading(btn);
}

// ==================== 삭제 ====================

let deleteTarget = { documentId: null, groupId: null };

function openDeleteModal(documentId, groupId, docName, version) {
    deleteTarget = { documentId, groupId };
    document.getElementById('deleteDocInfo').textContent = `${docName} (${version})`;
    openModal('deleteModal');
}

async function confirmDelete() {
    const btn = document.querySelector('#deleteModal button[onclick="confirmDelete()"]');
    startLoading(btn);
    try {
        const result = await callApi(
            `/api/projects/${projectId}/documents/${deleteTarget.documentId}`,
            { method: 'DELETE' }
        );
        if (result.success) {
            await completeLoading(btn);
            closeModal('deleteModal');
            loadGroups(currentPage);
            return;
        } else {
            alert(result.error?.message || '문서 삭제에 실패했습니다.');
        }
    } catch (e) {
        alert('문서 삭제에 실패했습니다.');
        console.error(e);
    }
    stopLoading(btn);
}

// ==================== 모두접기 ====================

function collapseAll() {
    document.querySelectorAll('[id^="docs-"]:not(.hidden)').forEach(el => {
        el.classList.add('hidden');
        const groupId = el.id.replace('docs-', '');
        const arrow = document.getElementById(`arrow-${groupId}`);
        if (arrow) arrow.classList.remove('rotate-180');
    });
    updateCollapseAllButton();
}

function updateCollapseAllButton() {
    const btn = document.getElementById('btnCollapseAll');
    if (!btn) return;
    const hasExpanded = document.querySelectorAll('[id^="docs-"]:not(.hidden)').length > 0;
    btn.classList.toggle('hidden', !hasExpanded);
}

// ==================== 그룹/문서 로딩 ====================

function getExpandedGroupIds() {
    return [...document.querySelectorAll('[id^="docs-"]:not(.hidden)')]
        .map(el => el.id.replace('docs-', ''));
}

async function restoreExpandedGroups(groupIds) {
    for (const groupId of groupIds) {
        const docsContainer = document.getElementById(`docs-${groupId}`);
        if (docsContainer) {
            await toggleDocuments(groupId);
        }
    }
}

async function loadGroups(page) {
    const expandedIds = getExpandedGroupIds();
    try {
        const result = await callApi(
            `/api/projects/${projectId}/groups?page=${page}&size=${pageSize}`
        );
        if (result.success) {
            renderGroups(result.data);
            renderPagination(result.meta);
            await restoreExpandedGroups(expandedIds);
        } else {
            alert(result.error?.message || '그룹 목록을 불러오는데 실패했습니다.');
        }
    } catch (e) {
        alert('그룹 목록을 불러오는데 실패했습니다.');
        console.error('그룹 목록 조회 실패', e);
    }
}

function renderGroups(groups) {
    const container = document.getElementById('groupList');
    const emptyState = document.getElementById('emptyState');

    if (!groups || groups.length === 0) {
        container.innerHTML = '';
        emptyState.classList.remove('hidden');
        return;
    }

    emptyState.classList.add('hidden');
    container.innerHTML = groups.map(group => `
        <div class="border-b border-divider last:border-b-0" data-group-id="${group.groupId}">
            <!-- 그룹 행 -->
            <div class="grid grid-cols-12 gap-4 px-6 py-4 items-center hover:bg-surface-sub">
                <div class="col-span-4 flex items-center gap-2">
                    <svg class="w-5 h-5 text-docu-secondary" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/></svg>
                    <!-- 그룹명 표시 모드 -->
                    <span id="groupName-display-${group.groupId}" class="font-semibold text-docu-ink cursor-pointer hover:text-docu-primary"
                          data-action="toggleDocuments" data-group-id="${group.groupId}">${escapeHtml(group.groupName)}</span>
                    <button data-action="startEditGroupName" data-group-id="${group.groupId}" data-group-name="${escapeAttr(group.groupName)}" class="text-docu-warning hover:text-docu-warning-dark" title="그룹명 수정">
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z"/></svg>
                    </button>
                    <!-- 그룹명 수정 모드 -->
                    <div id="groupName-edit-${group.groupId}" class="hidden flex items-center gap-1">
                        <input type="text" id="groupName-input-${group.groupId}" value="${escapeAttr(group.groupName)}"
                               class="form-input w-48 px-2 py-1"
                               maxlength="30"
                               data-action-enter="submitGroupName" data-action-escape="cancelEditGroupName"
                               data-group-id="${group.groupId}" data-group-name="${escapeAttr(group.groupName)}">
                        <button data-action="submitGroupName" data-group-id="${group.groupId}" class="text-docu-success hover:text-docu-success-dark" title="확인">
                            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"/></svg>
                        </button>
                        <button data-action="cancelEditGroupName" data-group-id="${group.groupId}" data-group-name="${escapeAttr(group.groupName)}" class="text-docu-secondary hover:text-docu-ink" title="취소">
                            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/></svg>
                        </button>
                    </div>
                </div>
                <div class="col-span-2 text-center relative">
                    <!-- 카테고리 표시 -->
                    <span class="inline-flex items-center gap-1">
                        <span id="category-display-${group.groupId}" class="inline-block bg-surface-muted text-docu-ink text-xs font-medium px-2.5 py-1 rounded-docu-btn">${escapeHtml(group.category)}</span>
                        <button data-action="toggleCategoryDropdown" data-group-id="${group.groupId}" class="text-docu-secondary hover:text-docu-primary" title="카테고리 수정">
                            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 9l4-4 4 4m0 6l-4 4-4-4"/></svg>
                        </button>
                    </span>
                    <!-- 카테고리 드롭다운 -->
                    <div id="category-dropdown-${group.groupId}" class="hidden fixed bg-surface-card border border-divider rounded-docu-btn shadow-docu-card z-50 w-48">
                        ${['기획서','보고서','기술문서','기타'].map(cat => `
                            <button data-action="submitCategory" data-group-id="${group.groupId}" data-category="${cat}"
                                    class="block w-full text-left px-4 py-2 text-sm hover:bg-docu-primary-light hover:text-docu-primary ${group.category === cat ? 'text-docu-primary font-medium bg-docu-primary-light' : 'text-docu-ink'}">
                                ${cat}
                            </button>
                        `).join('')}
                        <div class="border-t border-divider px-3 py-2">
                            <input type="text" id="category-custom-${group.groupId}" placeholder="직접 입력"
                                   class="form-input text-sm py-1"
                                   onkeydown="if(event.key==='Enter'){event.preventDefault();submitCategory(${group.groupId},this.value.trim())}">
                        </div>
                    </div>
                </div>
                <div class="col-span-2 text-center">
                    <span class="text-docu-primary font-medium text-sm">${escapeHtml(group.latestVersion)}</span>
                </div>
                <div class="col-span-2 text-center text-sm text-docu-secondary">
                    ${group.documentCount} 개
                </div>
                <div class="col-span-2 flex items-center justify-between">
                    <button class="border border-docu-success text-docu-success hover:bg-docu-success-light text-xs font-medium px-3 py-1.5 rounded-docu-btn"
                            data-action="openAddVersion" data-group-id="${group.groupId}" data-latest-version="${escapeAttr(group.latestVersion)}">
                        + 버전추가
                    </button>
                    <button class="text-sm text-docu-secondary hover:text-docu-ink flex items-center gap-1"
                            data-action="toggleDocuments" data-group-id="${group.groupId}">
                        펼치기
                        <svg class="w-4 h-4 transition-transform" id="arrow-${group.groupId}" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/></svg>
                    </button>
                </div>
            </div>
            <!-- 문서 목록 (펼치기) -->
            <div id="docs-${group.groupId}" class="hidden bg-surface-sub">
            </div>
        </div>
    `).join('');
}

async function toggleDocuments(groupId) {
    const docsContainer = document.getElementById(`docs-${groupId}`);
    const arrow = document.getElementById(`arrow-${groupId}`);

    if (!docsContainer.classList.contains('hidden')) {
        docsContainer.classList.add('hidden');
        arrow.classList.remove('rotate-180');
        updateCollapseAllButton();
        return;
    }

    // 그룹 행에서 그룹명/카테고리 추출
    const groupRow = document.querySelector(`[data-group-id="${groupId}"]`);
    const groupName = groupRow?.querySelector('.font-semibold')?.textContent?.trim() || '';
    const category = groupRow?.querySelector('.bg-surface-muted')?.textContent?.trim() || '';

    try {
        const result = await callApi(
            `/api/projects/${projectId}/groups/${groupId}/documents`
        );
        if (result.success) {
            renderDocuments(groupId, result.data, groupName, category);
            docsContainer.classList.remove('hidden');
            arrow.classList.add('rotate-180');
            updateCollapseAllButton();
        } else {
            alert(result.error?.message || '문서 목록을 불러오는데 실패했습니다.');
        }
    } catch (e) {
        alert('문서 목록을 불러오는데 실패했습니다.');
        console.error('문서 목록 조회 실패', e);
    }
}

function renderDocuments(groupId, documents, groupName, category) {
    const container = document.getElementById(`docs-${groupId}`);
    groupName = groupName || '';
    category = category || '';

    if (!documents || documents.length === 0) {
        container.innerHTML = '<div class="px-12 py-4 text-sm text-docu-secondary">문서가 없습니다.</div>';
        return;
    }

    container.innerHTML = `
        <div class="mx-6 mb-4 card-base overflow-hidden">
            <div class="grid grid-cols-12 gap-4 px-6 py-2.5 bg-surface-sub border-b border-divider text-xs font-medium text-docu-secondary">
                <div class="col-span-1">버전</div>
                <div class="col-span-3">원본 파일명</div>
                <div class="col-span-1 text-center">확장자</div>
                <div class="col-span-2 text-center">처음 업로드일시</div>
                <div class="col-span-2 text-center">마지막 수정일시</div>
                <div class="col-span-1 text-center">패치노트</div>
                <div class="col-span-1 text-center">임베딩</div>
                <div class="col-span-1"></div>
            </div>
            ${documents.map(doc => `
                <div class="grid grid-cols-12 gap-4 px-6 py-3 items-center border-b border-divider last:border-b-0 hover:bg-surface-sub">
                    <div class="col-span-1">
                        <span class="inline-block border border-docu-primary text-docu-primary text-xs font-medium px-2 py-0.5 rounded-docu-btn">${escapeHtml(doc.version)}</span>
                    </div>
                    <div class="col-span-3">
                        <a href="/projects/${projectId}/documents/${doc.documentId}" class="text-sm text-docu-ink hover:text-docu-primary truncate block">
                            ${escapeHtml(doc.documentName)}
                        </a>
                    </div>
                    <div class="col-span-1 text-center text-xs text-docu-secondary uppercase">${escapeHtml(doc.extension)}</div>
                    <div class="col-span-2 text-center text-xs text-docu-secondary">${formatDateTime(doc.uploadedAt)}</div>
                    <div class="col-span-2 text-center text-xs text-docu-secondary">${formatDateTime(doc.reuploadedAt)}</div>
                    <div class="col-span-1 text-center text-xs font-medium ${doc.isProcessed ? 'text-docu-secondary' : 'text-docu-success'}">${doc.isProcessed ? 'X' : 'O'}</div>
                    <div class="col-span-1 text-center" id="embedding-status-${doc.documentId}">${renderEmbeddingBadge(doc.embeddingStatus)}</div>
                    <div class="col-span-1 flex items-center justify-end gap-3" id="doc-actions-${doc.documentId}">
                        ${doc.embeddingStatus === 'FAILED' ? `
                        <button class="text-docu-secondary-dark hover:text-docu-ink" title="임베딩 재시도"
                                data-action="retryEmbedding" data-document-id="${doc.documentId}">
                            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"/></svg>
                        </button>` : ''}
                        <button class="text-docu-primary hover:text-docu-primary-dark" title="다운로드"
                                data-action="downloadDocument" data-document-id="${doc.documentId}">
                            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"/></svg>
                        </button>
                        <button class="${isEmbeddingInProgress(doc.embeddingStatus) ? 'text-docu-secondary/40 cursor-not-allowed' : 'text-docu-warning hover:text-docu-warning-dark'}" title="수정"
                                ${isEmbeddingInProgress(doc.embeddingStatus) ? 'disabled' : ''}
                                data-action="openEditDocument" data-document-id="${doc.documentId}" data-group-id="${groupId}"
                                data-group-name="${escapeAttr(groupName)}" data-category="${escapeAttr(category)}"
                                data-version="${escapeAttr(doc.version)}" data-is-processed="${doc.isProcessed || false}">
                            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z"/></svg>
                        </button>
                        <button class="${isEmbeddingInProgress(doc.embeddingStatus) ? 'text-docu-secondary/40 cursor-not-allowed' : 'text-docu-danger hover:text-docu-danger-dark'}" title="삭제"
                                ${isEmbeddingInProgress(doc.embeddingStatus) ? 'disabled' : ''}
                                data-action="openDeleteModal" data-document-id="${doc.documentId}" data-group-id="${groupId}"
                                data-doc-name="${escapeAttr(doc.documentName + '.' + doc.extension)}" data-version="${escapeAttr(doc.version)}">
                            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/></svg>
                        </button>
                    </div>
                </div>
            `).join('')}
        </div>
    `;
}

// ==================== 페이지네이션 ====================

function renderPagination(meta) {
    if (!meta) return;

    const pageInfo = document.getElementById('pageInfo');
    const pagination = document.getElementById('pagination');

    const start = meta.page * meta.size + 1;
    const end = Math.min(start + meta.size - 1, meta.totalElements);
    pageInfo.textContent = `총 ${meta.totalElements}개 항목 중 ${start}-${end} 표시`;

    let html = '';

    html += `<button class="px-3 py-1.5 text-sm border border-divider rounded-docu-btn ${meta.page === 0 ? 'text-docu-secondary/40 cursor-not-allowed' : 'text-docu-secondary hover:bg-surface-base'}"
                     ${meta.page === 0 ? 'disabled' : ''} onclick="goToPage(${meta.page - 1})">
                &lt; 이전
             </button>`;

    for (let i = 0; i < meta.totalPages; i++) {
        html += `<button class="w-9 h-9 text-sm rounded-docu-btn ${i === meta.page ? 'bg-docu-primary text-white' : 'text-docu-ink hover:bg-surface-base'}"
                         onclick="goToPage(${i})">${i + 1}</button>`;
    }

    html += `<button class="px-3 py-1.5 text-sm border border-divider rounded-docu-btn ${meta.page >= meta.totalPages - 1 ? 'text-docu-secondary/40 cursor-not-allowed' : 'text-docu-secondary hover:bg-surface-base'}"
                     ${meta.page >= meta.totalPages - 1 ? 'disabled' : ''} onclick="goToPage(${meta.page + 1})">
                다음 &gt;
             </button>`;

    pagination.innerHTML = html;
}

function goToPage(page) {
    currentPage = page;
    loadGroups(page);
}

// ==================== 유틸리티 ====================

function downloadDocument(documentId) {
    window.location.href = `/api/projects/${projectId}/documents/${documentId}/download`;
}

// ==================== 그룹명 인라인 수정 ====================

function startEditGroupName(groupId, currentName) {
    // 표시 모드 숨기고 수정 모드 표시
    document.getElementById(`groupName-display-${groupId}`).classList.add('hidden');
    document.getElementById(`groupName-display-${groupId}`).nextElementSibling.classList.add('hidden'); // 연필 버튼
    const editDiv = document.getElementById(`groupName-edit-${groupId}`);
    editDiv.classList.remove('hidden');
    const input = document.getElementById(`groupName-input-${groupId}`);
    input.value = currentName;
    input.focus();
    input.select();
}

function cancelEditGroupName(groupId, originalName) {
    document.getElementById(`groupName-edit-${groupId}`).classList.add('hidden');
    document.getElementById(`groupName-display-${groupId}`).classList.remove('hidden');
    document.getElementById(`groupName-display-${groupId}`).nextElementSibling.classList.remove('hidden');
}

async function submitGroupName(groupId) {
    const input = document.getElementById(`groupName-input-${groupId}`);
    const newName = input.value.trim();
    if (!newName) return;

    try {
        const result = await callApi(
            `/api/projects/${projectId}/groups/${groupId}/groupName`,
            { method: 'PATCH', body: JSON.stringify({ groupName: newName }) }
        );
        if (result.success) {
            loadGroups(currentPage);
        } else {
            alert(result.error?.message || '그룹명 수정에 실패했습니다.');
        }
    } catch (e) {
        alert('그룹명 수정에 실패했습니다.');
        console.error(e);
    }
}

// ==================== 카테고리 드롭다운 수정 ====================

function toggleCategoryDropdown(groupId, triggerElement) {
    // 다른 열린 드롭다운 닫기
    document.querySelectorAll('[id^="category-dropdown-"]').forEach(el => {
        if (el.id !== `category-dropdown-${groupId}`) el.classList.add('hidden');
    });
    const dropdown = document.getElementById(`category-dropdown-${groupId}`);
    dropdown.classList.toggle('hidden');

    if (!dropdown.classList.contains('hidden')) {
        const rect = triggerElement.getBoundingClientRect();
        dropdown.style.top = (rect.bottom + 4) + 'px';
        dropdown.style.left = (rect.left + rect.width / 2 - dropdown.offsetWidth / 2) + 'px';
    }
}

async function submitCategory(groupId, category) {
    document.getElementById(`category-dropdown-${groupId}`).classList.add('hidden');
    if (!category) return;

    try {
        const result = await callApi(
            `/api/projects/${projectId}/groups/${groupId}/category`,
            { method: 'PATCH', body: JSON.stringify({ category }) }
        );
        if (result.success) {
            loadGroups(currentPage);
        } else {
            alert(result.error?.message || '카테고리 수정에 실패했습니다.');
        }
    } catch (e) {
        alert('카테고리 수정에 실패했습니다.');
        console.error(e);
    }
}

// 바깥 클릭 시 드롭다운 닫기
document.addEventListener('click', (e) => {
    if (!e.target.closest('[id^="category-dropdown-"]') && !e.target.closest('[title="카테고리 수정"]')) {
        document.querySelectorAll('[id^="category-dropdown-"]').forEach(el => el.classList.add('hidden'));
    }
});

// ==================== 임베딩 배지 ====================

function renderEmbeddingBadge(status) {
    const badge = EMBEDDING_STATUS[status] || EMBEDDING_STATUS.NONE;
    return `<span class="text-xs font-medium ${badge.classes}">${badge.label}</span>`;
}

function isEmbeddingInProgress(status) {
    return status === 'PENDING' || status === 'PROCESSING';
}

// ==================== 임베딩 재시도 ====================

async function retryEmbedding(documentId) {
    try {
        const result = await callApi(
            `/api/projects/${projectId}/documents/${documentId}/retry-embedding`,
            { method: 'POST' }
        );
        if (result.success) {
            subscribeEmbeddingStatus(documentId);
        } else {
            alert(result.error?.message || '임베딩 재시도에 실패했습니다.');
        }
    } catch (e) {
        alert('임베딩 재시도에 실패했습니다.');
        console.error(e);
    }
}

function updateDocActions(documentId, status) {
    const container = document.getElementById(`doc-actions-${documentId}`);
    if (!container) return;

    const inProgress = isEmbeddingInProgress(status);

    // 수정/삭제 버튼 활성화/비활성화
    container.querySelectorAll('button[title="수정"], button[title="삭제"]').forEach(btn => {
        btn.disabled = inProgress;
        const isEdit = btn.title === '수정';
        btn.className = inProgress
            ? 'text-docu-secondary/40 cursor-not-allowed'
            : isEdit ? 'text-docu-warning hover:text-docu-warning-dark' : 'text-docu-danger hover:text-docu-danger-dark';
    });

    // 재시도 버튼: FAILED일 때만 표시
    let retryBtn = container.querySelector('button[title="임베딩 재시도"]');
    if (status === 'FAILED') {
        if (!retryBtn) {
            const downloadBtn = container.querySelector('button[title="다운로드"]');
            retryBtn = document.createElement('button');
            retryBtn.className = 'text-docu-secondary-dark hover:text-docu-ink';
            retryBtn.title = '임베딩 재시도';
            retryBtn.dataset.action = 'retryEmbedding';
            retryBtn.dataset.documentId = documentId;
            retryBtn.innerHTML = '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"/></svg>';
            container.insertBefore(retryBtn, downloadBtn);
        }
    } else if (retryBtn) {
        retryBtn.remove();
    }
}

// ==================== 임베딩 SSE ====================

function subscribeEmbeddingStatus(documentId) {
    const source = new EventSource(`/api/projects/${projectId}/documents/${documentId}/embedding-status`);

    source.addEventListener('embedding-status', (e) => {
        const status = e.data;

        const statusEl = document.getElementById(`embedding-status-${documentId}`);
        if (statusEl) {
            statusEl.innerHTML = renderEmbeddingBadge(status);
        }

        updateDocActions(documentId, status);

        if (status === 'SUCCESS' || status === 'FAILED') {
            source.close();
        }
    });

    source.onerror = () => {
        source.close();
    };
}
