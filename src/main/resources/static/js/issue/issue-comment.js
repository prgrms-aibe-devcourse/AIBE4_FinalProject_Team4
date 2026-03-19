/**
 * 이슈 댓글 관리
 * - 댓글 CRUD
 * - 멘션 자동완성
 * - 페이지네이션
 */

let currentPage = 0;
const pageSize = 10;
let totalPages = 0;

// 멘션 관련 상태
let mentionedMembers = []; // {memberId, nickname}
let currentMentionQuery = '';
let mentionStartPos = -1;
let selectedMemberIndex = -1; // 드롭다운에서 선택된 멤버 인덱스 (화살표 키 네비게이션용)
let currentMemberList = []; // 현재 드롭다운에 표시된 멤버 목록

// ==================== 페이지 로드 ====================
document.addEventListener('DOMContentLoaded', () => {
    loadComments(0);
    setupCommentInput();
});

// ==================== 댓글 목록 조회 ====================
async function loadComments(page = 0) {
    const projectId = document.getElementById('publicId').value;
    const issueId = document.getElementById('issueId').value;

    try {
        const response = await callApi(
            `/api/projects/${projectId}/issues/${issueId}/comments?page=${page}&size=${pageSize}`,
            { method: 'GET' }
        );

        if (response.success) {
            currentPage = response.meta.page;
            totalPages = response.meta.totalPages;

            renderCommentList(response.data);
            updatePagination(response.meta);
            updateCommentCount(response.meta.totalElements);
        }
    } catch (error) {
        console.error('댓글 목록 조회 실패:', error);
    }
}

// ==================== 댓글 목록 렌더링 ====================
function renderCommentList(comments) {
    const container = document.getElementById('commentList');

    if (comments.length === 0) {
        container.innerHTML = '<p class="text-xs text-gray-400 text-center py-4">댓글이 없습니다.</p>';
        return;
    }

    container.innerHTML = comments.map(comment => renderCommentItem(comment)).join('');
}

function renderCommentItem(comment) {
    const isAuthor = comment.author?.memberId === getCurrentMemberId();
    const authorName = comment.author?.nickname || '(알 수 없음)';
    const authorInitial = authorName.charAt(0).toUpperCase();
    const profileImageUrl = comment.author?.profileImageUrl;

    // 프로필 이미지 렌더링 (헤더 방식과 동일)
    const profileHtml = profileImageUrl
        ? `<img src="${escapeHtml(profileImageUrl)}" alt="${escapeHtml(authorName)}"
                class="w-6 h-6 object-cover border border-divider bg-surface-base rounded-full">`
        : `<div class="w-6 h-6 bg-docu-primary border border-divider flex items-center justify-center rounded-full">
               <span class="text-white text-xs font-bold">${authorInitial}</span>
           </div>`;

    // 멘션 하이라이트
    let content = escapeHtml(comment.content);
    if (comment.mentionedMembers && comment.mentionedMembers.length > 0) {
        comment.mentionedMembers.forEach(member => {
            const regex = new RegExp(`@${escapeRegex(member.nickname)}`, 'g');
            content = content.replace(regex, `<span class="text-indigo-600 font-medium">@${escapeHtml(member.nickname)}</span>`);
        });
    }

    const authorNameEscaped = escapeHtml(authorName);

    return `
        <div class="border-b border-gray-100 pb-3 last:border-0" data-comment-id="${comment.id}">
            <div class="flex items-start gap-2">
                ${profileHtml}
                <div class="flex-1 min-w-0">
                    <div class="flex items-center gap-2 mb-1">
                        <span class="text-xs font-semibold text-gray-900">${authorNameEscaped}</span>
                        <span class="text-xs text-gray-400">${formatDateTime(comment.createdAt)}</span>
                        ${comment.createdAt !== comment.updatedAt ? '<span class="text-xs text-gray-400">(수정됨)</span>' : ''}
                    </div>
                    <div class="comment-content text-xs text-gray-700 break-words whitespace-pre-wrap">${content}</div>
                    ${isAuthor ? `
                        <div class="flex items-center gap-2 mt-2">
                            <button onclick="editComment(${comment.id})" class="text-xs text-indigo-600 hover:text-indigo-800">수정</button>
                            <button onclick="deleteComment(${comment.id})" class="text-xs text-red-600 hover:text-red-800">삭제</button>
                        </div>
                    ` : ''}
                </div>
            </div>
        </div>
    `;
}

