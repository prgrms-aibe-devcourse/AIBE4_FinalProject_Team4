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
});

// ==================== 모달 공통 ====================

function openModal(modalId) {
    document.getElementById(modalId).classList.remove('hidden');
    document.body.style.overflow = 'hidden';
}

function closeModal(modalId) {
    document.getElementById(modalId).classList.add('hidden');
    document.body.style.overflow = '';
}

function showModalError(errorElId, message) {
    const el = document.getElementById(errorElId);
    el.textContent = message;
    el.classList.remove('hidden');
}

function hideModalError(errorElId) {
    document.getElementById(errorElId).classList.add('hidden');
}

function setupDropZone(zoneId, fileInputId) {
    const zone = document.getElementById(zoneId);
    if (!zone) return;

    zone.addEventListener('dragover', (e) => {
        e.preventDefault();
        zone.classList.add('border-sky-400', 'bg-sky-50/30');
    });
    zone.addEventListener('dragleave', () => {
        zone.classList.remove('border-sky-400', 'bg-sky-50/30');
    });
    zone.addEventListener('drop', (e) => {
        e.preventDefault();
        zone.classList.remove('border-sky-400', 'bg-sky-50/30');
        const fileInput = document.getElementById(fileInputId);
        if (e.dataTransfer.files.length > 0) {
            fileInput.files = e.dataTransfer.files;
            fileInput.dispatchEvent(new Event('change'));
        }
    });
}

// ==================== 새 파일 업로드 ====================

