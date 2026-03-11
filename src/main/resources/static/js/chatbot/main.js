document.addEventListener('DOMContentLoaded', () => {
    const publicId = document.getElementById('publicId').value;
    const scopeSelect = document.getElementById('scopeSelect');
    const scopeDetailWrapper = document.getElementById('scopeDetailWrapper');
    const scopeDetailSelect = document.getElementById('scopeDetailSelect');
    const chatInput = document.getElementById('chatInput');
    const sendBtn = document.getElementById('sendBtn');

    // 검색 범위 변경 이벤트
    scopeSelect.addEventListener('change', async () => {
        const type = scopeSelect.value;

        if (type === 'all') {
            scopeDetailWrapper.classList.add('hidden');
            scopeDetailSelect.innerHTML = '';
            return;
        }

        const url = type === 'group'
            ? `/api/projects/${publicId}/chatbot/scopes/groups`
            : `/api/projects/${publicId}/chatbot/scopes/categories`;

        try {
            const res = await callApi(url);
            const items = res.data ?? [];

            scopeDetailSelect.innerHTML = items
                .map(item => `<option value="${item}">${item}</option>`)
                .join('');

            scopeDetailWrapper.classList.remove('hidden');
        } catch (e) {
            console.error('검색 범위 목록 조회 실패:', e);
        }
    });

    // textarea 자동 높이 조절
    chatInput.addEventListener('input', () => {
        chatInput.style.height = 'auto';
        chatInput.style.height = Math.min(chatInput.scrollHeight, 120) + 'px';
    });

    // Enter로 전송 (Shift+Enter는 줄바꿈)
    chatInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });

    sendBtn.addEventListener('click', sendMessage);

    function sendMessage() {
        const message = chatInput.value.trim();
        if (!message) return;

        // 빈 상태 숨기고 메시지 영역 표시
        document.getElementById('emptyChat').classList.add('hidden');
        document.getElementById('chatMessages').classList.remove('hidden');

        // 사용자 메시지 렌더링
        appendUserMessage(message);

        // 입력 초기화
        chatInput.value = '';
        chatInput.style.height = 'auto';

        // TODO: AI 응답 요청 (추후 구현)
    }

    function appendUserMessage(message) {
        const chatMessages = document.getElementById('chatMessages');
        const messageEl = document.createElement('div');
        messageEl.className = 'flex justify-end';
        messageEl.innerHTML = `
            <div class="max-w-2xl">
                <div class="bg-white rounded-2xl rounded-tr-sm px-5 py-3 shadow-sm border border-gray-100">
                    <p class="text-sm text-gray-800 whitespace-pre-wrap">${escapeHtml(message)}</p>
                </div>
            </div>
        `;
        chatMessages.appendChild(messageEl);
        scrollToBottom();
    }

    function scrollToBottom() {
        const chatArea = document.getElementById('chatArea');
        chatArea.scrollTop = chatArea.scrollHeight;
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
});
