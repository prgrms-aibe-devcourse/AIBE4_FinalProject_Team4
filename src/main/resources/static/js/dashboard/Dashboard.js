/**
 * 대시보드 Alpine.js 컴포넌트.
 *
 * 보안 원칙:
 * - 모든 DOM 렌더링은 x-text / x-bind 사용 (innerHTML 금지)
 * - API 호출은 FetchUtil.js 의 callApi() 전용
 */

document.addEventListener('alpine:init', () => {
    Alpine.data('dashboardApp', () => ({
        // ── 상태 ───────────────────────────────────────────────────────────────
        publicId: null,
        views: [],
        currentView: null,
        widgets: [],
        globalTimeRange: '1h',
        editMode: false,
        isDirty: false,
        isLoadingViews: false,

        widgetData: {},
        widgetErrors: {},
        widgetLoading: {},
        chartInstances: {},

        columnOptions: [],

        presets: [],

        refreshTimer: null,
        currentRefreshMs: null,

        modal: {
            open: false,
            step: 1,
            draftWidget: null,
            queryResult: null,
            isPreviewLoading: false,
            error: null,
        },

        createViewModal: {
            open: false,
            name: '',
            description: '',
        },

        // ── 라이프사이클 ────────────────────────────────────────────────────────
        async init() {
            this.publicId = document.getElementById('publicId')?.value;
            if (!this.publicId) return;

            await Promise.all([this.loadViews(), this.loadColumns()]);
            this.setupPageVisibility();
        },

        // ── 컬럼 로드 ──────────────────────────────────────────────────────────
        async loadColumns() {
            try {
                const res = await callApi(`/api/projects/${this.publicId}/logs/columns`);
                if (res.success) {
                    const columns = res.data.columns || [];
                    const jsonbKeys = res.data.jsonbKeys || {};
                    const options = [];
                    for (const col of columns) {
                        if (!col.isJsonb) {
                            options.push({ value: col.name, label: col.name });
                        } else {
                            for (const key of (jsonbKeys[col.name] || [])) {
                                const path = col.name + '.' + key;
                                options.push({ value: path, label: path });
                            }
                        }
                    }
                    this.columnOptions = options;
                }
            } catch (e) {
                console.error('컬럼 로드 실패:', e);
            }
        },

        // ── 뷰 목록 ────────────────────────────────────────────────────────────
        async loadViews() {
            this.isLoadingViews = true;
            try {
                const res = await callApi(`/api/projects/${this.publicId}/dashboard/views`);
                if (res.success) {
                    this.views = res.data || [];
                    if (this.views.length > 0) {
                        // 기본 뷰 또는 첫 번째 뷰 로드
                        const defaultView = this.views.find(v => v.defaultView) || this.views[0];
                        await this.loadView(defaultView.id);
                    } else {
                        await this.loadPresets();
                    }
                }
            } catch (e) {
                console.error('뷰 목록 로드 실패:', e);
            } finally {
                this.isLoadingViews = false;
            }
        },

        // ── 프리셋 로드 ────────────────────────────────────────────────────────
        async loadPresets() {
            try {
                const res = await callApi(`/api/projects/${this.publicId}/dashboard/presets`);
                if (res.success) {
                    this.presets = res.data || [];
                }
            } catch (e) {
                console.error('프리셋 로드 실패:', e);
            }
        },

        // ── 뷰 상세 로드 ───────────────────────────────────────────────────────
        async loadView(viewId) {
            try {
                const res = await callApi(`/api/projects/${this.publicId}/dashboard/views/${viewId}`);
                if (res.success) {
                    this.currentView = res.data;
                    this.widgets = (res.data.layoutConfig || []).map(w => ({ ...w }));
                    this.globalTimeRange = res.data.globalTimeRange || '1h';
                    this.editMode = false;
                    this.isDirty = false;
                    this.chartInstances = {};
                    this.widgetData = {};
                    this.widgetErrors = {};
                    this.widgetLoading = {};
                    await this.executeAllWidgets();
                }
            } catch (e) {
                console.error('뷰 로드 실패:', e);
                window.showTopToast?.('대시보드 뷰 로드에 실패했습니다.', 'danger');
            }
        },

        // ── 뷰 전환 ────────────────────────────────────────────────────────────
        async switchView(viewId) {
            if (this.isDirty) {
                if (!confirm('저장하지 않은 변경사항이 있습니다. 뷰를 전환하시겠습니까?')) {
                    return;
                }
            }
            this.stopRefresh();
            await this.loadView(viewId);
        },

        // ── 위젯 쿼리 실행 ─────────────────────────────────────────────────────
        async executeWidget(widget) {
            this.widgetLoading = { ...this.widgetLoading, [widget.id]: true };
            this.widgetErrors = { ...this.widgetErrors, [widget.id]: null };

            const { from, to } = resolveTimeRange(this.globalTimeRange);
            const query = widget.query || {};
            const overriddenQuery = {
                ...query,
                from,
                to,
                selects: (query.selects || []).map(({ _id, ...rest }) => rest),
                wheres: (query.wheres || []).map(({ _id, ...rest }) => rest),
                whereLogic: query.whereLogic || 'AND',
                groupBy: (query.groupBy || []).filter(g => g),
                orderBy: query.orderBy || { column: null, direction: 'DESC' },
                limit: query.limit || 50,
                offset: 0,
            };

            try {
                const res = await callApi(
                    `/api/projects/${this.publicId}/logs/query`,
                    { method: 'POST', body: JSON.stringify(overriddenQuery) }
                );
                if (res.success) {
                    this.widgetData = { ...this.widgetData, [widget.id]: res.data };
                } else {
                    const msg = res.error?.message || '쿼리 실행 실패';
                    this.widgetErrors = { ...this.widgetErrors, [widget.id]: msg };
                }
            } catch (e) {
                this.widgetErrors = { ...this.widgetErrors, [widget.id]: e.message || '조회 실패' };
            } finally {
                this.widgetLoading = { ...this.widgetLoading, [widget.id]: false };
                this.$nextTick(() => this.renderWidget(widget));
            }
        },

        async executeAllWidgets() {
            const results = await Promise.allSettled(
                this.widgets.map(w => this.executeWidget(w))
            );
            results.forEach((r, i) => {
                if (r.status === 'rejected') {
                    const wid = this.widgets[i]?.id;
                    if (wid) {
                        this.widgetErrors = {
                            ...this.widgetErrors,
                            [wid]: r.reason?.message || '조회 실패',
                        };
                    }
                }
            });
        },

        // ── 차트 렌더링 ────────────────────────────────────────────────────────
        renderWidget(widget) {
            if (!widget || widget.type === 'stat' || widget.type === 'table') return;
            if (this.widgetErrors[widget.id]) return;

            const data = this.widgetData[widget.id];
            if (!data) return;

            const containerId = 'chart-' + widget.id;
            const container = document.getElementById(containerId);
            if (!container) return;

            let instance = this.chartInstances[widget.id];
            if (!instance) {
                instance = echarts.init(container, null, { renderer: 'canvas' });
                this.chartInstances[widget.id] = instance;
            }

            const option = ChartRenderer.build(widget.type, data, widget.visualization || {});
            instance.setOption(option, true);

            // 컨테이너 크기 변경 시 resize
            const ro = new ResizeObserver(() => instance.resize());
            ro.observe(container);
        },

        // ── stat 값 추출 ───────────────────────────────────────────────────────
        getStatValue(widgetId) {
            const data = this.widgetData[widgetId];
            if (!data || !data.rows || data.rows.length === 0) return '-';
            const row = data.rows[0];
            const cols = data.columnNames || [];
            if (cols.length === 0) return '-';
            const val = row[cols[0]];
            if (val === null || val === undefined) return '-';
            return typeof val === 'number'
                ? val.toLocaleString('ko-KR')
                : String(val);
        },

        // ── 테이블 헬퍼 ────────────────────────────────────────────────────────
        getTableColumns(widgetId) {
            return this.widgetData[widgetId]?.columnNames || [];
        },

        getTableRows(widgetId) {
            return this.widgetData[widgetId]?.rows || [];
        },

        formatCell(val) {
            if (val === null || val === undefined) return '';
            if (typeof val === 'object') return JSON.stringify(val);
            return String(val);
        },

        // ── 글로벌 시간 범위 변경 ─────────────────────────────────────────────
        onGlobalTimeRangeChange() {
            this.isDirty = true;
            this.executeAllWidgets();
        },

        // ── 자동 새로고침 ──────────────────────────────────────────────────────
        onRefreshChange(event) {
            const ms = parseInt(event.target.value, 10);
            this.stopRefresh();
            if (ms >= 10000) {
                this.startRefresh(ms);
            }
        },

        startRefresh(ms) {
            if (!ms || ms < 10000) return;
            this.currentRefreshMs = ms;
            this.refreshTimer = setInterval(() => {
                if (!document.hidden) this.executeAllWidgets();
            }, ms);
        },

        stopRefresh() {
            if (this.refreshTimer) {
                clearInterval(this.refreshTimer);
                this.refreshTimer = null;
            }
            this.currentRefreshMs = null;
        },

        // ── 페이지 가시성 ──────────────────────────────────────────────────────
        setupPageVisibility() {
            document.addEventListener('visibilitychange', () => {
                if (document.hidden) {
                    if (this.refreshTimer) {
                        clearInterval(this.refreshTimer);
                        this.refreshTimer = null;
                    }
                } else {
                    if (this.currentRefreshMs) {
                        this.startRefresh(this.currentRefreshMs);
                    }
                }
            });
        },

        // ── 편집 모드 ──────────────────────────────────────────────────────────
        toggleEditMode() {
            this.editMode = !this.editMode;
        },

        // ── 위젯 제거 ──────────────────────────────────────────────────────────
        removeWidget(widgetId) {
            const instance = this.chartInstances[widgetId];
            if (instance) {
                instance.dispose();
                delete this.chartInstances[widgetId];
            }
            this.widgets = this.widgets.filter(w => w.id !== widgetId);
            this.isDirty = true;
        },

        // ── 뷰 저장 ────────────────────────────────────────────────────────────
        async saveView() {
            if (!this.currentView) return;

            const body = {
                name: this.currentView.name,
                description: this.currentView.description,
                layoutConfig: this.widgets.map(w => ({
                    id: w.id,
                    title: w.title,
                    type: w.type,
                    layout: w.layout,
                    query: w.query,
                    visualization: w.visualization,
                })),
                globalTimeRange: this.globalTimeRange,
                refreshIntervalMs: this.currentRefreshMs || null,
            };

            try {
                const res = await callApi(
                    `/api/projects/${this.publicId}/dashboard/views/${this.currentView.id}`,
                    { method: 'PUT', body: JSON.stringify(body) }
                );
                if (res.success) {
                    this.currentView = res.data;
                    this.isDirty = false;
                    this.editMode = false;
                    window.showTopToast?.('대시보드가 저장되었습니다.', 'success');

                    // 요약 목록 갱신
                    const idx = this.views.findIndex(v => v.id === this.currentView.id);
                    if (idx >= 0) {
                        this.views[idx] = {
                            ...this.views[idx],
                            widgetCount: this.widgets.length,
                            globalTimeRange: this.globalTimeRange,
                        };
                    }
                } else {
                    window.showTopToast?.(res.error?.message || '저장 실패', 'danger');
                }
            } catch (e) {
                window.showTopToast?.('저장 중 오류가 발생했습니다.', 'danger');
            }
        },

        // ── 새 뷰 생성 모달 ────────────────────────────────────────────────────
        openCreateViewModal() {
            this.createViewModal = { open: true, name: '', description: '' };
        },

        async submitCreateView() {
            const name = this.createViewModal.name.trim();
            if (!name) return;

            try {
                const res = await callApi(
                    `/api/projects/${this.publicId}/dashboard/views`,
                    {
                        method: 'POST',
                        body: JSON.stringify({
                            name,
                            description: this.createViewModal.description.trim() || null,
                            layoutConfig: [],
                            globalTimeRange: '1h',
                        }),
                    }
                );
                if (res.success) {
                    this.createViewModal.open = false;
                    await this.loadViews();
                    window.showTopToast?.('새 뷰가 생성되었습니다.', 'success');
                } else {
                    window.showTopToast?.(res.error?.message || '생성 실패', 'danger');
                }
            } catch (e) {
                window.showTopToast?.('뷰 생성 중 오류가 발생했습니다.', 'danger');
            }
        },

        // ── 프리셋으로 뷰 생성 ────────────────────────────────────────────────
        async createViewFromPreset(preset) {
            try {
                const res = await callApi(
                    `/api/projects/${this.publicId}/dashboard/views`,
                    {
                        method: 'POST',
                        body: JSON.stringify({
                            name: preset.name,
                            description: preset.description,
                            layoutConfig: preset.layoutConfig,
                            globalTimeRange: '1h',
                        }),
                    }
                );
                if (res.success) {
                    this.presets = [];
                    await this.loadViews();
                    window.showTopToast?.('프리셋으로 대시보드가 생성되었습니다.', 'success');
                } else {
                    window.showTopToast?.(res.error?.message || '생성 실패', 'danger');
                }
            } catch (e) {
                window.showTopToast?.('프리셋 생성 중 오류가 발생했습니다.', 'danger');
            }
        },

        // ── 위젯 생성 모달 ─────────────────────────────────────────────────────
        openWidgetModal() {
            this.modal = {
                open: true,
                step: 1,
                draftWidget: {
                    id: 'w-' + Date.now(),
                    title: '',
                    type: 'bar',
                    layout: { x: 1, y: 1, w: 6, h: 4 },
                    query: {
                        from: null,
                        to: null,
                        timeField: 'occurred_at',
                        selects: [],
                        wheres: [],
                        whereLogic: 'AND',
                        groupBy: [],
                        orderBy: { column: null, direction: 'DESC' },
                        limit: 100,
                    },
                    visualization: {
                        xAxis: '',
                        yAxis: [],
                        colorScheme: 'primary',
                        unit: '',
                        showLegend: true,
                        stacked: false,
                    },
                },
                queryResult: null,
                isPreviewLoading: false,
                error: null,
            };
        },

        closeModal() {
            this.modal.open = false;
        },

        // ── 쿼리 빌더 헬퍼 ────────────────────────────────────────────────────
        addDraftSelect() {
            this.modal.draftWidget.query.selects.push({
                _id: Date.now(),
                column: '',
                aggregation: null,
                alias: '',
            });
        },

        removeDraftSelect(idx) {
            this.modal.draftWidget.query.selects.splice(idx, 1);
        },

        addDraftWhere() {
            this.modal.draftWidget.query.wheres.push({
                _id: Date.now(),
                column: '',
                operator: 'EQ',
                value: '',
                value2: '',
                values: [],
            });
        },

        removeDraftWhere(idx) {
            this.modal.draftWidget.query.wheres.splice(idx, 1);
        },

        toggleYAxis(col) {
            const yAxis = this.modal.draftWidget.visualization.yAxis;
            const idx = yAxis.indexOf(col);
            if (idx >= 0) {
                yAxis.splice(idx, 1);
            } else {
                yAxis.push(col);
            }
            this.renderModalPreview();
        },

        // ── Step 1: 쿼리 미리보기 ─────────────────────────────────────────────
        async previewQuery() {
            this.modal.isPreviewLoading = true;
            this.modal.error = null;
            this.modal.queryResult = null;

            const { from, to } = resolveTimeRange(this.globalTimeRange);
            const q = this.modal.draftWidget.query;
            const payload = {
                from,
                to,
                timeField: q.timeField || 'occurred_at',
                selects: (q.selects || []).map(({ _id, ...rest }) => rest),
                wheres: (q.wheres || []).map(({ _id, ...rest }) => rest),
                whereLogic: q.whereLogic || 'AND',
                groupBy: (q.groupBy || []).filter(g => g),
                orderBy: q.orderBy || { column: null, direction: 'DESC' },
                limit: q.limit || 100,
                offset: 0,
            };

            try {
                const res = await callApi(
                    `/api/projects/${this.publicId}/logs/query`,
                    { method: 'POST', body: JSON.stringify(payload) }
                );
                if (res.success) {
                    this.modal.queryResult = res.data;
                    this.modal.step = 2;
                    this.$nextTick(() => this.renderModalPreview());
                } else {
                    this.modal.error = res.error?.message || '쿼리 실행 실패';
                }
            } catch (e) {
                this.modal.error = e.message || '쿼리 실행 중 오류가 발생했습니다.';
            } finally {
                this.modal.isPreviewLoading = false;
            }
        },

        // ── Step 2: 차트 미리보기 ─────────────────────────────────────────────
        renderModalPreview() {
            const widget = this.modal.draftWidget;
            if (!widget || widget.type === 'stat' || widget.type === 'table') return;
            if (!this.modal.queryResult) return;

            const container = document.getElementById('modal-chart-preview');
            if (!container) return;

            let instance = this._modalChartInstance;
            if (!instance) {
                instance = echarts.init(container, null, { renderer: 'canvas' });
                this._modalChartInstance = instance;
            }

            const option = ChartRenderer.build(
                widget.type,
                this.modal.queryResult,
                widget.visualization || {}
            );
            instance.setOption(option, true);
        },

        // ── Step 2: 위젯 확정 ─────────────────────────────────────────────────
        confirmWidget() {
            const widget = { ...this.modal.draftWidget };

            if (!widget.title.trim()) {
                this.modal.error = '위젯 제목을 입력하세요.';
                return;
            }

            // 레이아웃 자동 배치 (간단히 마지막 행 아래에 추가)
            const maxY = this.widgets.reduce(
                (max, w) => Math.max(max, (w.layout?.y || 1) + (w.layout?.h || 4)),
                1
            );
            widget.layout = { ...widget.layout, x: 1, y: maxY };

            // _id 내부 필드 제거
            widget.query = {
                ...widget.query,
                selects: (widget.query.selects || []).map(({ _id, ...rest }) => rest),
                wheres: (widget.query.wheres || []).map(({ _id, ...rest }) => rest),
            };

            this.widgets.push(widget);
            this.isDirty = true;

            // 모달 닫고 새 위젯 실행
            this.closeModal();
            if (this._modalChartInstance) {
                this._modalChartInstance.dispose();
                this._modalChartInstance = null;
            }
            this.$nextTick(() => this.executeWidget(widget));
        },

        // ── 차트 타입 레이블 ───────────────────────────────────────────────────
        chartTypeLabel(type) {
            const labels = {
                stat: '수치',
                table: '테이블',
                bar: '막대',
                line: '선',
                pie: '파이',
                heatmap: '히트맵',
            };
            return labels[type] || type;
        },
    }));
});

