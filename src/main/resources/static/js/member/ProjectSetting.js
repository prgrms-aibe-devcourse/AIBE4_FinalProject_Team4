
'use strict';

let _pendingProjectIconFile = null;

document.addEventListener('DOMContentLoaded', () => {
    showCreatedBanner();
    initProjectDropdown();
    initSmoothScroll();
    initScrollSpy();
});

function showCreatedBanner() {
    const msg = sessionStorage.getItem('projectCreatedMessage');
    if (!msg) return;
    sessionStorage.removeItem('projectCreatedMessage');
    showTopToast(msg);
}

function initProjectDropdown() {
    const btn  = document.getElementById('project-dropdown-btn');
    const menu = document.getElementById('project-dropdown-menu');
    if (!btn || !menu) return;

    btn.addEventListener('click', (e) => {
        e.stopPropagation();
        menu.classList.toggle('hidden');
    });

    document.addEventListener('click', (e) => {
        if (!menu.contains(e.target) && !btn.contains(e.target)) {
            menu.classList.add('hidden');
        }
    });
}


function initSmoothScroll() {
    document.querySelectorAll('.nav-item[data-target]').forEach(btn => {
        btn.addEventListener('click', () => {
            if (btn.dataset.target === 'section-access') {
                // 액세스는 제일 바닥으로 스크롤 (스크롤이 바닥에 닿아야 활성화되기 때문)
                window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });
            } else {
                const target = document.getElementById(btn.dataset.target);
                if (target) target.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
        });
    });
}

function initScrollSpy() {
    const navItems = document.querySelectorAll('.nav-item[data-target]');
    const sections = Array.from(navItems)
        .map(b => document.getElementById(b.dataset.target))
        .filter(Boolean);

    if (!sections.length) return;

    const setActive = (id) => {
        navItems.forEach(btn => {
            const isActive = btn.dataset.target === id;
            btn.classList.toggle('bg-gray-100',  isActive);
            btn.classList.toggle('text-gray-900', isActive);
            btn.classList.toggle('font-semibold', isActive);
            btn.classList.toggle('text-gray-600', !isActive);
        });
    };

    setActive(sections[0].id); // 초기 활성화

    // IntersectionObserver — API Key 섹션 스크롤 포인트를 넉넉하게 조정
    const observer = new IntersectionObserver(
        (entries) => {
            entries.forEach(entry => {
                if (entry.isIntersecting) setActive(entry.target.id);
            });
        },
        { rootMargin: '-5% 0px -70% 0px', threshold: 0 }
    );
    sections.forEach(sec => observer.observe(sec));

    // 스크롤이 바닥에 닿으면 마지막 nav 항목(액세스) 활성화
    window.addEventListener('scroll', () => {
        const atBottom = (window.innerHeight + window.scrollY) >= document.body.scrollHeight - 5;
        if (atBottom) {
            const lastNavItem = navItems[navItems.length - 1];
            if (lastNavItem) setActive(lastNavItem.dataset.target);
        }
    }, { passive: true });
}


async function saveDetails() {
    clearErrors();
    _hideDetailsMsg();

    const nameInput = document.getElementById('projectName');
    const name      = nameInput ? nameInput.value.trim() : '';
    if (!validateNameField(name, { fieldId: 'projectName', label: '프로젝트 이름', max: 100 })) return;

    const iconChanged = _pendingProjectIconFile !== null;
    const nameChanged = nameInput && name !== nameInput.defaultValue;

    if (!iconChanged && !nameChanged) {
        _showDetailsMsg('변경된 내용이 없습니다.', 'info');
        return;
    }

    const saveBtn = document.getElementById('details-save-btn');
    if (saveBtn) { saveBtn.disabled = true; saveBtn.textContent = '저장 중...'; }

    try {
        // ① 이미지 업로드 (변경된 경우)
        if (iconChanged) {
            const formData = new FormData();
            formData.append('file', _pendingProjectIconFile);
            const imgBody = await callApi(`/api/projects/${_PS.publicId}/profile-image`, {
                method: 'POST',
                body: formData,
            });
            if (imgBody.success) {
                _pendingProjectIconFile = null;
                if (imgBody.data?.url) {
                    _syncSidebarIcon(imgBody.data.url);
                    // 취소 복원용 원본 URL 갱신
                    const origInput = document.getElementById('project-icon-original-src');
                    if (origInput) origInput.value = imgBody.data.url;
                }
            } else {
                renderApiError(imgBody.error, 'details-global-error');
                return;
            }
        }

        // ② 이름 수정 (변경된 경우)
        if (nameChanged) {
            const body = await callApi(`/api/projects/${_PS.publicId}`, {
                method: 'PATCH',
                body: JSON.stringify({ name }),
            });
            if (body.success) {
                if (nameInput) nameInput.defaultValue = name; // 취소 기준값 갱신
                _PS.projectName = name;                       // deleteProject 확인용
                _syncSidebarName(name);                       // 사이드바 즉시 반영
            } else {
                renderApiError(body.error, 'details-global-error');
                return;
            }
        }

        _showDetailsMsg('프로젝트 정보가 저장되었습니다.', 'success');

    } catch (err) {
        showGlobalError(err.message, 'details-global-error');
    } finally {
        if (saveBtn) { saveBtn.disabled = false; saveBtn.textContent = '변경'; }
    }
}

