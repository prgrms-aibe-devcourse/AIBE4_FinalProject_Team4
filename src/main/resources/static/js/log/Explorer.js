/**
 * 로그 탐색기 Alpine.js 컴포넌트.
 *
 * 보안 원칙:
 * - 모든 DOM 렌더링은 x-text 사용 (innerHTML 금지)
 * - API 호출은 FetchUtil.js의 callApi() 전용
 */

document.addEventListener('alpine:init', () => {
    Alpine.data('explorerApp', () => ({
        // ── State ─────────────────────────────────────────────────────────────
        publicId: null,
        columns: [],

        query: {
            from: null,
            to: null,
            timeField: 'occurred_at',
            selects: [],
            wheres: [],
            whereLogic: 'AND',
            groupBy: [],
            orderBy: { column: null, direction: 'DESC' },
            limit: 50,
        },

        ui: {
            activeTab: 'table',
            autoRefreshMs: null,
            autoRefreshTimer: null,
            isLoading: false,
            rateLimited: false,
            retryAfter: 0,
            showAbsolutePicker: false,
            trimNotice: false,
        },

        results: {
            rows: [],
            columnNames: [],
            hasMore: false,
            currentOffset: 0,
        },

        // ── Lifecycle ─────────────────────────────────────────────────────────
        async init() {
            this.publicId = document.getElementById('publicId')?.value;
            this.applyRelativeRange('1h');
            await this.loadColumns();
            this.setupPageVisibility();
        },

        // ── Computed ──────────────────────────────────────────────────────────
        get sqlPreview() {
            return generateSqlPreview(this.query);
        },

        get allColumnOptions() {
            const options = [];
            for (const col of this.columns) {
                if (!col.isJsonb) {
                    options.push({ value: col.name, label: col.name });
                } else {
                    const keys = this.jsonbKeys?.[col.name] || [];
                    for (const key of keys) {
                        options.push({
                            value: col.name + '.' + key,
                            label: col.name + '.' + key,
                        });
                    }
                }
            }
            return options;
        },

        // ── Column Loading ─────────────────────────────────────────────────────
        async loadColumns() {
            if (!this.publicId) return;
            try {
                const res = await callApi(`/api/projects/${this.publicId}/logs/columns`);
                if (res.success) {
                    this.columns = res.data.columns || [];
                    this.jsonbKeys = res.data.jsonbKeys || {};
                }
            } catch (e) {
                console.error('컬럼 목록 로드 실패:', e);
            }
        },

        // ── Query Builder ──────────────────────────────────────────────────────
        addSelect() {
            this.query.selects.push({ id: Date.now(), column: '', aggregation: null, alias: '' });
        },
        removeSelect(id) {
            this.query.selects = this.query.selects.filter(s => s.id !== id);
        },
        addWhere() {
            this.query.wheres.push({
                id: Date.now(),
                column: '',
                operator: 'EQ',
                value: '',
                value2: '',
                values: [],
            });
        },
        removeWhere(id) {
            this.query.wheres = this.query.wheres.filter(w => w.id !== id);
        },
        addGroupBy() {
            if (this.query.groupBy.length < 3) {
                this.query.groupBy.push('');
            }
        },
        removeGroupBy(idx) {
            this.query.groupBy.splice(idx, 1);
        },

        needsValue(operator) {
            return ['EQ', 'NEQ', 'GT', 'LT', 'GTE', 'LTE', 'CONTAINS', 'NOT_CONTAINS', 'STARTS_WITH', 'ENDS_WITH'].includes(operator);
        },
        needsTwoValues(operator) {
            return ['BETWEEN', 'NOT_BETWEEN'].includes(operator);
        },
        needsListValues(operator) {
            return ['ANY_IN', 'NOT_IN'].includes(operator);
        },

        // ── Time Picker ────────────────────────────────────────────────────────
        // 사용자의 로컬 시간대에 맞춰 datetime-local에 들어갈 수 있는 형식(YYYY-MM-DDTHH:mm)으로 변환
        toLocalISOString(date) {
            const tzOffset = date.getTimezoneOffset() * 60000; // offset in milliseconds
            const localISOTime = (new Date(date.getTime() - tzOffset)).toISOString().slice(0, 16);
            return localISOTime;
        },

        // 백엔드로 보낼 때 ISO 8601 형식(시간대 포함)으로 변환
        // datetime-local의 값(예: "2026-03-17T14:00")에 브라우저의 시간대(예: "+09:00")를 결합
        toOffsetDateTimeString(localDateTimeStr) {
            if (!localDateTimeStr) return null;
            const date = new Date(localDateTimeStr);
            if (isNaN(date.getTime())) return null;

            const tzOffset = -date.getTimezoneOffset();
            const diff = tzOffset >= 0 ? '+' : '-';
            const pad = num => `${Math.floor(Math.abs(num))}`.padStart(2, '0');
            const timezoneString = diff + pad(tzOffset / 60) + ':' + pad(tzOffset % 60);

            // "2026-03-17T14:00:00+09:00" 형식으로 조립
            return localDateTimeStr + ':00' + timezoneString;
        },

        applyRelativeRange(range) {
            const now = new Date();
            const from = new Date(now);
            switch (range) {
                case '15m': from.setMinutes(from.getMinutes() - 15); break;
                case '1h':  from.setHours(from.getHours() - 1); break;
                case '6h':  from.setHours(from.getHours() - 6); break;
                case '24h': from.setDate(from.getDate() - 1); break;
                case '7d':  from.setDate(from.getDate() - 7); break;
                case '30d': from.setDate(from.getDate() - 30); break;
                default:    from.setHours(from.getHours() - 1);
            }
            this.query.from = this.toLocalISOString(from);
            this.query.to = this.toLocalISOString(now);
        },

        formatDateTime(iso) {
            if (!iso) return '';
            return new Date(iso).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' });
        },

        // ── Query Execution ────────────────────────────────────────────────────
        async runQuery(loadMore = false) {
            if (this.ui.isLoading) return;
            if (!this.publicId) return;

            this.ui.isLoading = true;
            this.ui.rateLimited = false;
            this.ui.trimNotice = false;

            const offset = loadMore ? this.results.currentOffset : 0;

            const payload = {
                from: this.toOffsetDateTimeString(this.query.from),
                to: this.toOffsetDateTimeString(this.query.to),
                timeField: this.query.timeField,
                selects: this.query.selects.map(({ id, ...rest }) => rest),
                wheres: this.query.wheres.map(({ id, ...rest }) => rest),
                whereLogic: this.query.whereLogic,
                groupBy: this.query.groupBy.filter(c => c),
                orderBy: this.query.orderBy,
                limit: this.query.limit,
                offset,
            };

            try {
                const res = await callApi(`/api/projects/${this.publicId}/logs/query`, {
                    method: 'POST',
                    body: JSON.stringify(payload),
                });

                if (!res.success) {
                    const msg = res.error?.message || '쿼리 실행 실패';
                    if (msg.includes('한도') || res.error?.status === 429) {
                        this.handleRateLimit(0);
                    } else {
                        window.showTopToast?.(msg, 'danger');
                    }
                    return;
                }

                const incoming = res.data.rows || [];
                const newRows = loadMore ? [...this.results.rows, ...incoming] : incoming;

                const TRIM_THRESHOLD = 3000;
                const trimmed =
                    newRows.length > TRIM_THRESHOLD
                        ? newRows.slice(newRows.length - TRIM_THRESHOLD)
                        : newRows;

                if (newRows.length > TRIM_THRESHOLD) {
                    this.ui.trimNotice = true;
                }

                this.results = {
                    rows: trimmed,
                    columnNames: res.data.columnNames || [],
                    hasMore: res.data.hasMore || false,
                    currentOffset: offset + incoming.length,
                };
            } catch (e) {
                console.error('쿼리 실행 오류:', e);
                window.showTopToast?.('쿼리 실행 중 오류가 발생했습니다.', 'danger');
            } finally {
                this.ui.isLoading = false;
            }
        },

        // ── Auto Refresh ───────────────────────────────────────────────────────
        startAutoRefresh(ms) {
            this.stopAutoRefresh();
            if (!ms) return;
            this.ui.autoRefreshMs = ms;
            this.ui.autoRefreshTimer = setInterval(() => {
                if (!document.hidden) this.runQuery(false);
            }, ms);
        },

        stopAutoRefresh() {
            if (this.ui.autoRefreshTimer) {
                clearInterval(this.ui.autoRefreshTimer);
                this.ui.autoRefreshTimer = null;
            }
            this.ui.autoRefreshMs = null;
        },

        onAutoRefreshChange(event) {
            const val = parseInt(event.target.value, 10);
            if (val > 0) {
                this.startAutoRefresh(val);
            } else {
                this.stopAutoRefresh();
            }
        },

        // ── Rate Limit ─────────────────────────────────────────────────────────
        handleRateLimit(retryAfter) {
            this.stopAutoRefresh();
            this.ui.rateLimited = true;
            this.ui.retryAfter = retryAfter || 60;
        },

        // ── Page Visibility ────────────────────────────────────────────────────
        setupPageVisibility() {
            document.addEventListener('visibilitychange', () => {
                if (document.hidden) {
                    if (this.ui.autoRefreshTimer) {
                        clearInterval(this.ui.autoRefreshTimer);
                    }
                } else {
                    if (this.ui.autoRefreshMs) {
                        this.startAutoRefresh(this.ui.autoRefreshMs);
                    }
                }
            });
        },

        // ── Table Cell Rendering ───────────────────────────────────────────────
        formatCellValue(value) {
            if (value === null || value === undefined) return '';
            if (typeof value === 'object') return JSON.stringify(value);
            return String(value);
        },

        getJsonPreview() {
            if (this.results.rows.length === 0) return '[]';
            return JSON.stringify(this.results.rows.slice(0, 100), null, 2);
        },
    }));
});