// ==================== 댓글 작성 ====================
async function submitComment() {
    const projectId = document.getElementById('publicId').value;
    const issueId = document.getElementById('issueId').value;
    const input = document.getElementById('commentInput');
    const content = input.value.trim();

    if (!content) {
        showTopToast('댓글 내용을 입력하세요', 'warning');
        return;
    }

    if (content.length > 2000) {
        showTopToast('댓글은 2000자를 초과할 수 없습니다', 'danger');
        return;
    }

    const requestBody = {
        content: content,
        mentionedMemberIds: mentionedMembers.map(m => m.memberId)
    };

    try {
        const response = await callApi(
            `/api/projects/${projectId}/issues/${issueId}/comments`,
            {
                method: 'POST',
                body: JSON.stringify(requestBody)
            }
        );

        if (response.success) {
            showTopToast('댓글이 작성되었습니다', 'success');
            input.value = '';
            mentionedMembers = [];
            updateMentionedMembersPreview();
            loadComments(0); // 첫 페이지로 이동

            // 타임라인 즉시 업데이트 (issue-analysis.js의 함수 호출)
            if (typeof renderTimeline === 'function' && currentIssue) {
                renderTimeline(currentIssue);
            }
        }
    } catch (error) {
        console.error('댓글 작성 실패:', error);
        showTopToast(error.message || '댓글 작성에 실패했습니다', 'danger');
    }
}

// ==================== 댓글 수정 ====================
async function editComment(commentId) {
    const commentElement = document.querySelector(`[data-comment-id="${commentId}"]`);
    const contentElement = commentElement.querySelector('.comment-content');
    const originalContent = contentElement.textContent.trim();

    // 편집 모드로 전환
    const textarea = document.createElement('textarea');
    textarea.className = 'w-full px-2 py-1 border border-gray-300 rounded text-xs resize-none focus:outline-none focus:ring-2 focus:ring-indigo-500';
    textarea.rows = 3;
    textarea.value = originalContent;

    const buttonContainer = document.createElement('div');
    buttonContainer.className = 'flex gap-2 mt-2';
    buttonContainer.innerHTML = `
        <button onclick="saveCommentEdit(${commentId}, this)" class="px-3 py-1 bg-indigo-600 hover:bg-indigo-700 text-white text-xs rounded">저장</button>
        <button onclick="cancelCommentEdit(${commentId})" class="px-3 py-1 bg-gray-200 hover:bg-gray-300 text-gray-700 text-xs rounded">취소</button>
    `;

    contentElement.replaceWith(textarea);
    commentElement.querySelector('.flex.items-center.gap-2.mt-2').replaceWith(buttonContainer);
}

async function saveCommentEdit(commentId, button) {
    const projectId = document.getElementById('publicId').value;
    const issueId = document.getElementById('issueId').value;
    const commentElement = document.querySelector(`[data-comment-id="${commentId}"]`);
    const textarea = commentElement.querySelector('textarea');
    const content = textarea.value.trim();

    if (!content) {
        showTopToast('댓글 내용을 입력하세요', 'warning');
        return;
    }

    // 멘션 추출 (간단한 정규식 사용)
    const mentions = extractMentions(content);
    const memberIds = await resolveMentionsToIds(mentions);

    const requestBody = {
        content: content,
        mentionedMemberIds: memberIds
    };

    try {
        const response = await callApi(
            `/api/projects/${projectId}/issues/${issueId}/comments/${commentId}`,
            {
                method: 'PUT',
                body: JSON.stringify(requestBody)
            }
        );

        if (response.success) {
            showTopToast('댓글이 수정되었습니다', 'success');
            loadComments(currentPage);
        }
    } catch (error) {
        console.error('댓글 수정 실패:', error);
        showTopToast(error.message || '댓글 수정에 실패했습니다', 'danger');
    }
}

