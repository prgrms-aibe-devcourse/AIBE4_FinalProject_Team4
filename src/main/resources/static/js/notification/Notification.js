document.addEventListener('alpine:init', () => {
    Alpine.data('notificationApp', () => ({
        // ── State (Reactivity Core) ──────────────────────────────────────────
        unreadCount: 0,
        notifications: [],
        isOpen: false,
        isLoading: false,
        isLoaded: false, // 최초 1회 로딩 여부
        pendingReadIds: new Set(),
        readTimeout: null,
        observer: null,
        visuallyReadIds: new Set(),

        // ── Lifecycle ────────────────────────────────────────────────────────
        async init() {
            // 매직 넘버(500ms) 제거: Promise를 활용한 정확한 비동기 대기 처리
            if (!window.notificationGlobalFetchPromise) {
                // 첫 번째 컴포넌트가 API 요청을 주도하고 해당 Promise를 공유
                window.notificationGlobalFetchPromise = this.getUnreadCount();
                await window.notificationGlobalFetchPromise;
                this.connectSse();
            } else {
                // 두 번째 렌더링된 컴포넌트는 무의미한 타이머 대기 없이,
                // 첫 번째 API 요청이 resolve 될 때까지 정확히 대기하여 Race Condition 방지
                await window.notificationGlobalFetchPromise;
                await this.getUnreadCount(); // 대기 완료 후 로컬 상태 안전하게 갱신
            }

            // 화면 노출 기반 일괄 읽음 처리(Batch Read) 옵저버 활성화
            if (typeof this.setupIntersectionObserver === 'function') {
                this.setupIntersectionObserver();
            }

            // 상태 변화 감시자(Watcher) 설정
            this.$watch('isOpen', (value) => {
                if (!value) {
                    // 드롭다운이 닫힐 때(false), 보류해두었던 시각적 읽음 처리를 일괄 반영
                    this.syncVisualReadState();
                }
            });
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

                    // 브라우저 전역으로 새 알림 도착 이벤트 브로드캐스팅 (Event Bus 패턴)
                    window.dispatchEvent(new CustomEvent('docu-new-notification', { detail: payload }));
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
                mainTypeLabel: payload.mainTypeLabel || '',
                subLabel: payload.subLabel || '',
                message: payload.message || '',
                actionLabel: payload.actionText || '확인하기 >',
                actionHref: payload.relatedUrl || '#',
                type: TYPE_MAP[payload.mainType] || payload.type || 'issue'
            });
        },

        // --- 화면 노출 감지 (Impression Tracking) ---
        setupIntersectionObserver() {
            this.observer = new IntersectionObserver((entries) => {
                let hasNewReads = false;
                entries.forEach(entry => {
                    if (entry.isIntersecting) {
                        const id = parseInt(entry.target.dataset.id, 10);
                        const noti = this.notifications.find(n => n.id === id);

                        // 이미 읽은 상태거나 펜딩 큐에 있다면 스킵
                        if (noti && !noti.isRead && !this.pendingReadIds.has(id)) {
                            this.pendingReadIds.add(id);
                            this.visuallyReadIds.add(id); // 시각적 갱신 큐에 추가
                            hasNewReads = true;

                            this.observer.unobserve(entry.target);

                            // GNB 배지 카운트는 즉각 감소 (종 모양 아이콘의 숫자)
                            this.unreadCount = Math.max(0, this.unreadCount - 1);
                        }
                    }
                });

                if (hasNewReads) {
                    clearTimeout(this.readTimeout);
                    this.readTimeout = setTimeout(() => this.flushPendingReads(), 500);
                }
            }, { threshold: 0.5 });
        },

        // 닫힘 이벤트 시 시각적 동기화 수행
        syncVisualReadState() {
            if (this.visuallyReadIds.size === 0) return;

            this.notifications.forEach(n => {
                if (this.visuallyReadIds.has(n.id)) {
                    n.isRead = true; // 이때 비로소 빨간 점이 사라짐
                }
            });
            this.visuallyReadIds.clear();
        },

        async flushPendingReads() {
            if (this.pendingReadIds.size === 0) return;
            const idsToUpdate = Array.from(this.pendingReadIds);
            this.pendingReadIds.clear();

            try {
                await callApi('/api/notifications/read-batch', {
                    method: 'PATCH',
                    body: JSON.stringify(idsToUpdate)
                });
            } catch (error) {
                console.error('[Notification] Batch read update failed:', error);
            }
        },

        // ── Helpers ───────────────────────────────────────────────────
        formatTime(utcString) {
            if (!utcString) return '';

            const date = new Date(utcString); // 브라우저가 자동으로 Local Timezone(KST)으로 파싱
            const now = new Date();
            const diffMs = now - date;
            const diffMins = Math.floor(diffMs / 60000);
            const diffHours = Math.floor(diffMins / 60);
            const diffDays = Math.floor(diffHours / 24);

            if (diffMins < 1) return '방금 전';
            if (diffMins < 60) return `${diffMins}분 전`;
            if (diffHours < 24) return `${diffHours}시간 전`;
            if (diffDays < 7) return `${diffDays}일 전`;

            // 7일 이상 지났다면 날짜와 시간 표시 (예: 03-20 14:30)
            const month = String(date.getMonth() + 1).padStart(2, '0');
            const day = String(date.getDate()).padStart(2, '0');
            const hours = String(date.getHours()).padStart(2, '0');
            const minutes = String(date.getMinutes()).padStart(2, '0');
            return `${month}-${day} ${hours}:${minutes}`;
        },
    }));
});
