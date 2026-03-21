/**
 * ECharts 옵션 빌더 (순수 함수).
 *
 * 각 함수는 ECharts option 객체를 반환한다.
 * DOM 조작 없음 — 렌더링은 Dashboard.js 의 renderWidget() 이 담당.
 */

const ChartRenderer = (() => {
    const COLOR_SCHEMES = {
        primary: ['#4A9EE8', '#2F7FC6', '#7EC2F5', '#1A5F9E', '#A3D5F7'],
        danger: ['#E84A4A', '#B93838', '#F27A7A', '#8C2020', '#F5AAAA'],
        success: ['#3BAA6E', '#2F8557', '#72C99A', '#1E6040', '#A8E0C3'],
        warning: ['#E8A020', '#B67C18', '#F5C35C', '#7A5010', '#FAE0A0'],
        default: ['#4A9EE8', '#3BAA6E', '#E8A020', '#E84A4A', '#9B59B6'],
    };

    function getColors(scheme) {
        return COLOR_SCHEMES[scheme] || COLOR_SCHEMES.default;
    }

    /**
     * stat — 단일 수치 (ECharts gauge 사용하지 않고, Dashboard.js 가 직접 x-text 로 처리).
     * 이 함수는 fallback 용으로만 존재.
     */
    function buildStat(data, config) {
        return {};
    }

    /**
     * bar — 막대 차트.
     *
     * @param {object} data - LogQueryResponse { rows, columnNames }
     * @param {object} config - VisualizationConfig { xAxis, yAxis[], colorScheme, stacked, showLegend }
     */
    function buildBar(data, config) {
        const rows = data.rows || [];
        const xField = config.xAxis;
        const yFields = config.yAxis || [];
        const colors = getColors(config.colorScheme);
        const categories = rows.map(r => formatValue(r[xField]));

        const series = yFields.map((yField, i) => ({
            name: yField,
            type: 'bar',
            stack: config.stacked ? 'total' : undefined,
            data: rows.map(r => r[yField]),
            itemStyle: { color: colors[i % colors.length] },
        }));

        return {
            color: colors,
            tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
            legend: config.showLegend ? { bottom: 0, type: 'scroll' } : undefined,
            grid: { left: '3%', right: '4%', bottom: config.showLegend ? 30 : 10, containLabel: true },
            xAxis: { type: 'category', data: categories, axisLabel: { rotate: 30, fontSize: 10 } },
            yAxis: { type: 'value', axisLabel: { fontSize: 10 } },
            series,
        };
    }

    /**
     * line — 선 차트.
     */
    function buildLine(data, config) {
        const rows = data.rows || [];
        const xField = config.xAxis;
        const yFields = config.yAxis || [];
        const colors = getColors(config.colorScheme);
        const categories = rows.map(r => formatValue(r[xField]));

        const series = yFields.map((yField, i) => ({
            name: yField,
            type: 'line',
            stack: config.stacked ? 'total' : undefined,
            areaStyle: config.stacked ? {} : undefined,
            smooth: true,
            data: rows.map(r => r[yField]),
            itemStyle: { color: colors[i % colors.length] },
        }));

        return {
            color: colors,
            tooltip: { trigger: 'axis' },
            legend: config.showLegend ? { bottom: 0, type: 'scroll' } : undefined,
            grid: { left: '3%', right: '4%', bottom: config.showLegend ? 30 : 10, containLabel: true },
            xAxis: { type: 'category', data: categories, axisLabel: { rotate: 30, fontSize: 10 } },
            yAxis: { type: 'value', axisLabel: { fontSize: 10 } },
            series,
        };
    }

    /**
     * pie — 파이 차트.
     */
    function buildPie(data, config) {
        const rows = data.rows || [];
        const nameField = config.xAxis;
        const valueField = (config.yAxis || [])[0];
        const colors = getColors(config.colorScheme);

        const seriesData = rows.map(r => ({
            name: formatValue(r[nameField]),
            value: r[valueField],
        }));

        return {
            color: colors,
            tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
            legend: config.showLegend
                ? { orient: 'vertical', left: 'left', type: 'scroll', textStyle: { fontSize: 10 } }
                : undefined,
            series: [
                {
                    type: 'pie',
                    radius: config.showLegend ? ['30%', '65%'] : ['30%', '70%'],
                    center: config.showLegend ? ['60%', '50%'] : ['50%', '50%'],
                    data: seriesData,
                    label: { fontSize: 10 },
                    emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.3)' } },
                },
            ],
        };
    }

    /**
     * heatmap — 히트맵.
     */
    function buildHeatmap(data, config) {
        const rows = data.rows || [];
        const xField = config.xAxis;
        const yFields = config.yAxis || [];
        const yField = yFields[0];
        const valueField = yFields[1] || yFields[0];

        const xCategories = [...new Set(rows.map(r => formatValue(r[xField])))];
        const yCategories = [...new Set(rows.map(r => formatValue(r[yField])))];

        const seriesData = rows.map(r => [
            xCategories.indexOf(formatValue(r[xField])),
            yCategories.indexOf(formatValue(r[yField])),
            r[valueField],
        ]);

        const values = seriesData.map(d => d[2]).filter(v => v != null);
        const minVal = values.length ? Math.min(...values) : 0;
        const maxVal = values.length ? Math.max(...values) : 1;

        return {
            tooltip: {
                position: 'top',
                formatter(params) {
                    return `${xCategories[params.data[0]]} / ${yCategories[params.data[1]]}: ${params.data[2]}`;
                },
            },
            grid: { left: '3%', right: '4%', bottom: 10, containLabel: true },
            xAxis: { type: 'category', data: xCategories, axisLabel: { rotate: 30, fontSize: 10 } },
            yAxis: { type: 'category', data: yCategories, axisLabel: { fontSize: 10 } },
            visualMap: {
                min: minVal,
                max: maxVal,
                calculable: true,
                orient: 'horizontal',
                left: 'center',
                bottom: 0,
                inRange: { color: ['#D8ECFB', '#4A9EE8', '#1A5F9E'] },
                textStyle: { fontSize: 10 },
            },
            series: [
                {
                    type: 'heatmap',
                    data: seriesData,
                    label: { show: false },
                    emphasis: { itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.3)' } },
                },
            ],
        };
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    function formatValue(val) {
        if (val === null || val === undefined) return '';
        if (typeof val === 'object') return JSON.stringify(val);
        return String(val);
    }

    // ── public API ─────────────────────────────────────────────────────────────

    function build(type, data, config) {
        switch (type) {
            case 'bar':     return buildBar(data, config);
            case 'line':    return buildLine(data, config);
            case 'pie':     return buildPie(data, config);
            case 'heatmap': return buildHeatmap(data, config);
            default:        return {};
        }
    }

    return { build, buildBar, buildLine, buildPie, buildHeatmap };
})();
