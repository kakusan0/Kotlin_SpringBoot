(() => {
    const b64uToArray = (b64) => Uint8Array.from(atob(b64.replace(/-/g, "+").replace(/_/g, "/")), c => c.charCodeAt(0));
    const arrayToB64u = (buf) => {
        const bytes = new Uint8Array(buf);
        const chunkSize = 0x8000;
        let binary = '';

        for (let index = 0; index < bytes.length; index += chunkSize) {
            binary += String.fromCharCode(...bytes.subarray(index, index + chunkSize));
        }

        return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=/g, "");
    };

    const regBtn = document.getElementById('btnPasskeyRegister');
    const regStatus = document.getElementById('registerStatus');

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
                if (!window.PublicKeyCredential || !navigator.credentials?.create) {
                    throw new Error('このブラウザはパスキー登録に対応していません');
                }

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
})();
