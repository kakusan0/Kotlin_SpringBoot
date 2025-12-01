// Session Timeout Auto Logout
(function () {
    'use strict';

    // 定数定義
    const CONFIG = {
        SESSION_TIMEOUT_MS: 5 * 60 * 1000,  // 5分
        WARNING_BEFORE_MS: 30 * 1000,        // 30秒前
        ALERT_Z_INDEX: 9999,
        ALERT_MAX_WIDTH: '500px'
    };

    const SELECTORS = {
        CSRF_TOKEN: 'meta[name="_csrf"]',
        CSRF_HEADER: 'meta[name="_csrf_header"]',
        WARNING_ALERT: '.alert-warning[role="alert"]'
    };

    const USER_EVENTS = ['mousedown', 'mousemove', 'keypress', 'scroll', 'touchstart', 'click'];

    let timeoutTimer = null;
    let warningTimer = null;

    function resetTimers() {
        clearTimeout(timeoutTimer);
        clearTimeout(warningTimer);

        warningTimer = setTimeout(showWarning, CONFIG.SESSION_TIMEOUT_MS - CONFIG.WARNING_BEFORE_MS);
        timeoutTimer = setTimeout(autoLogout, CONFIG.SESSION_TIMEOUT_MS);
    }

    function showWarning() {
        // 既存の警告を削除
        const existingAlerts = document.querySelectorAll(SELECTORS.WARNING_ALERT);
        existingAlerts.forEach(el => el.remove());

        // 警告表示（Bootstrapアラート）
        const alertHtml = `
            <div class="alert alert-warning alert-dismissible fade show position-fixed top-0 start-50 translate-middle-x mt-3" 
                 role="alert" style="z-index: ${CONFIG.ALERT_Z_INDEX}; max-width: ${CONFIG.ALERT_MAX_WIDTH};">
                <i class="bi bi-exclamation-triangle-fill me-2"></i>
                <strong>セッションタイムアウト警告</strong><br>
                30秒後に自動的にログアウトされます。
                <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
            </div>
        `;

        document.body.insertAdjacentHTML('afterbegin', alertHtml);
    }

    function autoLogout() {
        const form = document.createElement('form');
        Object.assign(form, {
            method: 'POST',
            action: '/logout'
        });

        // CSRFトークンを取得
        const csrfToken = getCsrfToken();

        if (csrfToken) {
            const input = document.createElement('input');
            Object.assign(input, {
                type: 'hidden',
                name: '_csrf',
                value: csrfToken
            });
            form.appendChild(input);
        }

        document.body.appendChild(form);
        form.submit();
    }

    function getCsrfToken() {
        // metaタグから取得
        const meta = document.querySelector(SELECTORS.CSRF_TOKEN);
        if (meta) return meta.getAttribute('content');

        // Cookieから取得（フォールバック）
        const cookies = document.cookie.split(';');
        for (const cookie of cookies) {
            const [name, value] = cookie.trim().split('=');
            if (name === 'XSRF-TOKEN') return decodeURIComponent(value);
        }
        return null;
    }


    // ユーザーアクティビティでタイマーをリセット
    const resetOnActivity = () => resetTimers();

    USER_EVENTS.forEach(event => {
        document.addEventListener(event, resetOnActivity, true);
    });

    // 初期タイマー開始
    resetTimers();

    console.log(`Session timeout initialized: ${CONFIG.SESSION_TIMEOUT_MS / 1000} seconds`);
})();

