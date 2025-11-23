// アプリ共通のメインクライアントスクリプト（main.js）
// - 目的: ページ初期化・イベント委譲・サイドバー切替・フラグメントのスクリプト読み込み
// - パフォーマンス配慮: requestIdleCallback を利用してアイドル時に初期化

(function () {
    'use strict';

    // Ensure pages restored from bfcache are reloaded to avoid showing stale or protected content
    window.addEventListener('pageshow', function (event) {
        if (event.persisted) {
            window.location.reload();
        }
    });

    // Utility: debounce to avoid frequent calls during resize
    const debounce = (fn, wait) => {
        let t = null;
        return function (...args) {
            const ctx = this;
            clearTimeout(t);
            t = setTimeout(() => fn.apply(ctx, args), wait);
        };
    };


    // runWhenIdle: ページがアイドルになったら初期化処理を実行するヘルパ
    const runWhenIdle = (fn) => {
        if ('requestIdleCallback' in window) {
            requestIdleCallback(fn, {timeout: 200});
        } else {
            if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', fn);
            } else {
                fn();
            }
        }
    };

    runWhenIdle(function () {
        // イベント委譲ヘルパ
        const on = (selector, evt, handler) => {
            if (selector === window || selector === document) {
                (selector || window).addEventListener(evt, handler);
                return;
            }
            if (selector instanceof Element) {
                selector.addEventListener(evt, handler);
                return;
            }
            document.addEventListener(evt, function (e) {
                const target = e.target;
                const el = target.closest(selector);
                if (el) handler.call(el, e);
            });
        };

        // PC: サイドバー折りたたみ切り替え
        on('#pcSidebarToggle', 'click', () => {
            const sidebar = document.getElementById('sidebarMenu');
            if (sidebar) sidebar.classList.toggle('is-collapsed');
            const mains = document.querySelectorAll('.main-content');
            mains.forEach(m => m.classList.toggle('is-collapsed'));
        });

        // サイドバーの Offcanvas を初期化
        const sidebarEl = document.getElementById('sidebarMenu');
        if (sidebarEl && window.bootstrap && bootstrap.Offcanvas) {
            bootstrap.Offcanvas.getOrCreateInstance(sidebarEl);
        }


        // ビューポート高さをCSSカスタムプロパティに反映
        const setAppHeight = () => document.documentElement.style.setProperty('--app-height', `${window.innerHeight}px`);
        window.addEventListener('resize', debounce(setAppHeight, 150));
        setAppHeight();

        // Fallback: ensure header moves to bottom on mobile via class toggle
        const mobileQuery = '(max-width: 767.98px)';
        const mq = window.matchMedia ? window.matchMedia(mobileQuery) : null;
        const applyHeaderBottomClass = () => {
            if (!mq) return;
            if (mq.matches) {
                document.body.classList.add('header-bottom');
            } else {
                document.body.classList.remove('header-bottom');
            }
        };
        if (mq) {
            // initial
            applyHeaderBottomClass();
            // listen for changes
            if (typeof mq.addEventListener === 'function') {
                mq.addEventListener('change', applyHeaderBottomClass);
            } else if (typeof mq.addListener === 'function') {
                mq.addListener(applyHeaderBottomClass);
            }
        }

        // トースト表示
        on('#liveToastBtn', 'click', () => {
            const el = document.getElementById('liveToast');
            if (el && window.bootstrap && bootstrap.Toast) bootstrap.Toast.getOrCreateInstance(el).show();
        });

        // モーダル別にバックドロップの色を切替
        const errorModal = document.getElementById('errorModal');
        if (errorModal) errorModal.addEventListener('shown.bs.modal', () => {
            const b = document.querySelectorAll('.modal-backdrop');
            if (b.length) b[b.length - 1].classList.add('backdrop-error');
        });

        // モーダル内のアイテム選択で /content をAJAX遷移（失敗時は通常遷移）。fetchWithTimeout を使いタイムアウトをつける
        on('.content-item', 'click', function (e) {
            e.preventDefault();
            const screenName = this.dataset.screenName;
            if (!screenName) return;
            const url = new URL(window.location.href);
            url.pathname = '/content';
            url.searchParams.set('screenName', screenName);

            fetchWithTimeout(url.toString(), {credentials: 'same-origin'}, 10000)
                .then(r => {
                    if (!r.ok) throw 0;
                    return r.text();
                })
                .then(html => {
                    const doc = new DOMParser().parseFromString(html, 'text/html');
                    const newMain = doc.querySelector('main.main-content');
                    if (newMain) {
                        const old = document.querySelector('main.main-content');
                        old && old.replaceWith(newMain);
                        loadAndRunScriptsFromFragment(newMain);
                        history.pushState({screenName}, '', url.toString());
                    } else {
                        window.location.href = url.toString();
                    }
                })
                .catch(() => window.location.href = url.toString());
        });

        // 戻る/進むで main コンテンツを再取得
        window.addEventListener('popstate', ev => {
            const sn = (ev.state || {}).screenName;
            if (!sn) return;
            const url = new URL(window.location.href);
            url.pathname = '/content';
            url.searchParams.set('screenName', sn);
            fetchWithTimeout(url.toString(), {credentials: 'same-origin'}, 10000)
                .then(r => r.text())
                .then(t => {
                    const doc = new DOMParser().parseFromString(t, 'text/html');
                    const nm = doc.querySelector('main.main-content');
                    if (nm) {
                        const om = document.querySelector('main.main-content');
                        om && om.replaceWith(nm);
                        loadAndRunScriptsFromFragment(nm);
                    }
                })
                .catch(() => {
                });
        });

        // helper: load scripts found inside a fragment (execute inline scripts, load external ones)
        function loadAndRunScriptsFromFragment(fragmentRoot) {
            try {
                const scripts = Array.from(fragmentRoot.querySelectorAll('script'));
                const loads = [];
                scripts.forEach(s => {
                    const src = s.getAttribute('src');
                    if (src) {
                        // resolve absolute URL (browser will handle relative)
                        const abs = src;
                        // skip if already present
                        if (!document.querySelector('script[src="' + abs + '"]')) {
                            loads.push(new Promise((res) => {
                                const sc = document.createElement('script');
                                sc.src = abs;
                                sc.async = false; // preserve execution order
                                sc.onload = () => res();
                                sc.onerror = () => { /* failed to load script (silently) */
                                    res();
                                };
                                document.head.appendChild(sc);
                            }));
                        }
                    } else {
                        // inline script: execute by creating a new script element
                        try {
                            const inline = document.createElement('script');
                            inline.text = s.textContent || s.innerText || '';
                            document.head.appendChild(inline);
                            // no need to wait
                        } catch (e) { /* ignore */
                        }
                    }
                });
                return Promise.all(loads);
            } catch (e) {
                return Promise.resolve();
            }
        }


    });

})();

// End of main.js