function resetUploadModal() {
    document.getElementById('uploadGroupName').value = '';
    document.getElementById('uploadCategory').selectedIndex = 0;
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
    const category = document.getElementById('uploadCategory').value;
    const majorVersion = parseInt(document.getElementById('uploadMajor').value);
    const minorVersion = parseInt(document.getElementById('uploadMinor').value);
    const patchVersion = parseInt(document.getElementById('uploadPatch').value);
    const isProcessed = document.getElementById('uploadIsProcessed').checked;
    const fileInput = document.getElementById('uploadFile');

    if (!groupName) { showModalError('uploadError', '그룹명을 입력해주세요.'); return; }
    if (!fileInput.files.length) { showModalError('uploadError', '파일을 선택해주세요.'); return; }

    const requestData = { groupName, category, majorVersion, minorVersion, patchVersion, isProcessed };
    const formData = new FormData();
    formData.append('request', new Blob([JSON.stringify(requestData)], { type: 'application/json' }));
    formData.append('file', fileInput.files[0]);

    try {
        const result = await callApi(`/api/projects/${projectId}/documents`, {
            method: 'POST',
            body: formData
        });
        if (result.success) {
            closeModal('uploadModal');
            loadGroups(currentPage);
        } else {
            showModalError('uploadError', result.error?.message || '업로드에 실패했습니다.');
        }
    } catch (e) {
        showModalError('uploadError', '업로드에 실패했습니다.');
        console.error(e);
    }
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
        const category = groupRow.querySelector('.bg-gray-100')?.textContent || '';
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

    try {
        const result = await callApi(`/api/projects/${projectId}/groups/${groupId}/documents`, {
            method: 'POST',
            body: formData
        });
        if (result.success) {
            closeModal('newVersionModal');
            loadGroups(currentPage);
        } else {
            showModalError('newVersionError', result.error?.message || '업로드에 실패했습니다.');
        }
    } catch (e) {
        showModalError('newVersionError', '업로드에 실패했습니다.');
        console.error(e);
    }
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

function toggleEditFile() {
    const area = document.getElementById('editFileArea');
    if (document.getElementById('editFileToggle').checked) {
        area.classList.remove('hidden');
    } else {
        area.classList.add('hidden');
        clearEditFile();
    }
}

function onEditFileSelected(input) {
    if (input.files.length > 0) {
        document.getElementById('editFileName').textContent = input.files[0].name;
        document.getElementById('editFileInfo').classList.remove('hidden');
        document.getElementById('editDropZone').classList.add('hidden');
    }
}

function clearEditFile() {
    document.getElementById('editFile').value = '';
    document.getElementById('editFileInfo').classList.add('hidden');
    document.getElementById('editDropZone').classList.remove('hidden');
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

    try {
        const result = await callApi(`/api/projects/${projectId}/documents/${documentId}`, {
            method: 'PATCH',
            body: formData
        });
        if (result.success) {
            closeModal('editModal');
            loadGroups(currentPage);
        } else {
            showModalError('editError', result.error?.message || '수정에 실패했습니다.');
        }
    } catch (e) {
        showModalError('editError', '수정에 실패했습니다.');
        console.error(e);
    }
}

// ==================== 삭제 ====================

let deleteTarget = { documentId: null, groupId: null };

function openDeleteModal(documentId, groupId, docName, version) {
    deleteTarget = { documentId, groupId };
    document.getElementById('deleteDocInfo').textContent = `${docName} (${version})`;
    openModal('deleteModal');
}

async function confirmDelete() {
    try {
        const result = await callApi(
            `/api/projects/${projectId}/documents/${deleteTarget.documentId}`,
            { method: 'DELETE' }
        );
        if (result.success) {
            closeModal('deleteModal');
            loadGroups(currentPage);
        }
    } catch (e) {
        alert('문서 삭제에 실패했습니다.');
        console.error(e);
    }
}

// ==================== 그룹/문서 로딩 ====================

async function loadGroups(page) {
    try {
        const result = await callApi(
            `/api/projects/${projectId}/groups?page=${page}&size=${pageSize}`
        );
        if (result.success) {
            renderGroups(result.data);
            renderPagination(result.meta);
        }
    } catch (e) {
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
        <div class="border-b border-gray-100 last:border-b-0" data-group-id="${group.groupId}">
            <!-- 그룹 행 -->
            <div class="grid grid-cols-12 gap-4 px-6 py-4 items-center hover:bg-gray-50">
                <div class="col-span-5 flex items-center gap-2">
                    <svg class="w-5 h-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/></svg>
                    <!-- 그룹명 표시 모드 -->
                    <span id="groupName-display-${group.groupId}" class="font-semibold text-gray-800">${escapeHtml(group.groupName)}</span>
                    <button onclick="startEditGroupName(${group.groupId}, '${escapeAttr(group.groupName)}')" class="text-yellow-500 hover:text-yellow-600" title="그룹명 수정">
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z"/></svg>
                    </button>
                    <!-- 그룹명 수정 모드 -->
                    <div id="groupName-edit-${group.groupId}" class="hidden flex items-center gap-1">
                        <input type="text" id="groupName-input-${group.groupId}" value="${escapeAttr(group.groupName)}"
                               class="px-2 py-1 border border-gray-300 rounded text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 w-48"
                               maxlength="30"
                               onkeydown="if(event.key==='Enter') submitGroupName(${group.groupId}); if(event.key==='Escape') cancelEditGroupName(${group.groupId}, '${escapeAttr(group.groupName)}');">
                        <button onclick="submitGroupName(${group.groupId})" class="text-green-500 hover:text-green-600" title="확인">
                            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"/></svg>
                        </button>
                        <button onclick="cancelEditGroupName(${group.groupId}, '${escapeAttr(group.groupName)}')" class="text-gray-400 hover:text-gray-600" title="취소">
                            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/></svg>
                        </button>
                    </div>
                </div>
                <div class="col-span-2 text-center relative">
                    <!-- 카테고리 표시 -->
                    <span class="inline-flex items-center gap-1">
                        <span id="category-display-${group.groupId}" class="inline-block bg-gray-100 text-gray-700 text-xs font-medium px-2.5 py-1 rounded">${escapeHtml(group.category)}</span>
                        <button onclick="toggleCategoryDropdown(${group.groupId}, event)" class="text-gray-400 hover:text-indigo-600" title="카테고리 수정">
                            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 9l4-4 4 4m0 6l-4 4-4-4"/></svg>
                        </button>
                    </span>
                    <!-- 카테고리 드롭다운 -->
                    <div id="category-dropdown-${group.groupId}" class="hidden fixed bg-white border border-gray-200 rounded-lg shadow-lg z-50 w-36">
                        ${['기획서','기술문서','디자인','보고서','기타'].map(cat => `
                            <button onclick="submitCategory(${group.groupId}, '${cat}')"
                                    class="block w-full text-left px-4 py-2 text-sm hover:bg-indigo-50 hover:text-indigo-600 ${group.category === cat ? 'text-indigo-600 font-medium bg-indigo-50' : 'text-gray-700'}">
                                ${cat}
                            </button>
                        `).join('')}
                    </div>
                </div>
                <div class="col-span-2 text-center">
                    <span class="text-indigo-600 font-medium text-sm">${escapeHtml(group.latestVersion)}</span>
                </div>
                <div class="col-span-1 text-center text-sm text-gray-600">
                    ${group.documentCount} 개
                </div>
                <div class="col-span-2 flex items-center justify-end gap-4">
                    <button class="border border-green-600 text-green-600 hover:bg-green-50 text-xs font-medium px-3 py-1.5 rounded-lg"
                            onclick="openAddVersion(${group.groupId}, '${escapeAttr(group.latestVersion)}')">
                        + 버전추가
                    </button>
                    <button class="text-sm text-gray-500 hover:text-gray-700 flex items-center gap-1"
                            onclick="toggleDocuments(${group.groupId})">
                        펼치기
                        <svg class="w-4 h-4 transition-transform" id="arrow-${group.groupId}" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/></svg>
                    </button>
                </div>
            </div>
            <!-- 문서 목록 (펼치기) -->
            <div id="docs-${group.groupId}" class="hidden bg-gray-50">
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
        return;
    }

    // 그룹 행에서 그룹명/카테고리 추출
    const groupRow = document.querySelector(`[data-group-id="${groupId}"]`);
    const groupName = groupRow?.querySelector('.font-semibold')?.textContent?.trim() || '';
    const category = groupRow?.querySelector('.bg-gray-100')?.textContent?.trim() || '';

    try {
        const result = await callApi(
            `/api/projects/${projectId}/groups/${groupId}/documents`
        );
        if (result.success) {
            renderDocuments(groupId, result.data, groupName, category);
            docsContainer.classList.remove('hidden');
            arrow.classList.add('rotate-180');
        }
    } catch (e) {
        console.error('문서 목록 조회 실패', e);
    }
}

function renderDocuments(groupId, documents, groupName, category) {
    const container = document.getElementById(`docs-${groupId}`);
    groupName = groupName || '';
    category = category || '';

    if (!documents || documents.length === 0) {
        container.innerHTML = '<div class="px-12 py-4 text-sm text-gray-400">문서가 없습니다.</div>';
        return;
    }

    container.innerHTML = `
        <div class="mx-6 mb-4 border border-gray-200 rounded-lg overflow-hidden bg-white">
            <div class="grid grid-cols-12 gap-4 px-6 py-2.5 bg-gray-50 border-b border-gray-200 text-xs font-medium text-gray-500">
                <div class="col-span-1">버전</div>
                <div class="col-span-3">원본 파일명</div>
                <div class="col-span-1 text-center">확장자</div>
                <div class="col-span-2 text-center">처음 업로드일시</div>
                <div class="col-span-2 text-center">마지막 수정일시</div>
                <div class="col-span-1 text-center">패치노트 반영</div>
                <div class="col-span-2"></div>
            </div>
            ${documents.map(doc => `
                <div class="grid grid-cols-12 gap-4 px-6 py-3 items-center border-b border-gray-100 last:border-b-0 hover:bg-gray-50">
                    <div class="col-span-1">
                        <span class="inline-block border border-indigo-300 text-indigo-600 text-xs font-medium px-2 py-0.5 rounded">${escapeHtml(doc.version)}</span>
                    </div>
                    <div class="col-span-3">
                        <a href="/projects/${projectId}/documents/${doc.documentId}" class="text-sm text-gray-800 hover:text-indigo-600">
                            ${escapeHtml(doc.documentName)}
                        </a>
                    </div>
                    <div class="col-span-1 text-center text-xs text-gray-500 uppercase">${escapeHtml(doc.extension)}</div>
                    <div class="col-span-2 text-center text-xs text-gray-500">${formatDateTime(doc.uploadedAt)}</div>
                    <div class="col-span-2 text-center text-xs text-gray-500">${formatDateTime(doc.reuploadedAt)}</div>
                    <div class="col-span-1 text-center text-xs font-medium ${doc.isProcessed ? 'text-gray-400' : 'text-green-600'}">${doc.isProcessed ? 'X' : 'O'}</div>
                    <div class="col-span-2 flex items-center justify-end gap-3">
                        <button class="text-blue-500 hover:text-blue-600" title="다운로드" onclick="downloadDocument(${doc.documentId})">
                            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"/></svg>
                        </button>
                        <button class="text-yellow-500 hover:text-yellow-600" title="수정"
                                onclick="openEditDocument(${doc.documentId}, ${groupId}, '${escapeAttr(groupName)}', '${escapeAttr(category)}', '${escapeAttr(doc.version)}', ${doc.isProcessed || false})">
                            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z"/></svg>
                        </button>
                        <button class="text-red-500 hover:text-red-600" title="삭제"
                                onclick="openDeleteModal(${doc.documentId}, ${groupId}, '${escapeAttr(doc.documentName + '.' + doc.extension)}', '${escapeAttr(doc.version)}')">
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

    html += `<button class="px-3 py-1.5 text-sm border border-gray-300 rounded-lg ${meta.page === 0 ? 'text-gray-300 cursor-not-allowed' : 'text-gray-600 hover:bg-gray-100'}"
                     ${meta.page === 0 ? 'disabled' : ''} onclick="goToPage(${meta.page - 1})">
                &lt; 이전
             </button>`;

    for (let i = 0; i < meta.totalPages; i++) {
        html += `<button class="w-9 h-9 text-sm rounded-lg ${i === meta.page ? 'bg-indigo-600 text-white' : 'text-gray-600 hover:bg-gray-100'}"
                         onclick="goToPage(${i})">${i + 1}</button>`;
    }

    html += `<button class="px-3 py-1.5 text-sm border border-gray-300 rounded-lg ${meta.page >= meta.totalPages - 1 ? 'text-gray-300 cursor-not-allowed' : 'text-gray-600 hover:bg-gray-100'}"
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

function formatDateTime(dateTimeStr) {
    if (!dateTimeStr) return '-';
    const dt = new Date(dateTimeStr);
    const y = dt.getFullYear();
    const m = String(dt.getMonth() + 1).padStart(2, '0');
    const d = String(dt.getDate()).padStart(2, '0');
    const h = String(dt.getHours()).padStart(2, '0');
    const min = String(dt.getMinutes()).padStart(2, '0');
    return `${y}-${m}-${d} ${h}:${min}`;
}

function escapeHtml(str) {
    if (!str) return '';
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

function escapeAttr(str) {
    if (!str) return '';
    return str.replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/"/g, '&quot;');
}

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
        }
    } catch (e) {
        alert('그룹명 수정에 실패했습니다.');
        console.error(e);
    }
}

// ==================== 카테고리 드롭다운 수정 ====================

function toggleCategoryDropdown(groupId, event) {
    // 다른 열린 드롭다운 닫기
    document.querySelectorAll('[id^="category-dropdown-"]').forEach(el => {
        if (el.id !== `category-dropdown-${groupId}`) el.classList.add('hidden');
    });
    const dropdown = document.getElementById(`category-dropdown-${groupId}`);
    dropdown.classList.toggle('hidden');

    if (!dropdown.classList.contains('hidden')) {
        const btn = event.currentTarget;
        const rect = btn.getBoundingClientRect();
        dropdown.style.top = (rect.bottom + 4) + 'px';
        dropdown.style.left = (rect.left + rect.width / 2 - dropdown.offsetWidth / 2) + 'px';
    }
}

async function submitCategory(groupId, category) {
    document.getElementById(`category-dropdown-${groupId}`).classList.add('hidden');

    try {
        const result = await callApi(
            `/api/projects/${projectId}/groups/${groupId}/category`,
            { method: 'PATCH', body: JSON.stringify({ category }) }
        );
        if (result.success) {
            loadGroups(currentPage);
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
