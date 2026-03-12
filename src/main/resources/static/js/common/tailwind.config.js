/** @type {import('tailwindcss').Config} */
const defaultTheme = require('tailwindcss/defaultTheme')

module.exports = {
    content: [
        "./src/main/resources/templates/**/*.html",
        "./src/main/resources/static/**/*.js"
    ],
    theme: {
        extend: {
            colors: {
                // 1. 브랜드 및 시만틱 컬러 (Primary, Secondary, Danger)
                docu: {
                    primary: {
                        light: '#E8F3FD',
                        DEFAULT: '#1D85E1', // 메인 블루
                        dark: '#1669B3',
                    },
                    secondary: {
                        DEFAULT: '#64748B', // Slate-500 대용
                        dark: '#475569',
                    },
                    danger: {
                        light: '#FEE2E2',
                        DEFAULT: '#EF4444', // Critical 에러
                        dark: '#B91C1C',
                    },
                    warning: {
                        light: '#FEF3C7',
                        DEFAULT: '#F59E0B', // High/Warning
                    },
                    success: {
                        light: '#DCFCE7',
                        DEFAULT: '#10B981', // Low/정상/Active
                    },
                },
                // 2. 배경 및 테두리용 그레이 스케일 (Surface & Border)
                surface: {
                    base: '#F9FAFB',     // 전체 페이지 배경
                    card: '#FFFFFF',     // 위젯/카드 배경
                    sidebar: '#F3F4F6',  // 사이드바 배경
                    code: '#1E293B',     // 로그/코드 배경
                },
                divider: '#E5E7EB',    // 보더 및 구분선
            },
            // 3. 타이포그래피 (Size & Line-height)
            fontSize: {
                'display': ['1.875rem', { lineHeight: '2.25rem', fontWeight: '700' }], // 대시보드 지표
                'h1': ['1.5rem', { lineHeight: '2rem', fontWeight: '700' }],         // 페이지 제목
                'h2': ['1.25rem', { lineHeight: '1.75rem', fontWeight: '600' }],       // 위젯 제목
                'base': ['1rem', { lineHeight: '1.5rem', fontWeight: '400' }],         // 본문
                'sm': ['0.875rem', { lineHeight: '1.25rem', fontWeight: '400' }],      // 폼, 설명
                'xs': ['0.75rem', { lineHeight: '1rem', fontWeight: '500' }],          // 배지, 로그, 시간
            },
            // 4. 간격 및 레이아웃 (Spacing)
            spacing: {
                'sidebar': '240px',   // 좌측 사이드바 너비 고정
                'header': '64px',    // 상단 헤더 높이 고정
                'content-p': '1.5rem', // 페이지 기본 패딩
                'widget-p': '1.25rem', // 위젯 내부 패딩
            },
            // 5. 둥글기 (Radius)
            borderRadius: {
                'docu-btn': '0.375rem',  // 버튼용
                'docu-card': '0.75rem', // 위젯/카드용
                'docu-modal': '1rem',   // 모달용
            },
            // 6. 그림자 (Shadow)
            boxShadow: {
                'docu-sm': '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
                'docu-card': '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -2px rgba(0, 0, 0, 0.1)',
                'docu-modal': '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
            },
            // 기본 폰트 설정 (가독성 좋은 Pretendard 권장)
            fontFamily: {
                sans: ['Pretendard', 'Inter', ...defaultTheme.fontFamily.sans],
            },
        },
    },
    plugins: [
        require('@tailwindcss/forms'), // 폼 요소 기본 스타일 최적화
    ],
}
