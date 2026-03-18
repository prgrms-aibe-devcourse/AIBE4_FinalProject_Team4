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

    // 시스템 메시지 토글
    const systemMsgToggle = document.getElementById('systemMsgToggle');
    const systemMsgArea = document.getElementById('systemMsgArea');
    const systemMsgArrow = document.getElementById('systemMsgArrow');

    systemMsgToggle.addEventListener('click', () => {
        systemMsgArea.classList.toggle('hidden');
        systemMsgArrow.classList.toggle('rotate-180');
    });

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

            if (items.length === 0) {
                const emptyLabel = type === 'group' ? '그룹이 없습니다' : '카테고리가 없습니다';
                scopeDetailSelect.innerHTML = `<option value="" disabled selected>${emptyLabel}</option>`;
                scopeDetailSelect.disabled = true;
            } else {
                scopeDetailSelect.innerHTML = items
                    .map(item => `<option value="${escapeHtml(item)}">${escapeHtml(item)}</option>`)
                    .join('');
                scopeDetailSelect.disabled = false;
            }

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
                contentEl.classList.add('text-docu-danger');
            }
        } finally {
            setStreaming(false);
            scrollToBottom();
        }
    }

    function buildChatRequest(message) {
        const scope = scopeSelect.value;
        const systemMessage = document.getElementById('systemMessage').value.trim();
        const request = {
            modelAlias: document.getElementById('modelSelect').value,
            userMessage: message,
        };

        if (systemMessage) {
            request.userSystemMessage = systemMessage;
        }

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
                dataLines.push(line.slice(5));
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
            case 'token': {
                const typingEl = contentEl.parentElement.querySelector('.ai-typing');
                if (typingEl) typingEl.remove();
                contentEl.textContent += data;
                scrollToBottom();
                break;
            }
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
            case 'error': {
                const typingOnError = contentEl.parentElement.querySelector('.ai-typing');
                if (typingOnError) typingOnError.remove();
                contentEl.textContent += data;
                contentEl.classList.add('text-docu-danger');
                break;
            }
            case 'done':
                break;
        }
    }

    function renderReferences(refs, answerText, refsEl) {
        if (!refs || refs.length === 0) return;

        // 답변에서 실제 인용된 번호만 필터링
        const citedIndices = extractCitedIndices(answerText);
        const citedRefs = refs
            .map((ref, i) => ({ ref, originalIndex: i + 1 }))
            .filter(({ originalIndex }) => citedIndices.has(originalIndex));

        if (citedRefs.length === 0) return;

        refsEl.classList.remove('hidden');
        const listEl = refsEl.querySelector('.ref-list');

        citedRefs.forEach(({ ref, originalIndex }) => {
            const pageInfo = ref.pageNumber ? ` - p.${ref.pageNumber}` : '';
            const detailUrl = `/projects/${publicId}/documents/${ref.documentId}${ref.pageNumber ? '?page=' + ref.pageNumber : ''}`;

            const refItem = document.createElement('div');
            refItem.className = 'flex items-center gap-2 px-3 py-2 bg-surface-sub rounded-docu-btn border border-divider text-xs cursor-pointer hover:border-docu-primary hover:bg-docu-primary-light/30 transition-colors';
            refItem.innerHTML = `
                <span class="font-semibold text-docu-primary">[${originalIndex}]</span>
                <span class="font-medium text-docu-ink">${escapeHtml(ref.documentName)}</span>
                <span class="text-docu-secondary">${escapeHtml(ref.version ?? '')}${pageInfo}</span>
                <a href="${detailUrl}" target="_blank"
                   class="ml-auto text-docu-secondary hover:text-docu-primary hover:underline flex-shrink-0"
                   onclick="event.stopPropagation()">상세페이지 →</a>
            `;

            refItem.addEventListener('click', () => {
                openPreviewPanel(ref.documentId, ref.documentName, ref.pageNumber || '');
            });

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
        return html.replace(/\[(\d+)]/g, '<strong class="text-docu-primary cursor-default">[$1]</strong>');
    }

    function appendUserMessage(message) {
        const messageEl = document.createElement('div');
        messageEl.className = 'flex justify-end';
        messageEl.innerHTML = `
            <div class="max-w-2xl">
                <div class="bg-docu-primary text-white rounded-2xl rounded-tr-sm px-5 py-3 shadow-md">
                    <p class="text-sm whitespace-pre-wrap">${escapeHtml(message)}</p>
                </div>
            </div>
        `;
        chatMessages.appendChild(messageEl);
        scrollToBottom();
    }

    function appendAiMessage() {
        const messageEl = document.createElement('div');
        messageEl.className = 'flex justify-start gap-3';
        messageEl.innerHTML = `
            <img src="/images/logo1.png" alt="AI" class="w-8 h-8 rounded-full flex-shrink-0 mt-1 object-cover">
            <div class="max-w-2xl w-full">
                <div class="bg-surface-sub rounded-2xl rounded-tl-sm px-5 py-3 shadow-sm border border-divider">
                    <p class="ai-content text-sm text-docu-ink whitespace-pre-wrap"></p>
                    <div class="ai-typing flex items-center gap-1.5 py-1">
                        <span class="w-2 h-2 bg-docu-primary/40 rounded-full animate-bounce" style="animation-delay: 0ms"></span>
                        <span class="w-2 h-2 bg-docu-primary/40 rounded-full animate-bounce" style="animation-delay: 150ms"></span>
                        <span class="w-2 h-2 bg-docu-primary/40 rounded-full animate-bounce" style="animation-delay: 300ms"></span>
                    </div>
                </div>
                <div class="ai-refs hidden mt-2">
                    <p class="text-xs font-medium text-docu-secondary mb-1">참조 문서</p>
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

    // ==================== 미리보기 패널 ====================

    const previewPanel = document.getElementById('previewPanel');
    const previewIframe = document.getElementById('previewIframe');
    const previewTitle = document.getElementById('previewTitle');
    const previewPage = document.getElementById('previewPage');
    const previewDetailLink = document.getElementById('previewDetailLink');
    const previewClose = document.getElementById('previewClose');

    function openPreviewPanel(documentId, documentName, page) {
        const baseUrl = `/api/projects/${publicId}/documents/${documentId}/preview`;
        const previewUrl = baseUrl + (page ? `#page=${page}` : '');
        const detailUrl = `/projects/${publicId}/documents/${documentId}`
            + (page ? `?page=${page}` : '');

        // 같은 문서에서 페이지만 다른 경우 iframe이 갱신되지 않으므로 초기화 후 재설정
        previewIframe.src = 'about:blank';
        setTimeout(() => {
            previewIframe.src = previewUrl;
        }, 50);

        previewTitle.textContent = documentName;
        previewPage.textContent = page ? `p.${page}` : '';
        previewDetailLink.href = detailUrl;

        previewPanel.classList.remove('hidden');
    }

    function closePreviewPanel() {
        previewPanel.classList.add('hidden');
        previewIframe.src = '';
    }

    previewClose.addEventListener('click', closePreviewPanel);
});
