/* ──────────────────────────────────────────────
   대시보드 - 프로젝트 생성 모달
   ────────────────────────────────────────────── */

// ── 페이지 로드 시 sessionStorage 토스트 표시 ──────────
document.addEventListener('DOMContentLoaded', function () {
    const msg = sessionStorage.getItem('successMessage');
    if (msg) {
        sessionStorage.removeItem('successMessage');
        showTopToast(msg, 'success');
    }
});

// bfcache로 복원된 경우 최신 목록 반영을 위해 새로고침
window.addEventListener('pageshow', function (e) {
    if (e.persisted) {
        window.location.reload();
    }
});

// ── 프로젝트 목록 더 보기 / 접기 ────────────────────

function toggleProjectList() {
    const container = document.getElementById('project-list-container');
    const label     = document.getElementById('project-list-toggle-label');
    const icon      = document.getElementById('project-list-toggle-icon');
    const expanded  = container.dataset.expanded === 'true';
    const articles  = container.querySelectorAll('article');

    if (expanded) {
        // 접기: 4번째 이후 숨기고 페이지 맨 위로 스크롤
        articles.forEach(function (art, i) {
            if (i >= 3) art.classList.add('hidden');
        });
        container.dataset.expanded = 'false';
        label.textContent = '더 보기';
        icon.style.transform = '';
        window.scrollTo({ top: 0, behavior: 'smooth' });
    } else {
        // 더 보기: 모든 카드 표시
        articles.forEach(function (art) {
            art.classList.remove('hidden');
        });
        container.dataset.expanded = 'true';
        label.textContent = '접기';
        icon.style.transform = 'rotate(180deg)';
    }
}

let _pendingProjectIconFile = null;

// ── 모달 열기 / 닫기 ──────────────────────────────

function openProjectModal() {
    _pendingProjectIconFile = null;
    document.getElementById('projectName').value = '';
    document.getElementById('project-icon-preview').classList.add('hidden');
    document.getElementById('project-icon-placeholder').classList.remove('hidden');
    document.getElementById('project-icon-input').value = '';
    clearErrors();
    document.getElementById('project-modal').classList.remove('hidden');
    document.getElementById('projectName').focus();
}

function closeProjectModal() {
    document.getElementById('project-modal').classList.add('hidden');
}

// ESC 키로 모달 닫기
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') closeProjectModal();
});

// 모달 바깥 클릭 시 닫기
document.getElementById('project-modal').addEventListener('click', (e) => {
    if (e.target === document.getElementById('project-modal')) closeProjectModal();
});

// ── 이미지 미리보기 ───────────────────────────────

function previewProjectIcon(input) {
    if (!input.files || !input.files[0]) return;
    _pendingProjectIconFile = input.files[0];

    const reader = new FileReader();
    reader.onload = (e) => {
        const preview     = document.getElementById('project-icon-preview');
        const placeholder = document.getElementById('project-icon-placeholder');
        preview.src = e.target.result;
        preview.classList.remove('hidden');
        placeholder.classList.add('hidden');
    };
    reader.readAsDataURL(_pendingProjectIconFile);
}

// ── 프로젝트 생성 ─────────────────────────────────

async function submitCreateProject() {
    clearErrors();

    const name = document.getElementById('projectName').value.trim();
    if (!validateNameField(name, { fieldId: 'projectName', label: '프로젝트 이름', max: 100 })) return;

    const createBtn = document.getElementById('project-create-btn');
    createBtn.disabled = true;
    createBtn.textContent = '생성 중...';

    try {
        // ① 프로젝트 생성
        const body = await callApi('/api/projects', {
            method: 'POST',
            body: JSON.stringify({ name }),
        });

        if (!body.success) {
            renderApiError(body.error, 'project-global-error');
            return;
        }

        const publicId = body.data?.publicId;

        // ② 이미지가 있으면 업로드
        if (_pendingProjectIconFile && publicId) {
            try {
                const formData = new FormData();
                formData.append('file', _pendingProjectIconFile);
                await callApi(`/api/projects/${publicId}/profile-image`, {
                    method: 'POST',
                    body: formData,
                });
            } catch (imgErr) {
                // 이미지 업로드 실패는 무시하고 설정 페이지로 이동
                console.warn('프로젝트 이미지 업로드 실패:', imgErr.message);
            }
        }

        // ③ 성공 메시지 저장 후 프로젝트 설정 페이지로 이동
        sessionStorage.setItem('projectCreatedMessage', '프로젝트가 생성되었습니다! 로고와 이름을 수정할 수 있습니다.');
        window.location.href = `/projects/${publicId}/settings`;

    } catch (err) {
        showGlobalError(err.message, 'project-global-error');
    } finally {
        createBtn.disabled = false;
        createBtn.textContent = '새 프로젝트 생성';
    }
}