function cancelCommentEdit(commentId) {
    loadComments(currentPage); // 편집 취소 - 새로고침
}

// ==================== 댓글 삭제 ====================
async function deleteComment(commentId) {
    if (!confirm('댓글을 삭제하시겠습니까?\n삭제된 댓글은 복구할 수 없습니다.')) {
        return;
    }

    const projectId = document.getElementById('publicId').value;
    const issueId = document.getElementById('issueId').value;

    try {
        const response = await callApi(
            `/api/projects/${projectId}/issues/${issueId}/comments/${commentId}`,
            { method: 'DELETE' }
        );

        if (response.success) {
            showTopToast('댓글이 삭제되었습니다', 'success');
            loadComments(currentPage);

            // 타임라인 즉시 업데이트
            if (typeof renderTimeline === 'function' && currentIssue) {
                renderTimeline(currentIssue);
            }
        }
    } catch (error) {
        console.error('댓글 삭제 실패:', error);
        showTopToast(error.message || '댓글 삭제에 실패했습니다', 'danger');
    }
}

// ==================== 멘션 자동완성 ====================
function setupCommentInput() {
    const input = document.getElementById('commentInput');
    const dropdown = document.getElementById('mentionDropdown');

    input.addEventListener('input', handleInputChange);
    input.addEventListener('keydown', handleKeyDown);

    // 외부 클릭 시 드롭다운 닫기
    document.addEventListener('click', (e) => {
        if (!input.contains(e.target) && !dropdown.contains(e.target)) {
            dropdown.classList.add('hidden');
        }
    });
}

function handleInputChange(e) {
    const input = e.target;
    const value = input.value;
    const cursorPos = input.selectionStart;

    // @ 위치 찾기
    const textBeforeCursor = value.substring(0, cursorPos);
    const lastAtIndex = textBeforeCursor.lastIndexOf('@');

    if (lastAtIndex !== -1) {
        // @ 이후 공백이 없는 텍스트 추출
        const afterAt = textBeforeCursor.substring(lastAtIndex + 1);
        if (!afterAt.includes(' ') && !afterAt.includes('\n')) {
            mentionStartPos = lastAtIndex;
            currentMentionQuery = afterAt;
            searchMembers(afterAt);
            return;
        }
    }

    // @ 없으면 드롭다운 닫기
    const dropdown = document.getElementById('mentionDropdown');
    dropdown.classList.add('hidden');
    selectedMemberIndex = -1;
    currentMemberList = [];
}

function handleKeyDown(e) {
    const dropdown = document.getElementById('mentionDropdown');

    if (!dropdown.classList.contains('hidden')) {
        if (e.key === 'Escape') {
            dropdown.classList.add('hidden');
            selectedMemberIndex = -1;
            e.preventDefault();
        } else if (e.key === 'ArrowDown') {
            // 아래 화살표 - 다음 멤버 선택
            e.preventDefault();
            if (currentMemberList.length > 0) {
                selectedMemberIndex = (selectedMemberIndex + 1) % currentMemberList.length;
                updateSelectedItem();
            }
        } else if (e.key === 'ArrowUp') {
            // 위 화살표 - 이전 멤버 선택
            e.preventDefault();
            if (currentMemberList.length > 0) {
                selectedMemberIndex = selectedMemberIndex <= 0
                    ? currentMemberList.length - 1
                    : selectedMemberIndex - 1;
                updateSelectedItem();
            }
        } else if (e.key === 'Enter') {
            // Enter - 선택된 멤버 삽입
            e.preventDefault();
            if (selectedMemberIndex >= 0 && selectedMemberIndex < currentMemberList.length) {
                const member = currentMemberList[selectedMemberIndex];
                selectMention(member.memberId, member.nickname);
            }
        }
    }
}

