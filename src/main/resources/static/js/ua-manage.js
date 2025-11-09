(function(){
  'use strict';

  // CSRF helpers (same as other pages)
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

  async function addRule(pattern, matchType) {
    const res = await fetch('/api/ua-blacklist', {
      method: 'POST',
      headers: getHeaders(true),
      body: JSON.stringify({ pattern, matchType })
    });
    if (!res.ok) {
      let msg = 'ルール追加に失敗しました';
      try { const j = await res.json(); if (j && j.message) msg = j.message; } catch(_){}
      throw new Error(msg);
    }
    return res.json();
  }

  async function deleteRule(id) {
    const res = await fetch(`/api/ua-blacklist/${encodeURIComponent(id)}`, {
      method: 'DELETE',
      headers: getHeaders(false)
    });
    if (!res.ok && res.status !== 204) {
      throw new Error('ルール削除に失敗しました');
    }
  }

  function bind() {
    const addBtn = document.getElementById('btnAddUaRule');
    const patternEl = document.getElementById('uaPattern');
    const typeEl = document.getElementById('uaMatchType');

    if (addBtn && patternEl && typeEl) {
      addBtn.addEventListener('click', async () => {
        const pattern = (patternEl.value || '').trim();
        const matchType = (typeEl.value || 'EXACT').trim();
        if (!pattern) {
          alert('パターンを入力してください');
          return;
        }
        addBtn.disabled = true;
        try {
          await addRule(pattern, matchType);
          location.reload();
        } catch(e) {
          console.error(e);
          alert(e.message || 'ルール追加に失敗しました');
        } finally {
          addBtn.disabled = false;
        }
      });
    }

    document.querySelectorAll('.btn-ua-delete').forEach(btn => {
      btn.addEventListener('click', async () => {
        const id = btn.getAttribute('data-id');
        if (!id) return;
        if (!confirm('このルールを削除しますか？')) return;
        btn.disabled = true;
        try {
          await deleteRule(id);
          location.reload();
        } catch(e) {
          console.error(e);
          alert('削除に失敗しました');
        } finally {
          btn.disabled = false;
        }
      });
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', bind);
  } else {
    bind();
  }
})();

