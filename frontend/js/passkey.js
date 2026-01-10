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

    // ログインフロー（ユーザー名あり/なし両対応）
    if (loginBtn) {
        loginBtn.addEventListener('click', async () => {
            const username = (loginUserInput?.value || '').trim();
            try {
                showStatus(loginStatus, 'チャレンジ取得中...');

                // ユーザー名があれば従来の認証、なければDiscoverable認証
                const optionsUrl = username
                    ? `/api/webauthn/authentication/options?username=${encodeURIComponent(username)}`
                    : '/api/webauthn/authentication/options';

                const res = await fetch(optionsUrl);
                if (!res.ok) {
                    const msg = (await res.json().catch(() => ({}))).message || 'options取得失敗';
                    throw new Error(msg);
                }
                const options = await res.json();
                const challengeId = options.challengeId; // Discoverable認証用

                const publicKeyOptions = {
                    challenge: b64uToArray(options.challenge).buffer,
                    rpId: options.rpId,
                    timeout: options.timeout,
                    userVerification: options.userVerification
                };

                // allowCredentialsがある場合のみ設定（Discoverable認証では空）
                if (options.allowCredentials && options.allowCredentials.length > 0) {
                    publicKeyOptions.allowCredentials = options.allowCredentials.map(ac => ({
                        ...ac,
                        id: b64uToArray(ac.id).buffer
                    }));
                }

                showStatus(loginStatus, '認証中...');
                const assertion = await navigator.credentials.get({publicKey: publicKeyOptions});
                if (!assertion) throw new Error('認証に失敗');

                // 認証完了エンドポイント（Discoverable認証用かどうかで分岐）
                const finishUrl = challengeId
                    ? `/api/webauthn/authentication/finish/discoverable?challengeId=${encodeURIComponent(challengeId)}`
                    : `/api/webauthn/authentication/finish?username=${encodeURIComponent(username)}`;

                const finishRes = await fetch(finishUrl, {
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
                const result = await finishRes.json();
                showStatus(loginStatus, `ログイン成功${result.username ? ` (${result.username})` : ''}。リダイレクトします...`);
                setTimeout(() => window.location.href = '/tools', 300);
            } catch (e) {
                console.error(e);
                showStatus(loginStatus, `エラー: ${e.message}`, true);
            }
        });
    }
})();
