// アプリ共通のメインクライアントスクリプト（main.js）
// - 目的: ページ初期化・イベント委譲・サイドバーのメニュー注入・フラグメントのスクリプト読み込み・pwgenの初期化など
// - パフォーマンス配慮: requestIdleCallback を利用してアイドル時に初期化を行い、ユーザー体験を向上します
// - フォールバック: DOMContentLoaded / load / setTimeout / MutationObserver により動的挿入や遅延読み込みに対応します

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

  // Simple in-memory cache for content API responses
  const contentCache = {
    ttl: 30000, // 30 seconds
    all: { data: null, ts: 0 }, // cache for /all
    byMenu: new Map(), // key: menuName -> { data, ts }
    clear() {
      this.all = { data: null, ts: 0 };
      this.byMenu.clear();
    }
  };

  async function getContentScreens(menuName) {
    const now = Date.now();
    try {
      if (!menuName) {
        // use /api/content/all
        if (contentCache.all.data && (now - contentCache.all.ts) < contentCache.ttl) {
          return contentCache.all.data;
        }
        const resp = await fetchWithTimeout('/api/content/all', { credentials: 'same-origin' }, 10000);
        if (!resp || !resp.ok) return [];
        const data = await resp.json();
        contentCache.all = { data, ts: now };
        return data;
      } else {
        const key = String(menuName);
        const hit = contentCache.byMenu.get(key);
        if (hit && (now - hit.ts) < contentCache.ttl) {
          return hit.data;
        }
        const resp = await fetchWithTimeout('/api/content?menuName=' + encodeURIComponent(key), { credentials: 'same-origin' }, 10000);
        if (!resp || !resp.ok) return [];
        const data = await resp.json();
        contentCache.byMenu.set(key, { data, ts: now });
        return data;
      }
    } catch (_) {
      return [];
    }
  }

  // runWhenIdle: ページがアイドルになったら初期化処理を実行するヘルパ
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

    // 追加: 選択中のサイドバーメニュー名（画面管理で登録した menuName）
    let selectedSidebarMenu = null;

    // サイドバーにサーバー上のメニュー（画面管理で登録された menuName の集合）を表示する
    async function loadAndRenderSidebarMenus() {
      try {
        const path = window.location && window.location.pathname ? window.location.pathname : '';
        // do not inject or modify manage page sidebar (manage page has its own sidebar content)
        if (path === '/manage' || path.startsWith('/manage')) return;

        const screens = await getContentScreens('');
        if (!Array.isArray(screens)) return;

        // exclude screens without a valid pathName, itemName, and menuName
        const hasValidData = (s) => {
          // Check pathName validity
          const pn = (s && s.pathName != null) ? String(s.pathName).trim() : '';
          const hasValidPath = pn && pn.toLowerCase() !== 'null' && pn.length > 0;

          // Check itemName validity (screen name must be set in management screen)
          const itemName = (s && s.itemName != null) ? String(s.itemName).trim() : '';
          const hasValidItemName = itemName && itemName.length > 0;

          // Check menuName validity
          const menuNameValue = (s && s.menuName != null) ? String(s.menuName).trim() : '';
          const hasValidMenuName = menuNameValue && menuNameValue.length > 0;

          return hasValidPath && hasValidItemName && hasValidMenuName;
        };
        const validScreens = screens.filter(hasValidData);

        // derive unique menu names from screens' menuName (only from screens with valid data)
        const menuNames = validScreens.map(s => s.menuName).filter(Boolean);
        const uniqueMenuNames = Array.from(new Set(menuNames));

        const ul = document.querySelector('#sidebarMenu .offcanvas-body ul.nav');
        if (!ul) return;

        // Remove previous injected menu items (marked with data-injected="true")
        Array.from(ul.querySelectorAll('li[data-injected="true"]')).forEach(n => n.remove());

        // Find the Manage item to insert before it, otherwise append
        const manageLi = Array.from(ul.querySelectorAll('li')).find(li => {
          const a = li.querySelector('a');
          return a && a.getAttribute('href') === '/manage';
        });

        uniqueMenuNames.forEach(menuName => {
          const label = String(menuName).trim();
          if (!label) return;
          // avoid duplicates: check for either server-rendered or previously injected items
          const existsByMenuAttr = ul.querySelector('a[data-menu-name="' + CSS.escape(label) + '"]');
          const existsByScreenAttr = ul.querySelector('a[data-screen-name="' + CSS.escape(label) + '"]');
          const existsByText = Array.from(ul.querySelectorAll('a')).some(a => (a.textContent || '').trim() === label);
          if (existsByMenuAttr || existsByScreenAttr || existsByText) return;

          const li = document.createElement('li');
          li.className = 'nav-item';
          li.setAttribute('data-injected', 'true');
          const a = document.createElement('a');
          a.className = 'nav-link text-dark sidebar-menu-link';
          a.href = '#';
          a.setAttribute('data-menu-name', label);
          a.innerHTML = '<i class="bi bi-folder"></i> <span>' + label + '</span>';
          // clicking a sidebar menu will set it as selected and open the content selection modal
          a.addEventListener('click', function (ev) {
            ev.preventDefault();
            selectedSidebarMenu = label;
            // open modal
            const modalEl = document.getElementById('scrollableModal');
            if (modalEl && window.bootstrap && bootstrap.Modal) {
              // populate modal body before showing
              // fetch filtered screens from server for the selected menu
              (async function () {
                try {
                  const filteredScreens = await getContentScreens(label);
                  // apply same data validation filter on modal items
                  const filteredValid = Array.isArray(filteredScreens) ? filteredScreens.filter(hasValidData) : [];
                  if (filteredValid.length) {
                    populateContentModal(selectedSidebarMenu, filteredValid);
                  } else {
                    populateContentModal(selectedSidebarMenu, validScreens);
                  }
                } catch (e) { populateContentModal(selectedSidebarMenu, validScreens); }
               })();
               const inst = bootstrap.Modal.getOrCreateInstance(modalEl);
               inst.show();
             } else {
               // fallback: populate only
              (async function () {
                try {
                  const filteredScreens = await getContentScreens(label);
                  const filteredValid = Array.isArray(filteredScreens) ? filteredScreens.filter(hasValidData) : [];
                  if (filteredValid.length) {
                    populateContentModal(selectedSidebarMenu, filteredValid);
                    return;
                  }
                } catch (e) { /* ignore */ }
                populateContentModal(selectedSidebarMenu, validScreens);
               })();
             }
           });

          if (manageLi) ul.insertBefore(li, manageLi);
          else ul.appendChild(li);
          li.appendChild(a);
        });

      } catch (e) {
        // silent fail
      }
    }

    // Populate the content selection modal list-group with screens filtered by menuName
    function populateContentModal(menuName, screens) {
      try {
        const modal = document.getElementById('scrollableModal');
        if (!modal) return;
        // Update the small label in the modal header to show which sidebar/menu is being displayed
        try {
          const sidebarLabelEl = modal.querySelector('#modalSidebarName');
          if (sidebarLabelEl) sidebarLabelEl.textContent = menuName ? String(menuName) : '（全て）';
        } catch (_) { /* ignore */ }
        const listGroup = modal.querySelector('.modal-body .list-group');
        if (!listGroup) return;
        // clear existing
        listGroup.innerHTML = '';
        let items = screens || [];
        // If no menu selected, prompt user to select a menu from the sidebar
        if (!menuName) {
          const el = document.createElement('div');
          el.className = 'list-group-item text-muted';
          el.textContent = 'サイドバーのメニューを選択してください';
          listGroup.appendChild(el);
          return;
        }
        if (menuName) {
          items = items.filter(s => s.menuName === menuName);
        }
        // exclude items without valid pathName AND valid itemName - use same validation as sidebar menu loading
        items = items.filter(s => {
          // Check pathName validity
          const pn = (s && s.pathName != null) ? String(s.pathName).trim() : '';
          const hasValidPath = pn && pn.toLowerCase() !== 'null' && pn.length > 0;

          // Check itemName validity (screen name must be set in management screen)
          const itemName = (s && s.itemName != null) ? String(s.itemName).trim() : '';
          const hasValidItemName = itemName && itemName.length > 0;

          // Check menuName validity
          const menuNameValue = (s && s.menuName != null) ? String(s.menuName).trim() : '';
          const hasValidMenuName = menuNameValue && menuNameValue.length > 0;

          return hasValidPath && hasValidItemName && hasValidMenuName;
        });
        if (!items.length) {
          const el = document.createElement('div');
          el.className = 'list-group-item text-muted';
          el.textContent = '該当する画面がありません';
          listGroup.appendChild(el);
          return;
        }
        items.forEach(s => {
          const a = document.createElement('a');
          a.className = 'list-group-item list-group-item-action text-start content-item';
          a.href = '#';
          a.setAttribute('data-screen-name', s.itemName);
          // Preserve the menuName on each item so the click handler can update the header to show menuName
          if (menuName) a.setAttribute('data-menu-name', menuName);
          a.textContent = s.itemName || '(無題)';
          listGroup.appendChild(a);
        });
      } catch (e) { /* ignore */ }
    }

    // 初回ロードでメニューを描画
    loadAndRenderSidebarMenus();

    // When header's select button opens modal, populate it using currently cached screens
    const itemSelectButton = document.getElementById('itemSelectButton');
    if (itemSelectButton) {
      itemSelectButton.addEventListener('click', async function () {
        // fetch screens and populate modal, filter by selectedSidebarMenu if set
        // Prefer the sidebar-selection variable if it's been set; otherwise use the header label
        const headerLabelEl = document.getElementById('selectedItemName');
        const headerLabel = headerLabelEl ? (headerLabelEl.textContent || '').trim() : '';
        // ignore placeholder texts
        const headerName = (headerLabel && headerLabel !== '画面を選択' && headerLabel !== 'メニューを選択') ? headerLabel : '';
        const menuToUse = selectedSidebarMenu || headerName || '';
        try {
          const screens = await getContentScreens(menuToUse);
           populateContentModal(menuToUse, screens);
        } catch (e) { /* ignore */ }
      });
    }

    // BroadcastChannel で同一ブラウザ内のタブからの通知を受け取る（フォールバックで storage もある）
    if (typeof BroadcastChannel !== 'undefined') {
      try {
        const bch = new BroadcastChannel('menus-channel');
        bch.addEventListener('message', function (ev) {
          if (!ev) return;
          if (ev.data === 'menus-updated') {
            contentCache.clear();
            loadAndRenderSidebarMenus();
          }
        });
      } catch (err) {
        // ignore
      }
    }

    // 他タブで menus が更新されたときに再描画する（storage イベントは同一タブでは発火しない点に注意）
    window.addEventListener('storage', function (e) {
      if (!e) return;
      if (e.key === 'menus-updated') {
        contentCache.clear();
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

    // Invalidate caches when menus are updated or modal is hidden (optional)
    if (scrollableModal) scrollableModal.addEventListener('hidden.bs.modal', () => {
      // keep selection, but we could clear selection if desired
    });

    // モーダル内のアイテム選択で /content をAJAX遷移（失敗時は通常遷移）。fetchWithTimeout を使いタイムアウトをつける
    on('.content-item', 'click', function (e) {
      e.preventDefault();
      const screenName = this.dataset.screenName;
      const clickedMenuName = (this.dataset && this.dataset.menuName) ? this.dataset.menuName : (selectedSidebarMenu || '');
      if (!screenName) return;
      const url = new URL(window.location.href);
      url.pathname = '/content';
      url.searchParams.set('screenName', screenName);

      fetchWithTimeout(url.toString(), { credentials: 'same-origin' }, 10000)
        .then(resp => {
          if (!resp.ok) {
            // レスポンスが正常でない場合はフルナビゲーションへフォールバック
            return Promise.reject('network');
          }
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
              if (selectedEl) {
                // Prefer the clicked menuName (if available) to show menu name in the header; otherwise use server-provided value
                selectedEl.textContent = clickedMenuName || newSelectedName;
              }
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
                } catch (err) { /* initPwgen failed (silently) */ }
              })();
              // Keep menuName in history state so popstate can restore header label correctly
              history.pushState({ screenName, menuName: clickedMenuName }, '', url.toString());
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
      const stateMenuName = state.menuName || null;
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
            if (selectedEl) selectedEl.textContent = (stateMenuName || newSelectedName);
            // Load fragment scripts, wait for pwgen elements, then initialize
            (async function () {
              await loadAndRunScriptsFromFragment(newMain);
              try {
                try { if (typeof initPwgen === 'function') initPwgen(); } catch (_) {}
                setTimeout(() => { try { if (typeof initPwgen === 'function') initPwgen(); } catch (_) {} }, 120);
                setTimeout(() => { try { if (typeof initPwgen === 'function') initPwgen(); } catch (_) {} }, 350);
              } catch (err) { /* initPwgen failed (silently) */ }
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
                sc.onerror = () => { /* failed to load script (silently) */ res(); };
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


  });

})();

// End of main.js
