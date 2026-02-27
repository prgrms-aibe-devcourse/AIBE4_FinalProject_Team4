async function callApi(url, options = {}) {
    const defaultOptions = {
        headers: {
            'Content-Type': 'application/json',
        },
        ...options,
    };

    const response = await fetch(url, defaultOptions);

    const contentType = response.headers.get('content-type');
    if (!contentType || !contentType.includes('application/json')) {
        throw new Error(`서버 오류가 발생했습니다. (status: ${response.status})`);
    }

    const body = await response.json();
    return body; // ApiResponse<T> 구조 그대로 반환
}

function renderApiError(error, globalErrorId = 'globalError') {
    if (error.details && error.details.length > 0) {
        // 필드별 인라인 에러 표시
        error.details.forEach(fe => showFieldError(fe.field, fe.reason));
    } else {
        // 글로벌 에러 메시지 표시
        showGlobalError(error.message, globalErrorId);
    }
}

function showFieldError(field, message) {
    const input = document.getElementById(field);
    if (!input) return;
    input.classList.add('is-invalid');
    const feedback = input.nextElementSibling;
    if (feedback) feedback.textContent = message;
}

function showGlobalError(message, elementId = 'globalError') {
    const el = document.getElementById(elementId);
    if (!el) return;
    el.textContent = message;
    el.classList.remove('d-none');
}

function clearErrors() {
    document.querySelectorAll('.is-invalid')
    .forEach(el => el.classList.remove('is-invalid'));
    document.querySelectorAll('.invalid-feedback')
    .forEach(el => el.textContent = '');
    const globalError = document.getElementById('globalError');
    if (globalError) globalError.classList.add('d-none');
}
