// main.html内のインラインJSを外部ファイル化
(function () {
    const loginModal = document.getElementById('loginModal');
    if (!loginModal) return;

    const form = document.getElementById('loginModalForm');
    const usernameInput = document.getElementById('modalUsername');
    const passwordInput = document.getElementById('modalPassword');
    const toggleBtn = document.getElementById('modalTogglePassword');
    const toggleIcon = document.getElementById('modalToggleIcon');
    const errorAlert = document.getElementById('loginErrorAlert');
    const submitBtn = document.getElementById('loginSubmitBtn');

    // モーダル表示時にユーザー名にフォーカス
    loginModal.addEventListener('shown.bs.modal', () => {
        usernameInput.focus();
    });

    // モーダルを閉じた時にフォームをリセット
    loginModal.addEventListener('hidden.bs.modal', () => {
        form.reset();
        errorAlert.classList.add('d-none');
        errorAlert.textContent = '';
        passwordInput.type = 'password';
        toggleIcon.classList.remove('bi-eye-slash');
        toggleIcon.classList.add('bi-eye');
    });

    // パスワード表示切替
    if (toggleBtn) {
        toggleBtn.addEventListener('click', (e) => {
            e.preventDefault();
            const type = passwordInput.type === 'password' ? 'text' : 'password';
            passwordInput.type = type;
            toggleIcon.classList.toggle('bi-eye');
            toggleIcon.classList.toggle('bi-eye-slash');
        });
    }

    // Ajaxでログイン処理
    form.addEventListener('submit', async (e) => {
        e.preventDefault();

        const username = usernameInput.value.trim();
        const password = passwordInput.value.trim();

        if (!username || !password) {
            errorAlert.textContent = 'ユーザー名とパスワードを入力してください。';
            errorAlert.classList.remove('d-none');
            return;
        }

        submitBtn.disabled = true;
        submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>ログイン中...';
        errorAlert.classList.add('d-none');

        try {
            // ここでAjaxリクエストを実装
            // ...
        } finally {
            submitBtn.disabled = false;
            submitBtn.innerHTML = '<i class="bi bi-box-arrow-in-right me-1"></i>ログイン';
        }
    });
})();

