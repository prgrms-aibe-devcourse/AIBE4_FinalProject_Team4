async function callApi(url, options = {}) {
    const isFormData = options.body instanceof FormData;
    const method = (options.method ?? 'GET').toUpperCase();

    const headers = isFormData
        ? { ...(options.headers ?? {}) }
        : { 'Content-Type': 'application/json', ...(options.headers ?? {}) };

    if (!['GET', 'HEAD'].includes(method)) {
        const csrfToken = getCookie('XSRF-TOKEN');
        if (csrfToken) {
            headers['X-XSRF-TOKEN'] = csrfToken;
        }
    }

    const response = await fetch(url, {
        credentials: 'same-origin',
        ...options,
        headers,
    });

    const contentType = response.headers.get('content-type');
    if (!contentType || !contentType.includes('application/json')) {
        throw new Error(`서버 오류가 발생했습니다. (status: ${response.status})`);
    }

    return await response.json();
}

function renderApiError(error, globalErrorId = 'globalError') {
    if (!error) return;
    if (error.details && error.details.length > 0) {
        error.details.forEach(fe => showFieldError(fe.field, fe.reason));
    } else {
        showGlobalError(error.message, globalErrorId);
    }
}

function showFieldError(field, message) {
    const errorEl = document.getElementById(`${field}-error`);
    if (errorEl) {
        errorEl.textContent = message;
        errorEl.classList.remove('hidden', 'd-none');
        return;
    }
    const input = document.getElementById(field);
    if (input) {
        input.classList.add('is-invalid');
        const feedback = input.nextElementSibling;
        if (feedback) feedback.textContent = message;
    }
}

function showGlobalError(message, elementId = 'globalError') {
    const el = document.getElementById(elementId);
    if (!el) return;
    el.textContent = message;
    el.classList.remove('hidden', 'd-none');
}

function clearErrors() {
    document.querySelectorAll('.is-invalid')
    .forEach(el => el.classList.remove('is-invalid'));
    document.querySelectorAll('.invalid-feedback')
    .forEach(el => el.textContent = '');
    // Tailwind hidden 패턴 에러 요소 초기화
    document.querySelectorAll('[id$="-error"]')
    .forEach(el => {
        el.textContent = '';
        el.classList.add('hidden');
    });
    // 글로벌 에러 영역 초기화
    ['globalError', 'global-error'].forEach(id => {
        const el = document.getElementById(id);
        if (el) {
            el.textContent = '';
            el.classList.add('hidden', 'd-none');
        }
    });
}

function getCookie(name) {
    const match = document.cookie.match(new RegExp('(?:^|;\\s*)' + name + '=([^;]*)'));
    return match ? decodeURIComponent(match[1]) : null;
}

function validateNameField(value, { fieldId, label, max, required = true }) {
    if (required && !value) {
        showFieldError(fieldId, `${label}을 입력해주세요.`);
        return false;
    }
    if (value) {
        if (value.length > max) {
            showFieldError(fieldId, `${label}은 ${max}자 이하로 입력해주세요.`);
            return false;
        }
        if (!/^[가-힣a-zA-Z0-9 ]*$/.test(value)) {
            showFieldError(fieldId, `${label}은 한글, 영문, 숫자, 공백만 사용할 수 있습니다.`);
            return false;
        }
    }
    return true;
}
