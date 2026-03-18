/**
 * CSS Grid 드래그 레이아웃 매니저.
 *
 * 편집 모드에서 위젯 카드에 마우스 드래그 이벤트를 연결해
 * grid-column / grid-row 위치를 업데이트한다.
 *
 * 사용법:
 *   const dl = new DragLayout(gridContainer, widgets, onChange);
 *   dl.enable();   // 편집 모드 진입 시
 *   dl.disable();  // 편집 모드 종료 시
 */
class DragLayout {
    /**
     * @param {HTMLElement} container - CSS Grid 컨테이너
     * @param {function(): Array} getWidgets - 위젯 배열 반환 콜백
     * @param {function(string, {x,y,w,h}): void} onLayoutChange - 위젯 ID + 새 레이아웃 콜백
     * @param {number} cols - 그리드 열 수 (기본 12)
     */
    constructor(container, getWidgets, onLayoutChange, cols = 12) {
        this.container = container;
        this.getWidgets = getWidgets;
        this.onLayoutChange = onLayoutChange;
        this.cols = cols;
        this._handlers = [];
        this._dragging = null;
    }

    enable() {
        this.disable();
        const cards = this.container.querySelectorAll('[data-widget-id]');
        cards.forEach(card => {
            const onMousedown = (e) => this._onMousedown(e, card);
            card.addEventListener('mousedown', onMousedown);
            this._handlers.push({ card, onMousedown });
        });
    }

    disable() {
        this._handlers.forEach(({ card, onMousedown }) => {
            card.removeEventListener('mousedown', onMousedown);
        });
        this._handlers = [];
        this._dragging = null;
    }

    _onMousedown(e, card) {
        if (e.button !== 0) return;
        e.preventDefault();

        const widgetId = card.dataset.widgetId;
        const widget = this.getWidgets().find(w => w.id === widgetId);
        if (!widget) return;

        const startX = e.clientX;
        const startY = e.clientY;
        const containerRect = this.container.getBoundingClientRect();
        const colWidth = containerRect.width / this.cols;
        const rowHeight = parseInt(getComputedStyle(this.container).gridAutoRows) || 80;

        let moved = false;

        const onMousemove = (ev) => {
            const dx = ev.clientX - startX;
            const dy = ev.clientY - startY;

            const deltaCol = Math.round(dx / colWidth);
            const deltaRow = Math.round(dy / rowHeight);

            if (deltaCol === 0 && deltaRow === 0) return;
            moved = true;

            const newX = Math.max(1, Math.min(this.cols - widget.layout.w + 1, widget.layout.x + deltaCol));
            const newY = Math.max(1, widget.layout.y + deltaRow);

            card.style.outline = '2px dashed #4A9EE8';
            card.dataset.pendingX = newX;
            card.dataset.pendingY = newY;
        };

        const onMouseup = () => {
            document.removeEventListener('mousemove', onMousemove);
            document.removeEventListener('mouseup', onMouseup);
            card.style.outline = '';

            if (moved && card.dataset.pendingX) {
                const newX = parseInt(card.dataset.pendingX);
                const newY = parseInt(card.dataset.pendingY);
                delete card.dataset.pendingX;
                delete card.dataset.pendingY;

                this.onLayoutChange(widgetId, {
                    ...widget.layout,
                    x: newX,
                    y: newY,
                });
            }
        };

        document.addEventListener('mousemove', onMousemove);
        document.addEventListener('mouseup', onMouseup);
    }
}
