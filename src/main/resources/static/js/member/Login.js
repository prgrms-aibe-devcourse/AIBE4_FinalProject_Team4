let selectedRole = null;

document.addEventListener("DOMContentLoaded", () => {
    const params = new URLSearchParams(window.location.search);
    const error = params.get("error");
    const existingProvider = params.get("existing_provider");

    if (error === "email_conflict" && existingProvider) {
        initConflictStep(existingProvider);
        return;
    }

    if (error === "role_conflict") {
        const existingRole = params.get("existing_role");
        const provider = params.get("provider");
        if (existingRole && provider) {
            initRoleConflictStep(existingRole, provider);
        }
    }
});

function selectRole(card) {
    selectedRole = card.dataset.role;
    const labelMap = { CEO: "관리자 (CEO)", EMPLOYEE: "일반 직원" };
    const labelEl = document.getElementById("selected-role-label");
    if (labelEl) labelEl.textContent = labelMap[selectedRole] ?? selectedRole;
    const expiresDate = new Date(Date.now() + 10 * 60 * 1000).toUTCString();
    document.cookie = `oauth_pending_role=${selectedRole}; path=/; expires=${expiresDate}; SameSite=Lax`;
    showStep(2);
}

function goBack() {
    selectedRole = null;
    document.cookie = "oauth_pending_role=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT";
    showStep(1);
}

function goBackFromConflict() {
    selectedRole = null;
    document.cookie = "oauth_pending_role=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT";
    window.history.replaceState({}, "", "/auth/login");
    showStep(1);
}

function goBackFromRoleConflict() {
    selectedRole = null;
    document.cookie = "oauth_pending_role=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT";
    window.history.replaceState({}, "", "/auth/login");
    showStep(1);
}

function showStep(step) {
    const step1 = document.getElementById("step-1");
    const step2 = document.getElementById("step-2");
    const stepConflict = document.getElementById("step-conflict");
    const stepRoleConflict = document.getElementById("step-role-conflict");
    [step1, step2, stepConflict, stepRoleConflict].forEach(el => el?.classList.add("hidden"));
    if (step === 1) step1.classList.remove("hidden");
    else if (step === 2) step2.classList.remove("hidden");
    else if (step === "conflict") stepConflict?.classList.remove("hidden");
    else if (step === "role-conflict") stepRoleConflict?.classList.remove("hidden");
}

function initConflictStep(existingProvider) {
    const params = new URLSearchParams(window.location.search);
    const nickname = params.get("nickname") || "";
    const email = params.get("email") || "";
    const profileImage = params.get("profile_image") || "";
    const existingRole = params.get("existing_role") || "";
    const newProvider = existingProvider === "google" ? "github" : "google";

    const avatarEl = document.getElementById("conflict-avatar");
    if (avatarEl) {
        if (profileImage) {
            const img = document.createElement("img");
            img.src = profileImage;
            img.className = "w-full h-full object-cover";
            img.alt = "프로필";
            avatarEl.appendChild(img);
        } else {
            const initial = (nickname || existingProvider || "U").charAt(0).toUpperCase();
            avatarEl.className =
                "w-12 h-12 rounded-full shrink-0 flex items-center justify-center "
                + (existingProvider === "google" ? "bg-blue-500" : "bg-gray-800");
            const span = document.createElement("span");
            span.className = "text-white font-bold text-lg";
            span.textContent = initial;
            avatarEl.appendChild(span);
        }
    }

    const nicknameEl = document.getElementById("conflict-nickname");
    if (nicknameEl) {
        nicknameEl.textContent =
            nickname || (existingProvider === "google" ? "Google" : "GitHub") + " 계정";
    }

    const roleEl = document.getElementById("conflict-role-badge");
    if (roleEl && existingRole) {
        const roleNames = { ceo: "관리자", employee: "일반 직원", admin: "시스템 관리자" };
        const roleColors = {
            ceo: "bg-amber-100 text-amber-700",
            employee: "bg-blue-100 text-blue-700",
            admin: "bg-red-100 text-red-700",
        };
        roleEl.textContent = roleNames[existingRole] || existingRole;
        roleEl.className =
            "px-2 py-0.5 rounded text-[10px] font-bold "
            + (roleColors[existingRole] || "bg-gray-200 text-gray-600");
    }

    const emailEl = document.getElementById("conflict-email");
    if (emailEl) emailEl.textContent = email || "이메일 정보 없음";

    const googleSvg = `<svg class="w-5 h-5" viewBox="0 0 24 24"><path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/><path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/><path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/><path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/></svg>`;
    const githubSvg = `<svg class="w-5 h-5" viewBox="0 0 24 24" fill="currentColor"><path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/></svg>`;

    function providerBtn(provider, label, isLink, href) {
        const isGoogle = provider === "google";
        const cls = isGoogle
            ? "flex items-center justify-center gap-3 w-full px-5 py-3.5 bg-white hover:bg-gray-50 border-2 border-gray-200 hover:border-gray-300 text-gray-700 font-medium rounded-xl transition-all duration-200 text-sm"
            : "flex items-center justify-center gap-3 w-full px-5 py-3.5 bg-gray-900 hover:bg-gray-800 text-white font-medium rounded-xl transition-all duration-200 text-sm";
        const icon = isGoogle ? googleSvg : githubSvg;
        if (isLink) return `<a href="${href}" class="${cls}">${icon}${label}</a>`;
        return `<button type="button" id="conflict-new-account-btn" class="${cls}">${icon}${label}</button>`;
    }

    const buttonsEl = document.getElementById("conflict-buttons");
    if (buttonsEl) {
        const existingLabel = (existingProvider === "google" ? "Google" : "GitHub") + "로 로그인";
        const newLabel = (newProvider === "google" ? "Google" : "GitHub") + "로 새 계정 생성";
        buttonsEl.innerHTML =
            providerBtn(existingProvider, existingLabel, true, `/oauth2/authorization/${existingProvider}`)
            + providerBtn(newProvider, newLabel, false, "");

        document.getElementById("conflict-new-account-btn")?.addEventListener("click", () => {
            const expires = new Date(Date.now() + 10 * 60 * 1000).toUTCString();
            document.cookie =
                `oauth_allow_email_duplicate=true; path=/; expires=${expires}; SameSite=Lax`;
            window.location.href = `/oauth2/authorization/${newProvider}`;
        });
    }

    showStep("conflict");
}

function initRoleConflictStep(existingRole, provider) {
    const roleLabels = { ceo: "관리자 (CEO)", employee: "일반 직원", admin: "시스템 관리자" };
    const roleEl = document.getElementById("existing-role-label");
    if (roleEl) roleEl.textContent = roleLabels[existingRole] ?? existingRole;

    const googleBtn = document.getElementById("role-conflict-google-btn");
    const githubBtn = document.getElementById("role-conflict-github-btn");
    if (provider === "google" && googleBtn) googleBtn.classList.remove("hidden");
    else if (provider === "github" && githubBtn) githubBtn.classList.remove("hidden");

    showStep("role-conflict");
}
