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

    // 임베딩 진행중이면 SSE 구독
    const embeddingStatus = document.getElementById('docEmbeddingStatus').value;
    if (embeddingStatus === 'PENDING' || embeddingStatus === 'PROCESSING') {
        subscribeEmbeddingStatus();
    }
});

// ==================== UTC → 로컬 시간 변환 ====================

function convertLocalDateTimes() {
    document.querySelectorAll('.local-datetime').forEach(el => {
        const utc = el.dataset.utc;
        el.textContent = utc ? formatLocalDateTime(utc) : '-';
    });
    document.querySelectorAll('.local-date').forEach(el => {
        const utc = el.dataset.utc;
        el.textContent = utc ? formatLocalDate(utc) : '-';
    });
}

function formatLocalDateTime(utcStr) {
    const dt = new Date(utcStr);
    if (isNaN(dt.getTime())) return '-';
    const y = dt.getFullYear();
    const m = String(dt.getMonth() + 1).padStart(2, '0');
    const d = String(dt.getDate()).padStart(2, '0');
    const h = String(dt.getHours()).padStart(2, '0');
    const min = String(dt.getMinutes()).padStart(2, '0');
    return `${y}-${m}-${d} ${h}:${min}`;
}

function formatLocalDate(utcStr) {
    const dt = new Date(utcStr);
    if (isNaN(dt.getTime())) return '-';
    const y = dt.getFullYear();
    const m = String(dt.getMonth() + 1).padStart(2, '0');
    const d = String(dt.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
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
    window.location.href = `/projects/${projectId}/chatbot`;
});

// ==================== 임베딩 SSE ====================

const EMBEDDING_STATUS_MAP = {
    NONE:       { classes: 'font-medium text-docu-secondary', label: '-' },
    PENDING:    { classes: 'font-medium text-docu-secondary', label: '대기' },
    PROCESSING: { classes: 'font-medium text-docu-primary', label: '진행중' },
    SUCCESS:    { classes: 'font-medium text-docu-success', label: '성공' },
    FAILED:     { classes: 'font-medium text-docu-danger', label: '실패' },
};

function subscribeEmbeddingStatus() {
    const source = new EventSource(`/api/projects/${projectId}/documents/${documentId}/embedding-status`);

    source.addEventListener('embedding-status', (e) => {
        const status = e.data;
        updateEmbeddingBadge(status);

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

    const config = EMBEDDING_STATUS_MAP[status] || EMBEDDING_STATUS_MAP.NONE;
    badge.className = config.classes;
    badge.textContent = config.label;
}
