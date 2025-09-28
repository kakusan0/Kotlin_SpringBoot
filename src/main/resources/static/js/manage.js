// 管理画面用クライアントスクリプト（manage.js）
// - 役割: コンテンツ項目とメニューの一覧取得、追加・編集・削除を行う
// - 非同期通信は fetch を使用し、簡易なプロンプトUIで動作します
// - エラー時はユーザーへ alert を表示してフォールバックします

// Simple manage.js: fetch list of content items and provide add/edit/delete using prompt dialogs
(function () {
  'use strict';

  // API エンドポイントの定義
  const apiContent = '/api/content';
  const apiMenus = '/api/menus';
  const apiPaths = '/api/paths';

  // DOM ヘルパ関数
  function qs(sel) { return document.querySelector(sel); }
  function qsa(sel) { return Array.from(document.querySelectorAll(sel)); }

  // キャッシュ: メニュー一覧を保持して選択肢を高速に構築
  let menusCache = [];
  // キャッシュ: パス一覧（有効のみ）を保持
  // 削除（無効）含む全件を保持し、UIで disabled 表示を行う
  let pathsCache = [];

  // BroadcastChannel を使って同一ブラウザ内のタブ間で更新を通知する
  const menusBroadcast = (typeof BroadcastChannel !== 'undefined') ? new BroadcastChannel('menus-channel') : null;

  // --- レンダリング用関数 ---
  // テーブル行を作成するユーティリティ
  function renderMenuRow(menu) {
    const tr = document.createElement('tr');
    const idTd = document.createElement('td'); idTd.textContent = menu.id || '';
    const nameTd = document.createElement('td'); nameTd.textContent = menu.name || '';
    const actionsTd = document.createElement('td');

    const editBtn = document.createElement('button');
    editBtn.className = 'btn btn-sm btn-outline-primary me-2';
    editBtn.textContent = '編集';
    editBtn.addEventListener('click', () => onEditMenu(menu));

    const delBtn = document.createElement('button');
    delBtn.className = 'btn btn-sm btn-outline-danger';
    delBtn.textContent = '削除';
    delBtn.addEventListener('click', () => onDeleteMenu(menu));

    actionsTd.appendChild(editBtn);
    actionsTd.appendChild(delBtn);

    tr.appendChild(idTd);
    tr.appendChild(nameTd);
    tr.appendChild(actionsTd);
    return tr;
  }

  // パス管理用テーブル行を作成するユーティリティ
  function renderPathRow(path) {
    const tr = document.createElement('tr');
    const idTd = document.createElement('td'); idTd.textContent = path.id || '';
    const nameTd = document.createElement('td'); nameTd.textContent = path.name || '';
    const statusTd = document.createElement('td');
    const actionsTd = document.createElement('td');

    // 状態プルダウン（有効/無効）
    const statusSelect = document.createElement('select');
    statusSelect.className = 'form-select form-select-sm';
    const optEnabled = document.createElement('option'); optEnabled.value = 'enabled'; optEnabled.textContent = '有効';
    const optDisabled = document.createElement('option'); optDisabled.value = 'disabled'; optDisabled.textContent = '無効';
    statusSelect.appendChild(optEnabled);
    statusSelect.appendChild(optDisabled);
    statusSelect.value = (path.deleted ? 'disabled' : 'enabled');
    statusSelect.addEventListener('change', async () => {
      const willDisable = statusSelect.value === 'disabled';
      await onChangePathStatus(path, willDisable, statusSelect);
    });
    statusTd.appendChild(statusSelect);

    const editBtn = document.createElement('button');
    editBtn.className = 'btn btn-sm btn-outline-primary me-2';
    editBtn.textContent = '名称編集';
    editBtn.addEventListener('click', () => onEditPath(path));
    actionsTd.appendChild(editBtn);

    // 削除 / 復元 ボタンを追加
    if (path.deleted) {
      const restoreBtn = document.createElement('button');
      restoreBtn.className = 'btn btn-sm btn-outline-success ms-2';
      restoreBtn.textContent = '復元';
      restoreBtn.addEventListener('click', () => onRestorePath(path));
      actionsTd.appendChild(restoreBtn);
    } else {
      const delBtn = document.createElement('button');
      delBtn.className = 'btn btn-sm btn-outline-danger ms-2';
      delBtn.textContent = '削除';
      delBtn.addEventListener('click', () => onDeletePath(path));
      actionsTd.appendChild(delBtn);
    }

    tr.appendChild(idTd);
    tr.appendChild(nameTd);
    tr.appendChild(statusTd);
    tr.appendChild(actionsTd);
    return tr;
  }

  // セレクト要素を作成して現在値を反映する
  function buildMenuSelect(currentValue, item) {
    const select = document.createElement('select');
    select.className = 'form-select form-select-sm menu-select';
    // for debugging/tracking
    if (item && item.id) select.setAttribute('data-item-id', item.id);

    // ensure currentValue is represented even if it's not in menusCache
    const hasCurrent = menusCache.some(m => m.name === currentValue);

    // empty option
    const emptyOpt = document.createElement('option');
    emptyOpt.value = '';
    emptyOpt.textContent = '未選択';
    select.appendChild(emptyOpt);

    // add known menus
    menusCache.forEach(m => {
      const opt = document.createElement('option');
      opt.value = m.name;
      opt.textContent = m.name;
      select.appendChild(opt);
    });

    // if current value missing from menusCache, add it so the select shows it
    if (currentValue && !hasCurrent) {
      const opt = document.createElement('option');
      opt.value = currentValue;
      opt.textContent = currentValue + ' (削除済み)';
      // insert after empty option
      select.insertBefore(opt, select.children[1] || null);
    }

    select.value = currentValue || '';

    // handle change
    select.addEventListener('change', async function (ev) {
      const newVal = ev.target.value || null;
      await onChangeMenuForItem(item, newVal, ev.target);
    });

    return select;
  }

  // パス名用のセレクトを構築
  function buildPathSelect(currentValue, item) {
    const select = document.createElement('select');
    select.className = 'form-select form-select-sm path-select';
    if (item && item.id) select.setAttribute('data-item-id', item.id);

    const hasCurrent = pathsCache.some(p => p.name === currentValue);

    // 空（未選択）
    const emptyOpt = document.createElement('option');
    emptyOpt.value = '';
    emptyOpt.textContent = '未選択';
    select.appendChild(emptyOpt);

    // 有効/無効を含む全パス（無効は選択不可）
    pathsCache.forEach(p => {
      const opt = document.createElement('option');
      opt.value = p.name;
      opt.textContent = p.name + (p.deleted ? '（無効）' : '');
      if (p.deleted) opt.disabled = true;
      select.appendChild(opt);
    });

    // 現在値が（削除等で）有効リストにない場合は表示用に追加
    if (currentValue && !hasCurrent) {
      const opt = document.createElement('option');
      opt.value = currentValue;
      opt.textContent = currentValue + '（無効）';
      opt.disabled = true;
      select.insertBefore(opt, select.children[1] || null);
    }

    select.value = currentValue || '';

    // 変更ハンドラ
    select.addEventListener('change', async function (ev) {
      const newVal = ev.target.value || null;
      await onChangePathForItem(item, newVal, ev.target);
    });

    return select;
  }

  // 画面名用の入力欄を構築（インライン編集）
  function buildItemNameInput(currentValue, item) {
    const input = document.createElement('input');
    input.type = 'text';
    input.className = 'form-control form-control-sm name-input';
    if (item && item.id) input.setAttribute('data-item-id', item.id);
    input.value = currentValue || '';

    let prev = input.value;

    const commit = async () => {
      const newVal = (input.value != null) ? String(input.value).trim() : '';
      if (newVal === prev) return; // no change
      await onChangeItemName(item, newVal, input);
      // on success, prev is updated inside handler
    };

    input.addEventListener('change', commit);
    input.addEventListener('blur', commit);
    input.addEventListener('keydown', function (ev) {
      if (ev.key === 'Enter') {
        ev.preventDefault();
        input.blur(); // triggers commit
      } else if (ev.key === 'Escape') {
        ev.preventDefault();
        try { input.value = prev || ''; } catch (_) {}
        input.blur();
      }
    });

    // 外部から元値を更新できるようにヘルパ保管
    input.__getPrev = () => prev;
    input.__setPrev = (v) => { prev = v; };

    return input;
  }

  // 画面項目の行レンダリング
  function renderScreenRow(item) {
    const tr = document.createElement('tr');
    const idTd = document.createElement('td'); idTd.textContent = item.id || '';
    const menuTd = document.createElement('td');
    const nameTd = document.createElement('td');
    const pathTd = document.createElement('td');
    const actionsTd = document.createElement('td');

    const editBtn = document.createElement('button');
    editBtn.className = 'btn btn-sm btn-outline-primary me-2';
    editBtn.textContent = '編集';
    editBtn.addEventListener('click', () => onEditScreen(item));

    const delBtn = document.createElement('button');
    delBtn.className = 'btn btn-sm btn-outline-danger';
    delBtn.textContent = '削除';
    delBtn.addEventListener('click', () => onDeleteScreen(item));

    actionsTd.appendChild(editBtn);
    actionsTd.appendChild(delBtn);

    // create select for menu name
    const select = buildMenuSelect(item.menuName, item);
    menuTd.appendChild(select);

    // create input for item name (inline editable)
    const nameInput = buildItemNameInput(item.itemName, item);
    nameTd.appendChild(nameInput);

    // create select for pathName sourced from path master
    const pathSelect = buildPathSelect(item.pathName, item);
    pathTd.appendChild(pathSelect);

    tr.appendChild(idTd);
    tr.appendChild(menuTd);
    tr.appendChild(nameTd);
    tr.appendChild(pathTd);
    tr.appendChild(actionsTd);
    return tr;
  }

  // --- ヘルパ ---
  // メニューキャッシュが更新された際に、既存のセレクト要素を再構築する
  function refreshMenuSelects() {
    const selects = qsa('.menu-select');
    selects.forEach(sel => {
      const currentVal = sel.value || '';
      // try to preserve selection
      // rebuild options
      while (sel.firstChild) sel.removeChild(sel.firstChild);

      const emptyOpt = document.createElement('option');
      emptyOpt.value = '';
      emptyOpt.textContent = '未選択';
      sel.appendChild(emptyOpt);
      menusCache.forEach(m => {
        const opt = document.createElement('option');
        opt.value = m.name;
        opt.textContent = m.name;
        sel.appendChild(opt);
      });
      if (currentVal && !menusCache.some(m => m.name === currentVal)) {
        const opt = document.createElement('option');
        opt.value = currentVal;
        opt.textContent = currentVal + ' (削除済み)';
        sel.insertBefore(opt, sel.children[1] || null);
      }
      sel.value = currentVal;
    });
  }

  // パスキャッシュが更新された際に、既存のパスセレクトを再構築
  function refreshPathSelects() {
    const selects = qsa('.path-select');
    selects.forEach(sel => {
      const currentVal = sel.value || '';
      while (sel.firstChild) sel.removeChild(sel.firstChild);
      const emptyOpt = document.createElement('option');
      emptyOpt.value = '';
      emptyOpt.textContent = '未選択';
      sel.appendChild(emptyOpt);
      pathsCache.forEach(p => {
        const opt = document.createElement('option');
        opt.value = p.name;
        opt.textContent = p.name + (p.deleted ? '（無効）' : '');
        if (p.deleted) opt.disabled = true;
        sel.appendChild(opt);
      });
      if (currentVal && !pathsCache.some(p => p.name === currentVal)) {
        const opt = document.createElement('option');
        opt.value = currentVal;
        opt.textContent = currentVal + '（無効）';
        opt.disabled = true;
        sel.insertBefore(opt, sel.children[1] || null);
      }
      sel.value = currentVal;
    });
  }

  // 内容のメニュー変更をサーバへ送信するハンドラ
  async function onChangeMenuForItem(item, newMenuName, selectElem) {
    if (!item || item.id == null) return;
    const prev = item.menuName || null;
    if (newMenuName === prev) return; // nothing to do

    const payload = Object.assign({}, item, { menuName: newMenuName });
    try {
      const resp = await fetch(apiContent, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
      if (!resp.ok) {
        // サーバエラー: ユーザーへ通知して選択を元に戻す
        alert('メニューの更新に失敗しました');
        try { selectElem.value = prev || ''; } catch (_) {}
        return;
      }
      // update local item and UI
      item.menuName = newMenuName;
      // optional: show tiny feedback (could be improved)
      // e.g., briefly flash background
      selectElem.classList.add('is-valid');
      setTimeout(() => selectElem.classList.remove('is-valid'), 800);
    } catch (e) {
      alert('メニューの更新に失敗しました');
      // revert selection
      try { selectElem.value = prev || ''; } catch (_) { }
    }
  }

  // 内容のパス名変更をサーバへ送信するハンドラ
  async function onChangePathForItem(item, newPathName, inputElem) {
    if (!item || item.id == null) return;
    const prev = item.pathName || null;
    // normalize empty string to null
    const normalized = (newPathName == null || newPathName === '') ? null : String(newPathName).trim();
    if (normalized === prev) return; // nothing to do

    const payload = Object.assign({}, item, { pathName: normalized });
    try {
      const resp = await fetch(apiContent, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
      if (!resp.ok) {
        alert('パス名の更新に失敗しました');
        try { if (inputElem) inputElem.value = prev || ''; if (inputElem) inputElem.classList.add('is-invalid'); } catch (_) {}
        return;
      }
      // update local item and UI
      item.pathName = normalized;
      if (inputElem) {
        inputElem.classList.add('is-valid');
        setTimeout(() => inputElem.classList.remove('is-valid'), 800);
      }
    } catch (e) {
      alert('パス名の更新に失敗しました');
      try { if (inputElem) inputElem.value = prev || ''; } catch (_) { }
    }
  }

  // 画面名変更をサーバへ送信するハンドラ（インライン）
  async function onChangeItemName(item, newName, inputElem) {
    if (!item || item.id == null) return;
    const prev = item.itemName || '';
    // newName は空文字も許容（サーバ側に任せる）。正規化: trim のみ
    const normalized = (newName == null) ? '' : String(newName).trim();
    if (normalized === prev) return;

    const payload = Object.assign({}, item, { itemName: normalized });
    try {
      const resp = await fetch(apiContent, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
      if (!resp.ok) {
        alert('画面名の更新に失敗しました');
        try {
          if (inputElem) {
            inputElem.value = prev || '';
            inputElem.classList.add('is-invalid');
            setTimeout(() => inputElem.classList.remove('is-invalid'), 1000);
          }
        } catch (_) {}
        return;
      }
      // update local item and UI
      item.itemName = normalized;
      if (inputElem) {
        if (typeof inputElem.__setPrev === 'function') inputElem.__setPrev(normalized);
        inputElem.classList.add('is-valid');
        setTimeout(() => inputElem.classList.remove('is-valid'), 800);
      }
    } catch (e) {
      alert('画面名の更新に失敗しました');
      try { if (inputElem) inputElem.value = prev || ''; } catch (_) { }
    }
  }

  // パスの状態変更（有効/無効）をPUT更新
  async function onChangePathStatus(path, willDisable, selectElem) {
    const prev = !!path.deleted;
    if (prev === willDisable) return;
    const payload = Object.assign({}, path, { deleted: willDisable });
    try {
      const resp = await fetch(apiPaths, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
      if (!resp.ok) {
        alert('状態の更新に失敗しました');
        try { selectElem.value = prev ? 'disabled' : 'enabled'; } catch(_){}
        return;
      }
      path.deleted = willDisable;
      // パスの有効/無効に応じて画面管理のプルダウンを更新
      await loadPaths();
      await loadScreens();
    } catch (e) {
      alert('状態の更新に失敗しました');
      try { selectElem.value = prev ? 'disabled' : 'enabled'; } catch(_){}
    }
  }

  // --- データロード ---
  // パス一覧（有効のみ）を読み込みテーブルを更新
  async function loadPaths() {
    const tbody = qs('#pathTable tbody');
    // パス管理タブ用テーブルがないページではスキップ
    const isPathsPage = !!tbody;
    if (isPathsPage) tbody.innerHTML = '<tr><td colspan="4">読み込み中...</td></tr>';
    try {
      // 削除（無効）含む一覧を1回で取得し、キャッシュ・UI双方で利用
      const resp = await fetch(apiPaths + '/allIncludingDeleted');
      if (!resp.ok) {
        if (isPathsPage) tbody.innerHTML = '<tr><td colspan="4">読み込みに失敗しました</td></tr>';
        pathsCache = [];
        refreshPathSelects();
        return [];
      }
      const all = await resp.json();
      pathsCache = all || [];
      refreshPathSelects();
      tbody.innerHTML = '';
      if (!all || !all.length) {
        tbody.innerHTML = '<tr><td colspan="4">パスがありません</td></tr>';
        return pathsCache;
      }
      {
        const frag = document.createDocumentFragment();
        all.forEach(p => frag.appendChild(renderPathRow(p)));
        tbody.appendChild(frag);
      }
      return pathsCache;
    } catch (e) {
      if (isPathsPage) tbody.innerHTML = '<tr><td colspan="4">読み込みに失敗しました</td></tr>';
      pathsCache = [];
      refreshPathSelects();
      return [];
    }
  }

  // メニュー一覧を読み込みテーブルを更新
  async function loadMenus() {
    const tbody = qs('#menuTable tbody');
    if (!tbody) return [];
    tbody.innerHTML = '<tr><td colspan="3">読み込み中...</td></tr>';
    try {
      const resp = await fetch(apiMenus + '/all');
      if (!resp.ok) {
        tbody.innerHTML = '<tr><td colspan="3">読み込みに失敗しました</td></tr>';
        return [];
      }
      const menus = await resp.json();
      menusCache = menus || [];
      tbody.innerHTML = '';
      if (!menusCache || !menusCache.length) {
        // If there are no menus defined in menu management, try to show menuNames derived from screens
        // This avoids confusing mismatch where home shows menus (from screens) but admin shows none.
        // Load screens and extract unique menuName values.
        try {
          const r = await fetch(apiContent + '/all');
          if (!r.ok) {
            tbody.innerHTML = '<tr><td colspan="3">メニューがありません</td></tr>';
            // still refresh selects to show empty options
            refreshMenuSelects();
            return [];
          }
          const items = await r.json();
          const menuNames = Array.from(new Set((items || []).map(it => it.menuName).filter(Boolean)));
          if (!menuNames.length) {
            tbody.innerHTML = '<tr><td colspan="3">メニューがありません</td></tr>';
            // still refresh selects to show empty options
            refreshMenuSelects();
            return [];
          }
          const frag = document.createDocumentFragment();
          menuNames.forEach((name) => {
            const tr = document.createElement('tr');
            const idTd = document.createElement('td'); idTd.textContent = '';
            const nameTd = document.createElement('td'); nameTd.textContent = name + ' (画面管理由来)';
            const actionsTd = document.createElement('td');

            const importBtn = document.createElement('button');
            importBtn.className = 'btn btn-sm btn-outline-success me-2';
            importBtn.textContent = 'インポート';
            importBtn.addEventListener('click', async () => {
              // create menu via API
              try {
                const resp2 = await fetch(apiMenus, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name }) });
                if (!resp2.ok) {
                  alert('インポートに失敗しました');
                  return;
                }
                // reload official menus
                await loadMenus();
                await loadScreens();
              } catch (e) {
                alert('インポートに失敗しました');
              }
            });

            actionsTd.appendChild(importBtn);
            tr.appendChild(idTd);
            tr.appendChild(nameTd);
            tr.appendChild(actionsTd);
            frag.appendChild(tr);
          });
          tbody.appendChild(frag);
           // ensure selects are refreshed
           refreshMenuSelects();
           return menuNames;
        } catch (e) {
          tbody.innerHTML = '<tr><td colspan="3">メニューがありません</td></tr>';
          // still refresh selects to show empty options
          refreshMenuSelects();
          return [];
        }
      }
      {
        const frag = document.createDocumentFragment();
        menusCache.forEach(m => frag.appendChild(renderMenuRow(m)));
        tbody.appendChild(frag);
      }
       // update any existing select elements in screens
       refreshMenuSelects();
       return menusCache;
    } catch (e) {
      tbody.innerHTML = '<tr><td colspan="3">読み込みに失敗しました</td></tr>';
      return [];
    }
  }

  // 画面項目一覧を読み込みテーブルを更新
  async function loadScreens() {
    const tbody = qs('#manageTable tbody');
    if (!tbody) return [];
    tbody.innerHTML = '<tr><td colspan="5">読み込み中...</td></tr>';
    try {
      // ensure menusCache is populated so selects can be built properly
      if (!menusCache || !menusCache.length) {
        await loadMenus();
      }
      // ensure pathsCache is populated so path selects can be built properly
      if (!pathsCache || !pathsCache.length) {
        await loadPaths();
      }
      const resp = await fetch(apiContent + '/all');
      if (!resp.ok) {
        tbody.innerHTML = '<tr><td colspan="5">読み込みに失敗しました</td></tr>';
        return [];
      }
      const items = await resp.json();
      tbody.innerHTML = '';
      if (!items || !items.length) {
        tbody.innerHTML = '<tr><td colspan="5">項目がありません</td></tr>';
        return [];
      }
      {
        const frag = document.createDocumentFragment();
        items.forEach(it => frag.appendChild(renderScreenRow(it)));
        tbody.appendChild(frag);
      }
       return items;
    } catch (e) {
      tbody.innerHTML = '<tr><td colspan="5">読み込みに失敗しました</td></tr>';
      return [];
    }
  }

  // --- メニュー操作 ---
  // 追加・編集・削除の各操作（ユーザー確認/プロンプトを含む）
  async function onAddMenu() {
    const name = window.prompt('追加するメニュー名を入力してください', '新しいメニュー');
    if (!name) return;
    const payload = { name };
    try {
      const resp = await fetch(apiMenus, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
      if (!resp.ok) {
        alert('メニュー作成に失敗しました');
        return;
      }
      await loadMenus();
      // refresh menu dropdowns for adding screens
      await loadScreens();
      // notify other tabs/pages to refresh sidebar menus
      try { localStorage.setItem('menus-updated', String(Date.now())); } catch (_) { }
      try { if (menusBroadcast) menusBroadcast.postMessage('menus-updated'); } catch (_) { }
    } catch (e) {
      alert('メニュー作成に失敗しました');
    }
  }

  async function onEditMenu(menu) {
    const name = window.prompt('メニュー名を編集してください', menu.name || '');
    if (name == null) return;
    const payload = Object.assign({}, menu, { name });
    try {
      const resp = await fetch(apiMenus, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
      if (!resp.ok) {
        alert('更新に失敗しました');
        return;
      }
      await loadMenus();
      await loadScreens();
      try { localStorage.setItem('menus-updated', String(Date.now())); } catch (_) { }
      try { if (menusBroadcast) menusBroadcast.postMessage('menus-updated'); } catch (_) { }
    } catch (e) {
      alert('更新に失敗しました');
    }
  }

  async function onDeleteMenu(menu) {
    if (!window.confirm('このメニューを削除してよいですか?（メニューに紐づく画面がある場合、連鎖的な整合性は考慮していません）')) return;
    try {
      const resp = await fetch(apiMenus + '/' + menu.id, { method: 'DELETE' });
      if (!resp.ok) {
        alert('削除に失敗しました');
        return;
      }
      await loadMenus();
      await loadScreens();
      try { localStorage.setItem('menus-updated', String(Date.now())); } catch (_) { }
      try { if (menusBroadcast) menusBroadcast.postMessage('menus-updated'); } catch (_) { }
    } catch (e) {
      alert('削除に失敗しました');
    }
  }

  // --- パス操作 ---
  async function onAddPath() {
    const name = window.prompt('追加するパス名を入力してください（例: passwordGeneration）', '');
    if (!name) return;
    const payload = { name };
    try {
      const resp = await fetch(apiPaths, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
      if (!resp.ok) { alert('パス作成に失敗しました'); return; }
      await loadPaths();
      await loadScreens();
    } catch (_) { alert('パス作成に失敗しました'); }
  }

  async function onEditPath(path) {
    const name = window.prompt('パス名を編集してください', path.name || '');
    if (name == null) return;
    const payload = Object.assign({}, path, { name });
    try {
      const resp = await fetch(apiPaths, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
      if (!resp.ok) { alert('更新に失敗しました'); return; }
      await loadPaths();
      await loadScreens();
    } catch (_) { alert('更新に失敗しました'); }
  }

  async function onDeletePath(path) {
    if (!window.confirm('このパスを論理削除します。よろしいですか？')) return;
    try {
      const resp = await fetch(apiPaths + '/' + path.id, { method: 'DELETE' });
      if (!resp.ok) { alert('削除に失敗しました'); return; }
      await loadPaths();
      await loadScreens();
    } catch (_) { alert('削除に失敗しました'); }
  }

  async function onRestorePath(path) {
    try {
      const resp = await fetch(apiPaths + '/' + path.id + '/restore', { method: 'POST' });
      if (!resp.ok) { alert('復元に失敗しました'); return; }
      await loadPaths();
      await loadScreens();
    } catch (_) { alert('復元に失敗しました'); }
  }

  // --- 画面項目操作 ---
  async function onAddScreen() {
    const name = window.prompt('追加する画面名を入力してください', '新しい画面');
    if (!name) return;
    // choose menu from existing
    const menus = await loadMenus();
    if (!menus || !menus.length) {
      alert('先にメニューを追加してください');
      return;
    }
    // build selection prompt
    let list = 'メニューを選んで番号を入力してください:\n';
    menus.forEach((m, idx) => { list += `${idx + 1}: ${m.name}\n`; });
    const sel = window.prompt(list, '1');
    if (!sel) return;
    const idx = parseInt(sel) - 1;
    if (isNaN(idx) || idx < 0 || idx >= menus.length) { alert('不正な選択'); return; }
    const menuName = menus[idx].name;
    // optional pathName input
    const pathName = window.prompt('この画面のパス名を入力してください（例: passwordGeneration）。空欄でも可', '');
    const payload = { itemName: name, menuName, pathName: (pathName ? pathName.trim() : null) };
    try {
      const resp = await fetch(apiContent, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
      if (!resp.ok) {
        alert('作成に失敗しました');
        return;
      }
      await loadScreens();
    } catch (e) {
      alert('作成に失敗しました');
    }
  }

  async function onEditScreen(item) {
    // Per requirement, menuName is editable via select — here only itemName editable
    const name = window.prompt('画面名を編集してください', item.itemName || '');
    if (name == null) return;
    const payload = Object.assign({}, item, { itemName: name });
    try {
      const resp = await fetch(apiContent, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
      if (!resp.ok) {
        alert('更新に失敗しました');
        return;
      }
      await loadScreens();
    } catch (e) {
      alert('更新に失敗しました');
    }
  }

  async function onDeleteScreen(item) {
    if (!window.confirm('削除してよいですか?')) return;
    try {
      const resp = await fetch(apiContent + '/' + item.id, { method: 'DELETE' });
      if (!resp.ok) {
        alert('削除に失敗しました');
        return;
      }
      await loadScreens();
    } catch (e) {
      alert('削除に失敗しました');
    }
  }

  // 初期化: イベントリスナ登録とデータロード
  function initManagePage() {
    try {
      const addMenuBtn = qs('#addMenuBtn');
      if (addMenuBtn && !addMenuBtn.__bound) {
        addMenuBtn.addEventListener('click', onAddMenu);
        addMenuBtn.__bound = true;
      }
      const addScreenBtn = qs('#addScreenBtn');
      if (addScreenBtn && !addScreenBtn.__bound) {
        addScreenBtn.addEventListener('click', onAddScreen);
        addScreenBtn.__bound = true;
      }
      const addPathBtn = qs('#addPathBtn');
      if (addPathBtn && !addPathBtn.__bound) {
        addPathBtn.addEventListener('click', onAddPath);
        addPathBtn.__bound = true;
      }
      // 初回ロード順序
      loadMenus().then(() => loadPaths()).then(() => loadScreens());
    } catch (_) { /* ignore */ }
  }

  // DOMContentLoaded で初期化
  document.addEventListener('DOMContentLoaded', initManagePage);
  // 既に読み込み済みなら即時初期化（フォールバック）
  if (document.readyState === 'interactive' || document.readyState === 'complete') {
    // 少し遅延してDOM構築完了を待つ
    setTimeout(initManagePage, 0);
  }

  // expose for debugging/static-analysis to avoid 'unused function' warnings
  try { if (typeof window !== 'undefined') { window.__manage_onDeletePath = onDeletePath; window.__manage_onRestorePath = onRestorePath; } } catch(_) {}

})();
