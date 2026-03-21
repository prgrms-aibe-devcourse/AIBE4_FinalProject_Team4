/* ── 대기 중인 파일 (저장 전까지 업로드하지 않음) ── */
let pendingProfileFile = null;

/* ── 헤더 즉시 반영 ── */
function _syncHeaderNickname(nickname) {
    const el = document.getElementById('header-user-nickname');
    if (el) el.textContent = nickname;
}

function _syncHeaderProfileImage(url) {
    const img      = document.getElementById('header-user-profile-img');
    const fallback = document.getElementById('header-user-profile-fallback');
    if (img) {
        img.src = url;
    } else if (fallback) {
        const newImg = document.createElement('img');
        newImg.id        = 'header-user-profile-img';
        newImg.src       = url;
        newImg.className = 'w-9 h-9 object-cover border-2 border-divider shadow-docu-sm bg-surface-base rounded-full';
        newImg.alt       = '프로필 이미지';
        fallback.replaceWith(newImg);
    }
}

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

async function saveProfile() {
    clearErrors();

    const nicknameInput = document.getElementById('nickname');
    const positionInput = document.getElementById('position');

    const nickname = nicknameInput?.value?.trim();
    const position = positionInput?.value?.trim() || null;

    // 변경 여부를 먼저 계산 (검증보다 앞에 두어 변경되지 않은 필드는 검증 생략)
    const imageChanged = pendingProfileFile !== null;
    const nicknameChanged = nickname !== (nicknameInput?.defaultValue?.trim() ?? '');
    const positionChanged =
        positionInput !== null &&
        position !== (positionInput.defaultValue?.trim() || null);

    // 닉네임 검증 — 변경된 경우에만 수행 (기존 닉네임이 규칙에 맞지 않아도 직급만 수정 가능)
    if (nicknameChanged &&
        !validateNameField(nickname, { fieldId: 'nickname', label: '닉네임', max: 20 })) return;
    // 직급은 선택 필드: 빈칸이면 null(=기존 값 유지), 값이 있을 때만 검증
    if (position !== null &&
        !validateNameField(position, { fieldId: 'position', label: '직급', max: 20, required: false })) return;

    // 변경 사항 없으면 저장 스킵
    if (!imageChanged && !nicknameChanged && !positionChanged) {
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
            if (body.data?.profileImageUrl) {
                _syncHeaderProfileImage(body.data.profileImageUrl);
            }
        } catch (err) {
            showGlobalError(err.message, 'global-error');
            return;
        }
    }

    // ② 닉네임/직급 저장
    if (nicknameChanged || positionChanged) {
        const payload = {};
        // 닉네임은 변경된 경우에만 전송 — null이면 서버에서 기존 값 유지
        if (nicknameChanged) {
            payload.nickname = nickname;
        }
        // position 이 null 이면 기존 값 유지 (서버 측에서도 null = 업데이트 안 함)
        if (positionChanged && position !== null) {
            payload.position = position;
        }

        try {
            const body = await callApi('/api/members/me/profile', {
                method: 'PATCH',
                body: JSON.stringify(payload),
            });
            if (body.success) {
                if (nicknameChanged) {
                    nicknameInput.defaultValue = nickname;
                    _syncHeaderNickname(nickname);
                }
                if (positionInput && positionChanged) {
                    positionInput.defaultValue = position ?? positionInput.defaultValue;
                }
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

/* 취소: 이미지·닉네임·직급 모두 원래 값으로 복원 */
function resetForm() {
    // 닉네임 복원
    const nicknameInput = document.getElementById('nickname');
    if (nicknameInput) nicknameInput.value = nicknameInput.defaultValue;

    // 직급 복원
    const positionInput = document.getElementById('position');
    if (positionInput) positionInput.value = positionInput.defaultValue;

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
