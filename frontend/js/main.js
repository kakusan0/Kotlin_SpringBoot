// アプリ共通のメインクライアントスクリプト（main.js）
(function () {
    'use strict';

    // 定数定義
    const CONFIG = {
        IDLE_TIMEOUT: 200,
        DEBOUNCE_DELAY: 150,
        FETCH_TIMEOUT: 10000,
        MOBILE_BREAKPOINT: '(max-width: 767.98px)'
    };

    const SELECTORS = {
        PC_SIDEBAR_TOGGLE: '#pcSidebarToggle',
        SIDEBAR_MENU: '#sidebarMenu',
        MAIN_CONTENT: 'main.main-content',
        LIVE_TOAST_BTN: '#liveToastBtn',
        LIVE_TOAST: '#liveToast',
        ERROR_MODAL: '#errorModal',
        CONTENT_ITEM: '.content-item',
        MODAL_BACKDROP: '.modal-backdrop'
    };

    // Ensure pages restored from bfcache are reloaded to avoid showing stale or protected content
    window.addEventListener('pageshow', event => {
        if (event.persisted) window.location.reload();
    });

    // Utility: debounce to avoid frequent calls during resize
    const debounce = (fn, wait) => {
        let timer = null;
        return function (...args) {
            clearTimeout(timer);
            timer = setTimeout(() => fn.apply(this, args), wait);
        };
    };

    // runWhenIdle: ページがアイドルになったら初期化処理を実行
    const runWhenIdle = (fn) => {
        if ('requestIdleCallback' in window) {
            requestIdleCallback(fn, {timeout: CONFIG.IDLE_TIMEOUT});
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
            if (selector === window || selector === document || selector instanceof Element) {
                selector.addEventListener(evt, handler);
                return;
            }
            document.addEventListener(evt, e => {
                const el = e.target.closest(selector);
                if (el) handler.call(el, e);
            });
        };

        // PC: サイドバー折りたたみ切り替え
        on(SELECTORS.PC_SIDEBAR_TOGGLE, 'click', () => {
            const sidebar = document.querySelector(SELECTORS.SIDEBAR_MENU);
            if (sidebar) sidebar.classList.toggle('is-collapsed');

            document.querySelectorAll(SELECTORS.MAIN_CONTENT).forEach(m => {
                m.classList.toggle('is-collapsed');
            });
        });

        // サイドバーの Offcanvas を初期化
        const sidebarEl = document.querySelector(SELECTORS.SIDEBAR_MENU);
        if (sidebarEl && window.bootstrap?.Offcanvas) {
            bootstrap.Offcanvas.getOrCreateInstance(sidebarEl);
        }


        // ビューポート高さをCSSカスタムプロパティに反映
        const setAppHeight = () => {
            document.documentElement.style.setProperty('--app-height', `${window.innerHeight}px`);
        };
        window.addEventListener('resize', debounce(setAppHeight, CONFIG.DEBOUNCE_DELAY));
        setAppHeight();

        // Fallback: モバイルでヘッダーを下部に移動
        const mq = window.matchMedia?.(CONFIG.MOBILE_BREAKPOINT);
        const applyHeaderBottomClass = () => {
            document.body.classList.toggle('header-bottom', mq?.matches);
        };

        if (mq) {
            applyHeaderBottomClass();
            mq.addEventListener?.('change', applyHeaderBottomClass) || mq.addListener?.(applyHeaderBottomClass);
        }

        // トースト表示
        on(SELECTORS.LIVE_TOAST_BTN, 'click', () => {
            const el = document.querySelector(SELECTORS.LIVE_TOAST);
            if (el && window.bootstrap?.Toast) {
                bootstrap.Toast.getOrCreateInstance(el).show();
            }
        });

        // モーダル別にバックドロップの色を切替
        const errorModal = document.querySelector(SELECTORS.ERROR_MODAL);
        if (errorModal) {
            errorModal.addEventListener('shown.bs.modal', () => {
                const backdrops = document.querySelectorAll(SELECTORS.MODAL_BACKDROP);
                backdrops[backdrops.length - 1]?.classList.add('backdrop-error');
            });
        }

        // モーダル内のアイテム選択で /content をAJAX遷移
        on(SELECTORS.CONTENT_ITEM, 'click', function (e) {
            e.preventDefault();
            const screenName = this.dataset.screenName;
            if (!screenName) return;

            const url = new URL(window.location.href);
            url.pathname = '/content';
            url.searchParams.set('screenName', screenName);

            fetchWithTimeout(url.toString(), {credentials: 'same-origin'}, CONFIG.FETCH_TIMEOUT)
                .then(r => {
                    if (!r.ok) throw new Error('Fetch failed');
                    return r.text();
                })
                .then(html => {
                    const doc = new DOMParser().parseFromString(html, 'text/html');
                    const newMain = doc.querySelector(SELECTORS.MAIN_CONTENT);
                    if (newMain) {
                        const old = document.querySelector(SELECTORS.MAIN_CONTENT);
                        old?.replaceWith(newMain);
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
            const sn = ev.state?.screenName;
            if (!sn) return;

            const url = new URL(window.location.href);
            url.pathname = '/content';
            url.searchParams.set('screenName', sn);

            fetchWithTimeout(url.toString(), {credentials: 'same-origin'}, CONFIG.FETCH_TIMEOUT)
                .then(r => r.text())
                .then(html => {
                    const doc = new DOMParser().parseFromString(html, 'text/html');
                    const newMain = doc.querySelector(SELECTORS.MAIN_CONTENT);
                    if (newMain) {
                        const old = document.querySelector(SELECTORS.MAIN_CONTENT);
                        old?.replaceWith(newMain);
                        loadAndRunScriptsFromFragment(newMain);
                    }
                })
                .catch(() => {
                });
        });

        // helper: load scripts found inside a fragment
        function loadAndRunScriptsFromFragment(fragmentRoot) {
            try {
                const scripts = Array.from(fragmentRoot.querySelectorAll('script'));
                const loads = scripts.map(s => {
                    const src = s.getAttribute('src');

                    if (src) {
                        // Skip if already present
                        if (document.querySelector(`script[src="${src}"]`)) {
                            return Promise.resolve();
                        }

                        return new Promise(resolve => {
                            const sc = document.createElement('script');
                            Object.assign(sc, {
                                src,
                                async: false // preserve execution order
                            });
                            sc.onload = resolve;
                            sc.onerror = resolve; // Continue on error
                            document.head.appendChild(sc);
                        });
                    } else {
                        // inline script: execute by creating a new script element
                        try {
                            const inline = document.createElement('script');
                            inline.text = s.textContent || s.innerText || '';
                            document.head.appendChild(inline);
                        } catch (e) { /* ignore */
                        }
                        return Promise.resolve();
                    }
                });

                return Promise.all(loads);
            } catch (e) {
                return Promise.resolve();
            }
        }

        // Helper: fetch with timeout
        function fetchWithTimeout(url, options, timeout) {
            return Promise.race([
                fetch(url, options),
                new Promise((_, reject) =>
                    setTimeout(() => reject(new Error('Timeout')), timeout)
                )
            ]);
        }
    });

})();