function _showDetailsMsg(message, type = 'info') {
    const el = document.getElementById('details-success-message');
    if (!el) return;
    el.textContent = message;
    el.classList.remove('hidden', 'text-green-600', 'text-gray-500');
    el.classList.add(type === 'success' ? 'text-green-600' : 'text-gray-500');
    el.classList.remove('hidden');
    setTimeout(() => el.classList.add('hidden'), 3000);
}

function _hideDetailsMsg() {
    const el = document.getElementById('details-success-message');
    if (el) el.classList.add('hidden');
}

function _syncSidebarIcon(url) {
    // ① 사이드바 버튼 — 현재 프로젝트 아이콘
    const container = document.querySelector('#project-dropdown-btn > div');
    if (container) {
        const img = container.querySelector('img');
        if (img) {
            img.src = url;
        } else {
            container.innerHTML =
                `<img src="${url}" alt="프로젝트 로고" class="w-full h-full object-cover"/>`;
        }
    }

    // ② 드롭다운 메뉴 내 현재 프로젝트 항목 아이콘
    const currentLink = document.querySelector(
        `#project-dropdown-menu a[href*="${_PS.publicId}"]`);
    if (currentLink) {
        const iconDiv = currentLink.querySelector('div.overflow-hidden');
        if (iconDiv) {
            const img = iconDiv.querySelector('img');
            if (img) {
                img.src = url;
            } else {
                iconDiv.innerHTML =
                    `<img src="${url}" alt="" class="w-full h-full object-cover"/>`;
            }
        }
    }
}

function _syncSidebarName(name) {
    // ① 사이드바 버튼 — 현재 프로젝트명
    const span = document.querySelector('#project-dropdown-btn span.truncate');
    if (span) span.textContent = name;

    // ② 드롭다운 메뉴 내 현재 프로젝트 항목명
    const currentLink = document.querySelector(
        `#project-dropdown-menu a[href*="${_PS.publicId}"]`);
    if (currentLink) {
        const itemSpan = currentLink.querySelector('span.truncate');
        if (itemSpan) itemSpan.textContent = name;
    }
}


function cancelDetails() {
    // 이름 복원
    const nameInput = document.getElementById('projectName');
    if (nameInput) nameInput.value = nameInput.defaultValue;

    // 아이콘 복원
    _pendingProjectIconFile = null;
    const originalSrc = document.getElementById('project-icon-original-src')?.value || '';
    let   preview     = document.getElementById('project-icon-preview');
    const placeholder = document.getElementById('project-icon-placeholder');

    if (originalSrc) {
        if (preview) {
            preview.src = originalSrc;
        } else {
            // svg만 보이던 상태 → img 생성 (연필 배지가 아닌 버튼을 정확히 찾는다)
            const iconBtn = document.getElementById('project-icon-input')
                ?.closest('.relative')?.querySelector('button');
            if (iconBtn) {
                preview           = document.createElement('img');
                preview.id        = 'project-icon-preview';
                preview.alt       = '프로젝트 로고';
                preview.className = 'w-full h-full object-cover';
                preview.src       = originalSrc;
                iconBtn.prepend(preview);
            }
            if (placeholder) placeholder.classList.add('hidden');
        }
    } else {
        // 원본 이미지 없음 → placeholder 복원
        if (preview) preview.remove();
        if (placeholder) placeholder.classList.remove('hidden');
    }

    // 파일 input 초기화
    const fileInput = document.getElementById('project-icon-input');
    if (fileInput) fileInput.value = '';
    clearErrors();
    _hideDetailsMsg();
}


