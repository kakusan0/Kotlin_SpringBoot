(function () {
    // 既存変数取得
    const defaultStart = document.getElementById('defaultStart');
    const defaultEnd = document.getElementById('defaultEnd');
    const defaultBreak = document.getElementById('defaultBreak');
    const applyDefaultsBtn = document.getElementById('applyDefaults');
    // saveButton 削除: テンプレートから削除されたため参照しない
    const picker = document.getElementById('timePicker');
    // 動的にオーバーレイを用意（テンプレートに無ければ作る）
    const OVERLAY_ID = 'timePickerOverlay';
    let overlay = document.getElementById(OVERLAY_ID);
    if (!overlay) {
        overlay = document.createElement('div');
        overlay.id = OVERLAY_ID;
        overlay.style.display = 'none';
        overlay.style.position = 'fixed';
        overlay.style.inset = '0';
        overlay.style.background = 'rgba(0,0,0,0.25)';
        overlay.style.zIndex = '9998';
        document.body.appendChild(overlay);
    }
    const display = document.getElementById('timeDisplay');
    const hand = document.getElementById('hand');
    const modeLabel = document.getElementById('modeLabel');
    const dragArea = document.getElementById('dragArea');
    const clock = document.getElementById('clock');
    const selectedDateLabel = document.getElementById('selectedDateLabel');
    const cellTypeLabel = document.getElementById('cellTypeLabel');
    const holidayLabel = document.getElementById('holidayLabel');
    const monthInput = document.getElementById('monthInput');
    const prevBtn = document.getElementById('prevMonth');
    const nextBtn = document.getElementById('nextMonth');

    let currentCell = null;
    let selectingHour = true;
    let hour = 0;
    let minute = 0;
    let dragStarted = false;
    let dragStartTime = 0;

    const holidayCache = {};

    // タッチ端末判定
    const isTouchDevice = ('ontouchstart' in window) || (navigator.maxTouchPoints && navigator.maxTouchPoints > 0);

    // 秒付き(常に00)へ正規化するヘルパ
    function ensureSeconds(t) {
        if (!t) return t;
        if (/^\d\d:\d\d$/.test(t)) return t + ':00';
        if (/^\d\d:\d\d:00$/.test(t)) return t;
        // 想定外フォーマットはそのまま返す
        return t;
    }

    // タッチ端末向け: ネイティブ time input を表示して結果をセルへ反映
    function showNativeTimePicker(cell, type) {
        const row = cell.parentElement;
        // mark local editing while native picker is open
        if (row) row.dataset.localEditing = '1';
        const rect = cell.getBoundingClientRect();
        // 初期値から秒を取り除く（HH:mm）
        let init = cell.textContent.trim();
        if (/^\d\d:\d\d:\d\d$/.test(init)) init = init.split(':').slice(0, 2).join(':');
        const def = (type === 'start' ? defaultStart.value : defaultEnd.value) || '09:00';
        const inputVal = (/^\d\d:\d\d$/.test(init) ? init : (/^\d\d:\d\d$/.test(def) ? def : '09:00'));

        const input = document.createElement('input');
        input.type = 'time';
        input.step = '60'; // 秒は60秒単位（秒は使わない）
        input.value = inputVal;
        input.className = 'native-time-input';
        // 簡易スタイル
        input.style.position = 'absolute';
        input.style.zIndex = 9999;
        // 画面内に収まるよう配置
        const left = Math.max(8, rect.left);
        const top = Math.min(window.innerHeight - 56, rect.bottom + 6);
        input.style.left = left + 'px';
        input.style.top = top + 'px';
        document.body.appendChild(input);
        input.focus();
        // 一部ブラウザは showPicker() サポート
        if (typeof input.showPicker === 'function') {
            try {
                input.showPicker();
            } catch (err) { /* ignore */
            }
        }

        function applyAndRemove() {
            const v = input.value; // HH:mm
            if (v && /^\d\d:\d\d$/.test(v)) {
                cell.textContent = ensureSeconds(v);
                updateRowMetrics(row);
                autoSaveRow(row);
            }
            // clear local editing mark
            if (row && row.dataset.localEditing) delete row.dataset.localEditing;
            input.remove();
        }

        input.addEventListener('change', () => applyAndRemove());
        // ブラウザにより blur のタイミングが勝手なので両方で対応
        input.addEventListener('blur', () => setTimeout(() => {
            if (document.body.contains(input)) applyAndRemove();
        }, 150));
    }

    async function fetchHolidays(year) {
        if (holidayCache[year]) return holidayCache[year];
        try {
            const res = await fetch(`https://date.nager.at/api/v3/PublicHolidays/${year}/JP`);
            if (!res.ok) {
                holidayCache[year] = {};
                return {};
            }
            const data = await res.json();
            const map = {};
            for (const h of data) {
                map[h.date] = h.localName || h.name || '';
            }
            holidayCache[year] = map;
            return map;
        } catch (err) {
            console.warn('祝日取得失敗:', err);
            holidayCache[year] = {};
            return {};
        }
    }

    // Update the populateHolidayInfo function to filter holidays for the selected month
    function populateHolidayInfo(holidayMap, selectedMonth) {
        const holidayInfoDiv = document.getElementById('holidayInfo');
        holidayInfoDiv.innerHTML = ''; // Clear existing content

        Object.keys(holidayMap).forEach(date => {
            const holidayName = holidayMap[date];
            const holidayDate = new Date(date);
            if (holidayDate.getMonth() + 1 === selectedMonth) { // Match the selected month
                const holidayEntry = document.createElement('div');
                holidayEntry.textContent = `${date}: 祝: ${holidayName}`;
                holidayInfoDiv.appendChild(holidayEntry);
            }
        });
    }

    // Function to update the holiday accordion with the current month's holidays
    function updateHolidayAccordion(holidayMap, selectedMonth) {
        const holidayInfoDiv = document.getElementById('holidayInfo');
        holidayInfoDiv.innerHTML = ''; // Clear existing content

        Object.keys(holidayMap).forEach(date => {
            const holidayName = holidayMap[date];
            const holidayDate = new Date(date);
            if (holidayDate.getMonth() + 1 === selectedMonth) { // Match the selected month
                const holidayEntry = document.createElement('div');
                holidayEntry.textContent = `${holidayDate.getDate()}日: 祝: ${holidayName}`;
                holidayInfoDiv.appendChild(holidayEntry);
            }
        });
    }

    // 稼働(開始-終了)と実働(休憩差引)を同時に計算して返す。
    // 返却: { durationMinutes: number|null, workingMinutes: number|null }
    // 無効な入力は両方 null。
    // 終了が開始より前の場合は翌日跨ぎとみなし +24h。
    function computeDuration(start, end, breakMinutes = 0) {
        const timePattern = /^(\d\d):(\d\d)(?::(\d\d))?$/; // HH:mm or HH:mm:ss
        if (!start || !end) return {durationMinutes: null, workingMinutes: null};
        const mStart = timePattern.exec(start);
        const mEnd = timePattern.exec(end);
        if (!mStart || !mEnd) return {durationMinutes: null, workingMinutes: null};
        const sh = Number(mStart[1]);
        const sm = Number(mStart[2]);
        const ss = mStart[3] ? Number(mStart[3]) : 0;
        const eh = Number(mEnd[1]);
        const em = Number(mEnd[2]);
        const es = mEnd[3] ? Number(mEnd[3]) : 0;
        // 秒は常に00固定運用。00以外なら無効扱い。
        if (ss !== 0 || es !== 0) return {durationMinutes: null, workingMinutes: null};
        if ([sh, sm, eh, em].some(v => isNaN(v))) return {durationMinutes: null, workingMinutes: null};
        let diff = (eh * 60 + em) - (sh * 60 + sm);
        if (diff < 0) diff += 24 * 60; // 翌日跨ぎ
        const durationMinutes = diff >= 0 ? diff : null;
        const bVal = parseInt(breakMinutes, 10);
        const breakVal = isNaN(bVal) ? 0 : Math.max(bVal, 0);
        const workingMinutes = durationMinutes != null ? Math.max(durationMinutes - breakVal, 0) : null;
        return {durationMinutes, workingMinutes};
    }

    function fmtHM(m) {
        if (m == null || m === '') return '';
        const v = parseInt(m, 10);
        if (isNaN(v)) return '';
        const h = Math.floor(v / 60);
        const mm = v % 60;
        return `${h}時間${mm}分`;
    }

    function updateRowMetrics(row) {
        const start = row.querySelector('.time-cell[data-type="start"]').textContent.trim();
        const end = row.querySelector('.time-cell[data-type="end"]').textContent.trim();
        const breakValRaw = row.querySelector('.break-cell').textContent.trim();
        const breakVal = parseInt(breakValRaw || '0', 10) || 0;
        const metrics = computeDuration(start, end, breakVal);
        row.querySelector('.duration-cell').textContent = metrics.durationMinutes != null ? fmtHM(metrics.durationMinutes) : '';
        row.querySelector('.working-cell').textContent = metrics.workingMinutes != null ? fmtHM(metrics.workingMinutes) : '';
    }

    applyDefaultsBtn.addEventListener('click', () => {
        const s = ensureSeconds(defaultStart.value);
        const e = ensureSeconds(defaultEnd.value);
        const b = defaultBreak.value.trim();
        console.debug('[TS] applyDefaults clicked', {s, e, b});
        document.querySelectorAll('#tableBody tr').forEach(row => {
            // skip rows marked as holidayWork (switch ON)
            const hs = row.querySelector('.holiday-switch');
            if (hs && hs.checked) return;
            const sc = row.querySelector('.time-cell[data-type="start"]');
            const ec = row.querySelector('.time-cell[data-type="end"]');
            const bc = row.querySelector('.break-cell');
            const iso = row.querySelector('.date-cell')?.dataset?.iso;
            if (sc.textContent.trim() === '' && /^\d\d:\d\d(:00)?$/.test(s)) {
                console.debug('[TS] applyDefaults set start', iso, s);
                sc.textContent = s;
            }
            if (ec.textContent.trim() === '' && /^\d\d:\d\d(:00)?$/.test(e)) {
                console.debug('[TS] applyDefaults set end', iso, e);
                ec.textContent = e;
            }
            if (bc.textContent.trim() === '' && b !== '') {
                console.debug('[TS] applyDefaults set break', iso, b);
                bc.textContent = b;
            }
            updateRowMetrics(row);
        });
    });

    function getCsrf() {
        const t = document.querySelector('meta[name="_csrf"]');
        const h = document.querySelector('meta[name="_csrf_header"]');
        return t && h ? {token: t.content, header: h.content} : null;
    }

    // 時刻セルクリック
    document.getElementById('workTable').addEventListener('click', e => {
        const cell = e.target.closest('td.time-cell');
        if (!cell) return;
        // if a holiday-switch exists and is NOT checked (i.e. not allowed), disallow opening picker
        const row = cell.parentElement;
        const hs = row.querySelector('.holiday-switch');
        if (hs && !hs.checked) return; // do nothing when switch exists but is unchecked (disabled)
        document.querySelectorAll('td.time-cell.active').forEach(c => c.classList.remove('active'));
        cell.classList.add('active');
        currentCell = cell;
        // mark this row as locally editing to avoid SSE overwrites
        if (row) row.dataset.localEditing = '1';
        const type = cell.dataset.type;
        const dateCell = row.querySelector('.date-cell');
        const iso = dateCell ? dateCell.dataset.iso : '';
        selectedDateLabel.textContent = '日付: ' + iso;
        cellTypeLabel.textContent = '種類: ' + (type === 'start' ? '出勤' : '退勤');
        holidayLabel.style.display = 'none';
        const init = cell.textContent.trim();

        // タッチ端末ではネイティブ time input を使う
        if (isTouchDevice) {
            showNativeTimePicker(cell, type);
            return;
        }

        const timePattern = /^(\d\d):(\d\d)(?::(\d\d))?$/;
        let parsed = false;
        if (timePattern.test(init)) {
            const parts = init.split(':');
            hour = Number(parts[0]);
            minute = Number(parts[1]);
            parsed = true;
        } else {
            const def = (type === 'start' ? defaultStart.value : defaultEnd.value);
            if (timePattern.test(def)) {
                const parts = def.split(':');
                hour = Number(parts[0]);
                minute = Number(parts[1]);
                parsed = true;
            }
        }
        if (!parsed) {
            hour = 9;
            minute = 0;
        }
        selectingHour = true;
        modeLabel.textContent = '時を設定してください (0-23)';
        updateDisplay();
        setHand();
        const r = cell.getBoundingClientRect();
        picker.style.left = Math.max(8, r.left) + 'px';
        picker.style.top = (r.bottom + 6) + 'px';
        picker.style.display = 'block';
        picker.setAttribute('aria-hidden', 'false');
        // オーバーレイ表示
        if (overlay) overlay.style.display = 'block';
    });

    function updateDisplay() {
        // 秒は常に00固定表示
        display.textContent = `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:00`;
    }

    function setHand() {
        const angle = selectingHour ? (hour % 24) * 15 : minute * 6;
        hand.style.transform = `rotate(${angle}deg)`;
    }

    dragArea.addEventListener('mousedown', () => {
        dragStarted = false;
        dragStartTime = Date.now();
        document.addEventListener('mousemove', onDrag);
        document.addEventListener('mouseup', onDrop);
    });

    function onDrag(ev) {
        dragStarted = true;
        const rect = clock.getBoundingClientRect();
        const cx = rect.left + rect.width / 2;
        const cy = rect.top + rect.height / 2;
        const dx = ev.clientX - cx;
        const dy = ev.clientY - cy;
        const angle = (Math.atan2(dy, dx) * 180 / Math.PI + 90 + 360) % 360;
        hand.style.transform = `rotate(${angle}deg)`;
        if (selectingHour) {
            hour = Math.round(angle / 15) % 24;
        } else {
            minute = Math.round(angle / 6) % 60;
        }
        updateDisplay();
        // DO NOT write the value into the cell while dragging — only update the preview in the picker.
        // Writing to the cell should only happen on drop/confirm to avoid accidental default writes
        // when the UI is reconstructed (paging/refresh).
        if (currentCell) {
            // update metrics preview without committing to the table cell
            // we still update metrics visually by setting a data attribute and recalculating when dropped
            currentCell.dataset.preview = display.textContent;
            // update a temporary visual on the picker only; avoid touching DOM cell text here
            // (existing updateRowMetrics relies on cell text; we'll call updateRowMetrics on drop)
        }
    }

    function onDrop() {
        document.removeEventListener('mousemove', onDrag);
        document.removeEventListener('mouseup', onDrop);
        // only commit when a drag interaction occurred (user actively selected time)
        if (selectingHour) {
            selectingHour = false;
            modeLabel.textContent = '分を設定してください (0-59)';
            setHand();
            return;
        }
        if (currentCell) {
            if (dragStarted) {
                // Commit the picked value into the cell only when the user finishes the interaction
                // Prefer any preview value (dataset.preview) if present
                const commitValue = currentCell.dataset.preview || display.textContent;
                const iso = currentCell.parentElement.querySelector('.date-cell')?.dataset?.iso;
                console.debug('[TS] picker commit', iso, commitValue);
                currentCell.textContent = commitValue;
                delete currentCell.dataset.preview;
                currentCell.classList.remove('active');
                updateRowMetrics(currentCell.parentElement);
                // 自動保存: ドロップ後にサーバへ保存
                autoSaveRow(currentCell.parentElement);
            } else {
                // user opened picker but didn't select (no drag) -> discard preview, do not commit
                if (currentCell.dataset && currentCell.dataset.preview) delete currentCell.dataset.preview;
                currentCell.classList.remove('active');
            }
        }
        // reset drag flag for next interaction
        dragStarted = false;
        // 共通の閉じ処理を使う
        closePicker();
    }

    // 共通: ピッカーを閉じる処理
    function closePicker() {
        if (currentCell) {
            // remove any transient preview left on the cell
            if (currentCell.dataset && currentCell.dataset.preview) delete currentCell.dataset.preview;
            // clear local editing mark on the row
            const r = currentCell.parentElement;
            if (r && r.dataset.localEditing) delete r.dataset.localEditing;
            currentCell.classList.remove('active');
            currentCell = null;
        }
        if (picker) {
            picker.style.display = 'none';
            picker.setAttribute('aria-hidden', 'true');
        }
        if (overlay) overlay.style.display = 'none';
    }

    // Escape キーでピッカーを閉じる
    document.addEventListener('keydown', e => {
        if (e.key === 'Escape' || e.key === 'Esc') {
            closePicker();
        }
    });

    // オーバーレイクリックで閉じる
    overlay.addEventListener('mousedown', () => {
        closePicker();
    });

    // デバウンス用 map
    const saveTimers = new Map();

    // Conflict modal helpers
    const conflictModalEl = document.getElementById('conflictModal');
    const conflictReloadBtn = document.getElementById('conflictReload');
    const conflictForceBtn = document.getElementById('conflictForce');
    let pendingConflict = null; // { row, payload }

    function showConflictModal(row, payload) {
        pendingConflict = {row, payload};
        if (typeof bootstrap !== 'undefined' && conflictModalEl) {
            const m = new bootstrap.Modal(conflictModalEl);
            m.show();
        } else if (conflictModalEl) {
            conflictModalEl.style.display = 'block';
        }
    }

    if (conflictReloadBtn) {
        conflictReloadBtn.addEventListener('click', () => {
            // モーダルを閉じて最新データを取得
            if (typeof bootstrap !== 'undefined' && conflictModalEl) {
                bootstrap.Modal.getInstance(conflictModalEl)?.hide();
            } else if (conflictModalEl) {
                conflictModalEl.style.display = 'none';
            }
            pendingConflict = null;
            loadTimesheetData();
        });
    }

    if (conflictForceBtn) {
        conflictForceBtn.addEventListener('click', async () => {
            // force=true で現在の payload を再送
            if (!pendingConflict) return;
            if (typeof bootstrap !== 'undefined' && conflictModalEl) {
                bootstrap.Modal.getInstance(conflictModalEl)?.hide();
            } else if (conflictModalEl) {
                conflictModalEl.style.display = 'none';
            }
            const payload = pendingConflict.payload;
            pendingConflict = null;
            try {
                const csrf = getCsrf();
                const headers = {'Content-Type': 'application/json'};
                if (csrf) headers[csrf.header] = csrf.token;
                const resp = await fetch('/timesheet/api/entry', {
                    method: 'POST',
                    headers,
                    credentials: 'same-origin',
                    body: JSON.stringify(Object.assign({}, payload, {force: true}))
                });
                const json = await resp.json().catch(() => ({}));
                if (!resp.ok || !json.success) {
                    console.warn('[TS] 強制上書き失敗', json);
                    alert('強制上書きに失敗しました。');
                } else {
                    // 成功: 行を更新
                    loadTimesheetData();
                }
            } catch (e) {
                console.error('[TS] 強制上書きエラー', e);
                alert('ネットワークエラーが発生しました。');
            }
        });
    }

    function autoSaveRow(row) {
        const iso = row.querySelector('.date-cell').dataset.iso;
        const start = row.querySelector('.time-cell[data-type="start"]').textContent.trim();
        const end = row.querySelector('.time-cell[data-type="end"]').textContent.trim();
        const breakVal = row.querySelector('.break-cell').textContent.trim();
        const hs = row.querySelector('.holiday-switch');
        const holidayWork = hs ? !!hs.checked : false;
        const note = row.querySelector('.note-select')?.value || null;
        // クリアボタンで全て空欄にした場合も必ず保存する
        // 既存のタイマーがあればクリア
        if (saveTimers.has(iso)) clearTimeout(saveTimers.get(iso));
        // CSRF ヘッダ
        const csrf = getCsrf();
        // 準備するペイロード
        const payload = {
            workDate: iso,
            startTime: start || null,
            endTime: end || null,
            breakMinutes: breakVal || null,
            holidayWork: holidayWork,
            note: note // 備考列の値を追加
        };
        // すべて空欄の場合はforce=trueで明示的にクリアをサーバへ伝える
        if (!start && !end && !breakVal && !holidayWork) {
            payload.force = true;
        }
        // 即時保存（短い遅延でバッチ化）
        const t = setTimeout(async () => {
            try {
                const headers = {'Content-Type': 'application/json'};
                if (csrf) headers[csrf.header] = csrf.token;
                const resp = await fetch('/timesheet/api/entry', {
                    method: 'POST',
                    headers,
                    credentials: 'same-origin',
                    body: JSON.stringify(payload)
                });
                if (resp.status === 409) {
                    // 競合発生: モーダルを表示して対応を促す
                    showConflictModal(row, payload);
                    return;
                }
                const json = await resp.json().catch(() => ({}));
                if (!resp.ok || !json.success) {
                    console.warn('[TS] 自動保存失敗', json);
                    // 必要なら UI にエラー表示
                }
            } catch (e) {
                console.error('[TS] 自動保存エラー', e);
            } finally {
                saveTimers.delete(iso);
            }
        }, 300); // 300ms遅延でバッチ化
        saveTimers.set(iso, t);
    }

    // 休憩セルの入力に対してデバウンス自動保存
    document.getElementById('workTable').addEventListener('input', e => {
        const cell = e.target.closest('.break-cell');
        if (!cell) return;
        const row = cell.parentElement;
        updateRowMetrics(row);
        const iso = row.querySelector('.date-cell').dataset.iso;
        if (saveTimers.has(iso)) clearTimeout(saveTimers.get(iso));
        const t = setTimeout(() => autoSaveRow(row), 600);
        saveTimers.set(iso, t);
    });
    // holiday switch change: toggle editability and save
    document.getElementById('workTable').addEventListener('change', e => {
        const hs = e.target.closest('.holiday-switch');
        if (!hs) return;
        const row = hs.closest('tr');
        const checked = !!hs.checked;
        // Set editability: when holidayWork is ON, row should be editable (user may input times on holiday)
        setRowEditable(row, checked);
        // clear time/break/duration/working cells immediately per requirement
        try {
            row.querySelector('.time-cell[data-type="start"]').textContent = '';
            row.querySelector('.time-cell[data-type="end"]').textContent = '';
            const bc = row.querySelector('.break-cell');
            if (bc) bc.textContent = '';
            row.querySelector('.duration-cell').textContent = '';
            row.querySelector('.working-cell').textContent = '';
            updateRowMetrics(row);
        } catch (err) {
            console.warn('[TS] failed to clear cells on holiday switch', err);
        }
        // cancel any pending debounced save for this row
        const iso = row.querySelector('.date-cell')?.dataset?.iso;
        if (iso && saveTimers.has(iso)) {
            clearTimeout(saveTimers.get(iso));
            saveTimers.delete(iso);
        }
        // immediately persist the holidayWork flag (and clear times on server)
        saveHolidayFlag(row, checked);
    });

    // 備考プルダウンの変更で自動保存
    document.getElementById('workTable').addEventListener('change', e => {
        const select = e.target.closest('.note-select');
        if (!select) return;
        const row = select.closest('tr');
        if (!row) return;
        // 備考の変更時は即時保存
        autoSaveRow(row);
    });

    // Immediately POST holidayWork change for a row. This sends start/end/break as null to ensure server stores flag and clears times.
    async function saveHolidayFlag(row, holidayWork) {
        if (!row) return;
        const iso = row.querySelector('.date-cell')?.dataset?.iso;
        if (!iso) return;
        // skip if same as last saved to avoid redundant POST
        const last = row.dataset.lastSavedHoliday === '1';
        if (last === !!holidayWork) return;
        const csrf = getCsrf();
        const payload = {
            workDate: iso,
            startTime: null,
            endTime: null,
            breakMinutes: null,
            holidayWork: !!holidayWork
        };
        try {
            const headers = {'Content-Type': 'application/json'};
            if (csrf) headers[csrf.header] = csrf.token;
            const resp = await fetch('/timesheet/api/entry', {
                method: 'POST',
                headers,
                credentials: 'same-origin',
                body: JSON.stringify(payload)
            });
            if (resp.status === 409) {
                showConflictModal(row, payload);
                return;
            }
            const json = await resp.json().catch(() => ({}));
            if (!resp.ok || !json.success) {
                console.warn('[TS] holiday flag save failed', json);
                return;
            }
            // on success record lastSavedHoliday
            row.dataset.lastSavedHoliday = holidayWork ? '1' : '0';
        } catch (e) {
            console.error('[TS] holiday flag save error', e);
        }
    }

    // helper to enable/disable editable cells in a row
    function setRowEditable(row, editable) {
        // time cells: allow click only if editable (we'll use a CSS class to indicate disabled state)
        const timeCells = row.querySelectorAll('.time-cell');
        timeCells.forEach(tc => {
            if (editable) {
                tc.classList.remove('disabled');
                tc.style.pointerEvents = '';
                tc.style.opacity = '';
            } else {
                tc.classList.add('disabled');
                tc.style.pointerEvents = 'none';
                tc.style.opacity = '0.6';
                tc.textContent = ''; // clear displayed times per requirement
            }
        });
        const breakCell = row.querySelector('.break-cell');
        if (breakCell) {
            breakCell.contentEditable = editable ? 'true' : 'false';
            if (!editable) breakCell.textContent = '';
            breakCell.style.opacity = editable ? '' : '0.6';
        }
        // recalc metrics
        updateRowMetrics(row);
    }

    // SSE 受信: 他クライアントの更新を反映
    (function setupSse() {
        try {
            const es = new EventSource('/timesheet/api/stream');
            es.addEventListener('timesheet-updated', e => {
                try {
                    const data = JSON.parse(e.data);
                    const iso = data.workDate || data.workDateString || (data.workDate?.toString());
                    if (!iso) return;
                    // 該当行が存在すれば更新
                    const dateCell = document.querySelector(`#tableBody tr .date-cell[data-iso="${iso}"]`);
                    if (!dateCell) return;
                    const tr = dateCell.closest('tr');
                    if (!tr) return;
                    // do not overwrite if user is editing this row locally
                    if (tr.dataset.localEditing) {
                        console.debug('[TS] SSE update skipped for locally edited row', iso);
                        return;
                    }
                    // mark to suppress auto-save for programmatic update
                    tr.dataset.suppressAutoSave = '1';
                    try {
                        tr.querySelector('.time-cell[data-type="start"]').textContent = data.startTime ? ensureSeconds(data.startTime) : '';
                        tr.querySelector('.time-cell[data-type="end"]').textContent = data.endTime ? ensureSeconds(data.endTime) : '';
                        tr.querySelector('.break-cell').textContent = data.breakMinutes != null ? data.breakMinutes : '';
                        tr.querySelector('.duration-cell').textContent = data.durationMinutes != null ? fmtHM(data.durationMinutes) : '';
                        tr.querySelector('.working-cell').textContent = data.workingMinutes != null ? fmtHM(data.workingMinutes) : '';
                        // reflect holidayWork in switch and editability
                        const hs2 = tr.querySelector('.holiday-switch');
                        if (hs2) {
                            hs2.checked = !!data.holidayWork;
                            setRowEditable(tr, !hs2.checked);
                        }
                    } finally {
                        // remove suppression after microtask to allow any downstream DOM changes to settle
                        setTimeout(() => {
                            delete tr.dataset.suppressAutoSave;
                        }, 0);
                    }
                } catch (err) {
                    console.warn('SSE parse error', err);
                }
            });
            es.addEventListener('break', () => {
                // 個別イベントを無視して summary 等で一括更新する仕組みに任せる
            });
            es.addEventListener('error', _ => {
                console.warn('SSE error');
                es.close();
                // 再接続ロジックを入れる
                setTimeout(setupSse, 3000);
            });
        } catch (err) {
            console.warn('SSE unsupported', err);
        }
    })();

    // MutationObserver: tableBody 下の任意のセルのテキストが変わったら自動保存をトリガー
    (function setupAutoSaveObserver() {
        const tbody = document.getElementById('tableBody');
        if (!tbody) return;
        const observer = new MutationObserver(mutations => {
            const rowsToSave = new Set();
            for (const m of mutations) {
                // 変更元ノードから該当行を探す
                let target = m.target;
                // characterData の場合 target is text node -> parentElement
                if (target.nodeType === Node.TEXT_NODE) target = target.parentElement;
                if (!target) continue;
                const tr = target.closest('tr');
                if (!tr) continue;
                // suppress programmatic updates marked by dataset flag
                if (tr.dataset.suppressAutoSave) continue;
                rowsToSave.add(tr);
            }
            // call autoSaveRow for each unique row (autoSaveRow debounces internally)
            for (const tr of rowsToSave) {
                try {
                    autoSaveRow(tr);
                } catch (e) {
                    console.error('autoSaveObserver error', e);
                }
            }
        });
        observer.observe(tbody, {subtree: true, characterData: true, childList: true});
    })();

    // 月初期化
    (function () {
        const now = new Date();
        const y = now.getFullYear();
        const m = now.getMonth() + 1;
        monthInput.value = `${y}-${String(m).padStart(2, '0')}`;
    })();

    function dayLabel(date) {
        // 「〇日」表記のみ返す
        return `${date.getDate()}日`;
    }

    function shortDay(date) {
        return ['日', '月', '火', '水', '木', '金', '土'][date.getDay()];
    }

    function calcLastDay(year, month1) {
        return new Date(year, month1, 0).getDate();
    }

    function isHolidayIso(iso) {
        try {
            const y = Number(iso.split('-')[0]);
            const map = holidayCache[y];
            return map && map[iso];
        } catch (e) {
            return false;
        }
    }

    function isWeekendIso(iso) {
        try {
            const d = new Date(iso + 'T00:00:00');
            const wd = d.getDay();
            return wd === 0 || wd === 6;
        } catch (e) {
            return false;
        }
    }

    function applyRowShade(tr) {
        // remove previous shading
        tr.classList.remove('table-secondary');
        // prefer precomputed dataset flags when available
        const isHoliday = tr.dataset.isHoliday === '1' || isHolidayIso(tr.querySelector('.date-cell')?.dataset?.iso || '');
        const isWeekend = tr.dataset.isWeekend === '1' || isWeekendIso(tr.querySelector('.date-cell')?.dataset?.iso || '');
        if (isHoliday || isWeekend) tr.classList.add('table-secondary');
    }

    function rebuildRows(ym) {
        const tbody = document.getElementById('tableBody');
        if (!tbody) return;
        const [y, mStr] = ym.split('-');
        const yNum = Number(y);
        const mNum = Number(mStr);
        if (!yNum || !mNum) return;
        const lastDay = calcLastDay(yNum, mNum);
        tbody.innerHTML = '';
        for (let d = 1; d <= lastDay; d++) {
            const iso = `${yNum}-${String(mNum).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
            const date = new Date(yNum, mNum - 1, d);
            const tr = document.createElement('tr');
            if (iso === new Date().toISOString().substring(0, 10)) tr.classList.add('table-primary');
            const isWeekend = isWeekendIso(iso);
            // only render a holiday-work switch for weekends; weekday holidays will get a switch dynamically after fetching holidays
            const switchHtml = isWeekend ? '<div class="form-check form-switch"><input aria-label="休日出勤" class="form-check-input holiday-switch" role="switch" type="checkbox"></div>' : '';
            tr.innerHTML = `<td class="date-cell" data-iso="${iso}"><span>${dayLabel(date)}</span><span class="holiday" style="display:none;"></span></td>` +
                `<td class="weekday-cell">${shortDay(date)}</td>` +
                `<td class="holiday-cell">${switchHtml}</td>` +
                `<td class="note-cell">
                    <select class="form-select form-select-sm note-select">
                        <option value="">---</option>
                                <option value="年休">年休</option>
                                <option value="AM年休">AM年休</option>
                                <option value="PM年休">PM年休</option>
                                <option value="休日">休日</option>
                                <option value="祝日">祝日</option>
                                <option value="会社休">会社休</option>
                                <option value="祝日">祝日</option>
                                <option value="対象外">対象外</option>
                                <option value="休日出勤">休日出勤</option>
                    </select>
                </td>` +
                `<td class="time-cell" data-type="start"></td>` +
                `<td class="time-cell" data-type="end"></td>` +
                `<td class="break-cell" contenteditable="true"></td>` +
                `<td class="duration-cell"></td>` +
                `<td class="working-cell"></td>` +
                `<td class="clear-cell"><button class='btn btn-outline-secondary btn-sm clear-row-btn' type='button'>クリア</button></td>`;
            tbody.appendChild(tr);

            // For weekend rows we rendered a switch; for weekdays no switch is shown and row should be editable by default
            const hsInit = tr.querySelector('.holiday-switch');
            if (hsInit) {
                // weekends: switch exists but disabled until allowed (we'll enable after holiday fetch if needed)
                hsInit.checked = false;
                setRowEditable(tr, false);
                tr.dataset.lastSavedHoliday = '0';
            } else {
                // weekdays (no switch): editable by default
                setRowEditable(tr, true);
                tr.dataset.lastSavedHoliday = '0';
            }
            applyRowShade(tr);
        }

        (async () => {
            try {
                const monthInput = document.getElementById('monthInput');
                const [selectedYear, selectedMonth] = monthInput.value.split('-').map(Number); // Get selected year and month
                const holidayMap = holidayCache[selectedYear] || await fetchHolidays(selectedYear);
                populateHolidayInfo(holidayMap, selectedMonth); // Populate the accordion with holidays for the selected month
                tbody.querySelectorAll('.date-cell').forEach(cell => {
                    const iso = cell.dataset.iso;
                    if (holidayMap[iso]) {
                        const tr = cell.closest('tr');
                        if (tr) {
                            // if this is a weekday (no switch rendered), create a switch for the holiday
                            let hs = tr.querySelector('.holiday-switch');
                            if (!hs) {
                                const hc = tr.querySelector('.holiday-cell');
                                if (hc) {
                                    const div = document.createElement('div');
                                    div.className = 'form-check form-switch';
                                    const input = document.createElement('input');
                                    input.type = 'checkbox';
                                    input.className = 'form-check-input holiday-switch';
                                    input.setAttribute('aria-label', '休日出勤');
                                    div.appendChild(input);
                                    hc.appendChild(div);
                                    hs = tr.querySelector('.holiday-switch');
                                }
                            }
                            if (hs) {
                                // enable the switch for holidays; initial unchecked -> non-editable until user turns it on
                                hs.disabled = false;
                                hs.checked = false;
                                setRowEditable(tr, false);
                                tr.dataset.isHoliday = '1';
                                tr.dataset.lastSavedHoliday = '0';
                            }
                            applyRowShade(tr);
                        }
                    }
                });
            } catch (e) {
                console.warn('祝日再表示失敗', e);
            }
        })();
    }

    async function loadTimesheetData() {
        const [year, month] = monthInput.value.split('-').map(Number);
        const lastDay = calcLastDay(year, month);
        const from = `${year}-${String(month).padStart(2, '0')}-01`;
        const to = `${year}-${String(month).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`;
        try {
            const resp = await fetch(`/timesheet/api?from=${from}&to=${to}`, {credentials: 'same-origin'});
            if (!resp.ok) {
                const body = await resp.text().catch(() => '');
                console.error(`[TS] 取得失敗 status=${resp.status} body=${body.substring(0, 200)}`);
                if (resp.status === 401) {
                    console.warn('[TS] 認証期限切れ: ログイン画面へ遷移推奨');
                }
                return;
            }
            const entries = await resp.json();
            const map = {};
            for (const e of entries) map[e.workDate] = e;
            // ensure holiday info is available for the month so we can create switches for weekday holidays
            const ymYear = Number(monthInput.value.split('-')[0]);
            const holidayMap = holidayCache[ymYear] || await fetchHolidays(ymYear);

            document.querySelectorAll('#tableBody tr').forEach(row => {
                const iso = row.querySelector('.date-cell').dataset.iso;
                // don't overwrite rows that are being edited locally by the user
                if (row.dataset.localEditing) {
                    console.debug('[TS] loadTimesheetData skip locally editing row', iso);
                    return;
                }
                const data = map[iso];
                // suppress observer-triggered auto-save for this programmatic update
                row.dataset.suppressAutoSave = '1';
                try {
                    const allowed = isWeekendIso(iso) || !!holidayMap[iso];
                    let hs = row.querySelector('.holiday-switch');
                    if (!data) {
                        // clear cells
                        console.debug('[TS] loadTimesheetData clear row', iso);
                        row.querySelector('.time-cell[data-type="start"]').textContent = '';
                        row.querySelector('.time-cell[data-type="end"]').textContent = '';
                        row.querySelector('.break-cell').textContent = '';
                        row.querySelector('.duration-cell').textContent = '';
                        row.querySelector('.working-cell').textContent = '';

                        // if a switch exists (weekend or holiday), reset and disable/enable according to allowed
                        if (hs) {
                            hs.checked = false;
                            hs.disabled = !allowed;
                            setRowEditable(row, false);
                        } else {
                            // weekday without switch: editable
                            setRowEditable(row, true);
                        }

                        // set weekday from date
                        const date = new Date(iso + 'T00:00:00');
                        const wd = shortDay(date);
                        const wdEl = row.querySelector('.weekday-cell');
                        if (wdEl) wdEl.textContent = wd;
                        // ensure shading according to weekend/holiday
                        applyRowShade(row);
                        return;
                    }

                    // populate cells from data
                    console.debug('[TS] loadTimesheetData populate row', iso, data);
                    row.querySelector('.time-cell[data-type="start"]').textContent = data.startTime ? ensureSeconds(data.startTime) : '';
                    row.querySelector('.time-cell[data-type="end"]').textContent = data.endTime ? ensureSeconds(data.endTime) : '';
                    row.querySelector('.break-cell').textContent = data.breakMinutes != null ? data.breakMinutes : '';
                    row.querySelector('.duration-cell').textContent = data.durationMinutes != null ? fmtHM(data.durationMinutes) : '';
                    row.querySelector('.working-cell').textContent = data.workingMinutes != null ? fmtHM(data.workingMinutes) : '';

                    // reflect holidayWork. If needed create switch for weekday holidays
                    if (!hs && holidayMap[iso]) {
                        const hc = row.querySelector('.holiday-cell');
                        if (hc) {
                            const div = document.createElement('div');
                            div.className = 'form-check form-switch';
                            const input = document.createElement('input');
                            input.type = 'checkbox';
                            input.className = 'form-check-input holiday-switch';
                            input.setAttribute('aria-label', '休日出勤');
                            div.appendChild(input);
                            hc.appendChild(div);
                            hs = row.querySelector('.holiday-switch');
                        }
                    }
                    if (hs) {
                        hs.disabled = !allowed;
                        hs.checked = allowed && !!data.holidayWork;
                        // editable only when holidayWork is ON
                        setRowEditable(row, !!data.holidayWork);
                        row.dataset.lastSavedHoliday = hs.checked ? '1' : '0';
                    } else {
                        // no switch -> weekday normal editable
                        setRowEditable(row, true);
                        row.dataset.lastSavedHoliday = '0';
                    }

                    // reflect note data
                    const noteSelect = row.querySelector('.note-select');
                    if (noteSelect) {
                        noteSelect.value = data.note || '';
                    }

                    // ensure weekday shown
                    const date = new Date(iso + 'T00:00:00');
                    const wd = shortDay(date);
                    const wdEl = row.querySelector('.weekday-cell');
                    if (wdEl) wdEl.textContent = wd;
                    // update shading
                    applyRowShade(row);
                } finally {
                    // remove suppression after microtask to allow MutationObserver to ignore this programmatic change
                    setTimeout(() => {
                        delete row.dataset.suppressAutoSave;
                    }, 0);
                }
            });

        } catch (e) {
            console.error('[TS] ネットワーク/パース失敗', e);
        }
    }

    function setMonth(year, month) {
        if (month < 1) {
            year -= 1;
            month = 12;
        }
        if (month > 12) {
            year += 1;
            month = 1;
        }
        const ym = `${year}-${String(month).padStart(2, '0')}`;
        if (monthInput) monthInput.value = ym;
        rebuildRows(ym);
        loadTimesheetData();
    }

    if (prevBtn) {
        prevBtn.addEventListener('click', () => {
            if (!monthInput) return;
            const [y, m] = monthInput.value.split('-').map(Number);
            setMonth(y, m - 1);
        });
    }
    if (nextBtn) {
        nextBtn.addEventListener('click', () => {
            if (!monthInput) return;
            const [y, m] = monthInput.value.split('-').map(Number);
            setMonth(y, m + 1);
        });
    }
    if (monthInput) {
        monthInput.addEventListener('change', () => {
            const [y, m] = monthInput.value.split('-').map(Number);
            setMonth(y, m);
        });
    }
    if (monthInput) {
        rebuildRows(monthInput.value);
        loadTimesheetData();
    }

    // Ensure report download buttons work: CSV / PDF / XLSX
    // async function downloadReport(format, btn) {
    //     const msgEl = document.getElementById('reportMessage');
    //     if (btn) {
    //         btn.disabled = true;
    //         const orig = btn.innerHTML;
    //         btn.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> 生成中';
    //         try {
    //             if (msgEl) msgEl.textContent = 'レポートを生成しています...';
    //             const ym = (monthInput && monthInput.value) ? monthInput.value : (new Date().toISOString().substring(0, 7));
    //             const [y, m] = ym.split('-').map(Number);
    //             const from = `${y}-${String(m).padStart(2, '0')}-01`;
    //             const last = new Date(y, m, 0).getDate();
    //             const to = `${y}-${String(m).padStart(2, '0')}-${String(last).padStart(2, '0')}`;
    //             const username = (window.currentUserName || 'user1');
    //             console.log('Username:', username);
    //             const url = `/timesheet/report/${format}?username=${encodeURIComponent(username)}&from=${from}&to=${to}`;
    //             console.log('URL:', url);
    //             const resp = await fetch(url, {credentials: 'same-origin'});
    //             if (!resp.ok) {
    //                 const text = await resp.text().catch(() => '');
    //                 if (msgEl) msgEl.textContent = `レポート生成失敗 (${resp.status})`;
    //                 throw new Error('report fetch failed ' + resp.status + ' ' + text);
    //             }
    //             const blob = await resp.blob();
    //             // derive filename
    //             const disposition = resp.headers.get('Content-Disposition') || '';
    //             const fnMatch = /filename\*=UTF-8''(.+)$/.exec(disposition) || /filename=(.+)$/.exec(disposition);
    //             const filename = fnMatch ? decodeURIComponent(fnMatch[1].replace(/"/g, '')) : `timesheet_${from}_to_${to}.${format}`;
    //             const a = document.createElement('a');
    //             a.href = URL.createObjectURL(blob);
    //             a.download = filename;
    //             document.body.appendChild(a);
    //             a.click();
    //             a.remove();
    //             if (msgEl) msgEl.textContent = 'ダウンロード完了';
    //         } finally {
    //             btn.disabled = false;
    //             btn.innerHTML = orig;
    //         }
    //     }
    // }
    //
    // // Attach handlers if buttons exist
    // try {
    //     const csvBtn = document.getElementById('downloadCsvBtn');
    //     const pdfBtn = document.getElementById('downloadPdfBtn');
    //     const xlsxBtn = document.getElementById('downloadXlsxBtn');
    //     if (csvBtn) csvBtn.addEventListener('click', () => downloadReport('csv', csvBtn));
    //     if (pdfBtn) pdfBtn.addEventListener('click', () => downloadReport('pdf', pdfBtn));
    //     if (xlsxBtn) xlsxBtn.addEventListener('click', () => downloadReport('xlsx', xlsxBtn));
    // } catch (e) {
    //     console.warn('report button handlers init failed', e);
    // }

    // クリアボタン（各行）
    document.getElementById('workTable').addEventListener('click', e => {
        const btn = e.target.closest('.clear-row-btn');
        if (!btn) return;
        const row = btn.closest('tr');
        if (!row) return;
        // クリア時は suppressAutoSave を一時的にセットし、MutationObserverによる二重保存を防ぐ
        row.dataset.suppressAutoSave = '1';
        row.querySelector('.time-cell[data-type="start"]').textContent = '';
        row.querySelector('.time-cell[data-type="end"]').textContent = '';
        row.querySelector('.break-cell').textContent = '';
        row.querySelector('.duration-cell').textContent = '';
        row.querySelector('.working-cell').textContent = '';
        // 休日出勤スイッチもOFF
        const hs = row.querySelector('.holiday-switch');
        if (hs) {
            hs.checked = false;
            setRowEditable(row, false);
        } else {
            setRowEditable(row, true);
        }
        updateRowMetrics(row);
        // suppressAutoSaveを解除してからautoSaveRowを呼ぶことで即時反映
        setTimeout(() => {
            delete row.dataset.suppressAutoSave;
            autoSaveRow(row);
        }, 0);
    });

    // Ensure previous context menu is closed before opening a new one
    document.addEventListener('contextmenu', e => {
        e.preventDefault();
        const existingMenu = document.getElementById('contextMenu');
        if (existingMenu) {
            document.body.removeChild(existingMenu);
        }

        const menu = document.createElement('div');
        menu.id = 'contextMenu';
        menu.style.position = 'absolute';
        menu.style.top = `${e.clientY}px`;
        menu.style.left = `${e.clientX}px`;
        menu.style.backgroundColor = '#fff';
        menu.style.border = '1px solid #ccc';
        menu.style.padding = '5px';
        menu.style.boxShadow = '0 2px 5px rgba(0, 0, 0, 0.2)';
        menu.style.zIndex = '1000';

        const downloadExcelOption = document.createElement('div');
        downloadExcelOption.textContent = 'Excelダウンロード';
        downloadExcelOption.style.cursor = 'pointer';
        downloadExcelOption.style.padding = '5px';
        downloadExcelOption.addEventListener('click', () => {
            downloadReport('xlsx'); // ボタン参照を削除し、直接ダウンロード処理を呼び出す
            document.body.removeChild(menu);
        });

        const downloadPdfOption = document.createElement('div');
        downloadPdfOption.textContent = 'PDFダウンロード';
        downloadPdfOption.style.cursor = 'pointer';
        downloadPdfOption.style.padding = '5px';
        downloadPdfOption.addEventListener('click', () => {
            downloadReport('pdf'); // ボタン参照を削除し、直接ダウンロード処理を呼び出す
            document.body.removeChild(menu);
        });

        menu.appendChild(downloadExcelOption);
        menu.appendChild(downloadPdfOption);
        document.body.appendChild(menu);

        document.addEventListener('click', () => {
            if (document.body.contains(menu)) {
                document.body.removeChild(menu);
            }
        }, {once: true});
    });

    function adjustNoteColumnWidth() {
        const noteCells = document.querySelectorAll('.note-cell');
        let maxWidth = 0;

        noteCells.forEach(cell => {
            const selectElement = cell.querySelector('.note-select');
            const contentWidth = selectElement ? selectElement.scrollWidth : cell.scrollWidth;
            if (contentWidth > maxWidth) {
                maxWidth = contentWidth;
            }
        });

        noteCells.forEach(cell => {
            cell.style.width = `${maxWidth}px`;
        });
    }

    adjustNoteColumnWidth();

    // スクロール位置を保存
    window.addEventListener('scroll', () => {
        localStorage.setItem('scrollPosition', window.scrollY);
    });

    // ページ読み込み時にスクロール位置を復元
    window.addEventListener('load', () => {
        const savedPosition = localStorage.getItem('scrollPosition');
        if (savedPosition) {
            window.scrollTo(0, parseInt(savedPosition, 10));
        }
    });

    // Define the downloadReport function to handle Excel and PDF downloads
    async function downloadReport(format) {
        const msgEl = document.getElementById('reportMessage');
        // if (btn) {
        //     btn.disabled = true;
        //     const orig = btn.innerHTML;
        //     btn.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> 生成中';
        try {
            if (msgEl) msgEl.textContent = 'レポートを生成しています...';
            const ym = (monthInput && monthInput.value) ? monthInput.value : (new Date().toISOString().substring(0, 7));
            const [y, m] = ym.split('-').map(Number);
            const from = `${y}-${String(m).padStart(2, '0')}-01`;
            const last = new Date(y, m, 0).getDate();
            const to = `${y}-${String(m).padStart(2, '0')}-${String(last).padStart(2, '0')}`;
            const username = (window.currentUserName || 'user1');
            console.log('Username:', username);
            const url = `/timesheet/report/${format}?username=${encodeURIComponent(username)}&from=${from}&to=${to}`;
            console.log('URL:', url);
            const resp = await fetch(url, {credentials: 'same-origin'});
            if (!resp.ok) {
                const text = await resp.text().catch(() => '');
                if (msgEl) msgEl.textContent = `レポート生成失敗 (${resp.status})`;
                throw new Error('report fetch failed ' + resp.status + ' ' + text);
            }
            const blob = await resp.blob();
            // derive filename
            const disposition = resp.headers.get('Content-Disposition') || '';
            const fnMatch = /filename\*=UTF-8''(.+)$/.exec(disposition) || /filename=(.+)$/.exec(disposition);
            const filename = fnMatch ? decodeURIComponent(fnMatch[1].replace(/"/g, '')) : `timesheet_${from}_to_${to}.${format}`;
            const a = document.createElement('a');
            a.href = URL.createObjectURL(blob);
            a.download = filename;
            document.body.appendChild(a);
            a.click();
            a.remove();
            if (msgEl) msgEl.textContent = 'ダウンロード完了';
        } finally {
            // btn.disabled = false;
            // btn.innerHTML = orig;
        }
        // }
    }
})();