/**
 * 쿼리 상태로부터 SQL 미리보기 문자열 생성.
 * @param {Object} query
 * @returns {string}
 */
function generateSqlPreview(query) {
    const lines = [];

    // SELECT
    if (!query.selects || query.selects.length === 0) {
        lines.push('SELECT log_id, session_id, user_id, severity, event_category, archive,');
        lines.push('       occurred_at, ingested_at, trace_id, span_id, fingerprint');
    } else {
        const selectParts = query.selects.map(s => {
            let col = s.column || '';
            if (s.aggregation) {
                if (s.aggregation === 'COUNT_DISTINCT') {
                    col = `COUNT(DISTINCT ${col || '*'})`;
                } else {
                    col = `${s.aggregation}(${col || '*'})`;
                }
            }
            if (s.alias) col += ` AS ${s.alias}`;
            return col;
        }).filter(Boolean);
        lines.push('SELECT ' + selectParts.join(', '));
    }

    lines.push('FROM game_log');

    // WHERE
    const conditions = ['project_id = :projectId'];
    const tf = query.timeField || 'occurred_at';
    const from = query.from ? query.from.replace('T', ' ').replace('Z', '') : '...';
    const to = query.to ? query.to.replace('T', ' ').replace('Z', '') : '...';
    conditions.push(`${tf} BETWEEN '${from}' AND '${to}'`);

    if (query.wheres && query.wheres.length > 0) {
        const filterParts = query.wheres
            .filter(w => w.column && w.operator)
            .map(w => {
                const col = w.column;
                switch (w.operator) {
                    case 'EQ':           return `${col} = '${w.value || ''}'`;
                    case 'NEQ':          return `${col} <> '${w.value || ''}'`;
                    case 'GT':           return `${col} > '${w.value || ''}'`;
                    case 'LT':           return `${col} < '${w.value || ''}'`;
                    case 'GTE':          return `${col} >= '${w.value || ''}'`;
                    case 'LTE':          return `${col} <= '${w.value || ''}'`;
                    case 'BETWEEN':      return `${col} BETWEEN '${w.value || ''}' AND '${w.value2 || ''}'`;
                    case 'NOT_BETWEEN':  return `${col} NOT BETWEEN '${w.value || ''}' AND '${w.value2 || ''}'`;
                    case 'IS_NULL':      return `${col} IS NULL`;
                    case 'IS_NOT_NULL':  return `${col} IS NOT NULL`;
                    case 'CONTAINS':     return `${col} ILIKE '%${w.value || ''}%'`;
                    case 'NOT_CONTAINS': return `${col} NOT ILIKE '%${w.value || ''}%'`;
                    case 'STARTS_WITH':  return `${col} ILIKE '${w.value || ''}%'`;
                    case 'ENDS_WITH':    return `${col} ILIKE '%${w.value || ''}'`;
                    case 'IS_EMPTY':     return `${col} = ''`;
                    case 'IS_NOT_EMPTY': return `${col} <> ''`;
                    case 'ANY_IN':       return `${col} IN (${(w.values || []).map(v => `'${v}'`).join(', ')})`;
                    case 'NOT_IN':       return `${col} NOT IN (${(w.values || []).map(v => `'${v}'`).join(', ')})`;
                    default:             return `${col} ${w.operator} '${w.value || ''}'`;
                }
            });

        if (filterParts.length > 0) {
            const logic = query.whereLogic === 'OR' ? ' OR ' : ' AND ';
            conditions.push('(' + filterParts.join(logic) + ')');
        }
    }

    lines.push('WHERE ' + conditions.join('\n  AND '));

    // GROUP BY
    if (query.groupBy && query.groupBy.filter(c => c).length > 0) {
        lines.push('GROUP BY ' + query.groupBy.filter(c => c).join(', '));
    }

    // ORDER BY — Global Aggregation(GROUP BY 없이 집계 함수만 사용)이면 생략
    const hasAggregation =
        query.selects && query.selects.length > 0 && query.selects.some(s => s.aggregation);
    const hasGroupBy = query.groupBy && query.groupBy.filter(c => c).length > 0;
    const isGlobalAggregation = hasAggregation && !hasGroupBy;

    if (!isGlobalAggregation) {
        if (query.orderBy && query.orderBy.column) {
            lines.push(`ORDER BY ${query.orderBy.column} ${query.orderBy.direction || 'ASC'}`);
        } else {
            lines.push('ORDER BY occurred_at DESC');
        }
    }

    lines.push(`LIMIT ${query.limit || 50} OFFSET :offset`);

    return lines.join('\n');
}
