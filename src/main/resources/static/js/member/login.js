let selectedRole = null;

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
 * 특정 단계를 화면에 표시하고 나머지는 숨긴다.
 *
 * @param {number} step 표시할 단계 번호 (1 또는 2)
 */
function showStep(step) {
    const step1 = document.getElementById('step-1');
    const step2 = document.getElementById('step-2');

    if (step === 1) {
        step1.classList.remove('hidden');
        step2.classList.add('hidden');
    } else {
        step1.classList.add('hidden');
        step2.classList.remove('hidden');
    }
}