async function searchMembers(query) {
    if (query.length === 0) {
        renderMentionDropdown([]);
        return;
    }

    const projectId = document.getElementById('publicId').value;

    try {
        const response = await callApi(
            `/api/projects/${projectId}/members/search?query=${encodeURIComponent(query)}&limit=10`,
            { method: 'GET' }
        );

        if (response.success) {
            updateDropdownPosition();
            renderMentionDropdown(response.data);
        }
    } catch (error) {
        console.error('멤버 검색 실패:', error);
    }
}

/**
 * 커서 위치에 맞춰 드롭다운 위치 계산
 */
function updateDropdownPosition() {
    const input = document.getElementById('commentInput');
    const dropdown = document.getElementById('mentionDropdown');
    const computedStyle = window.getComputedStyle(input);

    // @ 위치까지의 텍스트
    const textBeforeCursor = input.value.substring(0, mentionStartPos);
    const lines = textBeforeCursor.split('\n');
    const currentLineIndex = lines.length - 1;

    // 줄 높이 계산
    const lineHeight = parseInt(computedStyle.lineHeight) || parseInt(computedStyle.fontSize) * 1.5;
    const paddingTop = parseInt(computedStyle.paddingTop) || 0;

    // Y 좌표: (줄 번호 * 줄 높이) + padding - 스크롤
    const top = (currentLineIndex * lineHeight) + paddingTop + lineHeight + 4 - input.scrollTop;

    // X 좌표: 왼쪽 padding
    const left = parseInt(computedStyle.paddingLeft) || 0;

    // 너비: 350px 또는 textarea 너비 중 작은 값
    const paddingRight = parseInt(computedStyle.paddingRight) || 0;
    const maxAvailableWidth = input.offsetWidth - left - paddingRight;
    const width = Math.min(350, maxAvailableWidth);

    dropdown.style.top = top + 'px';
    dropdown.style.left = left + 'px';
    dropdown.style.width = width + 'px';
}

function renderMentionDropdown(members) {
    const dropdown = document.getElementById('mentionDropdown');

    if (members.length === 0) {
        dropdown.classList.add('hidden');
        currentMemberList = [];
        selectedMemberIndex = -1;
        return;
    }

    currentMemberList = members;
    selectedMemberIndex = 0; // 첫 번째 항목 자동 선택

    dropdown.innerHTML = members.map((member, index) => {
        const profileImageUrl = member.profileImageUrl;
        const nickname = escapeHtml(member.nickname);
        const initial = member.nickname.charAt(0).toUpperCase();

        // 프로필 이미지 또는 이니셜 아바타
        const avatarHtml = profileImageUrl
            ? `<img src="${escapeHtml(profileImageUrl)}" alt="${nickname}"
                    class="w-8 h-8 object-cover rounded-md border border-gray-200">`
            : `<div class="w-8 h-8 bg-docu-primary rounded-md flex items-center justify-center border border-gray-200">
                   <span class="text-white text-sm font-semibold">${initial}</span>
               </div>`;

        return `
            <div class="mention-item px-3 py-2 cursor-pointer flex items-center gap-3 transition-colors duration-150 hover:bg-gray-50 rounded-md mx-1 ${index === 0 ? 'bg-indigo-50' : ''}"
                 data-index="${index}"
                 data-member-id="${escapeHtml(member.memberId)}"
                 data-nickname="${escapeHtml(member.nickname)}">
                ${avatarHtml}
                <div class="flex flex-col min-w-0">
                    <span class="text-sm font-medium text-gray-900 truncate">${nickname}</span>
                </div>
            </div>
        `;
    }).join('');

    // 이벤트 리스너 등록 (inline onclick 대신)
    dropdown.querySelectorAll('.mention-item').forEach(item => {
        item.addEventListener('click', () => {
            const memberId = item.dataset.memberId;
            const nickname = item.dataset.nickname;
            selectMention(memberId, nickname);
        });
    });

    dropdown.classList.remove('hidden');
}

/**
 * 드롭다운에서 선택된 항목 시각적 업데이트
 */
