// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 이슈 목록 페이지 (추천 이슈 + 기존 이슈)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

let currentStatus = null;
let currentProjectId = null;
let currentPublicId = null;
let currentMainTab = 'recommendations'; // 'recommendations' or 'issues'
let currentRecommendationId = null;
let currentDetailIssueId = null; // 상세 모달에서 보고 있는 이슈 ID

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 초기화
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

document.addEventListener('DOMContentLoaded', () => {
    currentPublicId = document.getElementById('publicId').value;
    currentProjectId = document.getElementById('projectId').value;

    // URL 파라미터에서 탭 정보 복원
    const urlParams = new URLSearchParams(window.location.search);
    const tabParam = urlParams.get('tab');

    if (tabParam === 'issues') {
        switchMainTab('issues', false); // false = URL 업데이트 안 함 (이미 URL에 있음)
    } else {
        switchMainTab('recommendations', false);
    }

    // 브라우저 뒤로/앞으로 버튼 감지
    window.addEventListener('popstate', (event) => {
        const urlParams = new URLSearchParams(window.location.search);
        const tabParam = urlParams.get('tab');
        switchMainTab(tabParam === 'issues' ? 'issues' : 'recommendations', false);
    });
});

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 메인 탭 전환
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

function switchMainTab(tab, updateUrl = true) {
    currentMainTab = tab;

    // URL 업데이트 (새로고침 시 탭 상태 유지)
    if (updateUrl) {
        const url = new URL(window.location);
        if (tab === 'issues') {
            url.searchParams.set('tab', 'issues');
        } else {
            url.searchParams.delete('tab'); // 기본 탭은 파라미터 제거
        }
        window.history.replaceState({}, '', url);
    }

    // 탭 버튼 스타일 변경
    const recommendationsTab = document.getElementById('recommendationsTab');
    const issuesTab = document.getElementById('issuesTab');

    if (tab === 'recommendations') {
        recommendationsTab.classList.add('border-docu-primary', 'text-docu-primary');
        recommendationsTab.classList.remove('border-transparent', 'text-docu-secondary');
        issuesTab.classList.add('border-transparent', 'text-docu-secondary');
        issuesTab.classList.remove('border-docu-primary', 'text-docu-primary');

        // 섹션 표시/숨김
        document.getElementById('recommendationsSection').classList.remove('hidden');
        document.getElementById('issuesSection').classList.add('hidden');
        document.getElementById('statusFilterTabs').classList.add('hidden');

        loadRecommendationList();
    } else {
        issuesTab.classList.add('border-docu-primary', 'text-docu-primary');
        issuesTab.classList.remove('border-transparent', 'text-docu-secondary');
        recommendationsTab.classList.add('border-transparent', 'text-docu-secondary');
        recommendationsTab.classList.remove('border-docu-primary', 'text-docu-primary');

        // 섹션 표시/숨김
        document.getElementById('issuesSection').classList.remove('hidden');
        document.getElementById('recommendationsSection').classList.add('hidden');
        document.getElementById('statusFilterTabs').classList.remove('hidden');

        loadIssueList();
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 추천 이슈 목록
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

async function loadRecommendationList() {
    try {
        const body = await callApi(`/api/projects/${currentPublicId}/issue-recommendations`, {
            method: 'GET'
        });

        if (body.success) {
            renderRecommendationList(body.data);
        } else {
            showError('추천 목록을 불러올 수 없습니다.');
        }
    } catch (err) {
        showError(err.message);
    }
}

function renderRecommendationList(recommendations) {
    const container = document.getElementById('recommendationList');
    const emptyState = document.getElementById('emptyRecommendations');

    if (!recommendations || recommendations.length === 0) {
        container.innerHTML = '';
        emptyState.classList.remove('hidden');
        return;
    }

    emptyState.classList.add('hidden');

    // 심각도 높은 순으로 정렬 (CRITICAL > HIGH > MEDIUM > LOW)
    const severityOrder = { CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 };
    const sortedRecs = [...recommendations].sort((a, b) => {
        return (severityOrder[b.severity] || 0) - (severityOrder[a.severity] || 0);
    });

    container.innerHTML = sortedRecs.map(rec => {
        return `
        <div class="mx-4 my-3 p-4 bg-surface-card border border-divider rounded-lg shadow-sm hover:bg-surface-hover hover:shadow-md transition-colors cursor-pointer"
             onclick="openRecommendationDetail(${rec.id})">
            <div class="grid grid-cols-12 gap-4 items-center">
                <div class="col-span-4">
                    <p class="text-sm font-semibold text-docu-ink mb-1">${rec.title}</p>
                    <div class="flex items-center gap-2 text-xs text-docu-secondary">
                        <span>${rec.errorType || 'UNKNOWN'}</span>
                    </div>
                </div>
                <div class="col-span-2 text-center">
                    ${getSeverityBadge(rec.severity, rec.severityScore)}
                </div>
                <div class="col-span-1 text-center">
                    <span class="text-sm font-medium text-docu-ink">${rec.occurrenceCount}회</span>
                </div>
                <div class="col-span-2 text-center">
                    <p class="text-xs text-docu-secondary">${formatDateTime(rec.lastOccurredAt)}</p>
                </div>
                <div class="col-span-1 text-center">
                    ${getQualityBadge(rec.fingerprintQuality)}
                </div>
                <div class="col-span-2 text-center flex items-center justify-center gap-2">
                    <button onclick="event.stopPropagation(); openRecommendationDetail(${rec.id})"
                            class="btn-primary">
                        상세보기
                    </button>
                    <button onclick="event.stopPropagation(); rejectRecommendation(${rec.id})"
                            class="px-4 py-2 text-sm font-medium text-docu-secondary hover:bg-surface-sub border border-divider rounded-lg transition-colors">
                        거부
                    </button>
                </div>
            </div>
        </div>
    `}).join('');
}

async function openRecommendationDetail(recommendationId) {
    currentRecommendationId = recommendationId;

    try {
        // 추천 상세 조회
        const detailBody = await callApi(`/api/projects/${currentPublicId}/issue-recommendations/${recommendationId}`, {
            method: 'GET'
        });

        // 프로젝트 멤버 목록 조회
        const membersBody = await callApi(`/api/projects/${currentPublicId}/members`, {
            method: 'GET'
        });

        if (detailBody.success && membersBody.success) {
            renderRecommendationDetail(detailBody.data, membersBody.data);
            openModal('recommendationDetailModal');
        } else {
            showError('추천 상세 정보를 불러올 수 없습니다.');
        }
    } catch (err) {
        showError(err.message);
    }
}

/**
 * 유사도 분석 결과를 HTML로 렌더링
 */
function renderSimilarityAnalysis(similarityResults) {
    if (!similarityResults || similarityResults.length === 0) {
        return `
            <div class="p-4 bg-surface-secondary border border-divider rounded-lg">
                <div class="flex items-start gap-2">
                    <svg class="w-5 h-5 text-docu-secondary mt-0.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/>
                    </svg>
                    <div class="text-sm text-docu-ink">
                        <strong class="font-semibold">AI 판단 근거</strong><br>
                        <span class="text-docu-ink">유사도 분석 중...</span>
                    </div>
                </div>
            </div>
        `;
    }

    return `<div class="space-y-3">${similarityResults.map((result, index) => renderSingleSimilarity(result, index, similarityResults.length)).join('')}</div>`;
}

function renderSingleSimilarity(similarityResult, index, total) {
    const { matchType, similarity, matchedIssueId, matchedIssueTitle, reason, details } = similarityResult;

    // 매치 타입별 색상 및 아이콘
    let colorClass, bgClass, borderClass, icon, badge;

    if (matchType === 'EXACT_MATCH') {
        colorClass = 'text-docu-ink';
        bgClass = 'bg-surface-secondary';
        borderClass = 'border-divider';
        badge = '완전 일치';
        icon = `<svg class="w-5 h-5 text-docu-danger mt-0.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/>
                </svg>`;
    } else if (matchType === 'HIGHLY_SIMILAR') {
        colorClass = 'text-docu-ink';
        bgClass = 'bg-surface-secondary';
        borderClass = 'border-divider';
        badge = '매우 유사';
        icon = `<svg class="w-5 h-5 text-docu-danger mt-0.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/>
                </svg>`;
    } else if (matchType === 'SIMILAR') {
        colorClass = 'text-docu-ink';
        bgClass = 'bg-surface-secondary';
        borderClass = 'border-divider';
        badge = '유사';
        icon = `<svg class="w-5 h-5 text-docu-warning mt-0.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/>
                </svg>`;
    } else {
        colorClass = 'text-docu-ink';
        bgClass = 'bg-surface-secondary';
        borderClass = 'border-divider';
        badge = '신규';
        icon = `<svg class="w-5 h-5 text-docu-success mt-0.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"/>
                </svg>`;
    }

    return `
        <div class="p-4 ${bgClass} border ${borderClass} rounded-lg">
            <div class="flex items-start gap-2">
                ${icon}
                <div class="text-sm ${colorClass} flex-1">
                    <div class="flex items-center justify-between mb-2">
                        <div class="flex items-center gap-2">
                            ${total > 1 && index === 0 ? '<strong class="text-xs">#1</strong>' : ''}
                            ${total > 1 && index > 0 ? `<span class="text-xs opacity-60">#${index + 1}</span>` : ''}
                            <span class="px-2 py-0.5 bg-surface-card rounded text-xs font-medium">${badge}</span>
                        </div>
                        ${similarity !== null ? `<span class="px-2 py-0.5 bg-surface-card rounded-full text-xs font-medium">유사도 ${similarity.toFixed(1)}%</span>` : ''}
                    </div>
                    <p class="mb-3">${reason}</p>

                    ${matchedIssueId ? `
                        <div class="mt-3 pt-3 border-t ${borderClass}">
                            <p class="text-xs font-medium mb-2">유사 이슈 #${matchedIssueId}</p>
                            <p class="text-xs opacity-80">${matchedIssueTitle}</p>
                        </div>
                    ` : ''}

                    ${details ? `
                        <details class="mt-3 pt-3 border-t ${borderClass}">
                            <summary class="cursor-pointer text-xs font-medium hover:underline">상세 점수 보기</summary>
                            <div class="mt-2 space-y-1 text-xs opacity-80">
                                ${details.fingerprintScore !== null ? `
                                <div class="flex justify-between">
                                    <span>Fingerprint 일치:</span>
                                    <span class="font-medium">${details.fingerprintScore.toFixed(0)}%</span>
                                </div>
                                ` : ''}
                                <div class="flex justify-between">
                                    <span>에러 타입 일치:</span>
                                    <span class="font-medium">${details.errorTypeScore.toFixed(0)}%</span>
                                </div>
                                <div class="flex justify-between">
                                    <span>스택 트레이스 유사도 (60%):</span>
                                    <span class="font-medium">${details.stackScore.toFixed(0)}%</span>
                                </div>
                                <div class="flex justify-between">
                                    <span>메시지 유사도 (20%):</span>
                                    <span class="font-medium">${details.messageScore.toFixed(0)}%</span>
                                </div>
                            </div>
                        </details>
                    ` : ''}
                </div>
            </div>
        </div>
    `;
}

function renderRecommendationDetail(rec, members) {
    const container = document.getElementById('recommendationDetailContent');
    const rightPanel = document.getElementById('recommendationDetailRight');

    // 기본 선택된 담당자
    let selectedAssigneeId = rec.assigneeId;

    // 왼쪽: 이슈 정보
    container.innerHTML = `
        <div class="space-y-5">
            <!-- 이슈 제목 -->
            <div>
                <label class="block text-sm font-semibold text-docu-ink mb-2">이슈 제목 <span class="text-docu-danger">*</span></label>
                <input type="text" id="editIssueTitle" value="${rec.title || ''}"
                       class="w-full px-4 py-3 border border-divider rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-docu-primary focus:border-transparent"
                       placeholder="이슈 제목을 입력하세요">
            </div>

            <!-- 이슈 설명 -->
            <div>
                <label class="block text-sm font-semibold text-docu-ink mb-2">이슈 설명</label>
                <textarea id="editIssueDescription" rows="8"
                          class="w-full px-4 py-3 border border-divider rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-docu-primary focus:border-transparent resize-none"
                          placeholder="이슈에 대한 상세 설명을 입력하세요">${rec.description || ''}</textarea>
                <div class="mt-2 p-3 bg-surface-secondary border border-divider rounded-lg text-xs text-docu-secondary">
                    <strong class="text-docu-ink">자동 수집된 정보:</strong><br>
                    Error Type: ${rec.errorType || 'UNKNOWN'} |
                    Stack Key: ${rec.stackKey || 'N/A'}
                </div>
            </div>

            <!-- AI 판단 근거 -->
            ${renderSimilarityAnalysis(rec.similarityResults)}
        </div>
    `;

    // 오른쪽: 메타 정보
    rightPanel.innerHTML = `
        <div class="space-y-5">
            <!-- 심각도 -->
            <div>
                <label class="block text-sm font-semibold text-docu-ink mb-3">심각도</label>
                <div class="grid grid-cols-2 gap-2">
                    ${[
                        { level: 'CRITICAL', label: 'CRITICAL', borderColor: 'border-docu-danger', textColor: 'text-docu-danger', dotColor: 'bg-docu-danger', shadow: 'shadow-docu-danger' },
                        { level: 'HIGH', label: 'HIGH', borderColor: 'border-docu-primary', textColor: 'text-docu-primary-dark', dotColor: 'bg-docu-primary', shadow: 'shadow-docu-primary' },
                        { level: 'MEDIUM', label: 'MEDIUM', borderColor: 'border-docu-warning', textColor: 'text-docu-warning-dark', dotColor: 'bg-docu-warning', shadow: 'shadow-docu-warning' },
                        { level: 'LOW', label: 'LOW', borderColor: 'border-docu-success', textColor: 'text-docu-success-dark', dotColor: 'bg-docu-success', shadow: 'shadow-docu-success' }
                    ].map(({ level, label, borderColor, textColor, dotColor, shadow }) => {
                        const isActive = rec.severity === level;
                        return `
                            <button type="button" class="inline-flex items-center justify-center gap-1.5 px-3 py-2 rounded-docu-btn text-xs font-bold border-2 ${isActive ? borderColor + ' ' + textColor + ' bg-surface-card ' + shadow : 'border-divider text-docu-tertiary bg-surface-card'} transition-transform hover:-translate-y-0.5 focus-ring">
                                <span class="w-2 h-2 rounded-full ${isActive ? dotColor : 'bg-gray-300'}" aria-hidden="true"></span>
                                <span class="w-14 text-center">${label}</span>
                            </button>
                        `;
                    }).join('')}
                </div>
                <p class="text-xs text-docu-secondary mt-2">심각도 점수: ${rec.severityScore}점</p>
            </div>

            <!-- 담당자 지정 -->
            <div>
                <label class="block text-sm font-semibold text-docu-ink mb-3">담당자 지정</label>
                <select id="assigneeSelect" class="w-full px-4 py-3 border border-divider rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-docu-primary focus:border-transparent"
                        onchange="selectedAssigneeId = this.value">
                    ${members.map(member => `
                        <option value="${member.memberId}" ${member.memberId === rec.assigneeId ? 'selected' : ''}>
                            ${member.nickname}
                        </option>
                    `).join('')}
                </select>
                <p class="text-xs text-docu-secondary mt-2">
                    💡 이슈 생성 시 선택한 담당자에게 알림이 전송됩니다
                </p>
            </div>

            <!-- 우선순위 선택 -->
            <div>
                <label class="block text-sm font-semibold text-docu-ink mb-3">우선순위 <span class="text-docu-danger">*</span></label>
                <select id="prioritySelect" class="w-full px-4 py-3 border border-divider rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-docu-primary focus:border-transparent">
                    <option value="">선택하세요</option>
                    <option value="P1">P1 긴급</option>
                    <option value="P2">P2 높음</option>
                    <option value="P3">P3 보통</option>
                    <option value="P4">P4 낮음</option>
                </select>
                <p class="text-xs text-docu-secondary mt-2">
                    💡 비즈니스 중요도에 따라 우선순위를 지정하세요
                </p>
            </div>

            <!-- 발생 정보 -->
            <div>
                <label class="block text-sm font-semibold text-docu-ink mb-2">발생 정보</label>
                <div class="space-y-2 text-sm">
                    <div class="flex justify-between">
                        <span class="text-docu-secondary">발생 횟수:</span>
                        <span class="font-medium text-docu-ink">${rec.occurrenceCount}회</span>
                    </div>
                    <div class="flex justify-between">
                        <span class="text-docu-secondary">최초 발생:</span>
                        <span class="text-docu-ink">${formatDateTime(rec.firstOccurredAt)}</span>
                    </div>
                    <div class="flex justify-between">
                        <span class="text-docu-secondary">최근 발생:</span>
                        <span class="text-docu-ink">${formatDateTime(rec.lastOccurredAt)}</span>
                    </div>
                </div>
            </div>
        </div>
    `;
}

async function approveRecommendation(recommendationId) {
    if (!confirm('이 추천을 승인하여 이슈로 생성하시겠습니까?')) {
        return;
    }

    try {
        const body = await callApi(`/api/projects/${currentPublicId}/issue-recommendations/${recommendationId}/approve`, {
            method: 'POST'
        });

        if (body.success) {
            showTopToast('추천이 승인되어 이슈로 생성되었습니다.', 'success');
            loadRecommendationList();
        } else {
            showError(body.error?.message || '승인에 실패했습니다.');
        }
    } catch (err) {
        showError(err.message);
    }
}

async function approveFromDetail() {
    if (!currentRecommendationId) {
        showError('추천 ID를 찾을 수 없습니다.');
        return;
    }

    // 선택된 담당자 ID 가져오기
    const assigneeSelect = document.getElementById('assigneeSelect');
    const selectedAssigneeId = assigneeSelect ? assigneeSelect.value : null;

    // 선택된 우선순위 가져오기
    const prioritySelect = document.getElementById('prioritySelect');
    const selectedPriority = prioritySelect ? prioritySelect.value : null;

    // 수정된 제목과 설명 가져오기
    const title = document.getElementById('editIssueTitle')?.value?.trim();
    const description = document.getElementById('editIssueDescription')?.value?.trim();

    // 제목 필수 검증
    if (!title) {
        showError('이슈 제목을 입력하세요.');
        document.getElementById('editIssueTitle')?.focus();
        return;
    }

    // 우선순위 필수 검증
    if (!selectedPriority) {
        showError('우선순위를 선택하세요.');
        document.getElementById('prioritySelect')?.focus();
        return;
    }

    try {
        const body = await callApi(`/api/projects/${currentPublicId}/issue-recommendations/${currentRecommendationId}/approve`, {
            method: 'POST',
            body: JSON.stringify({
                assigneeId: selectedAssigneeId,
                title: title,
                description: description,
                priority: selectedPriority
            })
        });

        if (body.success) {
            closeModal('recommendationDetailModal');
            showTopToast('추천이 승인되어 이슈로 생성되었습니다.', 'success');
            loadRecommendationList();
        } else {
            showError(body.error?.message || '승인에 실패했습니다.');
        }
    } catch (err) {
        showError(err.message);
    }
}

function rejectRecommendation(recommendationId) {
    // 거부 확인 모달 열기
    document.getElementById('rejectRecommendationId').value = recommendationId;
    openModal('rejectConfirmModal');
}

async function confirmReject() {
    const recommendationId = document.getElementById('rejectRecommendationId').value;

    if (!recommendationId) {
        showError('추천 ID를 찾을 수 없습니다.');
        return;
    }

    try {
        const body = await callApi(`/api/projects/${currentPublicId}/issue-recommendations/${recommendationId}/reject`, {
            method: 'POST'
        });

        if (body.success) {
            closeModal('rejectConfirmModal');
            showTopToast('추천이 거부되었습니다.', 'success');
            loadRecommendationList();
        } else {
            showError(body.error?.message || '거부에 실패했습니다.');
        }
    } catch (err) {
        showError(err.message);
    }
}

function rejectFromDetail() {
    if (!currentRecommendationId) {
        showError('추천 ID를 찾을 수 없습니다.');
        return;
    }
    closeModal('recommendationDetailModal');
    rejectRecommendation(currentRecommendationId);
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 이슈 목록 조회
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

async function loadIssueList() {
    try {
        let url = `/api/projects/${currentPublicId}/issues`;
        if (currentStatus) {
            url += `?status=${currentStatus}`;
        }

        const body = await callApi(url, { method: 'GET' });

        if (body.success) {
            renderIssueList(body.data);
        } else {
            showError('이슈 목록을 불러올 수 없습니다.');
        }
    } catch (err) {
        showError(err.message);
    }
}

function renderIssueList(issues) {
    const container = document.getElementById('issueList');
    const emptyState = document.getElementById('emptyIssues');

    if (!issues || issues.length === 0) {
        container.innerHTML = '';
        emptyState.classList.remove('hidden');
        return;
    }

    emptyState.classList.add('hidden');

    // 정렬: 최신순 (날짜가 같으면 심각도 순)
    const severityOrder = { CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 };
    const sortedIssues = [...issues].sort((a, b) => {
        // 1. 최근 발생 시각 내림차순 (최신순)
        const dateA = new Date(a.lastOccurredAt);
        const dateB = new Date(b.lastOccurredAt);
        if (dateB - dateA !== 0) {
            return dateB - dateA;
        }
        // 2. 날짜가 같으면 심각도 순 (CRITICAL > HIGH > MEDIUM > LOW)
        return (severityOrder[b.severity] || 0) - (severityOrder[a.severity] || 0);
    });

    container.innerHTML = sortedIssues.map(issue => {
        return `
        <div class="mx-4 my-3 p-4 bg-surface-card border border-divider rounded-lg shadow-sm hover:bg-surface-hover hover:shadow-md transition-colors cursor-pointer"
             onclick="openIssueDetail(${issue.id})">
            <div class="grid grid-cols-12 gap-4 items-center">
                <div class="col-span-3">
                    <p class="text-sm font-semibold text-docu-ink mb-1">${issue.title}</p>
                    <div class="flex items-center gap-2 text-xs text-docu-secondary">
                        <span>${issue.errorType || 'UNKNOWN'}</span>
                    </div>
                </div>
                <div class="col-span-2 text-center">
                    ${getSeverityBadge(issue.severity, issue.severityScore)}
                </div>
                <div class="col-span-1 text-center">
                    ${getPriorityBadge(issue.priority)}
                </div>
                <div class="col-span-1 text-center">
                    ${getStatusBadge(issue.status)}
                </div>
                <div class="col-span-1 text-center">
                    <span class="text-sm font-medium text-docu-ink">${issue.occurrenceCount}회</span>
                </div>
                <div class="col-span-1 text-center">
                    <p class="text-xs text-docu-secondary">${formatDateTime(issue.lastOccurredAt)}</p>
                </div>
                <div class="col-span-1 text-center flex items-center justify-center gap-1.5">
                    ${getAssigneeDisplay(issue.assignee)}
                </div>
                <div class="col-span-2 text-center flex items-center justify-center gap-2">
                    <button onclick="event.stopPropagation(); openIssueDetail(${issue.id})"
                            class="btn-primary">
                        상세보기
                    </button>
                    <button onclick="event.stopPropagation(); openStatusModal(${issue.id}, '${issue.status}')"
                            class="px-4 py-2 text-sm font-medium text-docu-secondary hover:bg-surface-sub border border-divider rounded-lg transition-colors">
                        상태변경
                    </button>
                </div>
            </div>
        </div>
    `}).join('');
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 상태 필터
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

function filterByStatus(status) {
    currentStatus = status;

    // 탭 활성화 스타일 변경
    document.querySelectorAll('.status-tab').forEach(tab => {
        const tabStatus = tab.dataset.status === 'all' ? null : tab.dataset.status;
        if (tabStatus === status) {
            tab.classList.remove('border-transparent', 'text-docu-secondary');
            tab.classList.add('border-docu-primary', 'text-docu-primary');
        } else {
            tab.classList.remove('border-docu-primary', 'text-docu-primary');
            tab.classList.add('border-transparent', 'text-docu-secondary');
        }
    });

    loadIssueList();
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 이슈 상세 조회
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

async function openIssueDetail(issueId) {
    currentDetailIssueId = issueId; // 현재 보고 있는 이슈 ID 저장

    try {
        const body = await callApi(`/api/projects/${currentPublicId}/issues/${issueId}`, {
            method: 'GET'
        });

        if (body.success) {
            renderIssueDetail(body.data);
            openModal('issueDetailModal');
        } else {
            showError('이슈 상세 정보를 불러올 수 없습니다.');
        }
    } catch (err) {
        showError(err.message);
    }
}

function openDetailedAnalysis() {
    if (!currentDetailIssueId) {
        showError('이슈 ID를 찾을 수 없습니다.');
        return;
    }

    // 상세 분석 페이지로 이동
    window.location.href = `/projects/${currentPublicId}/issues/${currentDetailIssueId}/analysis`;
}

function renderIssueDetail(issue) {
    // 헤더 정보
    document.getElementById('quickIssueId').textContent = issue.id;
    document.getElementById('quickIssueTitle').textContent = issue.title;

    // 상태 배지 (상태 변경 모달에서 사용하기 위해 data 속성 추가)
    const statusBadgeEl = document.getElementById('quickStatusBadge');
    statusBadgeEl.innerHTML = getStatusBadge(issue.status);
    statusBadgeEl.dataset.status = issue.status;

    // 심각도
    const severityColors = {
        CRITICAL: 'text-docu-danger',
        HIGH: 'text-docu-warning',
        MEDIUM: 'text-docu-warning',
        LOW: 'text-docu-secondary'
    };
    const severityColor = severityColors[issue.severity] || severityColors.LOW;
    document.getElementById('quickSeverity').innerHTML = `
        <span class="${severityColor}">${issue.severity}</span>
        <div class="text-xs text-docu-secondary mt-1">${issue.severityScore}점</div>
    `;

    // 발생 횟수
    document.getElementById('quickOccurrenceCount').textContent = `${issue.occurrenceCount}회`;

    // 영향 받은 플레이어 (임시: 발생 횟수의 70% 추정)
    const affectedUsers = Math.floor(issue.occurrenceCount * 0.7);
    document.getElementById('quickAffectedUsers').textContent = `${affectedUsers}명`;

    // 스택트레이스 미리보기 (첫 3줄만)
    const stackPreview = document.querySelector('#quickStackPreview pre');
    if (issue.stackKey) {
        const lines = issue.stackKey.split('\n').slice(0, 3);
        stackPreview.textContent = lines.join('\n') + '\n... (상세 분석에서 전체 보기)';
    } else {
        stackPreview.textContent = '스택트레이스 정보가 없습니다.';
    }

    // 발생 시각
    document.getElementById('quickFirstOccurred').textContent = formatDateTime(issue.firstOccurredAt);
    document.getElementById('quickLastOccurred').textContent = formatDateTime(issue.lastOccurredAt);
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 담당자 할당
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

function openAssignModal(issueId) {
    document.getElementById('assignIssueId').value = issueId;
    document.getElementById('assigneeId').value = '';
    document.getElementById('assignError').classList.add('hidden');
    openModal('assignModal');
}

async function submitAssign() {
    const issueId = document.getElementById('assignIssueId').value;
    const assigneeId = document.getElementById('assigneeId').value.trim();
    const errorEl = document.getElementById('assignError');

    errorEl.classList.add('hidden');

    if (!assigneeId) {
        errorEl.textContent = '담당자 ID를 입력하세요.';
        errorEl.classList.remove('hidden');
        return;
    }

    try {
        const body = await callApi(`/api/projects/${currentPublicId}/issues/${issueId}/assignee`, {
            method: 'PUT',
            body: JSON.stringify({ assigneeId })
        });

        if (body.success) {
            closeModal('assignModal');
            loadIssueList();
            showTopToast('담당자가 지정되었습니다.', 'success');
        } else {
            errorEl.textContent = body.error?.message || '담당자 지정에 실패했습니다.';
            errorEl.classList.remove('hidden');
        }
    } catch (err) {
        errorEl.textContent = err.message;
        errorEl.classList.remove('hidden');
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 상태 변경
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

function openStatusModal(issueId, currentStatus) {
    document.getElementById('statusIssueId').value = issueId;
    document.getElementById('newStatus').value = '';
    document.getElementById('statusError').classList.add('hidden');
    openModal('statusModal');
}

async function submitStatusChange() {
    const issueId = document.getElementById('statusIssueId').value;
    const status = document.getElementById('newStatus').value;
    const errorEl = document.getElementById('statusError');

    errorEl.classList.add('hidden');

    if (!status) {
        errorEl.textContent = '변경할 상태를 선택하세요.';
        errorEl.classList.remove('hidden');
        return;
    }

    try {
        const body = await callApi(`/api/projects/${currentPublicId}/issues/${issueId}/status`, {
            method: 'PUT',
            body: JSON.stringify({ status })
        });

        if (body.success) {
            closeModal('statusModal');
            loadIssueList();
            showTopToast('이슈 상태가 변경되었습니다.', 'success');
        } else {
            errorEl.textContent = body.error?.message || '상태 변경에 실패했습니다.';
            errorEl.classList.remove('hidden');
        }
    } catch (err) {
        errorEl.textContent = err.message;
        errorEl.classList.remove('hidden');
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 유틸리티
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

function getStatusBadge(status) {
    const statusMap = {
        TODO: { label: '대기중', class: 'badge-status-pending' },
        IN_PROGRESS: { label: '처리중', class: 'badge-base bg-docu-primary text-white border-docu-primary-dark' },
        RESOLVED: { label: '해결됨', class: 'badge-base bg-docu-success text-white border-docu-success-dark' }
    };
    const { label, class: badgeClass } = statusMap[status] || { label: status, class: 'badge-default' };
    return `<span class="${badgeClass}">${label}</span>`;
}

function getSeverityBadge(severity, score) {
    const severityMap = {
        LOW: {
            label: 'LOW',
            borderColor: 'border-docu-success',
            textColor: 'text-docu-success-dark',
            dotColor: 'bg-docu-success',
            shadow: 'shadow-docu-success'
        },
        MEDIUM: {
            label: 'MEDIUM',
            borderColor: 'border-docu-warning',
            textColor: 'text-docu-warning-dark',
            dotColor: 'bg-docu-warning',
            shadow: 'shadow-docu-warning'
        },
        HIGH: {
            label: 'HIGH',
            borderColor: 'border-docu-primary',
            textColor: 'text-docu-primary-dark',
            dotColor: 'bg-docu-primary',
            shadow: 'shadow-docu-primary'
        },
        CRITICAL: {
            label: 'CRITICAL',
            borderColor: 'border-docu-danger',
            textColor: 'text-docu-danger',
            dotColor: 'bg-docu-danger',
            shadow: 'shadow-docu-danger'
        }
    };
    const config = severityMap[severity] || {
        label: severity,
        borderColor: 'border-divider',
        textColor: 'text-gray-600',
        dotColor: 'bg-gray-400',
        shadow: 'shadow-docu-sm'
    };
    return `
        <button type="button" class="inline-flex items-center gap-1.5 px-3 py-2 rounded-docu-btn text-xs font-bold border-2 ${config.borderColor} ${config.textColor} bg-surface-card ${config.shadow} transition-transform hover:-translate-y-0.5 focus-ring">
            <span class="w-2 h-2 rounded-full ${config.dotColor}" aria-hidden="true"></span>
            <span class="w-14 text-center">${config.label}</span>
        </button>
    `;
}

function formatDateTime(dateTimeStr) {
    if (!dateTimeStr) return 'N/A';
    const date = new Date(dateTimeStr);
    return date.toLocaleString('ko-KR', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    });
}

function openModal(modalId) {
    document.getElementById(modalId).classList.remove('hidden');
}

function closeModal(modalId) {
    document.getElementById(modalId).classList.add('hidden');
    if (modalId === 'recommendationDetailModal') {
        currentRecommendationId = null;
    }
    if (modalId === 'issueDetailModal') {
        currentDetailIssueId = null;
    }
    if (modalId === 'rejectConfirmModal') {
        document.getElementById('rejectRecommendationId').value = '';
    }
}

function getRecommendationStatusBadge(status) {
    const statusMap = {
        RECOMMENDED: { label: '추천 대기', class: 'badge-event-suggest' },
        TODO: { label: '대기중', class: 'badge-status-pending' },
        IN_PROGRESS: { label: '처리중', class: 'badge-base bg-docu-primary text-white border-docu-primary-dark' },
        RESOLVED: { label: '해결됨', class: 'badge-base bg-docu-success text-white border-docu-success-dark' },
        REJECTED: { label: '거부됨', class: 'badge-default' }
    };
    const { label, class: badgeClass } = statusMap[status] || { label: status, class: 'badge-default' };
    return `<span class="${badgeClass}">${label}</span>`;
}

function getSeverityBorderColor(severity) {
    const borderMap = {
        CRITICAL: 'border-docu-danger',
        HIGH: 'border-docu-warning',
        MEDIUM: 'border-docu-warning',
        LOW: 'border-divider'
    };
    return borderMap[severity] || 'border-divider';
}

function getQualityBadge(fingerprintQuality) {
    if (!fingerprintQuality) {
        return '<span class="inline-block px-2.5 py-1 rounded-full text-xs font-medium bg-gray-100 text-gray-600">알 수 없음</span>';
    }

    // FingerprintQuality enum 기반
    const qualityMap = {
        'HIGH': {
            label: '높음',
            class: 'bg-green-100 text-green-800',
            needsReview: false
        },
        'MEDIUM': {
            label: '보통',
            class: 'bg-blue-100 text-blue-800',
            needsReview: false
        },
        'LOW': {
            label: '낮음',
            class: 'bg-yellow-100 text-yellow-800',
            needsReview: true
        },
        'VERY_LOW': {
            label: '매우 낮음',
            class: 'bg-orange-100 text-orange-800',
            needsReview: true
        },
        'FALLBACK': {
            label: '최소',
            class: 'bg-red-100 text-red-800',
            needsReview: true
        }
    };

    const config = qualityMap[fingerprintQuality] || qualityMap['FALLBACK'];
    const reviewBadge = config.needsReview
        ? '<span class="ml-1 text-[10px] text-orange-600" title="수동 검토 필요">⚠️</span>'
        : '';

    return `<span class="inline-block px-2.5 py-1 rounded-full text-xs font-medium ${config.class}">${config.label}${reviewBadge}</span>`;
}

function getPriorityBadge(priority) {
    if (!priority) {
        return '<span class="text-xs text-docu-tertiary">미설정</span>';
    }

    const priorityMap = {
        P1: {
            label: 'P1 긴급',
            borderColor: 'border-docu-danger',
            textColor: 'text-docu-danger',
            dotColor: 'bg-docu-danger',
            shadow: 'shadow-docu-danger'
        },
        P2: {
            label: 'P2 높음',
            borderColor: 'border-docu-primary',
            textColor: 'text-docu-primary-dark',
            dotColor: 'bg-docu-primary',
            shadow: 'shadow-docu-primary'
        },
        P3: {
            label: 'P3 보통',
            borderColor: 'border-docu-warning',
            textColor: 'text-docu-warning-dark',
            dotColor: 'bg-docu-warning',
            shadow: 'shadow-docu-warning'
        },
        P4: {
            label: 'P4 낮음',
            borderColor: 'border-docu-success',
            textColor: 'text-docu-success-dark',
            dotColor: 'bg-docu-success',
            shadow: 'shadow-docu-success'
        }
    };
    const config = priorityMap[priority] || {
        label: priority,
        borderColor: 'border-divider',
        textColor: 'text-gray-600',
        dotColor: 'bg-gray-400',
        shadow: 'shadow-docu-sm'
    };
    return `
        <button type="button" class="inline-flex items-center gap-1.5 px-3 py-2 rounded-docu-btn text-xs font-bold border-2 ${config.borderColor} ${config.textColor} bg-surface-card ${config.shadow} transition-transform hover:-translate-y-0.5 focus-ring">
            <span class="w-2 h-2 rounded-full ${config.dotColor}" aria-hidden="true"></span>
            <span>${config.label}</span>
        </button>
    `;
}

function getAssigneeDisplay(assignee) {
    if (!assignee) {
        return `<p class="text-xs text-docu-tertiary">-</p>`;
    }

    const initial = assignee.nickname ? assignee.nickname.charAt(0) : '?';

    if (assignee.profileImageUrl) {
        return `
            <div class="w-6 h-6 rounded-full overflow-hidden border border-divider bg-surface-sub shrink-0">
                <img src="${assignee.profileImageUrl}" alt="${assignee.nickname}" class="w-full h-full object-cover">
            </div>
            <p class="text-xs text-docu-ink truncate">${assignee.nickname}</p>
        `;
    } else {
        return `
            <div class="w-6 h-6 rounded-full overflow-hidden border border-divider bg-docu-primary flex items-center justify-center text-white text-[10px] font-bold shrink-0">
                ${initial}
            </div>
            <p class="text-xs text-docu-ink truncate">${assignee.nickname}</p>
        `;
    }
}

function showError(message) {
    showTopToast(message, 'danger');
}
