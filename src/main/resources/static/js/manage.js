// Simple manage.js: fetch list of content items and provide add/edit/delete using prompt dialogs
(function () {
  'use strict';

  const apiContent = '/api/content';
  const apiMenus = '/api/menus';

  function qs(sel) { return document.querySelector(sel); }
  function qsa(sel) { return Array.from(document.querySelectorAll(sel)); }

  // cache menus so we can populate selects quickly
  let menusCache = [];

  // BroadcastChannel for notifying other pages/tabs (fallback to null if not supported)
  const menusBroadcast = (typeof BroadcastChannel !== 'undefined') ? new BroadcastChannel('menus-channel') : null;

  // --- Renderers ---
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

  function renderScreenRow(item) {
    const tr = document.createElement('tr');
    const idTd = document.createElement('td'); idTd.textContent = item.id || '';
    const menuTd = document.createElement('td');
    const nameTd = document.createElement('td'); nameTd.textContent = item.itemName || '';
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

    tr.appendChild(idTd);
    tr.appendChild(menuTd);
    tr.appendChild(nameTd);
    tr.appendChild(actionsTd);
    return tr;
  }

  // --- Helpers ---
  // refresh all existing select elements when menusCache changes
  function refreshMenuSelects() {
    const selects = qsa('.menu-select');
    selects.forEach(sel => {
      const itemId = sel.getAttribute('data-item-id');
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

  // called when a select value changes for a screen item
  async function onChangeMenuForItem(item, newMenuName, selectElem) {
    if (!item || item.id == null) return;
    const prev = item.menuName || null;
    if (newMenuName === prev) return; // nothing to do

    const payload = Object.assign({}, item, { menuName: newMenuName });
    try {
      const resp = await fetch(apiContent, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
      if (!resp.ok) throw new Error('network');
      // update local item and UI
      item.menuName = newMenuName;
      // optional: show tiny feedback (could be improved)
      // e.g., briefly flash background
      selectElem.classList.add('is-valid');
      setTimeout(() => selectElem.classList.remove('is-valid'), 800);
    } catch (e) {
      console.error('update content menu failed', e);
      alert('メニューの更新に失敗しました');
      // revert selection
      try { selectElem.value = prev || ''; } catch (_) { }
    }
  }

  // --- Loaders ---
  async function loadMenus() {
    const tbody = qs('#menuTable tbody');
    if (!tbody) return [];
    tbody.innerHTML = '<tr><td colspan="3">読み込み中...</td></tr>';
    try {
      const resp = await fetch(apiMenus + '/all');
      if (!resp.ok) throw new Error('network');
      const menus = await resp.json();
      menusCache = menus || [];
      tbody.innerHTML = '';
      if (!menusCache || !menusCache.length) {
        tbody.innerHTML = '<tr><td colspan="3">メニューがありません</td></tr>';
        // still refresh selects to show empty options
        refreshMenuSelects();
        return [];
      }
      menusCache.forEach(m => tbody.appendChild(renderMenuRow(m)));
      // update any existing select elements in screens
      refreshMenuSelects();
      return menusCache;
    } catch (e) {
      tbody.innerHTML = '<tr><td colspan="3">読み込みに失敗しました</td></tr>';
      console.error('loadMenus failed', e);
      return [];
    }
  }

  async function loadScreens() {
    const tbody = qs('#manageTable tbody');
    if (!tbody) return [];
    tbody.innerHTML = '<tr><td colspan="4">読み込み中...</td></tr>';
    try {
      // ensure menusCache is populated so selects can be built properly
      if (!menusCache || !menusCache.length) {
        await loadMenus();
      }
      const resp = await fetch(apiContent + '/all');
      if (!resp.ok) throw new Error('network');
      const items = await resp.json();
      tbody.innerHTML = '';
      if (!items || !items.length) {
        tbody.innerHTML = '<tr><td colspan="4">項目がありません</td></tr>';
        return [];
      }
      items.forEach(it => tbody.appendChild(renderScreenRow(it)));
      return items;
    } catch (e) {
      tbody.innerHTML = '<tr><td colspan="4">読み込みに失敗しました</td></tr>';
      console.error('loadScreens failed', e);
      return [];
    }
  }

  // --- Menu actions ---
  async function onAddMenu() {
    const name = window.prompt('追加するメニュー名を入力してください', '新しいメニュー');
    if (!name) return;
    const payload = { name };
    try {
      const resp = await fetch(apiMenus, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
      if (!resp.ok) throw new Error('network');
      await loadMenus();
      // refresh menu dropdowns for adding screens
      await loadScreens();
      // notify other tabs/pages to refresh sidebar menus
      try { localStorage.setItem('menus-updated', String(Date.now())); } catch (_) { }
      try { if (menusBroadcast) menusBroadcast.postMessage('menus-updated'); } catch (_) { }
    } catch (e) {
      console.error('create menu failed', e);
      alert('メニュー作成に失敗しました');
    }
  }

  async function onEditMenu(menu) {
    const name = window.prompt('メニュー名を編集してください', menu.name || '');
    if (name == null) return;
    const payload = Object.assign({}, menu, { name });
    try {
      const resp = await fetch(apiMenus, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
      if (!resp.ok) throw new Error('network');
      await loadMenus();
      await loadScreens();
      try { localStorage.setItem('menus-updated', String(Date.now())); } catch (_) { }
      try { if (menusBroadcast) menusBroadcast.postMessage('menus-updated'); } catch (_) { }
    } catch (e) {
      console.error('update menu failed', e);
      alert('更新に失敗しました');
    }
  }

  async function onDeleteMenu(menu) {
    if (!window.confirm('このメニューを削除してよいですか?（メニューに紐づく画面がある場合、連鎖的な整合性は考慮していません）')) return;
    try {
      const resp = await fetch(apiMenus + '/' + menu.id, { method: 'DELETE' });
      if (!resp.ok) throw new Error('network');
      await loadMenus();
      await loadScreens();
      try { localStorage.setItem('menus-updated', String(Date.now())); } catch (_) { }
      try { if (menusBroadcast) menusBroadcast.postMessage('menus-updated'); } catch (_) { }
    } catch (e) {
      console.error('delete menu failed', e);
      alert('削除に失敗しました');
    }
  }

  // --- Screen actions ---
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
    const payload = { itemName: name, menuName };
    try {
      const resp = await fetch(apiContent, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
      if (!resp.ok) throw new Error('network');
      await loadScreens();
    } catch (e) {
      console.error('create screen failed', e);
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
      if (!resp.ok) throw new Error('network');
      await loadScreens();
    } catch (e) {
      console.error('update screen failed', e);
      alert('更新に失敗しました');
    }
  }

  async function onDeleteScreen(item) {
    if (!window.confirm('削除してよいですか?')) return;
    try {
      const resp = await fetch(apiContent + '/' + item.id, { method: 'DELETE' });
      if (!resp.ok) throw new Error('network');
      await loadScreens();
    } catch (e) {
      console.error('delete screen failed', e);
      alert('削除に失敗しました');
    }
  }

  document.addEventListener('DOMContentLoaded', function () {
    const addMenuBtn = qs('#addMenuBtn');
    if (addMenuBtn) addMenuBtn.addEventListener('click', onAddMenu);
    const addScreenBtn = qs('#addScreenBtn');
    if (addScreenBtn) addScreenBtn.addEventListener('click', onAddScreen);
    // initial load both lists (menus first to populate selects)
    loadMenus().then(() => loadScreens());
  });

})();
