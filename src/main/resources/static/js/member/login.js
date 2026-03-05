let selectedRole = null;

document.addEventListener('DOMContentLoaded', () => {
    const params = new URLSearchParams(window.location.search);
    const error = params.get('error');
    const existingProvider = params.get('existing_provider');

    if (error === 'email_conflict' && existingProvider) {
        initConflictStep(existingProvider);
    }
});

/**
 * Step 1: 역할 카드 선택 시 호출.
 *
 * @param {HTMLElement} card 클릭된 역할 카드 버튼
 */
function selectRole(card) {
    selectedRole = card.dataset.role;

    // 역할 레이블 표시
    const labelMap = {
        CEO: '관리자 (CEO)',
        EMPLOYEE: '일반 직원',
    };
    const labelEl = document.getElementById('selected-role-label');
    if (labelEl) {
        labelEl.textContent = labelMap[selectedRole] ?? selectedRole;
    }

    // oauth_pending_role 쿠키 설정 (SameSite=Lax, 10분 유효)
    const expiresDate = new Date(Date.now() + 10 * 60 * 1000).toUTCString();
    document.cookie = `oauth_pending_role=${selectedRole}; path=/; expires=${expiresDate}; SameSite=Lax`;

    // Step 2로 전환
    showStep(2);
}

/**
 * Step 2 → Step 1 으로 돌아가기.
 */
function goBack() {
    selectedRole = null;
    // 쿠키 삭제
    document.cookie = 'oauth_pending_role=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT';
    showStep(1);
}

/**
 * Step Conflict → Step 1 으로 돌아가기.
 * URL에서 에러 파라미터를 제거하고 역할 선택 단계로 초기화한다.
 */
function goBackFromConflict() {
    selectedRole = null;
    document.cookie = 'oauth_pending_role=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT';
    window.history.replaceState({}, '', '/auth/login');
    showStep(1);
}

/**
 * 특정 단계를 화면에 표시하고 나머지는 숨긴다.
 *
 * @param {number|string} step 표시할 단계 (1, 2 또는 'conflict')
 */
function showStep(step) {
    const step1 = document.getElementById('step-1');
    const step2 = document.getElementById('step-2');
    const stepConflict = document.getElementById('step-conflict');

    [step1, step2, stepConflict].forEach(el => el?.classList.add('hidden'));

    if (step === 1) {
        step1.classList.remove('hidden');
    } else if (step === 2) {
        step2.classList.remove('hidden');
    } else if (step === 'conflict') {
        stepConflict?.classList.remove('hidden');
    }
}

/**
 * 이메일 충돌 단계 초기화: 기존 provider 정보로 UI를 구성하고 표시한다.
 *
 * @param {string} provider 기존 가입에 사용된 OAuth provider (예: 'google', 'github')
 */
function initConflictStep(provider) {
    const providerNames = { google: 'Google', github: 'GitHub' };
    const displayName = providerNames[provider] ?? provider;

    const labelEl = document.getElementById('existing-provider-label');
    if (labelEl) {
        labelEl.textContent = displayName;
    }

    // 해당 provider 로그인 버튼만 표시
    const googleBtn = document.getElementById('conflict-google-btn');
    const githubBtn = document.getElementById('conflict-github-btn');
    if (provider === 'google' && googleBtn) {
        googleBtn.classList.remove('hidden');
    } else if (provider === 'github' && githubBtn) {
        githubBtn.classList.remove('hidden');
    }

    showStep('conflict');
}