function previewProjectIcon(input) {
    if (!input.files || !input.files[0]) return;
    _pendingProjectIconFile = input.files[0];

    const reader = new FileReader();
    reader.onload = (e) => {
        const placeholder = document.getElementById('project-icon-placeholder');
        let   preview     = document.getElementById('project-icon-preview');

        if (!preview) {
            // input.previousElementSibling 은 연필 배지 div → 부모에서 버튼을 정확히 찾는다
            const iconBtn = input.closest('.relative')?.querySelector('button');
            if (iconBtn) {
                preview           = document.createElement('img');
                preview.id        = 'project-icon-preview';
                preview.alt       = '프로젝트 로고';
                preview.className = 'w-full h-full object-cover';
                iconBtn.prepend(preview);
            }
        }

        if (preview) preview.src = e.target.result;
        if (placeholder) placeholder.classList.add('hidden');
    };
    reader.readAsDataURL(_pendingProjectIconFile);
}


function copyApiKey() {
    const display = document.getElementById('api-key-display');
    if (!display) return;

    navigator.clipboard.writeText(display.textContent.trim())
        .then(() => showTopToast('클립보드에 복사되었습니다.'))
        .catch(() => showTopToast('복사에 실패했습니다. 직접 선택 후 복사해 주세요.'));
}

async function reissueApiKey() {
    if (!confirm('API 키를 재발급하면 기존 키는 즉시 폐기됩니다.\n계속하시겠습니까?')) return;

    try {
        const body = await callApi(`/api/projects/${_PS.publicId}/api-keys`, {
            method: 'POST',
        });
        if (body.success) {
            showApiKeyRevealModal(body.data.plainKey);
        } else {
            alert(body.error?.message ?? 'API 키 발급에 실패했습니다.');
        }
    } catch (err) {
        alert(err.message);
    }
}

function showApiKeyRevealModal(plainKey) {
    const modal   = document.getElementById('api-key-reveal-modal');
    const display = document.getElementById('plain-api-key-display');
    if (!modal || !display) return;
    display.textContent = plainKey;
    modal.classList.remove('hidden');
}

function closeApiKeyRevealModal() {
    const modal = document.getElementById('api-key-reveal-modal');
    if (modal) modal.classList.add('hidden');
    window.location.reload();
}

function copyPlainApiKey() {
    const display = document.getElementById('plain-api-key-display');
    if (!display) return;
    navigator.clipboard.writeText(display.textContent.trim())
        .then(() => showTopToast('클립보드에 복사되었습니다.'))
        .catch(() => showTopToast('복사에 실패했습니다. 직접 선택 후 복사해 주세요.'));
}

async function toggleApiKey(currentStatus) {
    const isActive  = currentStatus === 'ACTIVE';
    const action    = isActive ? '정지' : '활성화';
    const newStatus = isActive ? 'SUSPENDED' : 'ACTIVE';

    if (!confirm(`API 키를 ${action}하시겠습니까?`)) return;

    try {
        const body = await callApi(`/api/projects/${_PS.publicId}/api-keys/status`, {
            method: 'PATCH',
            body: JSON.stringify({ status: newStatus }),
        });
        if (body.success) {
            window.location.reload();
        } else {
            alert(body.error?.message ?? `API 키 ${action}에 실패했습니다.`);
        }
    } catch (err) {
        alert(err.message);
    }
}


async function changeRole(selectElement) {
    const memberId = selectElement.dataset.mid;
    const newRole = selectElement.value;
    const isMe = selectElement.dataset.isMe === 'true';

    if (isMe && newRole === 'MEMBER') {
        const msg = "프로젝트 권한을 '구성원'으로 변경하시겠습니까?\n\n" +
                    "구성원 권한으로 변경되면 프로젝트 세부사항 변경, API 키 관리,\n" +
                    "멤버 초대 및 관리 기능을 더 이상 사용할 수 없게 됩니다.";
        if (!confirm(msg)) {
            selectElement.value = 'MANAGER'; // 취소 시 원래 값으로 복원
            return;
        }
    }

    try {
        const body = await callApi(`/api/projects/${_PS.publicId}/members/${memberId}/role`, {
            method: 'PATCH',
            body: JSON.stringify({ role: newRole }),
        });
        if (body.success) {
            alert('권한이 변경되었습니다.');
            if (isMe) {
                location.reload(); // 자신의 권한이 바뀌었으므로 새로고침
            }
        } else {
            alert(body.error?.message ?? '역할 변경에 실패했습니다.');
            selectElement.value = newRole === 'MANAGER' ? 'MEMBER' : 'MANAGER'; // API 실패 시 원래 값으로 복원
        }
    } catch (err) {
        alert(err.message);
        selectElement.value = newRole === 'MANAGER' ? 'MEMBER' : 'MANAGER'; // API 실패 시 원래 값으로 복원
    }
}

