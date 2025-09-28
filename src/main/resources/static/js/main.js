// filepath: c:\Users\gmaki\Downloads\demo (1)\demo\src\main\resources\static\js\main.js
// メインクライアントスクリプト（初期化はアイドル時またはDOMContentLoaded後に実行）

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

  // Utility: fetch with timeout using AbortController
  const fetchWithTimeout = (resource, options = {}, timeout = 10000) => {
    const controller = new AbortController();
    const id = setTimeout(() => controller.abort(), timeout);
    const signal = controller.signal;
    return fetch(resource, Object.assign({}, options, { signal })).finally(() => clearTimeout(id));
  };

  const runWhenIdle = (fn) => {
    if ('requestIdleCallback' in window) {
      requestIdleCallback(fn, { timeout: 200 });
    } else {
      if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', fn);
      } else {
        fn();
      }
    }
  };

  runWhenIdle(function () {
    // イベント委譲ヘルパ（selector に発火する evt を handler に委譲）
    // selector: CSS selector string OR DOM element OR window
    const on = (selector, evt, handler) => {
      if (selector === window || selector === document) {
        (selector || window).addEventListener(evt, handler);
        return;
      }

      // if selector is an element
      if (selector instanceof Element) {
        selector.addEventListener(evt, handler);
        return;
      }

      // delegation on document
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

    // サイドバーにサーバー上のメニューを表示する
    async function loadAndRenderSidebarMenus() {
      // do not inject menus into the manage page sidebar
      try {
        const path = window.location && window.location.pathname ? window.location.pathname : '';
        if (path === '/manage' || path.startsWith('/manage')) return;
      } catch (_) { /* safe fallback */ }
      try {
        const resp = await fetch('/api/menus/all');
        if (!resp.ok) throw new Error('network');
        const menus = await resp.json();
        if (!Array.isArray(menus)) return;
        const ul = document.querySelector('#sidebarMenu .offcanvas-body ul.nav');
        if (!ul) return;

        // Remove previous injected menu items (marked with data-injected="true")
        Array.from(ul.querySelectorAll('li[data-injected="true"]')).forEach(n => n.remove());

        // Find the Manage item to insert before it, otherwise append
        const manageLi = Array.from(ul.querySelectorAll('li')).find(li => {
          const a = li.querySelector('a');
          return a && a.getAttribute('href') === '/manage';
        });

        // Create li items for each menu
        menus.forEach(menu => {
          const screenName = (menu && menu.name) ? String(menu.name).trim() : '';
          // skip empty names
          if (!screenName) return;
          // if an element with same data-screen-name already exists, skip to avoid duplicate
          if (ul.querySelector('a[data-screen-name="' + CSS.escape(screenName) + '"]')) return;

          const li = document.createElement('li');
          li.className = 'nav-item';
          li.setAttribute('data-injected', 'true');
          const a = document.createElement('a');
          a.className = 'nav-link text-dark content-item';
          a.href = '#';
          a.setAttribute('data-screen-name', screenName);
          a.innerHTML = '<i class="bi bi-folder"></i> <span>' + screenName + '</span>';
          li.appendChild(a);
          if (manageLi) ul.insertBefore(li, manageLi);
          else ul.appendChild(li);
        });
      } catch (e) {
        // silently fail — menu injection is progressive enhancement
        console.error('loadAndRenderSidebarMenus failed', e);
      }
    }

    // 初回ロードでメニューを描画
    loadAndRenderSidebarMenus();

    // BroadcastChannel で同一ブラウザ内のタブからの通知を受け取る（フォールバックで storage もある）
    if (typeof BroadcastChannel !== 'undefined') {
      try {
        const bch = new BroadcastChannel('menus-channel');
        bch.addEventListener('message', function (ev) {
          if (!ev) return;
          if (ev.data === 'menus-updated') loadAndRenderSidebarMenus();
        });
      } catch (err) {
        // ignore
      }
    }

    // 他タブで menus が更新されたときに再描画する（storage イベントは同一タブでは発火しない点に注意）
    window.addEventListener('storage', function (e) {
      if (!e) return;
      if (e.key === 'menus-updated') {
        loadAndRenderSidebarMenus();
      }
    });

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

    const scrollableModal = document.getElementById('scrollableModal');
    if (scrollableModal) scrollableModal.addEventListener('shown.bs.modal', () => {
      const b = document.querySelectorAll('.modal-backdrop');
      if (b.length) b[b.length - 1].classList.add('backdrop-select');
    });

    // モーダル内のアイテム選択で /content をAJAX遷移（失敗時は通常遷移）。fetchWithTimeout を使いタイムアウトをつける
    on('.content-item', 'click', function (e) {
      e.preventDefault();
      const screenName = this.dataset.screenName;
      if (!screenName) return;
      const url = new URL(window.location.href);
      url.pathname = '/content';
      url.searchParams.set('screenName', screenName);

      fetchWithTimeout(url.toString(), { credentials: 'same-origin' }, 10000)
        .then(resp => {
          if (!resp.ok) throw new Error('network');
          return resp.text();
        })
        .then(responseText => {
          try {
            const parser = new DOMParser();
            const doc = parser.parseFromString(responseText, 'text/html');
            const newMain = doc.querySelector('main.main-content');
            const newSelectedName = (doc.querySelector('#selectedItemName') || {}).textContent || screenName;
            if (newMain) {
              const oldMain = document.querySelector('main.main-content');
              if (oldMain) oldMain.replaceWith(newMain);
              const selectedEl = document.getElementById('selectedItemName');
              if (selectedEl) selectedEl.textContent = newSelectedName;
              const modalEl = document.getElementById('scrollableModal');
              if (modalEl && window.bootstrap && bootstrap.Modal) {
                const modalInst = bootstrap.Modal.getInstance(modalEl) || bootstrap.Modal.getOrCreateInstance(modalEl);
                modalInst.hide();
              }
              // Load fragment scripts, wait for pwgen elements, then initialize
              (async function () {
                await loadAndRunScriptsFromFragment(newMain);
                try {
                  // call init immediately and schedule retries to catch late DOM insertion
                  try { if (typeof initPwgen === 'function') initPwgen(); } catch (_) {}
                  setTimeout(() => { try { if (typeof initPwgen === 'function') initPwgen(); } catch (_) {} }, 120);
                  setTimeout(() => { try { if (typeof initPwgen === 'function') initPwgen(); } catch (_) {} }, 350);
                } catch (err) { console.error('initPwgen failed', err); }
              })();
              history.pushState({ screenName }, '', url.toString());
              return;
            }
          } catch (err) {
            // パース失敗時はフル遷移
          }
          window.location.href = url.toString();
        })
        .catch(() => {
          window.location.href = url.toString();
        });
    });

    // 戻る/進むで main コンテンツを再取得
    window.addEventListener('popstate', function (event) {
      const state = event.state || {};
      const screenName = state.screenName || null;
      if (!screenName) return;
      const url = new URL(window.location.href);
      url.pathname = '/content';
      url.searchParams.set('screenName', screenName);
      fetchWithTimeout(url.toString(), { credentials: 'same-origin' }, 10000)
        .then(resp => resp.text())
        .then(responseText => {
          const parser = new DOMParser();
          const doc = parser.parseFromString(responseText, 'text/html');
          const newMain = doc.querySelector('main.main-content');
          const newSelectedName = (doc.querySelector('#selectedItemName') || {}).textContent || screenName;
          if (newMain) {
            const oldMain = document.querySelector('main.main-content');
            if (oldMain) oldMain.replaceWith(newMain);
            const selectedEl = document.getElementById('selectedItemName');
            if (selectedEl) selectedEl.textContent = newSelectedName;
            // Load fragment scripts, wait for pwgen elements, then initialize
            (async function () {
              await loadAndRunScriptsFromFragment(newMain);
              try {
                try { if (typeof initPwgen === 'function') initPwgen(); } catch (_) {}
                setTimeout(() => { try { if (typeof initPwgen === 'function') initPwgen(); } catch (_) {} }, 120);
                setTimeout(() => { try { if (typeof initPwgen === 'function') initPwgen(); } catch (_) {} }, 350);
              } catch (err) { console.error('initPwgen failed', err); }
            })();
          }
        })
        .catch(() => {});
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
                sc.onerror = () => { console.error('failed to load script', abs); res(); };
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
            } catch (e) { /* ignore */ }
          }
        });
        return Promise.all(loads);
      } catch (e) {
        return Promise.resolve();
      }
    }

    // helper: wait until pwgen-related elements exist, with timeout
    function ensurePwgenReady(timeout = 2000) {
      return new Promise((resolve) => {
        const check = () => {
          const hasEl = !!(document.getElementById('generate-btn') || document.getElementById('length'));
          const hasFn = typeof window.initPwgen === 'function';
          return hasEl && hasFn;
        };
        if (check()) return resolve(true);
        const start = Date.now();
        const iv = setInterval(() => {
          if (check()) {
            clearInterval(iv);
            return resolve(true);
          }
          if (Date.now() - start > timeout) {
            clearInterval(iv);
            return resolve(false);
          }
        }, 80);
      });
    }

  });

})();

// End of main.js
