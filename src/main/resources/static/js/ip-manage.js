// IP管理ページ用スクリプト（検索・操作）
(function(){
  'use strict';

  // --- CSRF helpers ---
  function getCsrfToken() {
    const meta = document.querySelector('meta[name="_csrf"]');
    if (meta) {
      const token = meta.getAttribute('content');
      if (token) return token;
    }
    const cookies = document.cookie.split(';');
    for (let cookie of cookies) {
      const [name, value] = cookie.trim().split('=');
      if (name === 'XSRF-TOKEN') return decodeURIComponent(value);
    }
    return null;
  }
  function getHeaders(includeContentType = true) {
    const headers = {};
    const token = getCsrfToken();
    const headerNameMeta = document.querySelector('meta[name="_csrf_header"]');
    const headerName = headerNameMeta ? headerNameMeta.getAttribute('content') : 'X-XSRF-TOKEN';
    if (token) headers[headerName] = token;
    if (includeContentType) headers['Content-Type'] = 'application/json';
    return headers;
  }

  // --- API base ---
  const apiIp = '/api/ip';

  // --- 検索フィルタ ---
  function applyIpFilter(queryRaw) {
    const q = (queryRaw || '').trim().toLowerCase();

    // 対象テーブルID
    const tables = [
      { id: 'whitelistTable', emptyText: '該当するIPがありません' },
      { id: 'blacklistTable', emptyText: '該当するIPがありません' }
    ];

    tables.forEach(({id, emptyText}) => {
      const table = document.getElementById(id);
      if (!table) return;
      const thead = table.querySelector('thead');
      const tbody = table.querySelector('tbody');
      if (!tbody) return;

      // 既存の no-match 行を除去
      Array.from(tbody.querySelectorAll('tr.no-match')).forEach(tr => tr.remove());

      let visibleCount = 0;
      const rows = Array.from(tbody.querySelectorAll('tr'));
      rows.forEach(tr => {
        if (tr.classList.contains('no-match')) return; // 念のため
        const ipAttr = (tr.getAttribute('data-ip') || '').toLowerCase();
        const ipCell = (tr.cells && tr.cells[1] && (tr.cells[1].textContent || '').toLowerCase()) || '';
        const ip = ipAttr || ipCell;
        if (!q) {
          tr.style.display = '';
          visibleCount++;
        } else {
          const matched = ip && ip.indexOf(q) !== -1;
          tr.style.display = matched ? '' : 'none';
          if (matched) visibleCount++;
        }
      });

      if (visibleCount === 0) {
        const thCount = thead ? thead.querySelectorAll('th').length : (rows[0]?.cells?.length || 1);
        const noTr = document.createElement('tr');
        noTr.className = 'no-match';
        const td = document.createElement('td');
        td.colSpan = thCount;
        td.className = 'text-muted';
        td.textContent = emptyText;
        noTr.appendChild(td);
        tbody.appendChild(noTr);
      }
    });
  }

  // デバウンス
  function debounce(fn, wait) {
    let t = null;
    return function(...args){
      clearTimeout(t);
      t = setTimeout(() => fn.apply(this, args), wait);
    };
  }

  // URLクエリ q から初期値を反映
  function getQueryParam(name) {
    try { return new URL(window.location.href).searchParams.get(name); } catch(_) { return null; }
  }
  function setQueryParam(name, value) {
    try {
      const url = new URL(window.location.href);
      if (value) url.searchParams.set(name, value); else url.searchParams.delete(name);
      window.history.replaceState({}, '', url);
    } catch(_) {}
  }

  function bindSearchUi() {
    const input = document.getElementById('ipSearchInput');
    const clearBtn = document.getElementById('ipSearchClear');
    if (!input) return;

    const initQ = getQueryParam('q') || '';
    if (initQ) {
      input.value = initQ;
      applyIpFilter(initQ);
    } else {
      applyIpFilter('');
    }

    const onInput = debounce(() => {
      const q = input.value || '';
      setQueryParam('q', q.trim());
      applyIpFilter(q);
    }, 150);

    input.addEventListener('input', onInput);
    input.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        onInput();
      }
    });
    if (clearBtn) {
      clearBtn.addEventListener('click', () => {
        input.value = '';
        setQueryParam('q', '');
        applyIpFilter('');
        input.focus();
      });
    }
  }

  // --- ブラックリスト管理 ---
  async function onAddToBlacklist() {
    const sel = document.getElementById('whitelistSelectForBlacklist');
    if (!sel) { alert('セレクトボックスが見つかりません'); return; }
    const ip = (sel.value || '').trim();
    if (!ip) { alert('IPアドレスを選択してください'); return; }
    try {
      const resp = await fetch(`${apiIp}/blacklist`, {
        method: 'POST',
        headers: getHeaders(),
        body: JSON.stringify({ ipAddress: ip })
      });
      if (!resp.ok) {
        alert('ブラックリスト登録に失敗しました');
        return;
      }
      // 反映のためリロード（サーバーサイド描画のテーブル更新）
      setTimeout(() => location.reload(), 300);
    } catch (e) {
      console.error(e);
      alert('ブラックリスト登録に失敗しました');
    }
  }

  async function onDeleteFromBlacklist(id) {
    if (!id) return;
    if (!confirm('このIPをブラックリストから論理削除しますか？')) return;
    try {
      const resp = await fetch(`${apiIp}/blacklist/${id}`, {
        method: 'DELETE',
        headers: getHeaders(false)
      });
      if (!resp.ok) {
        alert('ブラックリスト削除に失敗しました');
        return;
      }
      setTimeout(() => location.reload(), 300);
    } catch (e) {
      console.error(e);
      alert('ブラックリスト削除に失敗しました');
    }
  }

  function bindActions() {
    const addBtn = document.getElementById('btnAddToBlacklist');
    if (addBtn && !addBtn.__bound) {
      addBtn.addEventListener('click', onAddToBlacklist);
      addBtn.__bound = true;
    }
    document.querySelectorAll('.btn-blacklist-delete').forEach(btn => {
      if (!btn.__bound) {
        btn.addEventListener('click', () => onDeleteFromBlacklist(btn.getAttribute('data-id')));
        btn.__bound = true;
      }
    });
  }

  function init() {
    bindSearchUi();
    bindActions();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();

