/* ──────────────────────────────────────────────
   ADMIN 회사 관리 페이지 (company-admin.html)
   ────────────────────────────────────────────── */

// ── 탭 전환 ────────────────────────────────────
function switchTab(tab) {
    const tabs = ['pending', 'approved', 'suspended'];

    tabs.forEach(t => {
        const btn    = document.getElementById(`tab-btn-${t}`);
        const panel  = document.getElementById(`tab-${t}`);
        const active = t === tab;

        if (btn) {
            btn.classList.toggle('border-b-2',       active);
            btn.classList.toggle('border-blue-500',  active);
            btn.classList.toggle('text-blue-600',    active);
            btn.classList.toggle('font-semibold',    active);
            btn.classList.toggle('text-gray-500',   !active);
            btn.classList.toggle('hover:text-gray-700', !active);
        }
        if (panel) {
            panel.classList.toggle('hidden', !active);
        }
    });

    // URL hash 갱신 (뒤로 가기 대응)
    history.replaceState(null, '', `#tab-${tab}`);
}

// ── 승인 요청 ───────────────────────────────────
async function approveCompany(btn) {
    const companyId   = btn.dataset.id;
    const companyName = btn.dataset.name;

    if (!confirm(`"${companyName}" 회사를 승인하시겠습니까?`)) return;

    try {
        const body = await callApi(`/api/companies/${companyId}/approve`, {
            method: 'PATCH',
        });
        if (body.success) {
            sessionStorage.setItem('adminCompanyMsg', `"${companyName}" 회사가 승인되었습니다.`);
            location.reload();
        } else {
            alert(body.error?.message || '승인 처리 중 오류가 발생했습니다.');
        }
    } catch (err) {
        alert(err.message || '서버 오류가 발생했습니다.');
    }
}

// ── 거부 요청 ───────────────────────────────────
async function rejectCompany(btn) {
    const companyId   = btn.dataset.id;
    const companyName = btn.dataset.name;

    if (!confirm(`"${companyName}" 회사를 거부하시겠습니까?`)) return;

    try {
        const body = await callApi(`/api/companies/${companyId}/reject`, {
            method: 'PATCH',
        });
        if (body.success) {
            sessionStorage.setItem('adminCompanyMsg', `"${companyName}" 회사가 거부되었습니다.`);
            location.reload();
        } else {
            alert(body.error?.message || '거부 처리 중 오류가 발생했습니다.');
        }
    } catch (err) {
        alert(err.message || '서버 오류가 발생했습니다.');
    }
}

// ── 초기화 ──────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    // URL hash 기반 초기 탭 결정
    const hash    = location.hash;   // e.g. '#tab-approved'
    const tabName = hash.startsWith('#tab-') ? hash.slice(5) : 'pending';
    const valid   = ['pending', 'approved', 'suspended'].includes(tabName) ? tabName : 'pending';
    switchTab(valid);

    // sessionStorage 성공 메시지 표시
    const msg = sessionStorage.getItem('adminCompanyMsg');
    if (msg) {
        const el = document.getElementById('admin-success-msg');
        if (el) {
            el.textContent = msg;
            el.classList.remove('hidden');
            setTimeout(() => el.classList.add('hidden'), 4000);
        }
        sessionStorage.removeItem('adminCompanyMsg');
    }
});
