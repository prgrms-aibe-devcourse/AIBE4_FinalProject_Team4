document.addEventListener('DOMContentLoaded', () => {
    const logoutBtn = document.getElementById('logout-btn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', handleLogout);
    }
});

async function handleLogout() {
    try {
        const body = await callApi('/api/auth/logout', { method: 'POST' });
        if (body.success) {
            window.location.href = '/';
        } else {
            console.error('로그아웃 실패:', body.error?.message);
            window.location.href = '/';  // 실패해도 홈으로 이동
        }
    } catch (err) {
        console.error('로그아웃 오류:', err.message);
        window.location.href = '/';
    }
}
