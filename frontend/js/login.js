document.addEventListener('DOMContentLoaded', () => {
    const usernameInput = document.getElementById('username');
    const passwordInput = document.getElementById('password');
    const loginForm = document.getElementById('loginForm');
    const togglePassword = document.getElementById('togglePassword');
    const toggleIcon = document.getElementById('toggleIcon');

    if (!usernameInput || !passwordInput || !loginForm || !togglePassword || !toggleIcon) {
        return;
    }

    togglePassword.addEventListener('click', () => {
        const type = passwordInput.getAttribute('type') === 'password' ? 'text' : 'password';
        passwordInput.setAttribute('type', type);
        toggleIcon.classList.toggle('bi-eye');
        toggleIcon.classList.toggle('bi-eye-slash');
    });

    passwordInput.addEventListener('blur', (e) => {
        if (e.relatedTarget && e.relatedTarget.id === 'togglePassword') return;
        if (usernameInput.value.trim() && passwordInput.value.trim()) loginForm.submit();
    });

    passwordInput.addEventListener('keypress', (e) => {
        if (e.key !== 'Enter') return;
        e.preventDefault();
        if (usernameInput.value.trim() && passwordInput.value.trim()) loginForm.submit();
    });

    if (usernameInput.value.trim()) passwordInput.focus();
});

