(() => {
    const b64uToArray = (b64) => Uint8Array.from(atob(b64.replace(/-/g, "+").replace(/_/g, "/")), c => c.charCodeAt(0));
    const arrayToB64u = (buf) => btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=/g, "");

    const regBtn = document.getElementById('btnPasskeyRegister');
    const regStatus = document.getElementById('registerStatus');
    const loginBtn = document.getElementById('btnPasskeyLogin');
    const loginStatus = document.getElementById('loginStatus');
    const loginUserInput = document.getElementById('passkeyLoginUsername');

    const showStatus = (el, msg, isError = false) => {
        if (!el) return;
        el.textContent = msg;
        el.classList.toggle('text-danger', isError);
        el.classList.toggle('text-muted', !isError);
    };

    // 登録フロー
    if (regBtn) {
        regBtn.addEventListener('click', async () => {
            try {
                const username = (window.currentUserName || '').trim();
                if (!username) {
                    showStatus(regStatus, 'ログイン中のユーザーが取得できません', true);
                    return;
                }
                showStatus(regStatus, 'チャレンジ取得中...');
                const res = await fetch(`/api/webauthn/registration/options?username=${encodeURIComponent(username)}`);
                if (!res.ok) throw new Error('options取得失敗');
                const options = await res.json();

                options.challenge = b64uToArray(options.challenge).buffer;
                options.user.id = b64uToArray(options.user.id).buffer;
                options.pubKeyCredParams = options.pubKeyCredParams.map(p => ({type: p.type, alg: p.alg}));

                const cred = await navigator.credentials.create({publicKey: options});
                if (!cred) throw new Error('credential作成に失敗');

                const finishRes = await fetch(`/api/webauthn/registration/finish?username=${encodeURIComponent(username)}`, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({
                        id: cred.id,
                        rawId: arrayToB64u(cred.rawId),
                        type: cred.type,
                        response: {
                            attestationObject: arrayToB64u(cred.response.attestationObject),
                            clientDataJSON: arrayToB64u(cred.response.clientDataJSON)
                        }
                    })
                });

                if (!finishRes.ok) throw new Error('登録完了に失敗');
                showStatus(regStatus, '登録が完了しました');
            } catch (e) {
                console.error(e);
                showStatus(regStatus, `エラー: ${e.message}`, true);
            }
        });
    }

    // ログインフロー
    if (loginBtn) {
        loginBtn.addEventListener('click', async () => {
            const username = (loginUserInput?.value || '').trim();
            if (!username) {
                showStatus(loginStatus, 'ユーザー名を入力してください', true);
                return;
            }
            try {
                showStatus(loginStatus, 'チャレンジ取得中...');
                const res = await fetch(`/api/webauthn/authentication/options?username=${encodeURIComponent(username)}`);
                if (!res.ok) {
                    const msg = (await res.json().catch(() => ({}))).message || 'options取得失敗';
                    throw new Error(msg);
                }
                const options = await res.json();

                options.challenge = b64uToArray(options.challenge).buffer;
                options.allowCredentials = options.allowCredentials.map(ac => ({
                    ...ac,
                    id: b64uToArray(ac.id).buffer
                }));

                const assertion = await navigator.credentials.get({publicKey: options});
                if (!assertion) throw new Error('認証に失敗');

                const finishRes = await fetch(`/api/webauthn/authentication/finish?username=${encodeURIComponent(username)}`, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({
                        id: assertion.id,
                        rawId: arrayToB64u(assertion.rawId),
                        type: assertion.type,
                        response: {
                            authenticatorData: arrayToB64u(assertion.response.authenticatorData),
                            clientDataJSON: arrayToB64u(assertion.response.clientDataJSON),
                            signature: arrayToB64u(assertion.response.signature),
                            userHandle: assertion.response.userHandle ? arrayToB64u(assertion.response.userHandle) : null
                        }
                    })
                });

                if (!finishRes.ok) {
                    const msg = (await finishRes.json().catch(() => ({}))).message || '認証完了に失敗';
                    throw new Error(msg);
                }
                showStatus(loginStatus, 'ログイン成功。ページをリロードします...');
                setTimeout(() => window.location.reload(), 300);
            } catch (e) {
                console.error(e);
                showStatus(loginStatus, `エラー: ${e.message}`, true);
            }
        });
    }
})();