function updateSelectedItem() {
    const dropdown = document.getElementById('mentionDropdown');
    const items = dropdown.querySelectorAll('.mention-item');

    items.forEach((item, index) => {
        if (index === selectedMemberIndex) {
            item.classList.add('bg-indigo-50');
            // 스크롤하여 선택된 항목 보이도록
            item.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
        } else {
            item.classList.remove('bg-indigo-50');
        }
    });
}

function selectMention(memberId, nickname) {
    const input = document.getElementById('commentInput');
    const value = input.value;

    // @ 이전 텍스트 + @닉네임 + 이후 텍스트
    const before = value.substring(0, mentionStartPos);
    const after = value.substring(input.selectionStart);
    const newValue = before + '@' + nickname + ' ' + after;

    input.value = newValue;
    input.selectionStart = input.selectionEnd = (before + '@' + nickname + ' ').length;

    // 멘션 추가
    if (!mentionedMembers.find(m => m.memberId === memberId)) {
        mentionedMembers.push({ memberId, nickname });
        updateMentionedMembersPreview();
    }

    // 드롭다운 닫기 및 상태 리셋
    document.getElementById('mentionDropdown').classList.add('hidden');
    selectedMemberIndex = -1;
    currentMemberList = [];
    input.focus();
}

function updateMentionedMembersPreview() {
    const preview = document.getElementById('mentionedMembersPreview');

    if (mentionedMembers.length === 0) {
        preview.textContent = '';
        return;
    }

    preview.textContent = `멘션: ${mentionedMembers.map(m => '@' + m.nickname).join(', ')}`;
}

// ==================== 페이지네이션 ====================
function updatePagination(meta) {
    const pagination = document.getElementById('commentPagination');
    const pageInfo = document.getElementById('pageInfo');
    const prevBtn = document.getElementById('prevPage');
    const nextBtn = document.getElementById('nextPage');

    if (meta.totalPages <= 1) {
        pagination.classList.add('hidden');
        return;
    }

    pagination.classList.remove('hidden');
    pageInfo.textContent = `${meta.page + 1} / ${meta.totalPages}`;

    prevBtn.disabled = !meta.hasPrevious;
    nextBtn.disabled = !meta.hasNext;
}

function updateCommentCount(total) {
    document.getElementById('commentCount').textContent = `(${total})`;
}

// ==================== 유틸리티 ====================
function getCurrentMemberId() {
    // 현재 로그인한 사용자 ID (globalState에서 가져오거나 API 호출)
    return window.currentMemberId || null;
}

function formatDateTime(isoString) {
    const date = new Date(isoString);
    const now = new Date();
    const diff = now - date;

    // 1분 미만
    if (diff < 60000) return '방금 전';

    // 1시간 미만
    if (diff < 3600000) {
        const minutes = Math.floor(diff / 60000);
        return `${minutes}분 전`;
    }

    // 24시간 미만
    if (diff < 86400000) {
        const hours = Math.floor(diff / 3600000);
        return `${hours}시간 전`;
    }

    // 그 외
    return date.toLocaleDateString('ko-KR', {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });
}

function extractMentions(content) {
    const regex = /@(\S+)/g;
    const mentions = [];
    let match;

    while ((match = regex.exec(content)) !== null) {
        mentions.push(match[1]);
    }

    return [...new Set(mentions)]; // 중복 제거
}

async function resolveMentionsToIds(nicknames) {
    if (nicknames.length === 0) return [];

    const projectId = document.getElementById('publicId').value;
    const memberIds = [];

    for (const nickname of nicknames) {
        try {
            const response = await callApi(
                `/api/projects/${projectId}/members/search?query=${encodeURIComponent(nickname)}&limit=1`,
                { method: 'GET' }
            );

            if (response.success && response.data.length > 0) {
                const member = response.data.find(m => m.nickname === nickname);
                if (member) {
                    memberIds.push(member.memberId);
                }
            }
        } catch (error) {
            console.error(`멘션 해결 실패: ${nickname}`, error);
        }
    }

    return memberIds;
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function escapeRegex(text) {
    return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
