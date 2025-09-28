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

    // PC: サイドバー折りたたみ切替
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
          }
        })
        .catch(() => {});
    });

  });

})();

// End of main.js
