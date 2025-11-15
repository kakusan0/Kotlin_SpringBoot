// Session Timeout Auto Logout
(function () {
    'use strict';

    // セッションタイムアウト時間（5分 = 300秒）
    const SESSION_TIMEOUT_MS = 5 * 60 * 1000;
    // 警告表示時間（タイムアウト30秒前）
    const WARNING_BEFORE_MS = 30 * 1000;

    let timeoutTimer = null;
    let warningTimer = null;

    function resetTimers() {
        // 既存のタイマーをクリア
        if (timeoutTimer) clearTimeout(timeoutTimer);
        if (warningTimer) clearTimeout(warningTimer);

        // 警告タイマー（タイムアウト30秒前）
        warningTimer = setTimeout(() => {
            showWarning();
        }, SESSION_TIMEOUT_MS - WARNING_BEFORE_MS);

        // 自動ログアウトタイマー
        timeoutTimer = setTimeout(() => {
            autoLogout();
        }, SESSION_TIMEOUT_MS);
    }

    function showWarning() {
        // 警告表示（Bootstrapアラート）
        const alertHtml = `
            <div class="alert alert-warning alert-dismissible fade show position-fixed top-0 start-50 translate-middle-x mt-3" 
                 role="alert" style="z-index: 9999; max-width: 500px;">
                <i class="bi bi-exclamation-triangle-fill me-2"></i>
                <strong>セッションタイムアウト警告</strong><br>
                30秒後に自動的にログアウトされます。
                <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
            </div>
        `;

        // 既存の警告を削除
        document.querySelectorAll('.alert-warning[role="alert"]').forEach(el => el.remove());

        // 新しい警告を追加
        document.body.insertAdjacentHTML('afterbegin', alertHtml);
    }

    function autoLogout() {
        // ログアウト処理（CSRFトークンを含むPOSTリクエスト）
        const form = document.createElement('form');
        form.method = 'POST';
        form.action = '/logout';

        // CSRFトークンを取得
        const csrfToken = getCsrfToken();
        const csrfHeader = getCsrfHeaderName();

        if (csrfToken && csrfHeader) {
            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = csrfHeader.replace('X-', '').replace(/-/g, '').toLowerCase();
            // Spring Securityのデフォルトパラメータ名に変換
            if (input.name === 'xsrftoken') {
                input.name = '_csrf';
            }
            input.value = csrfToken;
            form.appendChild(input);
        }

        document.body.appendChild(form);
        form.submit();
    }

    function getCsrfToken() {
        // metaタグから取得
        const meta = document.querySelector('meta[name="_csrf"]');
        if (meta) return meta.getAttribute('content');

        // Cookieから取得
        const cookies = document.cookie.split(';');
        for (let cookie of cookies) {
            const [name, value] = cookie.trim().split('=');
            if (name === 'XSRF-TOKEN') return decodeURIComponent(value);
        }
        return null;
    }

    function getCsrfHeaderName() {
        const meta = document.querySelector('meta[name="_csrf_header"]');
        return meta ? meta.getAttribute('content') : 'X-XSRF-TOKEN';
    }

    // ユーザーアクティビティでタイマーをリセット
    const events = ['mousedown', 'mousemove', 'keypress', 'scroll', 'touchstart', 'click'];

    events.forEach(event => {
        document.addEventListener(event, () => {
            resetTimers();
        }, true);
    });

    // 初期タイマー開始
    resetTimers();

    console.log('Session timeout initialized: 5 minutes');
})();

