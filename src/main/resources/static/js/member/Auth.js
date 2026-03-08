document.addEventListener('DOMContentLoaded', () => {
    const logoutBtn     = document.getElementById('logout-btn');
    const logoutModal   = document.getElementById('logout-modal');
    const logoutConfirm = document.getElementById('logout-confirm');
    const logoutCancel  = document.getElementById('logout-cancel');

    // 로그아웃 버튼 클릭 → 모달 열기
    if (logoutBtn) {
        logoutBtn.addEventListener('click', () => {
            logoutModal?.classList.remove('hidden');
        });
    }

    // 취소 버튼 → 모달 닫기
    if (logoutCancel) {
        logoutCancel.addEventListener('click', () => {
            logoutModal?.classList.add('hidden');
        });
    }

    // 확인 버튼 → 로그아웃 실행
    if (logoutConfirm) {
        logoutConfirm.addEventListener('click', handleLogout);
    }

    // 배경 클릭 시 모달 닫기
    if (logoutModal) {
        logoutModal.addEventListener('click', (e) => {
            if (e.target === logoutModal) {
                logoutModal.classList.add('hidden');
            }
        });
    }

    // ESC 키 → 모달 닫기
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && logoutModal && !logoutModal.classList.contains('hidden')) {
            logoutModal.classList.add('hidden');
        }
    });
});

async function handleLogout() {
    try {
        const body = await callApi('/api/auth/logout', { method: 'POST' });
        if (body.success) {
            window.location.href = '/';
        } else {
            console.error('로그아웃 실패:', body.error?.message);
            window.location.href = '/';
        }
    } catch (err) {
        console.error('로그아웃 오류:', err.message);
        window.location.href = '/';
    }
}
