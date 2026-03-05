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

    // 확장자별 미리보기 분기
    const ext = currentDoc.extension.toLowerCase();
    const imageExtensions = ['png', 'jpg', 'jpeg'];

    if (ext === 'pdf') {
        // PDF → iframe (기본 표시)
    } else if (imageExtensions.includes(ext)) {
        // 이미지 → img 태그 (영역에 맞게 비율 조절)
        document.getElementById('pdfPreview').classList.add('hidden');
        document.getElementById('imagePreview').classList.remove('hidden');
    } else {
        // 그 외 → 폴백
        document.getElementById('pdfPreview').classList.add('hidden');
        document.getElementById('previewFallback').classList.remove('hidden');
    }
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
        const response = await fetch(`/api/projects/${projectId}/documents/${documentId}`, {
            method: 'PATCH',
            body: formData
        });
        const body = await response.json();
        if (body.success) {
            closeModal('editModal');
            window.location.reload();
        } else {
            showModalError('editError', body.error?.message || '수정에 실패했습니다.');
        }
    } catch (e) {
        showModalError('editError', '수정에 실패했습니다.');
        console.error(e);
    }
}

// ==================== 삭제 ====================

document.getElementById('btnDelete').addEventListener('click', () => {
    const fileName = currentDoc.documentName + '.' + currentDoc.extension;
    document.getElementById('deleteDocInfo').textContent = `${fileName} (${currentDoc.version})`;
    openModal('deleteModal');
});

async function confirmDelete() {
    try {
        const result = await callApi(
            `/api/projects/${projectId}/documents/${documentId}`,
            { method: 'DELETE' }
        );
        if (result.success) {
            closeModal('deleteModal');
            window.location.href = `/projects/${projectId}/groups`;
        }
    } catch (e) {
        alert('문서 삭제에 실패했습니다.');
        console.error(e);
    }
}

// ==================== 다운로드/채팅 ====================

document.getElementById('btnDownload').addEventListener('click', () => {
    window.location.href = `/api/projects/${projectId}/documents/${documentId}/download`;
});

document.getElementById('btnChat').addEventListener('click', () => {
    // TODO: 채팅 페이지로 이동
});
