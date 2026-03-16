document.addEventListener('DOMContentLoaded', () => {
    const publicId = document.getElementById('publicId').value;
    const scopeSelect = document.getElementById('scopeSelect');
    const scopeDetailWrapper = document.getElementById('scopeDetailWrapper');
    const scopeDetailSelect = document.getElementById('scopeDetailSelect');
    const chatInput = document.getElementById('chatInput');
    const sendBtn = document.getElementById('sendBtn');
    const chatMessages = document.getElementById('chatMessages');
    const chatArea = document.getElementById('chatArea');

    let streaming = false;

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
                .map(item => `<option value="${escapeHtml(item)}">${escapeHtml(item)}</option>`)
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

    async function sendMessage() {
        const message = chatInput.value.trim();
        if (!message || streaming) return;

        // 빈 상태 숨기고 메시지 영역 표시
        document.getElementById('emptyChat').classList.add('hidden');
        chatMessages.classList.remove('hidden');

        // 사용자 메시지 렌더링
        appendUserMessage(message);

        // 입력 초기화 & 비활성화
        chatInput.value = '';
        chatInput.style.height = 'auto';
        setStreaming(true);

        // 요청 본문 구성
        const body = buildChatRequest(message);

        // AI 응답 버블 생성
        const { contentEl, refsEl } = appendAiMessage();

        try {
            await streamChat(body, contentEl, refsEl);
        } catch (e) {
            console.error('채팅 오류:', e);
            if (!contentEl.textContent) {
                contentEl.textContent = '응답 생성 중 오류가 발생했습니다.';
                contentEl.classList.add('text-red-500');
            }
        } finally {
            setStreaming(false);
            scrollToBottom();
        }
    }

    function buildChatRequest(message) {
        const scope = scopeSelect.value;
        const request = {
            modelAlias: document.getElementById('modelSelect').value,
            userMessage: message,
        };

        if (scope === 'group') {
            request.groupName = scopeDetailSelect.value;
        } else if (scope === 'category') {
            request.categoryName = scopeDetailSelect.value;
        }

        return request;
    }

    async function streamChat(body, contentEl, refsEl) {
        const csrfToken = getCookie('XSRF-TOKEN');
        const headers = { 'Content-Type': 'application/json' };
        if (csrfToken) {
            headers['X-XSRF-TOKEN'] = csrfToken;
        }

        const response = await fetch(`/api/projects/${publicId}/chatbot`, {
            method: 'POST',
            credentials: 'same-origin',
            headers,
            body: JSON.stringify(body),
        });

        if (!response.ok) {
            throw new Error(`서버 오류 (status: ${response.status})`);
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let answerText = '';

        while (true) {
            const { done, value } = await reader.read();
            buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done });

            const events = buffer.split(/\r?\n\r?\n/);
            buffer = events.pop() ?? '';

            for (const rawEvent of events) {
                const parsed = parseSseEvent(rawEvent);
                if (!parsed) {
                    continue;
                }
                handleSseEvent(parsed.event, parsed.data, contentEl, refsEl, { answerText });
                if (parsed.event === 'token') {
                    answerText += parsed.data;
                }
            }

            if (done) {
                break;
            }
        }

        if (buffer.trim()) {
            const parsed = parseSseEvent(buffer);
            if (parsed) {
                handleSseEvent(parsed.event, parsed.data, contentEl, refsEl, { answerText });
            }
        }
    }

    function parseSseEvent(rawEvent) {
        const lines = rawEvent.split(/\r?\n/);
        let event = null;
        const dataLines = [];

        for (const line of lines) {
            if (!line || line.startsWith(':')) {
                continue;
            }
            if (line.startsWith('event:')) {
                event = line.slice(6).trim();
                continue;
            }
            if (line.startsWith('data:')) {
                dataLines.push(line.slice(5).trimStart());
            }
        }

        if (!event) {
            return null;
        }

        return {
            event,
            data: dataLines.join('\n'),
        };
    }

    function handleSseEvent(event, data, contentEl, refsEl, state) {
        switch (event) {
            case 'token':
                contentEl.textContent += data;
                scrollToBottom();
                break;
            case 'references':
                try {
                    const refs = JSON.parse(data);
                    const fullAnswer = state.answerText;
                    renderReferences(refs, fullAnswer, refsEl);
                    // 인용 번호 하이라이트 적용
                    contentEl.innerHTML = highlightCitations(escapeHtml(contentEl.textContent));
                } catch (e) {
                    console.error('참조문서 파싱 실패:', e);
                }
                break;
            case 'error':
                contentEl.textContent += data;
                contentEl.classList.add('text-red-500');
                break;
            case 'done':
                break;
        }
    }

    function renderReferences(refs, answerText, refsEl) {
        if (!refs || refs.length === 0) return;

        // 답변에서 실제 인용된 번호만 필터링
        const citedIndices = extractCitedIndices(answerText);
        const citedRefs = refs.filter((_, i) => citedIndices.has(i + 1));

        if (citedRefs.length === 0) return;

        refsEl.classList.remove('hidden');
        const listEl = refsEl.querySelector('.ref-list');

        citedRefs.forEach((ref, i) => {
            const originalIndex = refs.indexOf(ref) + 1;
            const previewText = ref.chunkText.length > 150
                ? ref.chunkText.substring(0, 150) + '...'
                : ref.chunkText;

            const pageInfo = ref.pageNumber ? ` - p.${ref.pageNumber}` : '';

            const refItem = document.createElement('div');
            refItem.className = 'flex flex-col gap-1 px-3 py-2 bg-gray-50 rounded-lg text-xs';
            refItem.innerHTML = `
                <div class="flex items-center gap-2">
                    <span class="font-semibold text-indigo-600">[${originalIndex}]</span>
                    <span class="font-medium text-gray-700">${escapeHtml(ref.documentName)}</span>
                    <span class="text-gray-400">${escapeHtml(ref.version ?? '')}${pageInfo}</span>
                </div>
                <p class="text-gray-500 leading-relaxed">${escapeHtml(previewText)}</p>
            `;
            listEl.appendChild(refItem);
        });
    }

    function extractCitedIndices(text) {
        const indices = new Set();
        const regex = /\[(\d+)]/g;
        let match;
        while ((match = regex.exec(text)) !== null) {
            indices.add(parseInt(match[1]));
        }
        return indices;
    }

    function highlightCitations(html) {
        return html.replace(/\[(\d+)]/g, '<strong class="text-indigo-600 cursor-default">[$1]</strong>');
    }

    function appendUserMessage(message) {
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

    function appendAiMessage() {
        const messageEl = document.createElement('div');
        messageEl.className = 'flex justify-start';
        messageEl.innerHTML = `
            <div class="max-w-2xl w-full">
                <div class="bg-white rounded-2xl rounded-tl-sm px-5 py-3 shadow-sm border border-gray-100">
                    <p class="ai-content text-sm text-gray-800 whitespace-pre-wrap"></p>
                </div>
                <div class="ai-refs hidden mt-2">
                    <p class="text-xs font-medium text-gray-500 mb-1">참조 문서</p>
                    <div class="ref-list space-y-1"></div>
                </div>
            </div>
        `;
        chatMessages.appendChild(messageEl);
        scrollToBottom();

        return {
            contentEl: messageEl.querySelector('.ai-content'),
            refsEl: messageEl.querySelector('.ai-refs'),
        };
    }

    function setStreaming(value) {
        streaming = value;
        chatInput.disabled = value;
        sendBtn.disabled = value;
        sendBtn.classList.toggle('opacity-50', value);
    }

    function scrollToBottom() {
        chatArea.scrollTop = chatArea.scrollHeight;
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
});
