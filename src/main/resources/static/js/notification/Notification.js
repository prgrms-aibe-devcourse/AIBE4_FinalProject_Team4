document.addEventListener('alpine:init', () => {
    Alpine.data('notificationApp', () => ({
        // ── State (Reactivity Core) ──────────────────────────────────────────
        unreadCount: 0,
        notifications: [],
        isOpen: false,
        isLoading: false,
        isLoaded: false, // 최초 1회 로딩 여부

        // ── Lifecycle ────────────────────────────────────────────────────────
        async init() {
            // 두 개의 UI(데스크톱/모바일)가 렌더링되더라도 데이터 패치는 1번만 수행
            if (!window.notificationGlobalDataLoaded) {
                window.notificationGlobalDataLoaded = true;
                await this.getUnreadCount();
                this.connectSse();
            } else {
                // 두 번째 렌더링된 컴포넌트는 잠깐 대기 후 첫 번째 컴포넌트의 데이터를 공유
                setTimeout(() => this.getUnreadCount(), 500);
            }
        },

        // ── API Actions (Global Access) ──────────────────────────────────────
        async getUnreadCount() {
            try {
                const res = await callApi('/api/notifications/unread-count');
                if (res.success) {
                    this.unreadCount = res.data || 0;
                }
            } catch (error) {
                console.error('[Notification] Unread count fetch failed:', error);
            }
        },

        async fetchNotifications() {
            if (this.isLoading) return;
            this.isLoading = true;
            try {
                // 전역 알림 API (projectId 파라미터 불필요, 서버측 memberId 기반)
                const res = await callApi('/api/notifications?size=10');
                if (res.success) {
                    this.notifications = res.data.notifications || [];
                    this.isLoaded = true;
                }
            } catch (error) {
                window.showTopToast?.('알림 목록을 불러오지 못했습니다.', 'danger');
            } finally {
                this.isLoading = false;
            }
        },

        async markAllAsRead() {
            try {
                const res = await callApi('/api/notifications/read-all', { method: 'PATCH' });
                if (res.success) {
                    this.unreadCount = 0;
                    // 모든 알림 상태를 읽음으로 즉시 반영 (Reactivity)
                    this.notifications.forEach(n => n.isRead = true);
                }
            } catch (error) {
                window.showTopToast?.('처리에 실패했습니다.', 'danger');
            }
        },

        async handleNotificationClick(notification) {
            if (!notification.relatedUrl) return;

            // 읽지 않은 알림인 경우 서버에 읽음 처리 요청
            if (!notification.isRead) {
                try {
                    await callApi(`/api/notifications/${notification.id}/read`, { method: 'PATCH' });
                } catch (e) {
                    // 무시: 이동이 더 중요함
                }
            }
            // 알림에 명시된 도메인 URL로 이동
            window.location.href = notification.relatedUrl;
        },

        // ── Business Logic ───────────────────────────────────────────────────
        toggleDropdown() {
            this.isOpen = !this.isOpen;
            if (this.isOpen && !this.isLoaded) {
                this.fetchNotifications();
            }
        },

        connectSse() {
            if (window.sseConnected) return;
            window.sseConnected = true;

            const es = new EventSource('/api/notifications/stream', { withCredentials: true });

            es.addEventListener('alarm-toast', (event) => {
                try {
                    const payload = JSON.parse(event.data);
                    // 1. 우측 하단 토스트 표시 (컨벤션 준수)
                    this.triggerAlarmToast(payload);
                    // 2. 미읽음 카운트 증가
                    this.unreadCount++;
                    // 3. 드롭다운이 열려있다면 목록 최상단에 추가 (Optional)
                    if (this.isOpen) this.fetchNotifications();
                } catch (err) {
                    console.error('[SSE] Payload parsing error');
                }
            });

            es.onerror = () => {
                // 에러 발생 시 커넥션 상태 초기화 (재연결 대비)
                window.sseConnected = false;
            };
        },

        triggerAlarmToast(payload) {
            // 프론트엔드 컨벤션 9번 항목 준수
            const TYPE_MAP = {
                ISSUE: 'issue',
                LOG: 'log',
                DOCUMENT: 'rag',
                PATCHNOTE: 'patchnote'
            };

            window.showAlarmToast?.({
                title: payload.title || '새 알림',
                badgeLabel: payload.badgeLabel || '알림',
                message: payload.message || '',
                actionLabel: payload.actionText || '확인하기 >',
                actionHref: payload.relatedUrl || '#',
                type: TYPE_MAP[payload.sourceType] || 'issue'
            });
        }
    }));
});
