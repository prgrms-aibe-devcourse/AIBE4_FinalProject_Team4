// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 이슈 상세 분석 페이지
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

let currentIssueId = null;
let currentProjectId = null;
let currentPublicId = null;
let currentTimeRange = '7d';
let trendChart = null;
let currentAssignee = null;

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 초기화
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

document.addEventListener('DOMContentLoaded', () => {
    currentPublicId = document.getElementById('publicId').value;
    currentProjectId = document.getElementById('projectId').value;
    currentIssueId = document.getElementById('issueId').value;

    loadIssueDetail();
});

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 이슈 상세 조회
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

async function loadIssueDetail() {
    try {
        const body = await callApi(`/api/projects/${currentProjectId}/issues/${currentIssueId}`, {
            method: 'GET'
        });

        if (body.success) {
            renderIssueDetail(body.data);
        } else {
            showError('이슈 정보를 불러올 수 없습니다.');
        }
    } catch (err) {
        showError(err.message);
    }
}

function renderIssueDetail(issue) {
    // 헤더 정보
    document.getElementById('issueTitle').textContent = issue.title;
    document.getElementById('statusBadge').innerHTML = getStatusBadge(issue.status);

    // 소스 위치 (stackKey에서 추출)
    if (issue.stackKey) {
        document.getElementById('sourceLocation').textContent = issue.stackKey;
    } else {
        document.getElementById('sourceLocation').textContent = 'N/A';
    }

    // Overview 섹션
    renderSeverityAnalysis(issue);
    renderGroupingInfo(issue);

    // 발생 추이 차트
    renderOccurrenceTrend(issue);

    // 분포 분석 차트
    renderDistributionAnalysis(issue);

    // 로그 상세
    renderLogDetails(issue);

    // 메타 정보
    renderMetaInfo(issue);

    // 관련 이슈
    renderRelatedIssues(issue);

    // 변경 이력
    renderTimeline(issue);
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Overview 섹션
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

function renderSeverityAnalysis(issue) {
    const container = document.getElementById('severityAnalysis');

    const severityColors = {
        CRITICAL: { bg: 'bg-red-500', text: 'text-red-600' },
        HIGH: { bg: 'bg-orange-500', text: 'text-orange-600' },
        MEDIUM: { bg: 'bg-yellow-500', text: 'text-yellow-600' },
        LOW: { bg: 'bg-gray-400', text: 'text-gray-600' }
    };

    const color = severityColors[issue.severity] || severityColors.LOW;

    container.innerHTML = `
        <div class="flex items-center gap-4 mb-4">
            <div class="flex-1">
                <div class="flex items-center justify-between mb-2">
                    <span class="text-sm font-medium text-gray-600">${issue.severity}</span>
                    <span class="text-2xl font-bold ${color.text}">${issue.severityScore}점</span>
                </div>
                <div class="w-full bg-gray-200 rounded-full h-3">
                    <div class="${color.bg} h-3 rounded-full" style="width: ${issue.severityScore}%"></div>
                </div>
            </div>
        </div>
        <div class="grid grid-cols-2 gap-3 text-sm">
            <div class="p-3 bg-gray-50 rounded-lg">
                <p class="text-xs text-gray-500 mb-1">발생 대비</p>
                <p class="font-semibold text-gray-900">+${Math.floor(Math.random() * 50)}%</p>
            </div>
            <div class="p-3 bg-gray-50 rounded-lg">
                <p class="text-xs text-gray-500 mb-1">영향 플레이어</p>
                <p class="font-semibold text-gray-900">${Math.floor(issue.occurrenceCount * 0.7)}명</p>
            </div>
        </div>
    `;
}

function renderGroupingInfo(issue) {
    const container = document.getElementById('groupingInfo');

    container.innerHTML = `
        <div class="space-y-3 text-sm">
            <div>
                <label class="block text-xs font-semibold text-gray-500 mb-1">에러 분류</label>
                <p class="text-gray-900">Fingerprint</p>
                <p class="text-xs text-gray-500 font-mono mt-1">${issue.fingerprint ? issue.fingerprint.substring(0, 16) + '...' : 'N/A'}</p>
            </div>
            <div>
                <label class="block text-xs font-semibold text-gray-500 mb-1">에러 타입</label>
                <p class="text-gray-900">${issue.errorType || 'UNKNOWN'}</p>
            </div>
            <div>
                <label class="block text-xs font-semibold text-gray-500 mb-1">스택트레이스</label>
                <p class="text-gray-600">${issue.stackKey ? '분석 완료' : 'N/A'}</p>
            </div>
            ${issue.similarityResults && issue.similarityResults.length > 0 ? `
            <div>
                <label class="block text-xs font-semibold text-gray-500 mb-1">유사 이슈</label>
                <p class="text-gray-900">${issue.similarityResults.length}건 발견</p>
            </div>
            ` : ''}
        </div>
    `;
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 발생 추이 차트
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

function renderOccurrenceTrend(issue) {
    // 임시 데이터 생성 (Phase 3에서 실제 API 연동)
    const labels = [];
    const data = [];
    const now = new Date();

    for (let i = 6; i >= 0; i--) {
        const date = new Date(now);
        date.setDate(date.getDate() - i);
        labels.push(date.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' }));
        data.push(Math.floor(Math.random() * issue.occurrenceCount / 7) + 1);
    }

    const ctx = document.getElementById('trendChart').getContext('2d');

    if (trendChart) {
        trendChart.destroy();
    }

    trendChart = new Chart(ctx, {
        type: 'line',
        data: {
            labels: labels,
            datasets: [{
                label: '발생 횟수',
                data: data,
                borderColor: 'rgb(99, 102, 241)',
                backgroundColor: 'rgba(99, 102, 241, 0.1)',
                tension: 0.4,
                fill: true
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    display: false
                },
                tooltip: {
                    mode: 'index',
                    intersect: false
                }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    ticks: {
                        precision: 0
                    }
                }
            }
        }
    });
}

function setTimeRange(range) {
    currentTimeRange = range;

    // 버튼 스타일 업데이트
    document.querySelectorAll('.time-range-btn').forEach(btn => {
        if (btn.dataset.range === range) {
            btn.classList.add('border-indigo-600', 'text-indigo-600');
            btn.classList.remove('border-transparent', 'text-gray-500');
        } else {
            btn.classList.remove('border-indigo-600', 'text-indigo-600');
            btn.classList.add('border-transparent', 'text-gray-500');
        }
    });

    // 차트 재렌더링 (실제 API에서 새 데이터 가져오기)
    // TODO: Phase 3에서 구현
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 분포 분석 차트
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

async function renderDistributionAnalysis(issue) {
    const container = document.getElementById('distributionAnalysis');

    try {
        const body = await callApi(`/api/projects/${currentProjectId}/issues/${currentIssueId}/distribution`, {
            method: 'GET'
        });

        if (!body.success || !body.data) {
            container.innerHTML = '<p class="text-sm text-gray-400">분포 분석 데이터를 불러올 수 없습니다.</p>';
            return;
        }

        const distributionData = body.data;

        // 데이터가 비어있는 경우
        if ((!distributionData.os || distributionData.os.length === 0) &&
            (!distributionData.version || distributionData.version.length === 0) &&
            (!distributionData.device || distributionData.device.length === 0)) {
            container.innerHTML = '<p class="text-sm text-gray-400">분포 분석 데이터가 없습니다.</p>';
            return;
        }

        container.innerHTML = `
            <div class="space-y-4">
                <!-- OS 분포 -->
                ${distributionData.os && distributionData.os.length > 0 ? `
                <div>
                    <h3 class="text-sm font-semibold text-gray-700 mb-3">운영체제</h3>
                    <div class="space-y-2">
                        ${distributionData.os.map(item => `
                            <div>
                                <div class="flex items-center justify-between text-sm mb-1">
                                    <span class="text-gray-600">${item.name}</span>
                                    <span class="font-medium text-gray-900">${item.count}회 (${item.percentage}%)</span>
                                </div>
                                <div class="w-full bg-gray-200 rounded-full h-2">
                                    <div class="bg-indigo-600 h-2 rounded-full" style="width: ${item.percentage}%"></div>
                                </div>
                            </div>
                        `).join('')}
                    </div>
                </div>
                ` : ''}

                <!-- 버전 분포 -->
                ${distributionData.version && distributionData.version.length > 0 ? `
                <div>
                    <h3 class="text-sm font-semibold text-gray-700 mb-3">앱 버전</h3>
                    <div class="space-y-2">
                        ${distributionData.version.map(item => `
                            <div>
                                <div class="flex items-center justify-between text-sm mb-1">
                                    <span class="text-gray-600">${item.name}</span>
                                    <span class="font-medium text-gray-900">${item.count}회 (${item.percentage}%)</span>
                                </div>
                                <div class="w-full bg-gray-200 rounded-full h-2">
                                    <div class="bg-green-600 h-2 rounded-full" style="width: ${item.percentage}%"></div>
                                </div>
                            </div>
                        `).join('')}
                    </div>
                </div>
                ` : ''}

                <!-- 디바이스 분포 -->
                ${distributionData.device && distributionData.device.length > 0 ? `
                <div>
                    <h3 class="text-sm font-semibold text-gray-700 mb-3">디바이스</h3>
                    <div class="space-y-2">
                        ${distributionData.device.map(item => `
                            <div>
                                <div class="flex items-center justify-between text-sm mb-1">
                                    <span class="text-gray-600">${item.name}</span>
                                    <span class="font-medium text-gray-900">${item.count}회 (${item.percentage}%)</span>
                                </div>
                                <div class="w-full bg-gray-200 rounded-full h-2">
                                    <div class="bg-orange-600 h-2 rounded-full" style="width: ${item.percentage}%"></div>
                                </div>
                            </div>
                        `).join('')}
                    </div>
                </div>
                ` : ''}
            </div>
        `;
    } catch (err) {
        container.innerHTML = `<p class="text-sm text-red-500">분포 분석 중 오류 발생: ${err.message}</p>`;
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 로그 상세
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

function renderLogDetails(issue) {
    const container = document.getElementById('logDetails');

    // 스택트레이스 표시
    let stackTrace = issue.stackKey || 'N/A';

    container.innerHTML = `
        <div class="space-y-4">
            <div>
                <label class="block text-sm font-semibold text-gray-700 mb-2">스택트레이스</label>
                <pre class="p-4 bg-gray-900 text-gray-100 rounded-lg text-xs font-mono overflow-x-auto">${stackTrace}</pre>
            </div>
            ${issue.description ? `
            <div>
                <label class="block text-sm font-semibold text-gray-700 mb-2">설명</label>
                <p class="text-sm text-gray-700">${issue.description}</p>
            </div>
            ` : ''}
            <div>
                <label class="block text-sm font-semibold text-gray-700 mb-2">전체 아카이브</label>
                <details class="cursor-pointer">
                    <summary class="text-sm text-indigo-600 hover:text-indigo-700">상세 로그 펼치기</summary>
                    <pre class="mt-2 p-4 bg-gray-50 rounded-lg text-xs text-gray-700 overflow-x-auto">${issue.title}</pre>
                </details>
            </div>
        </div>
    `;
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 메타 정보
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

function renderMetaInfo(issue) {
    // 담당자 정보 저장
    currentAssignee = issue.assignee || null;

    // 담당자 정보 렌더링
    const assigneeInfo = document.getElementById('assigneeInfo');
    if (currentAssignee) {
        const profileImage = currentAssignee.profileImageUrl
            ? `<img src="${currentAssignee.profileImageUrl}" alt="${currentAssignee.nickname}" class="w-8 h-8 rounded-full object-cover">`
            : `<div class="w-8 h-8 bg-indigo-100 rounded-full flex items-center justify-center">
                   <span class="text-indigo-600 text-xs font-medium">${currentAssignee.nickname.charAt(0)}</span>
               </div>`;

        assigneeInfo.innerHTML = `
            <div class="flex items-center gap-2">
                ${profileImage}
                <span class="text-gray-900 text-sm font-medium">${currentAssignee.nickname}</span>
            </div>
        `;
    } else {
        assigneeInfo.innerHTML = `
            <div class="flex items-center gap-2 text-gray-400 text-xs">
                <div class="w-8 h-8 bg-gray-200 rounded-full flex items-center justify-center text-gray-500">
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/>
                    </svg>
                </div>
                <span>담당자 없음</span>
            </div>
        `;
    }

    document.getElementById('priorityInfo').textContent = issue.priority || 'N/A';
    document.getElementById('occurrenceCount').textContent = `${issue.occurrenceCount}회`;
    document.getElementById('firstOccurred').textContent = formatDateTime(issue.firstOccurredAt);
    document.getElementById('lastOccurred').textContent = formatDateTime(issue.lastOccurredAt);
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 관련 이슈
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

function renderRelatedIssues(issue) {
    const container = document.getElementById('relatedIssues');

    if (issue.similarityResults && issue.similarityResults.length > 0) {
        container.innerHTML = issue.similarityResults.map(result => `
            <a href="/projects/${currentPublicId}/issues/${result.matchedIssueId}/analysis"
               class="block p-3 bg-gray-50 hover:bg-gray-100 rounded-lg transition-colors">
                <div class="flex items-center justify-between mb-1">
                    <span class="text-xs font-medium text-gray-900">#${result.matchedIssueId}</span>
                    <span class="text-xs text-indigo-600">${result.similarity.toFixed(0)}%</span>
                </div>
                <p class="text-xs text-gray-600 truncate">${result.matchedIssueTitle}</p>
            </a>
        `).join('');
    } else {
        container.innerHTML = '<p class="text-xs text-gray-400">유사한 이슈가 없습니다.</p>';
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 변경 이력
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

function renderTimeline(issue) {
    const container = document.getElementById('timeline');

    // 임시 타임라인 데이터 (Phase 3에서 실제 이력 API 연동)
    const events = [
        {
            time: issue.createdAt,
            user: 'System',
            action: '이슈 생성됨'
        }
    ];

    if (issue.updatedAt && issue.updatedAt !== issue.createdAt) {
        events.push({
            time: issue.updatedAt,
            user: 'Unknown',
            action: '이슈 업데이트됨'
        });
    }

    container.innerHTML = events.map(event => `
        <div class="flex gap-3">
            <div class="flex-shrink-0 w-8 h-8 bg-indigo-100 rounded-full flex items-center justify-center">
                <svg class="w-4 h-4 text-indigo-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/>
                </svg>
            </div>
            <div class="flex-1">
                <p class="text-sm font-medium text-gray-900">${event.user}</p>
                <p class="text-xs text-gray-600">${event.action}</p>
                <p class="text-xs text-gray-400 mt-1">${formatTimeAgo(event.time)}</p>
            </div>
        </div>
    `).join('');
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 담당자 관리
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

async function enterAssigneeEditMode() {
    try {
        // 프로젝트 멤버 목록 조회
        const body = await callApi(`/api/projects/${currentPublicId}/members`, {
            method: 'GET'
        });

        if (!body.success) {
            showError('프로젝트 멤버 목록을 불러올 수 없습니다.');
            return;
        }

        // 드롭다운 옵션 렌더링
        const select = document.getElementById('assigneeSelect');
        select.innerHTML = '<option value="">선택하세요</option>';

        body.data.forEach(member => {
            const option = document.createElement('option');
            option.value = member.memberId;
            option.textContent = member.nickname;

            // 현재 담당자라면 선택 상태로
            if (currentAssignee && currentAssignee.memberId === member.memberId) {
                option.selected = true;
            }

            select.appendChild(option);
        });

        // 표시 모드 숨기고 편집 모드 표시
        document.getElementById('assigneeDisplay').classList.add('hidden');
        document.getElementById('assigneeEdit').classList.remove('hidden');
        document.getElementById('assigneeEditError').classList.add('hidden');
    } catch (err) {
        showError(err.message);
    }
}

function cancelAssigneeEdit() {
    // 편집 모드 숨기고 표시 모드 표시
    document.getElementById('assigneeEdit').classList.add('hidden');
    document.getElementById('assigneeDisplay').classList.remove('hidden');
    document.getElementById('assigneeEditError').classList.add('hidden');
}

async function saveAssigneeChange() {
    const selectedAssigneeId = document.getElementById('assigneeSelect').value;
    const errorEl = document.getElementById('assigneeEditError');

    errorEl.classList.add('hidden');

    if (!selectedAssigneeId) {
        errorEl.textContent = '담당자를 선택하세요.';
        errorEl.classList.remove('hidden');
        return;
    }

    try {
        const body = await callApi(`/api/projects/${currentProjectId}/issues/${currentIssueId}/assignee`, {
            method: 'PUT',
            body: JSON.stringify({ assigneeId: selectedAssigneeId })
        });

        if (body.success) {
            loadIssueDetail(); // 새로고침
        } else {
            errorEl.textContent = body.error?.message || '담당자 변경에 실패했습니다.';
            errorEl.classList.remove('hidden');
        }
    } catch (err) {
        errorEl.textContent = err.message;
        errorEl.classList.remove('hidden');
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 댓글 기능
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

function submitComment() {
    const commentText = document.getElementById('newComment').value.trim();

    if (!commentText) {
        alert('댓글 내용을 입력하세요.');
        return;
    }

    // TODO: Phase 3에서 댓글 API 연동
    alert('댓글 기능은 곧 추가될 예정입니다.');
    document.getElementById('newComment').value = '';
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 상태 변경
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

function openStatusModal() {
    document.getElementById('newStatus').value = '';
    document.getElementById('shouldIncludeInPatchNote').checked = false;
    document.getElementById('patchNoteOption').classList.add('hidden');
    document.getElementById('statusError').classList.add('hidden');
    openModal('statusModal');
}

document.getElementById('newStatus')?.addEventListener('change', (e) => {
    const patchNoteOption = document.getElementById('patchNoteOption');
    if (e.target.value === 'RESOLVED') {
        patchNoteOption.classList.remove('hidden');
    } else {
        patchNoteOption.classList.add('hidden');
    }
});

async function submitStatusChange() {
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
        const body = await callApi(`/api/projects/${currentProjectId}/issues/${currentIssueId}/status`, {
            method: 'PUT',
            body: JSON.stringify({ status, shouldIncludeInPatchNote })
        });

        if (body.success) {
            closeModal('statusModal');
            alert('이슈 상태가 변경되었습니다.');
            loadIssueDetail(); // 새로고침
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
        RECOMMENDED: { label: '추천 대기', color: 'bg-blue-100 text-blue-700' },
        TODO: { label: '대기중', color: 'bg-gray-100 text-gray-700' },
        IN_PROGRESS: { label: '처리중', color: 'bg-blue-100 text-blue-700' },
        RESOLVED: { label: '해결됨', color: 'bg-green-100 text-green-700' }
    };
    const { label, color } = statusMap[status] || { label: status, color: 'bg-gray-100 text-gray-700' };
    return `<span class="inline-block px-2.5 py-1 rounded-full text-xs font-medium ${color}">${label}</span>`;
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

function formatTimeAgo(dateTimeStr) {
    if (!dateTimeStr) return 'N/A';

    const now = new Date();
    const date = new Date(dateTimeStr);
    const diffMs = now - date;
    const diffMins = Math.floor(diffMs / 60000);

    if (diffMins < 1) return '방금 전';
    if (diffMins < 60) return `${diffMins}분 전`;

    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `${diffHours}시간 전`;

    const diffDays = Math.floor(diffHours / 24);
    if (diffDays < 7) return `${diffDays}일 전`;

    return formatDateTime(dateTimeStr);
}

function shareIssue() {
    const url = window.location.href;

    if (navigator.clipboard) {
        navigator.clipboard.writeText(url).then(() => {
            alert('이슈 URL이 복사되었습니다.');
        }).catch(() => {
            prompt('이슈 URL:', url);
        });
    } else {
        prompt('이슈 URL:', url);
    }
}

function openModal(modalId) {
    document.getElementById(modalId).classList.remove('hidden');
}

function closeModal(modalId) {
    document.getElementById(modalId).classList.add('hidden');
}

function showError(message) {
    alert(message);
}
