/* ── 대기 중인 파일 (저장 전까지 업로드하지 않음) ── */
let pendingProfileFile = null;

/* 파일 선택 시: 로컬 미리보기만, S3 업로드는 '변경 사항 저장' 시 처리 */
function previewImage(input) {
    if (!input.files || !input.files[0]) return;
    pendingProfileFile = input.files[0];

    const reader = new FileReader();
    reader.onload = (e) => {
        const preview = document.getElementById('profile-img-preview');
        const fallback = document.getElementById('profile-img-fallback');
        if (preview) {
            preview.src = e.target.result;
        } else if (fallback) {
            const img = document.createElement('img');
            img.id = 'profile-img-preview';
            img.src = e.target.result;
            img.className = 'w-full h-full object-cover';
            img.alt = '프로필 이미지';
            fallback.replaceWith(img);
        }
    };
    reader.readAsDataURL(pendingProfileFile);
}

/* 변경 사항 저장: ① 이미지 업로드(대기 파일 있을 때) → ② 닉네임 저장 */
async function saveProfile() {
    clearErrors();
    const nicknameInput = document.getElementById('nickname');
    const nickname = nicknameInput?.value?.trim();
    if (!nickname) {
        document.getElementById('nickname-error').textContent = '닉네임을 입력해주세요.';
        document.getElementById('nickname-error').classList.remove('hidden');
        return;
    }

    const imageChanged = pendingProfileFile !== null;
    const nicknameChanged = nickname !== nicknameInput.defaultValue;

    // 변경 사항 없으면 저장 스킵
    if (!imageChanged && !nicknameChanged) {
        const msg = document.getElementById('success-message');
        msg.textContent = '변경된 내용이 없습니다.';
        msg.classList.remove('hidden');
        setTimeout(() => msg.classList.add('hidden'), 3000);
        return;
    }

    // ① 이미지 업로드
    if (imageChanged) {
        const formData = new FormData();
        formData.append('file', pendingProfileFile);
        try {
            const body = await callApi('/api/members/me/profile-image', {
                method: 'POST',
                body: formData,
            });
            if (!body.success) {
                renderApiError(body.error, 'global-error');
                return;
            }
            pendingProfileFile = null;
            const preview = document.getElementById('profile-img-preview');
            if (preview && body.data?.profileImageUrl) {
                preview.src = body.data.profileImageUrl;
            }
            const origInput = document.getElementById('profile-img-original-src');
            if (origInput && body.data?.profileImageUrl) {
                origInput.value = body.data.profileImageUrl;
            }
        } catch (err) {
            showGlobalError(err.message, 'global-error');
            return;
        }
    }

    // ② 닉네임 저장
    if (nicknameChanged) {
        try {
            const body = await callApi('/api/members/me/profile', {
                method: 'PATCH',
                body: JSON.stringify({ nickname }),
            });
            if (body.success) {
                nicknameInput.defaultValue = nickname; // 취소 기준값 갱신
                const msg = document.getElementById('success-message');
                msg.textContent = '변경 사항이 저장되었습니다.';
                msg.classList.remove('hidden');
                setTimeout(() => msg.classList.add('hidden'), 3000);
            } else {
                renderApiError(body.error, 'global-error');
            }
        } catch (err) {
            showGlobalError(err.message, 'global-error');
        }
    } else {
        // 이미지만 변경된 경우 성공 메시지
        const msg = document.getElementById('success-message');
        msg.textContent = '변경 사항이 저장되었습니다.';
        msg.classList.remove('hidden');
        setTimeout(() => msg.classList.add('hidden'), 3000);
    }
}

/* 취소: 이미지·닉네임 모두 원래 값으로 복원 */
function resetForm() {
    // 닉네임 복원
    const nicknameInput = document.getElementById('nickname');
    if (nicknameInput) nicknameInput.value = nicknameInput.defaultValue;

    // 이미지 복원
    pendingProfileFile = null;
    const originalSrc = document.getElementById('profile-img-original-src')?.value || '';
    const preview = document.getElementById('profile-img-preview');

    if (originalSrc) {
        // 원본 이미지 있음 → src 복원 (또는 fallback 을 img 로 교체)
        if (preview) {
            preview.src = originalSrc;
        } else {
            const fallback = document.getElementById('profile-img-fallback');
            if (fallback) {
                const img = document.createElement('img');
                img.id = 'profile-img-preview';
                img.src = originalSrc;
                img.className = 'w-full h-full object-cover';
                img.alt = '프로필 이미지';
                fallback.replaceWith(img);
            }
        }
    } else {
        // 원본 이미지 없음 → 미리보기를 fallback 으로 되돌림
        if (preview) {
            const initial = (nicknameInput?.defaultValue?.charAt(0) || 'U').toUpperCase();
            const fallbackDiv = document.createElement('div');
            fallbackDiv.id = 'profile-img-fallback';
            fallbackDiv.className = 'w-full h-full bg-amber-400 flex items-center justify-center';
            fallbackDiv.innerHTML = `<span class="text-3xl font-bold text-white">${initial}</span>`;
            preview.replaceWith(fallbackDiv);
        }
    }

    // 파일 input 초기화
    const fileInput = document.getElementById('profile-image-input');
    if (fileInput) fileInput.value = '';
    clearErrors();
}
