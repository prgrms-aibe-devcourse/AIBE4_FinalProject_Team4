// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 이슈 목록 페이지 (추천 이슈 + 기존 이슈)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

let currentStatus = null;
let currentProjectId = null;
let currentPublicId = null;
let currentMainTab = 'recommendations'; // 'recommendations' or 'issues'
let currentRecommendationId = null;

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 초기화
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

document.addEventListener('DOMContentLoaded', () => {
    currentPublicId = document.getElementById('publicId').value;
    currentProjectId = document.getElementById('projectId').value;
    loadRecommendationList(); // 기본 탭이 추천 이슈
});

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 메인 탭 전환
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

function switchMainTab(tab) {
    currentMainTab = tab;

    // 탭 버튼 스타일 변경
    const recommendationsTab = document.getElementById('recommendationsTab');
    const issuesTab = document.getElementById('issuesTab');

    if (tab === 'recommendations') {
        recommendationsTab.classList.add('border-indigo-600', 'text-indigo-600');
        recommendationsTab.classList.remove('border-transparent', 'text-gray-500');
        issuesTab.classList.add('border-transparent', 'text-gray-500');
        issuesTab.classList.remove('border-indigo-600', 'text-indigo-600');

        // 섹션 표시/숨김
        document.getElementById('recommendationsSection').classList.remove('hidden');
        document.getElementById('issuesSection').classList.add('hidden');
        document.getElementById('statusFilterTabs').classList.add('hidden');

        loadRecommendationList();
    } else {
        issuesTab.classList.add('border-indigo-600', 'text-indigo-600');
        issuesTab.classList.remove('border-transparent', 'text-gray-500');
        recommendationsTab.classList.add('border-transparent', 'text-gray-500');
        recommendationsTab.classList.remove('border-indigo-600', 'text-indigo-600');

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
        const body = await callApi(`/api/projects/${currentProjectId}/issue-recommendations`, {
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
        const borderColor = getSeverityBorderColor(rec.severity);
        return `
        <div class="mx-4 my-3 p-4 bg-white border-l-4 ${borderColor} rounded-lg shadow-sm hover:shadow-md transition-shadow cursor-pointer"
             onclick="openRecommendationDetail(${rec.id})">
            <div class="grid grid-cols-12 gap-4 items-center">
                <div class="col-span-4">
                    <p class="text-sm font-semibold text-gray-900 mb-1">${rec.title}</p>
                    <div class="flex items-center gap-2 text-xs text-gray-500">
                        <span>ID: ${rec.id}</span>
                        <span>•</span>
                        <span>${rec.errorType || 'UNKNOWN'}</span>
                    </div>
                </div>
                <div class="col-span-1 text-center">
                    ${getQualityBadge(rec.severityScore)}
                </div>
                <div class="col-span-2 text-center">
                    ${getSeverityBadge(rec.severity, rec.severityScore)}
                </div>
                <div class="col-span-1 text-center">
                    <span class="text-sm font-medium text-gray-700">${rec.occurrenceCount}회</span>
                </div>
                <div class="col-span-1 text-center">
                    <p class="text-xs text-gray-600">${formatDateTime(rec.lastOccurredAt)}</p>
                </div>
                <div class="col-span-3 text-center flex items-center justify-center gap-2">
                    <button onclick="event.stopPropagation(); openRecommendationDetail(${rec.id})"
                            class="px-4 py-2 text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 rounded-lg transition-colors">
                        상세보기
                    </button>
                    <button onclick="event.stopPropagation(); rejectRecommendation(${rec.id})"
                            class="px-4 py-2 text-sm font-medium text-gray-600 hover:bg-gray-100 border border-gray-300 rounded-lg transition-colors">
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
        const detailBody = await callApi(`/api/projects/${currentProjectId}/issue-recommendations/${recommendationId}`, {
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
            <div class="p-4 bg-gray-50 border border-gray-200 rounded-lg">
                <div class="flex items-start gap-2">
                    <svg class="w-5 h-5 text-gray-600 mt-0.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/>
                    </svg>
                    <div class="text-sm text-gray-900">
                        <strong class="font-semibold">AI 판단 근거</strong><br>
                        <span class="text-gray-700">유사도 분석 중...</span>
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
        colorClass = 'text-red-900';
        bgClass = 'bg-red-50';
        borderClass = 'border-red-200';
        badge = '완전 일치';
        icon = `<svg class="w-5 h-5 text-red-600 mt-0.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/>
                </svg>`;
    } else if (matchType === 'HIGHLY_SIMILAR') {
        colorClass = 'text-red-800';
        bgClass = 'bg-red-50';
        borderClass = 'border-red-200';
        badge = '매우 유사';
        icon = `<svg class="w-5 h-5 text-red-500 mt-0.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/>
                </svg>`;
    } else if (matchType === 'SIMILAR') {
        colorClass = 'text-orange-900';
        bgClass = 'bg-orange-50';
        borderClass = 'border-orange-200';
        badge = '유사';
        icon = `<svg class="w-5 h-5 text-orange-600 mt-0.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/>
                </svg>`;
    } else {
        colorClass = 'text-green-900';
        bgClass = 'bg-green-50';
        borderClass = 'border-green-200';
        badge = '신규';
        icon = `<svg class="w-5 h-5 text-green-600 mt-0.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
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
                            <span class="px-2 py-0.5 bg-white rounded text-xs font-medium">${badge}</span>
                        </div>
                        ${similarity !== null ? `<span class="px-2 py-0.5 bg-white rounded-full text-xs font-medium">유사도 ${similarity.toFixed(1)}%</span>` : ''}
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
                <label class="block text-sm font-semibold text-gray-700 mb-2">이슈 제목</label>
                <p class="text-base font-medium text-gray-900">${rec.title}</p>
            </div>

            <!-- 이슈 설명 -->
            <div>
                <label class="block text-sm font-semibold text-gray-700 mb-2">이슈 설명</label>
                <div class="p-4 bg-gray-50 border border-gray-200 rounded-lg text-sm text-gray-700 space-y-3">
                    <div>
                        <strong class="text-gray-900">[예외 상세]</strong><br>
                        Type: ${rec.errorType || 'UNKNOWN'}<br>
                        Module: ${rec.stackKey ? rec.stackKey.split(':')[0] : 'N/A'}<br>
                        Function: ${rec.stackKey ? rec.stackKey.split(':')[2] : 'N/A'}
                    </div>
                    ${rec.description ? `<div><strong class="text-gray-900">[추가 정보]</strong><br>${rec.description}</div>` : ''}
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
                <label class="block text-sm font-semibold text-gray-700 mb-3">심각도</label>
                <div class="grid grid-cols-2 gap-2">
                    ${['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map(level => {
                        const isActive = rec.severity === level;
                        const colors = {
                            CRITICAL: 'bg-red-500 text-white',
                            HIGH: 'bg-orange-500 text-white',
                            MEDIUM: 'bg-yellow-500 text-white',
                            LOW: 'bg-gray-400 text-white'
                        };
                        return `<div class="px-3 py-2 ${isActive ? colors[level] : 'bg-gray-100 text-gray-400'} rounded-lg text-center text-sm font-medium">${level}</div>`;
                    }).join('')}
                </div>
                <p class="text-xs text-gray-500 mt-2">심각도 점수: ${rec.severityScore}점</p>
            </div>

            <!-- 담당자 지정 -->
            <div>
                <label class="block text-sm font-semibold text-gray-700 mb-3">담당자 지정</label>
                <select id="assigneeSelect" class="w-full px-4 py-3 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                        onchange="selectedAssigneeId = this.value">
                    ${members.map(member => `
                        <option value="${member.memberId}" ${member.memberId === rec.assigneeId ? 'selected' : ''}>
                            ${member.nickname}
                        </option>
                    `).join('')}
                </select>
                <p class="text-xs text-gray-500 mt-2">
                    💡 이슈 생성 시 선택한 담당자에게 알림이 전송됩니다
                </p>
            </div>

            <!-- 발생 정보 -->
            <div>
                <label class="block text-sm font-semibold text-gray-700 mb-2">발생 정보</label>
                <div class="space-y-2 text-sm">
                    <div class="flex justify-between">
                        <span class="text-gray-600">발생 횟수:</span>
                        <span class="font-medium text-gray-900">${rec.occurrenceCount}회</span>
                    </div>
                    <div class="flex justify-between">
                        <span class="text-gray-600">최초 발생:</span>
                        <span class="text-gray-900">${formatDateTime(rec.firstOccurredAt)}</span>
                    </div>
                    <div class="flex justify-between">
                        <span class="text-gray-600">최근 발생:</span>
                        <span class="text-gray-900">${formatDateTime(rec.lastOccurredAt)}</span>
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
        const body = await callApi(`/api/projects/${currentProjectId}/issue-recommendations/${recommendationId}/approve`, {
            method: 'POST'
        });

        if (body.success) {
            alert('추천이 승인되어 이슈로 생성되었습니다.');
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

    try {
        const body = await callApi(`/api/projects/${currentProjectId}/issue-recommendations/${currentRecommendationId}/approve`, {
            method: 'POST',
            // TODO: 백엔드에서 assigneeId를 받아서 업데이트하도록 수정 필요
            // 현재는 기존 assigneeId 사용
        });

        if (body.success) {
            closeModal('recommendationDetailModal');
            alert('추천이 승인되어 이슈로 생성되었습니다.');
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
        const body = await callApi(`/api/projects/${currentProjectId}/issue-recommendations/${recommendationId}/reject`, {
            method: 'POST'
        });

        if (body.success) {
            closeModal('rejectConfirmModal');
            alert('추천이 거부되었습니다.');
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
        let url = `/api/projects/${currentProjectId}/issues`;
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
        const borderColor = getSeverityBorderColor(issue.severity);
        return `
        <div class="mx-4 my-3 p-4 bg-white border-l-4 ${borderColor} rounded-lg shadow-sm hover:shadow-md transition-shadow cursor-pointer"
             onclick="openIssueDetail(${issue.id})">
            <div class="grid grid-cols-12 gap-4 items-center">
                <div class="col-span-4">
                    <p class="text-sm font-semibold text-gray-900 mb-1">${issue.title}</p>
                    <div class="flex items-center gap-2 text-xs text-gray-500">
                        <span>ID: ${issue.id}</span>
                        <span>•</span>
                        <span>${issue.errorType || 'UNKNOWN'}</span>
                    </div>
                </div>
                <div class="col-span-1 text-center">
                    ${getStatusBadge(issue.status)}
                </div>
                <div class="col-span-2 text-center">
                    ${getSeverityBadge(issue.severity, issue.severityScore)}
                </div>
                <div class="col-span-1 text-center">
                    <span class="text-sm font-medium text-gray-700">${issue.occurrenceCount}회</span>
                </div>
                <div class="col-span-1 text-center">
                    <p class="text-xs text-gray-600">${formatDateTime(issue.lastOccurredAt)}</p>
                </div>
                <div class="col-span-3 text-center flex items-center justify-center gap-2">
                    <button onclick="event.stopPropagation(); openIssueDetail(${issue.id})"
                            class="px-4 py-2 text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 rounded-lg transition-colors">
                        상세보기
                    </button>
                    <button onclick="event.stopPropagation(); openStatusModal(${issue.id}, '${issue.status}')"
                            class="px-4 py-2 text-sm font-medium text-gray-600 hover:bg-gray-100 border border-gray-300 rounded-lg transition-colors">
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
            tab.classList.remove('border-transparent', 'text-gray-500');
            tab.classList.add('border-indigo-600', 'text-indigo-600');
        } else {
            tab.classList.remove('border-indigo-600', 'text-indigo-600');
            tab.classList.add('border-transparent', 'text-gray-500');
        }
    });

    loadIssueList();
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 이슈 상세 조회
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

async function openIssueDetail(issueId) {
    try {
        const body = await callApi(`/api/projects/${currentProjectId}/issues/${issueId}`, {
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

function renderIssueDetail(issue) {
    const container = document.getElementById('issueDetailContent');
    container.innerHTML = `
        <div class="space-y-4">
            <div>
                <label class="block text-xs font-semibold text-gray-500 mb-1">제목</label>
                <p class="text-sm text-gray-900">${issue.title}</p>
            </div>
            <div class="grid grid-cols-2 gap-4">
                <div>
                    <label class="block text-xs font-semibold text-gray-500 mb-1">상태</label>
                    ${getStatusBadge(issue.status)}
                </div>
                <div>
                    <label class="block text-xs font-semibold text-gray-500 mb-1">심각도</label>
                    ${getSeverityBadge(issue.severity, issue.severityScore)}
                </div>
            </div>
            <div>
                <label class="block text-xs font-semibold text-gray-500 mb-1">설명</label>
                <p class="text-sm text-gray-700">${issue.description || '설명 없음'}</p>
            </div>
            <div class="grid grid-cols-2 gap-4">
                <div>
                    <label class="block text-xs font-semibold text-gray-500 mb-1">이슈 유형</label>
                    <p class="text-sm text-gray-700">${issue.issueType}</p>
                </div>
                <div>
                    <label class="block text-xs font-semibold text-gray-500 mb-1">에러 타입</label>
                    <p class="text-sm text-gray-700">${issue.errorType}</p>
                </div>
            </div>
            <div>
                <label class="block text-xs font-semibold text-gray-500 mb-1">스택 키</label>
                <p class="text-xs font-mono text-gray-600 bg-gray-50 p-2 rounded">${issue.stackKey || 'N/A'}</p>
            </div>
            <div class="grid grid-cols-2 gap-4">
                <div>
                    <label class="block text-xs font-semibold text-gray-500 mb-1">발생 횟수</label>
                    <p class="text-sm text-gray-900 font-medium">${issue.occurrenceCount}회</p>
                </div>
                <div>
                    <label class="block text-xs font-semibold text-gray-500 mb-1">우선순위</label>
                    <p class="text-sm text-gray-700">${issue.priority || 'N/A'}</p>
                </div>
            </div>
            <div class="grid grid-cols-2 gap-4">
                <div>
                    <label class="block text-xs font-semibold text-gray-500 mb-1">최초 발생</label>
                    <p class="text-xs text-gray-600">${formatDateTime(issue.firstOccurredAt)}</p>
                </div>
                <div>
                    <label class="block text-xs font-semibold text-gray-500 mb-1">최근 발생</label>
                    <p class="text-xs text-gray-600">${formatDateTime(issue.lastOccurredAt)}</p>
                </div>
            </div>
            ${issue.resolvedAt ? `
            <div>
                <label class="block text-xs font-semibold text-gray-500 mb-1">해결 시각</label>
                <p class="text-xs text-gray-600">${formatDateTime(issue.resolvedAt)}</p>
            </div>
            ` : ''}
            ${issue.resolutionNote ? `
            <div>
                <label class="block text-xs font-semibold text-gray-500 mb-1">해결 노트</label>
                <p class="text-sm text-gray-700">${issue.resolutionNote}</p>
            </div>
            ` : ''}
        </div>
    `;
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
        const body = await callApi(`/api/projects/${currentProjectId}/issues/${issueId}/assignee`, {
            method: 'PUT',
            body: JSON.stringify({ assigneeId })
        });

        if (body.success) {
            closeModal('assignModal');
            loadIssueList();
            alert('담당자가 지정되었습니다.');
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
    document.getElementById('shouldIncludeInPatchNote').checked = false;
    document.getElementById('patchNoteOption').classList.add('hidden');
    document.getElementById('statusError').classList.add('hidden');
    openModal('statusModal');
}

// RESOLVED 선택 시 패치노트 옵션 표시
document.getElementById('newStatus')?.addEventListener('change', (e) => {
    const patchNoteOption = document.getElementById('patchNoteOption');
    if (e.target.value === 'RESOLVED') {
        patchNoteOption.classList.remove('hidden');
    } else {
        patchNoteOption.classList.add('hidden');
    }
});

async function submitStatusChange() {
    const issueId = document.getElementById('statusIssueId').value;
    const status = document.getElementById('newStatus').value;
    const shouldIncludeInPatchNote = document.getElementById('shouldIncludeInPatchNote').checked;
    const errorEl = document.getElementById('statusError');

    errorEl.classList.add('hidden');

    if (!status) {
        errorEl.textContent = '변경할 상태를 선택하세요.';
        errorEl.classList.remove('hidden');
        return;
    }

    try {
        const body = await callApi(`/api/projects/${currentProjectId}/issues/${issueId}/status`, {
            method: 'PUT',
            body: JSON.stringify({ status, shouldIncludeInPatchNote })
        });

        if (body.success) {
            closeModal('statusModal');
            loadIssueList();
            alert('이슈 상태가 변경되었습니다.');
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
        TODO: { label: '대기중', color: 'bg-gray-100 text-gray-700' },
        IN_PROGRESS: { label: '처리중', color: 'bg-blue-100 text-blue-700' },
        RESOLVED: { label: '해결됨', color: 'bg-green-100 text-green-700' }
    };
    const { label, color } = statusMap[status] || { label: status, color: 'bg-gray-100 text-gray-700' };
    return `<span class="inline-block px-2.5 py-1 rounded-full text-xs font-medium ${color}">${label}</span>`;
}

function getSeverityBadge(severity, score) {
    const severityMap = {
        LOW: { label: '낮음', color: 'bg-gray-100 text-gray-700' },
        MEDIUM: { label: '보통', color: 'bg-yellow-100 text-yellow-700' },
        HIGH: { label: '높음', color: 'bg-orange-100 text-orange-700' },
        CRITICAL: { label: '심각', color: 'bg-red-100 text-red-700' }
    };
    const { label, color } = severityMap[severity] || { label: severity, color: 'bg-gray-100 text-gray-700' };
    return `<div class="inline-flex flex-col items-center">
        <span class="inline-block px-2.5 py-1 rounded-full text-xs font-medium ${color}">${label}</span>
        <span class="text-xs text-gray-400 mt-1">${score}점</span>
    </div>`;
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
    if (modalId === 'rejectConfirmModal') {
        document.getElementById('rejectRecommendationId').value = '';
    }
}

function getRecommendationStatusBadge(status) {
    const statusMap = {
        RECOMMENDED: { label: '추천 대기', color: 'bg-blue-100 text-blue-700' },
        TODO: { label: '대기중', color: 'bg-gray-100 text-gray-700' },
        IN_PROGRESS: { label: '처리중', color: 'bg-blue-100 text-blue-700' },
        RESOLVED: { label: '해결됨', color: 'bg-green-100 text-green-700' },
        REJECTED: { label: '거부됨', color: 'bg-red-100 text-red-700' }
    };
    const { label, color } = statusMap[status] || { label: status, color: 'bg-gray-100 text-gray-700' };
    return `<span class="inline-block px-2.5 py-1 rounded-full text-xs font-medium ${color}">${label}</span>`;
}

function getSeverityBorderColor(severity) {
    const borderMap = {
        CRITICAL: 'border-red-500',
        HIGH: 'border-orange-500',
        MEDIUM: 'border-yellow-500',
        LOW: 'border-gray-400'
    };
    return borderMap[severity] || 'border-gray-300';
}

function getQualityBadge(severityScore) {
    if (severityScore >= 80) {
        return '<span class="px-2.5 py-1 bg-green-100 text-green-700 rounded-full text-xs font-medium">높음</span>';
    } else if (severityScore >= 50) {
        return '<span class="px-2.5 py-1 bg-yellow-100 text-yellow-700 rounded-full text-xs font-medium">보통</span>';
    } else {
        return '<span class="px-2.5 py-1 bg-red-100 text-red-700 rounded-full text-xs font-medium">낮음</span>';
    }
}

function showError(message) {
    alert(message);
}
