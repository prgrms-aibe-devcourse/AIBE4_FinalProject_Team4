let pendingCompanyIconFile = null;

function previewCompanyIcon(input) {
    if (!input.files || !input.files[0]) return;
    pendingCompanyIconFile = input.files[0];

    const reader = new FileReader();
    reader.onload = (e) => {
        const preview = document.getElementById('company-icon-preview');
        const fallback = document.getElementById('company-icon-fallback');
        if (preview) {
            preview.src = e.target.result;
        } else if (fallback) {
            const img = document.createElement('img');
            img.id = 'company-icon-preview';
            img.src = e.target.result;
            img.className = 'w-full h-full object-cover';
            img.alt = '회사 아이콘';
            fallback.replaceWith(img);
        }
    };
    reader.readAsDataURL(pendingCompanyIconFile);
}

async function uploadCompanyIcon() {
    if (!pendingCompanyIconFile) return null;
    const formData = new FormData();
    formData.append('file', pendingCompanyIconFile);
    const body = await callApi('/api/companies/me/profile-image', {
        method: 'POST',
        body: formData,
    });
    if (body.success) {
        pendingCompanyIconFile = null;
        const preview = document.getElementById('company-icon-preview');
        if (preview && body.data?.profileImageUrl) {
            preview.src = body.data.profileImageUrl;
        }
        const origInput = document.getElementById('company-icon-original-src');
        if (origInput && body.data?.profileImageUrl) {
            origInput.value = body.data.profileImageUrl;
        }
    }
    return body;
}

async function updateCompany() {
    clearErrors();
    const nameInput = document.getElementById('companyName');
    const name = nameInput?.value?.trim();
    if (!validateNameField(name, { fieldId: 'companyName', label: '회사명', max: 100 })) return;

    const iconChanged = pendingCompanyIconFile !== null;
    const nameChanged = name !== nameInput.defaultValue;

    // 변경 사항 없으면 저장 스킵
    if (!iconChanged && !nameChanged) {
        const msg = document.getElementById('success-message');
        msg.textContent = '변경된 내용이 없습니다.';
        msg.classList.remove('hidden');
        setTimeout(() => msg.classList.add('hidden'), 3000);
        return;
    }

    // ① 아이콘 업로드 (변경된 경우에만)
    if (iconChanged) {
        try {
            const iconResult = await uploadCompanyIcon();
            if (iconResult && !iconResult.success) {
                renderApiError(iconResult.error, 'global-error');
                return;
            }
        } catch (err) {
            showGlobalError(err.message, 'global-error');
            return;
        }
    }

    // ② 회사명 저장 (변경된 경우에만)
    if (nameChanged) {
        try {
            const body = await callApi('/api/companies/me', {
                method: 'PATCH',
                body: JSON.stringify({ name }),
            });
            if (body.success) {
                nameInput.defaultValue = name; // 취소 기준값 갱신
                const msg = document.getElementById('success-message');
                msg.textContent = '회사 정보가 저장되었습니다.';
                msg.classList.remove('hidden');
                setTimeout(() => msg.classList.add('hidden'), 3000);
            } else {
                renderApiError(body.error, 'global-error');
            }
        } catch (err) {
            showGlobalError(err.message, 'global-error');
        }
    } else {
        // 아이콘만 변경된 경우 성공 메시지
        const msg = document.getElementById('success-message');
        msg.textContent = '회사 정보가 저장되었습니다.';
        msg.classList.remove('hidden');
        setTimeout(() => msg.classList.add('hidden'), 3000);
    }
}

async function registerCompany() {
    clearErrors();
    const nameInput = document.getElementById('companyName');
    const name = nameInput?.value?.trim();
    if (!validateNameField(name, { fieldId: 'companyName', label: '회사명', max: 100 })) return;

    try {
        const body = await callApi('/api/companies', {
            method: 'POST',
            body: JSON.stringify({ name }),
        });
        if (!body.success) {
            renderApiError(body.error, 'global-error');
            return;
        }
    } catch (err) {
        showGlobalError(err.message, 'global-error');
        return;
    }

    if (pendingCompanyIconFile) {
        try {
            await uploadCompanyIcon();
        } catch (e) {
            // 아이콘 업로드 실패는 무시하고 페이지 리로드
        }
    }

    location.reload();
}

/* 취소: 아이콘·이름 모두 원래 값으로 복원 */
function resetCompanyForm() {
    // 이름 복원
    const nameInput = document.getElementById('companyName');
    if (nameInput) nameInput.value = nameInput.defaultValue;

    // 아이콘 복원
    pendingCompanyIconFile = null;
    const originalSrc = document.getElementById('company-icon-original-src')?.value || '';
    const preview = document.getElementById('company-icon-preview');

    if (originalSrc) {
        // 원본 이미지 있음 → src 복원
        if (preview) {
            preview.src = originalSrc;
        } else {
            const fallback = document.getElementById('company-icon-fallback');
            if (fallback) {
                const img = document.createElement('img');
                img.id = 'company-icon-preview';
                img.src = originalSrc;
                img.className = 'w-full h-full object-cover';
                img.alt = '회사 아이콘';
                fallback.replaceWith(img);
            }
        }
    } else {
        // 원본 이미지 없음 → 미리보기를 fallback div 로 되돌림
        if (preview) {
            const companyInitial = (nameInput?.defaultValue?.charAt(0) || '+').toUpperCase();
            const fallbackDiv = document.createElement('div');
            fallbackDiv.id = 'company-icon-fallback';
            fallbackDiv.className = 'w-full h-full flex items-center justify-center bg-docu-primary text-white text-2xl font-bold';
            fallbackDiv.innerHTML = `<span>${companyInitial}</span>`;
            preview.replaceWith(fallbackDiv);
        }
    }

    // 파일 input 초기화
    const fileInput = document.getElementById('company-icon-input');
    if (fileInput) fileInput.value = '';
    clearErrors();
}
