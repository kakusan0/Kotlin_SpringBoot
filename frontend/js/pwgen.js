// パスワード生成ユーティリティ（pwgen.js）
// - 目的: パスワード生成UIの初期化、イベント委譲、生成・コピー・表示切替等の操作を提供します。
// - 特長: フラグメント差し替えや動的挿入に対しても安全に動作するよう、idempotent な初期化と MutationObserver/フォールバック初期化を備えます。
// - 実装方針: グローバル名前空間の衝突を避けるために window.__pwgen を使い、重複バインドや短時間の再初期化を防止します。

// global guard to prevent duplicate attachment across multiple init calls
if (!window.__pwgen) window.__pwgen = {listenersAttached: false};

function initPwgen() {
    // 初期化のデバウンス（短い間隔での重複実行を抑制）
    try {
        var now = Date.now();
        // ensure shared lastInit exists
        if (typeof window.__pwgen.lastInit !== 'number') window.__pwgen.lastInit = 0;
        if (window.__pwgen.lastInit && now - window.__pwgen.lastInit < 250) {
            return;
        }
        window.__pwgen.lastInit = now;
    } catch (_) {
    }

    // DOM 要素の参照（必要なものだけを初期に取得し、その他は都度取得して stale 参照を回避）
    let lengthEl = document.getElementById('length');
    let lengthValueEl = document.getElementById('length-value');
    let generateBtn = document.getElementById('generate-btn');
    let copyBtn = document.getElementById('copy-btn');
    let showHideBtn = document.getElementById('show-hide-btn');

    // パスワード表示状態
    let pwVisible = false;

    // トグル要素の id リスト（必要時に都度取得して使う）
    const toggleIds = ['use-lower', 'use-upper', 'use-num', 'use-symbol'];

    // 以下、補助関数群: DOM 操作やトグル状態更新、生成ロジックなど

    function getToggleEl(id) {
        return document.getElementById(id);
    }

    // 要素のクローン差し替えで旧リスナを除去するユーティリティ
    function cloneReplace(el) {
        if (!el || !el.parentNode) return el;
        try {
            const newEl = el.cloneNode(true);
            el.parentNode.replaceChild(newEl, el);
            return newEl;
        } catch (e) {
            return el;
        }
    }

    // length input の初期化（スライダー/数値入力）
    if (lengthEl && lengthValueEl) {
        lengthEl = cloneReplace(lengthEl);
        lengthEl.addEventListener('input', () => {
            lengthValueEl.textContent = lengthEl.value;
        });
    }

    // ホイールでの増減処理（軽いスロットルあり）
    let wheelQueued = false;
    if (lengthEl) {
        lengthEl.addEventListener('wheel', (e) => {
            const ev = e;
            const min = Number(lengthEl.min || 4);
            const max = Number(lengthEl.max || 64);
            const step = ev.shiftKey ? 5 : 1;
            let val = Number(lengthEl.value);
            if (ev.deltaY < 0) val = Math.min(max, val + step);
            else val = Math.max(min, val - step);
            if (val !== Number(lengthEl.value)) {
                e.preventDefault();
                if (!wheelQueued) {
                    wheelQueued = true;
                    requestAnimationFrame(() => {
                        lengthEl.value = val;
                        lengthEl.dispatchEvent(new Event('input', {bubbles: true}));
                        wheelQueued = false;
                    });
                }
            }
        }, {passive: false});
    }

    // トグル状態の視覚更新
    function setToggleState(el, on) {
        if (!el) return;
        el.setAttribute('aria-pressed', on ? 'true' : 'false');
        el.dataset.checked = on ? 'ON' : 'OFF';
        el.classList.toggle('opt-on', !!on);
    }

    function anyToggleOn() {
        return toggleIds.some(id => {
            const el = getToggleEl(id);
            return el && el.dataset && el.dataset.checked === 'ON';
        });
    }

    function allTogglesOn() {
        return toggleIds.every(id => {
            const el = getToggleEl(id);
            return el && el.dataset && el.dataset.checked === 'ON';
        });
    }

    function resetPasswordDisplay() {
        try {
            const _generatedPassword = document.getElementById('generated-password');
            if (_generatedPassword) _generatedPassword.value = '';
        } catch (_) {
        }
        try {
            const _pwStrength = document.getElementById('pw-strength');
            if (_pwStrength) _pwStrength.style.width = '0%';
        } catch (_) {
        }
        try {
            const _pwStrengthLabel = document.getElementById('pw-strength-label');
            if (_pwStrengthLabel) _pwStrengthLabel.textContent = '';
        } catch (_) {
        }
        try {
            const _pwgenResult = document.getElementById('pwgen-result');
            if (_pwgenResult) _pwgenResult.style.display = 'none';
        } catch (_) {
        }
    }

    function updateGenerateBtnState() {
        try {
            const btn = document.getElementById('generate-btn');
            if (!btn) return;
            btn.disabled = !anyToggleOn();
        } catch (_) {
        }
    }

    // パスワード生成のコア処理
    function doGenerate() {
        // 必要な要素は都度参照して stale を回避
        const _lengthEl = document.getElementById('length');
        const _useLower = document.getElementById('use-lower');
        const _useUpper = document.getElementById('use-upper');
        const _useNum = document.getElementById('use-num');
        const _useSymbol = document.getElementById('use-symbol');
        const _generatedPassword = document.getElementById('generated-password');
        const _pwgenResult = document.getElementById('pwgen-result');
        const _pwStrength = document.getElementById('pw-strength');
        const _pwStrengthLabel = document.getElementById('pw-strength-label');

        const len = Math.max(4, Math.min(64, parseInt((_lengthEl && _lengthEl.value) || 12) || 12));
        const pw = generatePassword(
            len,
            (_useLower && _useLower.dataset && _useLower.dataset.checked === 'ON'),
            (_useUpper && _useUpper.dataset && _useUpper.dataset.checked === 'ON'),
            (_useNum && _useNum.dataset && _useNum.dataset.checked === 'ON'),
            (_useSymbol && _useSymbol.dataset && _useSymbol.dataset.checked === 'ON')
        );

        if (anyToggleOn()) {
            if (_generatedPassword) {
                _generatedPassword.value = pw;
                _generatedPassword.type = pwVisible ? 'text' : 'password';
            }
            if (_pwgenResult) _pwgenResult.style.display = 'block';
        } else {
            resetPasswordDisplay();
        }
        const score = calcStrength(pw);
        const info = strengthLabel(score);
        if (_pwStrength) _pwStrength.style.width = (score * 20) + '%';
        if (_pwStrengthLabel) _pwStrengthLabel.textContent = info.label;
    }

    // 初期化: トグルの見た目を DOM から初期化
    toggleIds.forEach(id => {
        const el = getToggleEl(id);
        if (el) {
            const init = el.dataset.checked === 'ON';
            setToggleState(el, init);
        }
    });

    // Delegated handler for toggles (covers dynamic/replaced elements)
    // Use a short suppression window to so pointerdown+click don't double-toggle
    const recentToggle = new Map();
    const SUPPRESSION_MS = 300;

    function handleDelegatedToggle(id) {
        const now = Date.now();
        const last = recentToggle.get(id) || 0;
        if (now - last < SUPPRESSION_MS) return; // skip duplicate
        recentToggle.set(id, now);
        const el = getToggleEl(id);
        if (el) {
            const current = el.dataset.checked === 'ON';
            setToggleState(el, !current);
            updateGenerateBtnState();
            // if any toggle is OFF (i.e. not all ON), hide/reset the generated password
            if (!allTogglesOn()) resetPasswordDisplay();
        }
        // clear after timeout to avoid memory leaks
        setTimeout(() => recentToggle.delete(id), SUPPRESSION_MS + 50);
    }

    // Attach document-level delegated listeners only once
    if (!window.__pwgen.listenersAttached) {
        // delegated handler for generate button to ensure clicks are handled even if the button was replaced
        document.addEventListener('click', function (e) {
            const g = e.target.closest('#generate-btn');
            if (!g) return;
            e.preventDefault();
            try {
                doGenerate();
            } catch (err) {
                // silently ignore
            }
        });
        // pointerdown delegated as fallback (some devices trigger pointer events instead)
        document.addEventListener('pointerdown', function (e) {
            const g = e.target.closest('#generate-btn');
            if (!g) return;
            e.preventDefault();
            try {
                doGenerate();
            } catch (err) {
                // silently ignore
            }
        });

        document.addEventListener('pointerdown', function (e) {
            const btn = e.target.closest('.opt-btn');
            if (!btn) return;
            const id = btn.id;
            if (!toggleIds.includes(id)) return;
            e.preventDefault();
            handleDelegatedToggle(id);
        });

        // click fallback for environments without pointer events
        document.addEventListener('click', function (e) {
            const btn = e.target.closest('.opt-btn');
            if (!btn) return;
            const id = btn.id;
            if (!toggleIds.includes(id)) return;
            handleDelegatedToggle(id);
        });

        // keyboard shortcuts
        document.addEventListener('keydown', (e) => {
            if (e.altKey || e.ctrlKey || e.metaKey) return;
            const active = document.activeElement;
            if (active && (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA' || active.isContentEditable)) return;
            const map = {'1': 'use-lower', '2': 'use-upper', '3': 'use-num', '4': 'use-symbol'};
            const id = map[e.key];
            const target = id ? getToggleEl(id) : null;
            if (target) {
                e.preventDefault();
                const newState = target.dataset.checked !== 'ON';
                setToggleState(target, newState);
                updateGenerateBtnState();
                if (!allTogglesOn()) resetPasswordDisplay();
                try {
                    target.focus();
                } catch (_) {
                }
            }
        });

        window.__pwgen.listenersAttached = true;
    }

    // generate
    if (generateBtn) {
        try {
            // avoid binding twice: mark element when bound
            if (!generateBtn.dataset.pwgenBound) {
                const newBtn = cloneReplace(generateBtn) || generateBtn;
                newBtn.addEventListener('click', (e) => {
                    e.preventDefault();
                    try {
                        doGenerate();
                    } catch (err) {
                        // silently ignore
                    }
                });
                newBtn.dataset.pwgenBound = '1';
            }
        } catch (_) {
        }

        // ensure generate button state reflects latest toggle states after cloning and binding
        try {
            updateGenerateBtnState();
        } catch (_) {
        }

        // If any toggle is OFF on init, hide any previously generated result to avoid stale display
        try {
            if (!allTogglesOn()) resetPasswordDisplay();
        } catch (_) {
        }

        // copy
        if (copyBtn) {
            try {
                if (!copyBtn.dataset.pwgenBound) {
                    const newCopy = cloneReplace(copyBtn) || copyBtn;
                    newCopy.addEventListener('click', () => {
                        const val = (document.getElementById('generated-password') || {}).value || '';
                        if (!val) return;
                        navigator.clipboard.writeText(val).then(() => {
                            const tmp = document.createElement('div');
                            tmp.className = 'pw-toast';
                            tmp.textContent = 'コピーしました';
                            document.body.appendChild(tmp);
                            setTimeout(() => {
                                tmp.style.transition = 'opacity 0.3s';
                                tmp.style.opacity = '0';
                                setTimeout(() => tmp.remove(), 300);
                            }, 1000);
                        });
                    });
                    newCopy.dataset.pwgenBound = '1';
                }
            } catch (_) {
            }

            // show/hide
            if (showHideBtn) {
                try {
                    if (!showHideBtn.dataset.pwgenBound) {
                        const newShow = cloneReplace(showHideBtn) || showHideBtn;
                        newShow.addEventListener('click', () => {
                            pwVisible = !pwVisible;
                            newShow.textContent = pwVisible ? '非表示' : '表示';
                            const gen = document.getElementById('generated-password');
                            if (gen) gen.type = pwVisible ? 'text' : 'password';
                        });
                        newShow.dataset.pwgenBound = '1';
                    }
                } catch (_) {
                }
            }

            if (lengthEl) lengthEl.focus();
        }

        // keep DOMContentLoaded auto-init for first load
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', function () {
                initPwgen();
            });
        } else {
            // already loaded - safe to init once
            try {
                initPwgen();
            } catch (_) {
            }
        }

        // Also ensure init runs on full window load as a fallback (covers edge cases of defer/order)
        try {
            window.addEventListener('load', function () {
                try {
                    initPwgen();
                } catch (_) {
                }
            });
        } catch (_) {
        }

        // Extra short-delay fallback in case scripts are dynamically manipulated
        setTimeout(function () {
            try {
                initPwgen();
            } catch (_) {
            }
        }, 200);

        // MutationObserver fallback: if relevant elements are inserted later (fragment/AJAX), ensure init runs
        (function () {
            try {
                // attach only once globally to avoid creating many observers that each retrigger inits
                if (window.__pwgen.observerAttached) return;
                window.__pwgen.observerAttached = true;
                const observedIds = new Set(['generate-btn', 'length', 'use-lower']);
                const MIN_REINIT_MS = 300; // debounce re-inits
                const obs = new MutationObserver((mutations) => {
                    try {
                        const now = Date.now();
                        if (typeof window.__pwgen.lastInit !== 'number') window.__pwgen.lastInit = 0;
                        if (now - window.__pwgen.lastInit < MIN_REINIT_MS) return;
                        for (const m of mutations) {
                            if (m.addedNodes && m.addedNodes.length) {
                                for (const node of m.addedNodes) {
                                    if (!(node instanceof Element)) continue;
                                    for (const id of observedIds) {
                                        if (node.id === id || (node.querySelector && node.querySelector('#' + id))) {
                                            try {
                                                initPwgen();
                                                window.__pwgen.lastInit = Date.now();
                                            } catch (_) {
                                            }
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_) {
                    }
                });
                if (document && document.body) obs.observe(document.body, {childList: true, subtree: true});
                // Stop observing after some time to avoid long-running overhead but keep for a short window
                setTimeout(() => {
                    try {
                        obs.disconnect();
                    } catch (_) {
                    }
                }, 60000);
            } catch (_) {
            }
        })();

        function generatePassword(length, useLower, useUpper, useNum, useSymbol) {
            let chars = '';
            if (useLower) chars += 'abcdefghijklmnopqrstuvwxyz';
            if (useUpper) chars += 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
            if (useNum) chars += '0123456789';
            if (useSymbol) chars += '!@#$%^&*()';
            if (!chars) return '';

            let password = '';
            // 暗号学的に安全な乱数生成器を使用
            if (window.crypto && window.crypto.getRandomValues) {
                const array = new Uint32Array(length);
                window.crypto.getRandomValues(array);
                for (let i = 0; i < length; i++) {
                    password += chars.charAt(array[i] % chars.length);
                }
            } else {
                // フォールバック（非推奨）: 古いブラウザ向け
                console.warn('crypto.getRandomValues not available, falling back to Math.random()');
                for (let i = 0; i < length; i++) {
                    password += chars.charAt(Math.floor(Math.random() * chars.length));
                }
            }
            return password;
        }

        function calcStrength(pw) {
            let score = 0;
            if (pw.length >= 8) score++;
            if (/[a-z]/.test(pw)) score++;
            if (/[A-Z]/.test(pw)) score++;
            if (/[0-9]/.test(pw)) score++;
            if (/[!@#$%^&*()]/.test(pw)) score++;
            return score;
        }

        function strengthLabel(score) {
            switch (score) {
                case 5:
                    return {label: '非常に強い'};
                case 4:
                    return {label: '強い'};
                case 3:
                    return {label: '普通'};
                case 2:
                    return {label: '弱い'};
                default:
                    return {label: 'とても弱い'};
            }
        }
    }
}

// Fallback initializers: ensure initPwgen runs on full page refresh even when elements
// are not yet present when the script executes (e.g. defer ordering, bfcache, etc.).
// initPwgen is idempotent/debounced, so calling it multiple times is safe.
(function () {
    try {
        // DOMContentLoaded / immediate run
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', function () {
                try {
                    if (typeof initPwgen === 'function') initPwgen();
                } catch (_) {
                }
            });
        } else {
            try {
                if (typeof initPwgen === 'function') initPwgen();
            } catch (_) {
            }
        }

        // window load fallback
        try {
            window.addEventListener('load', function () {
                try {
                    if (typeof initPwgen === 'function') initPwgen();
                } catch (_) {
                }
            });
        } catch (_) {
        }

        // short timeout fallback
        setTimeout(function () {
            try {
                if (typeof initPwgen === 'function') initPwgen();
            } catch (_) {
            }
        }, 250);

        // lightweight MutationObserver fallback: if pwgen elements are inserted later, trigger init
        if (!window.__pwgen || !window.__pwgen.fallbackObserverAttached) {
            try {
                window.__pwgen = window.__pwgen || {};
                window.__pwgen.fallbackObserverAttached = true;
                const obs = new MutationObserver((mutations) => {
                    try {
                        for (const m of mutations) {
                            if (m.addedNodes && m.addedNodes.length) {
                                for (const node of m.addedNodes) {
                                    if (!(node instanceof Element)) continue;
                                    if (node.id === 'generate-btn' || node.querySelector && node.querySelector('#generate-btn')) {
                                        try {
                                            if (typeof initPwgen === 'function') initPwgen();
                                        } catch (_) {
                                        }
                                        obs.disconnect();
                                        return;
                                    }
                                }
                            }
                        }
                    } catch (_) {
                    }
                });
                if (document && document.body) obs.observe(document.body, {childList: true, subtree: true});
                // disconnect after short window to avoid long-lived observers
                setTimeout(() => {
                    try {
                        obs.disconnect();
                    } catch (_) {
                    }
                }, 60000);
            } catch (_) {
            }
        }
    } catch (_) {
    }
})();
