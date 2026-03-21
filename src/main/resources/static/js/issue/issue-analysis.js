// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 이슈 상세 분석 페이지
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

let currentIssueId = null;
let currentProjectId = null;
let currentPublicId = null;
let currentTimeRange = '7d';
let trendChart = null;
let currentAssignee = null;
let projectMembers = []; // 프로젝트 멤버 캐시
let currentIssue = null; // 현재 이슈 정보 캐시

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 초기화
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

document.addEventListener('DOMContentLoaded', () => {
    currentPublicId = document.getElementById('publicId').value;
    currentProjectId = document.getElementById('projectId').value;
    currentIssueId = document.getElementById('issueId').value;

    // Breadcrumb 링크 업데이트 (Referrer에서 탭 정보 추출)
    updateBreadcrumbLink();

    loadIssueDetail();
});

/**
 * Breadcrumb 링크에 이전 페이지의 탭 상태 추가
 */
function updateBreadcrumbLink() {
    // Referrer URL에서 tab 파라미터 추출
    if (document.referrer) {
        try {
            const referrerUrl = new URL(document.referrer);
            const tabParam = referrerUrl.searchParams.get('tab');

            if (tabParam === 'issues') {
                const breadcrumbLink = document.getElementById('breadcrumbBackLink');
                if (breadcrumbLink) {
                    const href = breadcrumbLink.getAttribute('href');
                    breadcrumbLink.setAttribute('href', href + '?tab=issues');
                }
            }
        } catch (e) {
            // Referrer URL 파싱 실패 시 무시
            console.debug('[updateBreadcrumbLink] Referrer URL 파싱 실패:', e);
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 이슈 상세 조회
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

async function loadIssueDetail() {
    try {
        // 이슈 상세 정보와 프로젝트 멤버 목록을 병렬로 로드
        const [issueBody, membersBody] = await Promise.all([
            callApi(`/api/projects/${currentPublicId}/issues/${currentIssueId}`, {
                method: 'GET'
            }),
            callApi(`/api/projects/${currentPublicId}/members`, {
                method: 'GET'
            })
        ]);

        if (issueBody.success) {
            // 프로젝트 멤버 캐시 저장
            if (membersBody.success) {
                projectMembers = membersBody.data;
            }

            renderIssueDetail(issueBody.data);
        } else {
            showError('이슈 정보를 불러올 수 없습니다.');
        }
    } catch (err) {
        showError(err.message);
    }
}

async function renderIssueDetail(issue) {
    // 현재 이슈 캐시에 저장
    currentIssue = issue;

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
    await renderDistributionAnalysis(issue);

    // 발생 맥락
    await renderIssueContext(issue);

    // 근본 원인 분석
    await renderRootCauseAnalysis(issue);

    // 로그 상세
    renderLogDetails(issue);

    // 영향받은 플레이어
    await renderAffectedPlayers(issue);

    // 메타 정보
    renderMetaInfo(issue);

    // 관련 이슈
    renderRelatedIssues(issue);

    // 변경 이력
    await renderTimeline(issue);
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Overview 섹션
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

function renderSeverityAnalysis(issue) {
    const container = document.getElementById('severityAnalysis');

    const severityColors = {
        CRITICAL: { bg: 'bg-docu-danger', text: 'text-docu-danger' },
        HIGH: { bg: 'bg-docu-warning', text: 'text-docu-warning' },
        MEDIUM: { bg: 'bg-docu-warning', text: 'text-docu-warning' },
        LOW: { bg: 'bg-surface-sub', text: 'text-docu-secondary' }
    };

    const color = severityColors[issue.severity] || severityColors.LOW;

    container.innerHTML = `
        <div class="flex items-center gap-4 mb-4">
            <div class="flex-1">
                <div class="flex items-center justify-between mb-2">
                    <span class="text-sm font-medium text-docu-secondary">${issue.severity}</span>
                    <span class="text-2xl font-bold ${color.text}">${issue.severityScore}점</span>
                </div>
                <div class="w-full bg-surface-sub rounded-full h-3">
                    <div class="${color.bg} h-3 rounded-full" style="width: ${issue.severityScore}%"></div>
                </div>
            </div>
        </div>
        <div class="grid grid-cols-2 gap-3 text-sm">
            <div class="p-3 bg-surface-secondary rounded-lg">
                <p class="text-xs text-docu-secondary mb-1">발생 대비</p>
                <p class="font-semibold text-docu-ink">+${Math.floor(Math.random() * 50)}%</p>
            </div>
            <div class="p-3 bg-surface-secondary rounded-lg">
                <p class="text-xs text-docu-secondary mb-1">영향 플레이어</p>
                <p class="font-semibold text-docu-ink">${Math.floor(issue.occurrenceCount * 0.7)}명</p>
            </div>
        </div>
    `;
}

function renderGroupingInfo(issue) {
    const container = document.getElementById('groupingInfo');

    container.innerHTML = `
        <div class="space-y-3 text-sm">
            <div>
                <label class="block text-xs font-semibold text-docu-secondary mb-1">오류 발생 위치</label>
                <p class="text-docu-ink font-mono text-xs">${issue.stackKey || 'N/A'}</p>
            </div>
            <div>
                <label class="block text-xs font-semibold text-docu-secondary mb-1">에러 타입</label>
                <p class="text-docu-ink">${issue.errorType || 'UNKNOWN'}</p>
            </div>
            <div>
                <label class="block text-xs font-semibold text-docu-secondary mb-1">유사 이슈</label>
                <p class="text-docu-ink">${issue.similarityResults && issue.similarityResults.length > 0 ? `${issue.similarityResults.length}건 발견` : '0건'}</p>
            </div>
        </div>
    `;
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 발생 추이 차트
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

async function renderOccurrenceTrend(issue) {
    try {
        // 실제 API에서 데이터 가져오기
        const body = await callApi(
            `/api/projects/${currentPublicId}/issues/${currentIssueId}/trend?days=${getDaysFromRange(currentTimeRange)}`,
            { method: 'GET' }
        );

        if (!body.success) {
            console.error('발생 추이 데이터를 불러올 수 없습니다.');
            return;
        }

        const trendData = body.data;

        // 라벨과 데이터 추출
        const labels = trendData.map(item => {
            const date = new Date(item.date);
            return date.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' });
        });

        const data = trendData.map(item => item.count);

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
                    borderColor: '#4A9EE8',
                    backgroundColor: 'rgba(74, 158, 232, 0.1)',
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
    } catch (err) {
        console.error('발생 추이 차트 로드 중 오류:', err);
    }
}

async function setTimeRange(range) {
    currentTimeRange = range;

    // 버튼 스타일 업데이트
    document.querySelectorAll('.time-range-btn').forEach(btn => {
        if (btn.dataset.range === range) {
            btn.classList.add('border-docu-primary', 'text-docu-primary');
            btn.classList.remove('border-transparent', 'text-docu-secondary');
        } else {
            btn.classList.remove('border-docu-primary', 'text-docu-primary');
            btn.classList.add('border-transparent', 'text-docu-secondary');
        }
    });

    // 차트 재렌더링 (실제 API에서 새 데이터 가져오기)
    try {
        const body = await callApi(`/api/projects/${currentPublicId}/issues/${currentIssueId}`, {
            method: 'GET'
        });

        if (body.success) {
            await renderOccurrenceTrend(body.data);
        }
    } catch (err) {
        console.error('차트 업데이트 중 오류:', err);
    }
}

function getDaysFromRange(range) {
    const rangeMap = {
        '7d': 7,
        '14d': 14,
        '30d': 30
    };
    return rangeMap[range] || 7;
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 분포 분석 차트
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

async function renderDistributionAnalysis(issue) {
    const container = document.getElementById('distributionAnalysis');

    try {
        const body = await callApi(`/api/projects/${currentPublicId}/issues/${currentIssueId}/distribution`, {
            method: 'GET'
        });

        if (!body.success || !body.data) {
            container.innerHTML = '<p class="text-sm text-docu-tertiary">분포 분석 데이터를 불러올 수 없습니다.</p>';
            return;
        }

        const distributionData = body.data;

        // 데이터가 비어있는 경우
        if ((!distributionData.os || distributionData.os.length === 0) &&
            (!distributionData.version || distributionData.version.length === 0) &&
            (!distributionData.device || distributionData.device.length === 0)) {
            container.innerHTML = '<p class="text-sm text-docu-tertiary">분포 분석 데이터가 없습니다.</p>';
            return;
        }

        container.innerHTML = `
            <div class="space-y-4">
                <!-- OS 분포 -->
                ${distributionData.os && distributionData.os.length > 0 ? `
                <div>
                    <h3 class="text-sm font-semibold text-docu-ink mb-3">운영체제</h3>
                    <div class="space-y-2">
                        ${distributionData.os.map(item => `
                            <div>
                                <div class="flex items-center justify-between text-sm mb-1">
                                    <span class="text-docu-secondary">${item.name}</span>
                                    <span class="font-medium text-docu-ink">${item.count}회 (${item.percentage}%)</span>
                                </div>
                                <div class="w-full bg-surface-sub rounded-full h-2">
                                    <div class="bg-docu-primary h-2 rounded-full" style="width: ${item.percentage}%"></div>
                                </div>
                            </div>
                        `).join('')}
                    </div>
                </div>
                ` : ''}

                <!-- 버전 분포 -->
                ${distributionData.version && distributionData.version.length > 0 ? `
                <div>
                    <h3 class="text-sm font-semibold text-docu-ink mb-3">앱 버전</h3>
                    <div class="space-y-2">
                        ${distributionData.version.map(item => `
                            <div>
                                <div class="flex items-center justify-between text-sm mb-1">
                                    <span class="text-docu-secondary">${item.name}</span>
                                    <span class="font-medium text-docu-ink">${item.count}회 (${item.percentage}%)</span>
                                </div>
                                <div class="w-full bg-surface-sub rounded-full h-2">
                                    <div class="bg-docu-success h-2 rounded-full" style="width: ${item.percentage}%"></div>
                                </div>
                            </div>
                        `).join('')}
                    </div>
                </div>
                ` : ''}

                <!-- 디바이스 분포 -->
                ${distributionData.device && distributionData.device.length > 0 ? `
                <div>
                    <h3 class="text-sm font-semibold text-docu-ink mb-3">디바이스</h3>
                    <div class="space-y-2">
                        ${distributionData.device.map(item => `
                            <div>
                                <div class="flex items-center justify-between text-sm mb-1">
                                    <span class="text-docu-secondary">${item.name}</span>
                                    <span class="font-medium text-docu-ink">${item.count}회 (${item.percentage}%)</span>
                                </div>
                                <div class="w-full bg-surface-sub rounded-full h-2">
                                    <div class="bg-docu-warning h-2 rounded-full" style="width: ${item.percentage}%"></div>
                                </div>
                            </div>
                        `).join('')}
                    </div>
                </div>
                ` : ''}
            </div>
        `;
    } catch (err) {
        container.innerHTML = `<p class="text-sm text-docu-danger">분포 분석 중 오류 발생: ${err.message}</p>`;
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 근본 원인 분석
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

async function renderRootCauseAnalysis(issue) {
    const container = document.getElementById('rootCauseAnalysis');

    try {
        const body = await callApi(`/api/projects/${currentPublicId}/issues/${currentIssueId}/root-cause`, {
            method: 'GET'
        });

        if (!body.success || !body.data) {
            container.innerHTML = '<p class="text-sm text-docu-tertiary">근본 원인 분석 데이터를 불러올 수 없습니다.</p>';
            return;
        }

        const rca = body.data;

        // 패턴 아이콘 매핑
        const patternIcons = {
            'TIME': '⏰',
            'DAY': '📅',
            'USER': '👤',
            'ENVIRONMENT': '💻'
        };

        container.innerHTML = `
            <div class="space-y-6">
                <!-- 에러 타입 -->
                <div class="p-4 bg-surface-secondary border border-divider rounded-lg">
                    <div class="flex items-center gap-2 mb-2">
                        <span class="text-2xl">⚠️</span>
                        <div>
                            <h3 class="text-sm font-semibold text-docu-ink">${rca.errorType}</h3>
                            <p class="text-xs text-docu-danger">${rca.errorDescription}</p>
                        </div>
                    </div>
                    ${rca.hotspot !== 'N/A' ? `
                        <div class="mt-2 flex items-center gap-2 text-xs text-docu-ink">
                            <span class="font-mono bg-surface-secondary px-2 py-1 rounded">${rca.hotspot}</span>
                        </div>
                    ` : ''}
                </div>

                <!-- 발견된 패턴 -->
                ${rca.patterns && rca.patterns.length > 0 ? `
                <div>
                    <h3 class="text-sm font-semibold text-docu-ink mb-3">📊 발생 패턴</h3>
                    <div class="space-y-2">
                        ${rca.patterns.map(pattern => `
                            <div class="flex items-start gap-2 p-3 bg-surface-secondary border border-divider rounded-lg">
                                <span class="text-lg">${patternIcons[pattern.type] || '📌'}</span>
                                <p class="text-sm text-docu-ink flex-1">${pattern.description}</p>
                            </div>
                        `).join('')}
                    </div>
                </div>
                ` : ''}

                <!-- 가능한 원인 -->
                <div>
                    <h3 class="text-sm font-semibold text-docu-ink mb-3">🔍 가능한 원인</h3>
                    <ul class="space-y-2">
                        ${rca.possibleCauses.map((cause, index) => `
                            <li class="flex items-start gap-2">
                                <span class="flex-shrink-0 w-5 h-5 bg-surface-secondary text-docu-warning rounded-full flex items-center justify-center text-xs font-medium">
                                    ${index + 1}
                                </span>
                                <span class="flex-1 text-sm text-docu-ink">${cause}</span>
                            </li>
                        `).join('')}
                    </ul>
                </div>

                <!-- 권장 해결책 -->
                <div>
                    <h3 class="text-sm font-semibold text-docu-ink mb-3">💡 권장 해결책</h3>
                    <ul class="space-y-2">
                        ${rca.solutions.map((solution, index) => `
                            <li class="flex items-start gap-2">
                                <span class="flex-shrink-0 w-5 h-5 bg-docu-success-light text-docu-success rounded-full flex items-center justify-center text-xs font-medium">
                                    ${index + 1}
                                </span>
                                <span class="flex-1 text-sm text-docu-ink bg-surface-secondary p-2 rounded">${solution}</span>
                            </li>
                        `).join('')}
                    </ul>
                </div>

                <!-- 유사 해결 사례 -->
                ${rca.similarResolution ? `
                <div class="p-4 bg-surface-secondary border border-divider rounded-lg">
                    <h3 class="text-sm font-semibold text-docu-ink mb-2">✅ 유사 해결 사례</h3>
                    <div class="text-sm text-docu-ink">
                        <p class="font-medium">
                            <a href="/projects/${currentPublicId}/issues/${rca.similarResolution.issueId}/analysis"
                               class="text-docu-success hover:text-docu-ink underline">
                                #${rca.similarResolution.issueId} ${rca.similarResolution.issueTitle}
                            </a>
                        </p>
                        <p class="mt-1 text-xs">"${rca.similarResolution.resolutionNote}"</p>
                    </div>
                </div>
                ` : ''}
            </div>
        `;
    } catch (err) {
        container.innerHTML = `<p class="text-sm text-docu-danger">근본 원인 분석 중 오류 발생: ${err.message}</p>`;
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 발생 맥락
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

async function renderIssueContext(issue) {
    const container = document.getElementById('issueContext');

    try {
        const body = await callApi(`/api/projects/${currentPublicId}/issues/${currentIssueId}/context`, {
            method: 'GET'
        });

        if (!body.success || !body.data) {
            container.innerHTML = '<p class="text-sm text-docu-tertiary">맥락 정보를 불러올 수 없습니다.</p>';
            return;
        }

        const ctx = body.data;
        const { mostFrequentEnvironment, gameStateExample, commonAttributes } = ctx;

        container.innerHTML = `
            <div class="space-y-6">
                <!-- 가장 빈번한 환경 -->
                ${mostFrequentEnvironment ? `
                <div>
                    <h3 class="text-sm font-semibold text-docu-ink mb-3">💻 가장 빈번한 환경</h3>
                    <div class="p-4 bg-surface-secondary border border-divider rounded-lg">
                        <div class="grid grid-cols-2 gap-4 text-sm">
                            <div>
                                <span class="text-docu-secondary">OS:</span>
                                <span class="ml-2 font-medium text-docu-ink">${mostFrequentEnvironment.os}</span>
                            </div>
                            <div>
                                <span class="text-docu-secondary">디바이스:</span>
                                <span class="ml-2 font-medium text-docu-ink">${mostFrequentEnvironment.device}</span>
                            </div>
                            <div>
                                <span class="text-docu-secondary">앱 버전:</span>
                                <span class="ml-2 font-medium text-docu-ink">${mostFrequentEnvironment.appVersion}</span>
                            </div>
                            <div>
                                <span class="text-docu-secondary">발생 비율:</span>
                                <span class="ml-2 font-medium text-docu-primary">${mostFrequentEnvironment.percentage}%</span>
                            </div>
                        </div>
                    </div>
                </div>
                ` : ''}

                <!-- 게임 상태 예시 -->
                ${gameStateExample && (gameStateExample.playerLevel || gameStateExample.currentStage || gameStateExample.currency) ? `
                <div>
                    <h3 class="text-sm font-semibold text-docu-ink mb-3">🎮 게임 상태 예시</h3>
                    <div class="p-4 bg-surface-secondary border border-divider rounded-lg">
                        <div class="grid grid-cols-2 gap-3 text-sm">
                            ${gameStateExample.playerLevel ? `
                            <div>
                                <span class="text-docu-secondary">플레이어 레벨:</span>
                                <span class="ml-2 font-medium text-docu-ink">${gameStateExample.playerLevel}</span>
                            </div>
                            ` : ''}
                            ${gameStateExample.currentStage ? `
                            <div>
                                <span class="text-docu-secondary">현재 스테이지:</span>
                                <span class="ml-2 font-medium text-docu-ink">${gameStateExample.currentStage}</span>
                            </div>
                            ` : ''}
                            ${gameStateExample.currency ? `
                            <div>
                                <span class="text-docu-secondary">보유 재화:</span>
                                <span class="ml-2 font-medium text-docu-ink">${gameStateExample.currency}</span>
                            </div>
                            ` : ''}
                        </div>
                        ${gameStateExample.additionalState && Object.keys(gameStateExample.additionalState).length > 0 ? `
                        <div class="mt-3 pt-3 border-t border-divider">
                            <p class="text-xs font-semibold text-docu-secondary mb-2">기타 상태:</p>
                            <div class="grid grid-cols-2 gap-2 text-xs">
                                ${Object.entries(gameStateExample.additionalState).slice(0, 6).map(([key, value]) => `
                                    <div class="text-docu-ink">
                                        <span class="text-docu-secondary">${key}:</span>
                                        <span class="ml-1">${JSON.stringify(value)}</span>
                                    </div>
                                `).join('')}
                            </div>
                        </div>
                        ` : ''}
                    </div>
                </div>
                ` : ''}

                <!-- 공통 속성 -->
                ${commonAttributes && Object.keys(commonAttributes).length > 0 ? `
                <div>
                    <h3 class="text-sm font-semibold text-docu-ink mb-3">🔗 공통 속성 (반복 패턴)</h3>
                    <div class="p-4 bg-surface-secondary border border-divider rounded-lg">
                        <div class="grid grid-cols-2 gap-2 text-sm">
                            ${Object.entries(commonAttributes).map(([key, value]) => `
                                <div class="flex items-start gap-2">
                                    <span class="text-docu-primary">▪</span>
                                    <div class="flex-1">
                                        <span class="font-medium text-docu-ink">${key}:</span>
                                        <span class="ml-1 text-docu-secondary">${value}</span>
                                    </div>
                                </div>
                            `).join('')}
                        </div>
                    </div>
                </div>
                ` : ''}

                ${!mostFrequentEnvironment && !gameStateExample && (!commonAttributes || Object.keys(commonAttributes).length === 0) ? `
                <p class="text-sm text-docu-tertiary text-center py-4">맥락 정보가 없습니다.</p>
                ` : ''}
            </div>
        `;
    } catch (err) {
        container.innerHTML = `<p class="text-sm text-docu-danger">맥락 정보를 불러오는 중 오류 발생: ${err.message}</p>`;
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
                <label class="block text-sm font-semibold text-docu-ink mb-2">스택트레이스</label>
                <pre class="p-4 bg-gray-900 text-gray-100 rounded-lg text-xs font-mono overflow-x-auto">${stackTrace}</pre>
            </div>
            ${issue.description ? `
            <div>
                <label class="block text-sm font-semibold text-docu-ink mb-2">설명</label>
                <p class="text-sm text-docu-ink">${issue.description}</p>
            </div>
            ` : ''}
            <div>
                <label class="block text-sm font-semibold text-docu-ink mb-2">전체 아카이브</label>
                <details class="cursor-pointer">
                    <summary class="text-sm text-docu-primary hover:text-docu-primary">상세 로그 펼치기</summary>
                    <pre class="mt-2 p-4 bg-surface-secondary rounded-lg text-sm font-mono text-docu-ink overflow-x-auto">${issue.title}</pre>
                </details>
            </div>
        </div>
    `;
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 영향받은 플레이어
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

let affectedPlayersPage = 0;
const affectedPlayersPageSize = 10;

async function renderAffectedPlayers(issue) {
    await loadAffectedPlayers(affectedPlayersPage);
}

async function loadAffectedPlayers(page) {
    const container = document.getElementById('affectedPlayers');

    try {
        const body = await callApi(
            `/api/projects/${currentPublicId}/issues/${currentIssueId}/affected-players?page=${page}&size=${affectedPlayersPageSize}`,
            { method: 'GET' }
        );

        if (!body.success) {
            container.innerHTML = '<p class="text-sm text-docu-tertiary text-center py-8">플레이어 데이터를 불러올 수 없습니다.</p>';
            return;
        }

        const data = body.data;
        const players = data.content;

        if (players.length === 0) {
            container.innerHTML = '<p class="text-sm text-docu-tertiary text-center py-8">영향받은 플레이어가 없습니다.</p>';
            return;
        }

        // 테이블 렌더링
        container.innerHTML = `
            <div class="overflow-x-auto">
                <table class="w-full text-sm">
                    <thead>
                        <tr class="border-b border-divider text-docu-secondary text-xs">
                            <th class="text-left py-3 px-4 font-medium">플레이어 ID</th>
                            <th class="text-center py-3 px-4 font-medium">발생 횟수</th>
                            <th class="text-center py-3 px-4 font-medium">최초 발생</th>
                            <th class="text-center py-3 px-4 font-medium">최근 발생</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${players.map(player => `
                            <tr class="border-b border-divider hover:bg-surface-secondary transition-colors">
                                <td class="py-3 px-4">
                                    <span class="font-mono text-docu-ink">${player.userId}</span>
                                </td>
                                <td class="py-3 px-4 text-center">
                                    <span class="inline-block px-2 py-1 bg-docu-primary-light text-docu-primary rounded text-xs font-medium">
                                        ${player.occurrenceCount}회
                                    </span>
                                </td>
                                <td class="py-3 px-4 text-center text-docu-secondary text-xs">
                                    ${formatDateTime(player.firstOccurredAt)}
                                </td>
                                <td class="py-3 px-4 text-center text-docu-secondary text-xs">
                                    ${formatTimeAgo(player.lastOccurredAt)}
                                </td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
            </div>

            <!-- 페이지네이션 -->
            ${data.totalPages > 1 ? `
                <div class="flex items-center justify-between mt-4 pt-4 border-t border-divider">
                    <p class="text-xs text-docu-secondary">
                        총 ${data.totalElements}명의 플레이어 (${data.number + 1} / ${data.totalPages} 페이지)
                    </p>
                    <div class="flex gap-2">
                        <button onclick="loadAffectedPlayers(${page - 1})"
                                ${!data.first ? '' : 'disabled'}
                                class="px-3 py-1.5 text-xs border border-divider rounded hover:bg-surface-secondary disabled:opacity-50 disabled:cursor-not-allowed">
                            이전
                        </button>
                        <button onclick="loadAffectedPlayers(${page + 1})"
                                ${!data.last ? '' : 'disabled'}
                                class="px-3 py-1.5 text-xs border border-divider rounded hover:bg-surface-secondary disabled:opacity-50 disabled:cursor-not-allowed">
                            다음
                        </button>
                    </div>
                </div>
            ` : ''}
        `;

        affectedPlayersPage = page;
    } catch (err) {
        container.innerHTML = `<p class="text-sm text-docu-danger text-center py-8">플레이어 데이터 로드 중 오류 발생: ${err.message}</p>`;
    }
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
            : `<div class="w-8 h-8 bg-docu-primary-light rounded-full flex items-center justify-center">
                   <span class="text-docu-primary text-xs font-medium">${currentAssignee.nickname.charAt(0)}</span>
               </div>`;

        assigneeInfo.innerHTML = `
            <div class="flex items-center gap-2">
                ${profileImage}
                <span class="text-docu-ink text-sm font-medium">${currentAssignee.nickname}</span>
            </div>
        `;
    } else {
        assigneeInfo.innerHTML = `
            <div class="flex items-center gap-2 text-docu-tertiary text-xs">
                <div class="w-8 h-8 bg-surface-sub rounded-full flex items-center justify-center text-docu-secondary">
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/>
                    </svg>
                </div>
                <span>담당자 없음</span>
            </div>
        `;
    }

    // 우선순위 표시 (severity-button 스타일)
    const priorityInfoEl = document.getElementById('priorityInfo');
    priorityInfoEl.dataset.priority = issue.priority || '';
    if (issue.priority) {
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
        const priority = priorityMap[issue.priority] || {
            label: issue.priority,
            borderColor: 'border-divider',
            textColor: 'text-gray-600',
            dotColor: 'bg-gray-400',
            shadow: 'shadow-docu-sm'
        };
        priorityInfoEl.innerHTML = `
            <button type="button" class="inline-flex items-center gap-1.5 px-3 py-2 rounded-docu-btn text-xs font-bold border-2 ${priority.borderColor} ${priority.textColor} bg-surface-card ${priority.shadow} transition-transform hover:-translate-y-0.5 focus-ring">
                <span class="w-2 h-2 rounded-full ${priority.dotColor}" aria-hidden="true"></span>
                <span>${priority.label}</span>
            </button>
        `;
    } else {
        priorityInfoEl.innerHTML = '<span class="text-xs text-docu-tertiary">미설정</span>';
    }

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
               class="block p-3 bg-surface-secondary hover:bg-surface-hover rounded-lg transition-colors">
                <div class="flex items-center justify-between mb-1">
                    <span class="text-xs font-medium text-docu-ink">#${result.matchedIssueId}</span>
                    <span class="text-xs text-docu-primary">${result.similarity.toFixed(0)}%</span>
                </div>
                <p class="text-xs text-docu-secondary truncate">${result.matchedIssueTitle}</p>
            </a>
        `).join('');
    } else {
        container.innerHTML = '<p class="text-xs text-docu-tertiary">유사한 이슈가 없습니다.</p>';
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 변경 이력
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

async function renderTimeline(issue) {
    const container = document.getElementById('timeline');

    try {
        // 이력 API 호출
        const body = await callApi(`/api/projects/${currentPublicId}/issues/${currentIssueId}/histories`, {
            method: 'GET'
        });

        if (!body.success) {
            container.innerHTML = '<p class="text-sm text-docu-tertiary">타임라인을 불러올 수 없습니다.</p>';
            return;
        }

        const histories = body.data;

        // 이슈 생성 이벤트 추가
        const events = [
            {
                time: issue.createdAt,
                user: 'System',
                action: '이슈 생성됨',
                icon: 'plus'
            }
        ];

        // 변경 이력을 이벤트로 변환
        histories.forEach(history => {
            const member = projectMembers.find(m => m.memberId === history.modifierId);
            const userName = member ? member.nickname : '알 수 없음';

            let action = '';

            switch (history.fieldName) {
                case 'STATUS':
                    action = `상태를 "${history.beforeValue || '없음'}"에서 "${history.afterValue}"(으)로 변경`;
                    break;
                case 'ASSIGNEE':
                    const beforeMember = projectMembers.find(m => m.memberId === history.beforeValue);
                    const afterMember = projectMembers.find(m => m.memberId === history.afterValue);
                    const beforeName = beforeMember ? beforeMember.nickname : '미할당';
                    const afterName = afterMember ? afterMember.nickname : '미할당';
                    action = `담당자를 "${beforeName}"에서 "${afterName}"(으)로 변경`;
                    break;
                case 'PRIORITY':
                    action = `우선순위를 "${history.beforeValue || '없음'}"에서 "${history.afterValue}"(으)로 변경`;
                    break;
                case 'COMMENT':
                    action = history.afterValue;
                    break;
                default:
                    action = `${history.fieldName}을(를) 변경`;
            }

            events.push({
                time: history.createdAt,
                user: userName,
                action: action
            });
        });

        // 최신 순 정렬
        events.sort((a, b) => new Date(b.time) - new Date(a.time));

        // 렌더링 (DOM 노드 방식으로 XSS 방지)
        if (events.length === 0) {
            const emptyP = document.createElement('p');
            emptyP.className = 'text-sm text-docu-tertiary';
            emptyP.textContent = '타임라인이 없습니다.';
            container.appendChild(emptyP);
            return;
        }

        container.innerHTML = '';  // 초기화
        events.forEach((event, index) => {
            const isLast = index === events.length - 1;
            const isSystem = event.user === 'System';

            // 최상위 컨테이너
            const eventDiv = document.createElement('div');
            eventDiv.className = 'flex gap-3';

            // 타임라인 인디케이터
            const indicatorDiv = document.createElement('div');
            indicatorDiv.className = 'flex flex-col items-center shrink-0';

            const dot = document.createElement('div');
            dot.className = isSystem
                ? 'w-2 h-2 border-2 border-divider bg-surface-sub mt-0.5'
                : 'w-2 h-2 border-2 border-docu-primary bg-surface-card mt-0.5';
            indicatorDiv.appendChild(dot);

            if (!isLast) {
                const line = document.createElement('div');
                line.className = 'w-px flex-1 bg-divider mt-1';
                indicatorDiv.appendChild(line);
            }

            // 이벤트 내용
            const contentDiv = document.createElement('div');
            contentDiv.className = isLast ? 'flex-1' : 'flex-1 pb-2';

            const headerDiv = document.createElement('div');
            headerDiv.className = 'flex items-center justify-between mb-0.5 gap-4';

            const userSpan = document.createElement('span');
            userSpan.className = isSystem
                ? 'text-xs font-semibold text-docu-secondary'
                : 'text-xs font-semibold text-docu-ink';
            userSpan.textContent = event.user;  // textContent로 XSS 방지

            const timeSpan = document.createElement('span');
            timeSpan.className = 'text-[10px] text-docu-tertiary';
            timeSpan.textContent = formatTimeAgo(event.time);

            headerDiv.appendChild(userSpan);
            headerDiv.appendChild(timeSpan);

            const actionP = document.createElement('p');
            actionP.className = 'text-xs text-docu-secondary';
            actionP.textContent = event.action;  // textContent로 XSS 방지 (escapeHtml 불필요)

            contentDiv.appendChild(headerDiv);
            contentDiv.appendChild(actionP);

            eventDiv.appendChild(indicatorDiv);
            eventDiv.appendChild(contentDiv);

            container.appendChild(eventDiv);
        });
    } catch (err) {
        container.innerHTML = `<p class="text-sm text-docu-danger">변경 이력 로드 중 오류 발생: ${err.message}</p>`;
    }
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

    // 현재 담당자와 동일한 경우 검증
    if (currentAssignee && currentAssignee.memberId === selectedAssigneeId) {
        errorEl.textContent = '이미 해당 담당자로 지정되어 있습니다.';
        errorEl.classList.remove('hidden');
        return;
    }

    try {
        const body = await callApi(`/api/projects/${currentPublicId}/issues/${currentIssueId}/assignee`, {
            method: 'PUT',
            body: JSON.stringify({ assigneeId: selectedAssigneeId })
        });

        if (body.success) {
            cancelAssigneeEdit(); // 편집 모드 닫기
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
// 우선순위 변경
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

function enterPriorityEditMode() {
    // 표시 모드 숨기고 편집 모드 표시
    document.getElementById('priorityDisplay').classList.add('hidden');
    document.getElementById('priorityEdit').classList.remove('hidden');

    // 현재 우선순위 선택
    const currentPriority = document.getElementById('priorityInfo').dataset.priority;
    const select = document.getElementById('prioritySelect');
    if (currentPriority) {
        select.value = currentPriority;
    }
}

function cancelPriorityEdit() {
    // 편집 모드 숨기고 표시 모드 표시
    document.getElementById('priorityEdit').classList.add('hidden');
    document.getElementById('priorityDisplay').classList.remove('hidden');
    document.getElementById('priorityEditError').classList.add('hidden');
}

async function savePriorityChange() {
    const selectedPriority = document.getElementById('prioritySelect').value;
    const errorEl = document.getElementById('priorityEditError');

    errorEl.classList.add('hidden');

    if (!selectedPriority) {
        errorEl.textContent = '우선순위를 선택하세요.';
        errorEl.classList.remove('hidden');
        return;
    }

    // 현재 우선순위와 동일한 경우 검증
    const currentPriority = document.getElementById('priorityInfo').dataset.priority;
    if (currentPriority === selectedPriority) {
        errorEl.textContent = '이미 해당 우선순위로 설정되어 있습니다.';
        errorEl.classList.remove('hidden');
        return;
    }

    try {
        const body = await callApi(`/api/projects/${currentPublicId}/issues/${currentIssueId}/priority`, {
            method: 'PUT',
            body: JSON.stringify({ priority: selectedPriority })
        });

        if (body.success) {
            cancelPriorityEdit(); // 편집 모드 닫기
            loadIssueDetail(); // 새로고침
        } else {
            errorEl.textContent = body.error?.message || '우선순위 변경에 실패했습니다.';
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
        showTopToast('댓글 내용을 입력하세요.', 'warning');
        return;
    }

    // TODO: Phase 3에서 댓글 API 연동
    showTopToast('댓글 기능은 곧 추가될 예정입니다.', 'info');
    document.getElementById('newComment').value = '';
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 상태 변경
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

function openStatusModal() {
    document.getElementById('newStatus').value = '';
    document.getElementById('resolutionNote').value = '';
    document.getElementById('shouldIncludeInPatchNote').checked = false;
    document.getElementById('resolvedOptions').classList.add('hidden');
    document.getElementById('statusError').classList.add('hidden');
    openModal('statusModal');
}

document.getElementById('newStatus')?.addEventListener('change', (e) => {
    const resolvedOptions = document.getElementById('resolvedOptions');
    if (e.target.value === 'RESOLVED') {
        resolvedOptions.classList.remove('hidden');
    } else {
        resolvedOptions.classList.add('hidden');
    }
});

async function submitStatusChange() {
    const status = document.getElementById('newStatus').value;
    const resolutionNote = document.getElementById('resolutionNote')?.value?.trim() || null;
    const shouldIncludeInPatchNote = document.getElementById('shouldIncludeInPatchNote').checked;
    const errorEl = document.getElementById('statusError');

    errorEl.classList.add('hidden');

    if (!status) {
        errorEl.textContent = '변경할 상태를 선택하세요.';
        errorEl.classList.remove('hidden');
        return;
    }

    try {
        const body = await callApi(`/api/projects/${currentPublicId}/issues/${currentIssueId}/status`, {
            method: 'PUT',
            body: JSON.stringify({
                status,
                resolutionNote,
                shouldIncludeInPatchNote
            })
        });

        if (body.success) {
            closeModal('statusModal');
            showTopToast('이슈 상태가 변경되었습니다.', 'success');
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
        RECOMMENDED: { label: '추천 대기', color: 'bg-docu-primary-light text-docu-primary-dark' },
        TODO: { label: '대기중', color: 'bg-surface-base text-docu-ink' },
        IN_PROGRESS: { label: '처리중', color: 'bg-docu-primary-light text-docu-primary-dark' },
        RESOLVED: { label: '해결됨', color: 'bg-docu-success-light text-docu-success-dark' }
    };
    const { label, color } = statusMap[status] || { label: status, color: 'bg-gray-100 text-docu-ink' };
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
            showTopToast('이슈 URL이 복사되었습니다.', 'success');
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
    showTopToast(message, 'danger');
}

function escapeHtml(text) {
    if (!text) return '';
    return String(text)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}
