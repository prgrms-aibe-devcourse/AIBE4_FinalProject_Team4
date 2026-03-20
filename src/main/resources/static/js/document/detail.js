const projectId = document.getElementById('projectId').value;
const documentId = document.getElementById('documentId').value;

// 서버 렌더링된 데이터를 모달용으로 캐시
const currentDoc = {
    documentName: document.getElementById('docName').value,
    extension: document.getElementById('docExtension').value,
    version: document.getElementById('docVersion').value,
    groupName: document.getElementById('docGroupName').value,
    category: document.getElementById('docCategory').value,
    isProcessed: document.getElementById('docIsProcessed').value === 'true'
};

document.addEventListener('DOMContentLoaded', () => {
    setupDropZone('editDropZone', 'editFile');
    convertLocalDateTimes();

    // 임베딩 상태에 따른 처리
    const embeddingStatus = document.getElementById('docEmbeddingStatus').value;
    if (embeddingStatus === 'PENDING' || embeddingStatus === 'PROCESSING') {
        subscribeEmbeddingStatus();
    }
    if (embeddingStatus === 'FAILED') {
        updateRetryButton('FAILED');
    }
});

// ==================== UTC → 로컬 시간 변환 ====================

function convertLocalDateTimes() {
    document.querySelectorAll('.local-datetime').forEach(el => {
        const utc = el.dataset.utc;
        el.textContent = utc ? formatDateTime(utc) : '-';
    });
    document.querySelectorAll('.local-date').forEach(el => {
        const utc = el.dataset.utc;
        el.textContent = utc ? formatDate(utc) : '-';
    });
}

// ==================== 파일 수정 ====================

document.getElementById('btnEdit').addEventListener('click', () => {
    hideModalError('editError');
    document.getElementById('editGroupName').value = currentDoc.groupName;
    document.getElementById('editCategory').value = currentDoc.category;

    const vParts = currentDoc.version.replace('v', '').split('.');
    document.getElementById('editMajor').value = vParts[0] || '1';
    document.getElementById('editMinor').value = vParts[1] || '0';
    document.getElementById('editPatch').value = vParts[2] || '0';
    document.getElementById('editVersionHint').textContent = `(현재버전: ${currentDoc.version})`;

    document.getElementById('editIsProcessed').checked = currentDoc.isProcessed;
    document.getElementById('editFileToggle').checked = false;
    document.getElementById('editFileArea').classList.add('hidden');
    document.getElementById('editFile').value = '';
    document.getElementById('editFileInfo').classList.add('hidden');
    document.getElementById('editDropZone').classList.remove('hidden');

    openModal('editModal');
});

async function submitEdit() {
    hideModalError('editError');

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
            window.location.reload();
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

document.getElementById('btnDelete').addEventListener('click', () => {
    const fileName = currentDoc.documentName + '.' + currentDoc.extension;
    document.getElementById('deleteDocInfo').textContent = `${fileName} (${currentDoc.version})`;
    openModal('deleteModal');
});

async function confirmDelete() {
    const btn = document.querySelector('#deleteModal button[onclick="confirmDelete()"]');
    startLoading(btn);
    try {
        const result = await callApi(
            `/api/projects/${projectId}/documents/${documentId}`,
            { method: 'DELETE' }
        );
        if (result.success) {
            await completeLoading(btn);
            closeModal('deleteModal');
            window.location.href = `/projects/${projectId}/groups`;
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

// ==================== 다운로드/채팅 ====================

document.getElementById('btnDownload').addEventListener('click', () => {
    window.location.href = `/api/projects/${projectId}/documents/${documentId}/download`;
});

document.getElementById('btnChat').addEventListener('click', () => {
    window.location.href = `/projects/${projectId}/chatbot?documentId=${documentId}`;
});

// ==================== 임베딩 SSE ====================

function subscribeEmbeddingStatus() {
    const source = new EventSource(`/api/projects/${projectId}/documents/${documentId}/embedding-status`);

    source.addEventListener('embedding-status', (e) => {
        const status = e.data;
        updateEmbeddingBadge(status);
        updateChatButton(status);
        updateEditDeleteButtons(status);

        if (status === 'SUCCESS' || status === 'FAILED') {
            source.close();
        }
    });

    source.onerror = () => {
        source.close();
    };
}

function updateEmbeddingBadge(status) {
    const badge = document.getElementById('embeddingStatusBadge');
    if (!badge) return;

    const config = EMBEDDING_STATUS[status] || EMBEDDING_STATUS.NONE;
    badge.className = 'font-medium ' + config.classes;
    badge.textContent = config.label;

    updateRetryButton(status);
}

function updateRetryButton(status) {
    const container = document.getElementById('embeddingRetryContainer');
    if (!container) return;

    if (status === 'FAILED') {
        container.classList.remove('hidden');
    } else {
        container.classList.add('hidden');
    }
}

async function retryEmbedding() {
    const btn = document.getElementById('btnRetryEmbedding');
    btn.disabled = true;
    btn.textContent = '재시도 중...';

    try {
        const result = await callApi(
            `/api/projects/${projectId}/documents/${documentId}/retry-embedding`,
            { method: 'POST' }
        );
        if (result.success) {
            subscribeEmbeddingStatus();
        } else {
            alert(result.error?.message || '임베딩 재시도에 실패했습니다.');
            btn.disabled = false;
            btn.textContent = '임베딩 재시도';
        }
    } catch (e) {
        alert('임베딩 재시도에 실패했습니다.');
        btn.disabled = false;
        btn.textContent = '임베딩 재시도';
        console.error(e);
    }
}

function updateEditDeleteButtons(status) {
    const btnEdit = document.getElementById('btnEdit');
    const btnDelete = document.getElementById('btnDelete');
    const inProgress = status === 'PENDING' || status === 'PROCESSING';

    [btnEdit, btnDelete].forEach(btn => {
        if (!btn) return;
        btn.disabled = inProgress;
        if (inProgress) {
            btn.classList.add('opacity-50', 'cursor-not-allowed');
        } else {
            btn.classList.remove('opacity-50', 'cursor-not-allowed');
        }
    });
}

function updateChatButton(status) {
    const btn = document.getElementById('btnChat');
    if (!btn) return;

    if (status === 'SUCCESS') {
        btn.disabled = false;
        btn.className = 'flex-1 btn-primary py-3 flex items-center justify-center gap-2';
        btn.title = '';
    } else {
        btn.disabled = true;
        btn.className = 'flex-1 py-3 flex items-center justify-center gap-2 bg-surface-muted text-docu-secondary border border-divider rounded-docu-btn cursor-not-allowed';
        btn.title = '임베딩이 완료된 문서만 채팅할 수 있습니다.';
    }
}