// ── 글로벌 유틸 ────────────────────────────────────────────────────────────────

/**
 * 상대 시간 문자열을 {from, to} OffsetDateTime 문자열로 변환.
 */
function resolveTimeRange(relativeRange) {
    const now = new Date();
    const durations = {
        '15m': 15 * 60 * 1000,
        '1h':  60 * 60 * 1000,
        '6h':  6 * 3600 * 1000,
        '24h': 24 * 3600 * 1000,
        '7d':  7 * 86400 * 1000,
        '30d': 30 * 86400 * 1000,
    };
    const ms = durations[relativeRange] ?? 3600 * 1000;
    return {
        from: toOffsetDateTimeString(new Date(now.getTime() - ms)),
        to:   toOffsetDateTimeString(now),
    };
}

/**
 * Date 객체 → ISO 8601 OffsetDateTime 문자열 (백엔드 전송용).
 */
function toOffsetDateTimeString(date) {
    if (!date) return null;
    if (typeof date === 'string') {
        date = new Date(date);
    }
    if (isNaN(date.getTime())) return null;

    const tzOffset = -date.getTimezoneOffset();
    const diff = tzOffset >= 0 ? '+' : '-';
    const pad = num => `${Math.floor(Math.abs(num))}`.padStart(2, '0');
    const timezoneString = diff + pad(tzOffset / 60) + ':' + pad(tzOffset % 60);

    const localStr = new Date(date.getTime() - (date.getTimezoneOffset() * 60000))
        .toISOString()
        .slice(0, 16);
    return localStr + ':00' + timezoneString;
}