async function removeMember(memberId, memberName) {
    if (!confirm(`'${memberName}' 님을 프로젝트에서 제거하시겠습니까?`)) return;

    try {
        const body = await callApi(`/api/projects/${_PS.publicId}/members/${memberId}`, {
            method: 'DELETE',
        });
        if (body.success) {
            window.location.reload();
        } else {
            alert(body.error?.message ?? '멤버 제거에 실패했습니다.');
        }
    } catch (err) {
        alert(err.message);
    }
}


function openInviteModal() {
    const modal      = document.getElementById('invite-modal');
    const emailInput = document.getElementById('invite-email');
    const roleSelect = document.getElementById('invite-role');
    const errorEl    = document.getElementById('invite-error');
    if (!modal) return;

    if (emailInput) emailInput.value = '';
    if (roleSelect) roleSelect.value = 'MEMBER';  // 기본값: 구성원
    if (errorEl) { errorEl.textContent = ''; errorEl.classList.add('hidden'); }

    modal.classList.remove('hidden');
    if (emailInput) emailInput.focus();
}

function closeInviteModal() {
    const modal = document.getElementById('invite-modal');
    if (modal) modal.classList.add('hidden');
}

async function sendInvite() {
    const emailInput = document.getElementById('invite-email');
    const roleSelect = document.getElementById('invite-role');
    const errorEl    = document.getElementById('invite-error');
    const sendBtn    = document.getElementById('invite-send-btn');

    if (errorEl) { errorEl.textContent = ''; errorEl.classList.add('hidden'); }

    const email    = emailInput ? emailInput.value.trim() : '';
    const role     = roleSelect ? roleSelect.value : 'MEMBER';
    const setError = (msg) => {
        if (errorEl) { errorEl.textContent = msg; errorEl.classList.remove('hidden'); }
    };

    if (!email) { setError('이메일을 입력해주세요.'); return; }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        setError('올바른 이메일 형식이 아닙니다.'); return;
    }

    if (sendBtn) { sendBtn.disabled = true; sendBtn.textContent = '전송 중...'; }

    try {
        const body = await callApi(`/api/projects/${_PS.publicId}/invitations`, {
            method: 'POST',
            body: JSON.stringify({ targetEmail: email, targetRole: role }),
        });
        if (body.success) {
            closeInviteModal();
            showTopToast('초대를 발송했습니다. 메일은 비동기로 처리됩니다.');
        } else {
            setError(body.error?.message ?? '초대 전송에 실패했습니다.');
        }
    } catch (err) {
        setError(err.message);
    } finally {
        if (sendBtn) { sendBtn.disabled = false; sendBtn.textContent = '초대 전송'; }
    }
}

// 모달 외부 클릭 / ESC 닫기
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') closeInviteModal();
});

document.getElementById('invite-modal')?.addEventListener('click', (e) => {
    if (e.target === document.getElementById('invite-modal')) closeInviteModal();
});

async function leaveProject() {
    if (!confirm(
        '정말 이 프로젝트에서 나가시겠습니까?\n' +
        '접근 권한이 즉시 소멸됩니다.')) return;

    try {
        const body = await callApi(`/api/projects/${_PS.publicId}/members/me`, {
            method: 'DELETE',
        });
        if (body.success) {
            sessionStorage.setItem('successMessage', '프로젝트에서 나갔습니다.');
            window.location.href = '/member/dashboard';
        } else {
            alert(body.error?.message ?? '프로젝트 나가기에 실패했습니다.');
        }
    } catch (err) {
        alert(err.message);
    }
}

async function deleteProject() {
    const confirmInput = prompt(
        '프로젝트를 삭제하면 관련된 모든 로그, 문서, 벡터 데이터가 영구 삭제됩니다.\n' +
        `계속하려면 프로젝트 이름 "${_PS.projectName}" 을 정확히 입력하세요.`
    );
    if (confirmInput === null) return;
    if (confirmInput !== _PS.projectName) {
        alert('프로젝트 이름이 일치하지 않습니다. 삭제가 취소되었습니다.');
        return;
    }

    try {
        const body = await callApi(`/api/projects/${_PS.publicId}`, {
            method: 'DELETE',
        });
        if (body.success) {
            sessionStorage.setItem('successMessage', '프로젝트가 삭제되었습니다.');
            window.location.href = '/member/dashboard';
        } else {
            alert(body.error?.message ?? '프로젝트 삭제에 실패했습니다.');
        }
    } catch (err) {
        alert(err.message);
    }
}
