(function () {
    // DOM要素のキャッシュ（存在チェック付き）
    const elements = {
        defaultStart: document.getElementById('defaultStart'),
        defaultEnd: document.getElementById('defaultEnd'),
        defaultBreak: document.getElementById('defaultBreak'),
        applyDefaultsBtn: document.getElementById('applyDefaults'),
        picker: document.getElementById('timePicker'),
        display: document.getElementById('timeDisplay'),
        hand: document.getElementById('hand'),
        modeLabel: document.getElementById('modeLabel'),
        dragArea: document.getElementById('dragArea'),
        clock: document.getElementById('clock'),
        selectedDateLabel: document.getElementById('selectedDateLabel'),
        cellTypeLabel: document.getElementById('cellTypeLabel'),
        holidayLabel: document.getElementById('holidayLabel'),
        monthInput: document.getElementById('monthInput'),
        prevBtn: document.getElementById('prevMonth'),
        nextBtn: document.getElementById('nextMonth'),
        workTable: document.getElementById('workTable'),
        tableBody: document.getElementById('tableBody')
    };

    // 後方互換性のために個別変数も定義
    const {
        defaultStart, defaultEnd, defaultBreak, applyDefaultsBtn, picker, display, hand,
        modeLabel, dragArea, clock, selectedDateLabel, cellTypeLabel, holidayLabel,
        monthInput, prevBtn, nextBtn
    } = elements;

    // 動的にオーバーレイを用意
    const OVERLAY_ID = 'timePickerOverlay';
    let overlay = document.getElementById(OVERLAY_ID);
    if (!overlay) {
        overlay = document.createElement('div');
        overlay.id = OVERLAY_ID;
        Object.assign(overlay.style, {
            display: 'none',
            position: 'fixed',
            inset: '0',
            background: 'rgba(0,0,0,0.25)',
            zIndex: '9998'
        });
        document.body.appendChild(overlay);
    }

    let currentCell = null;
    let selectingHour = true;
    let hour = 0;
    let minute = 0;
    let dragStarted = false;
    let dragStartTime = 0;

    const holidayCache = {};

    // 正規表現パターンを定数化（再利用のため）
    const TIME_PATTERNS = {
        HH_MM: /^\d\d:\d\d$/,
        HH_MM_SS: /^\d\d:\d\d:\d\d$/,
        HH_MM_00: /^\d\d:\d\d:00$/,
        FULL: /^(\d\d):(\d\d)(?::(\d\d))?$/
    };

    // タッチ端末判定
    const isTouchDevice = ('ontouchstart' in window) || (navigator.maxTouchPoints && navigator.maxTouchPoints > 0);

    // 秒付き(常に00)へ正規化するヘルパ
    function ensureSeconds(t) {
        if (!t) return t;
        if (TIME_PATTERNS.HH_MM.test(t)) return t + ':00';
        if (TIME_PATTERNS.HH_MM_00.test(t)) return t;
        return t;
    }

    // タッチ端末向け: ネイティブ time input を表示して結果をセルへ反映
    function showNativeTimePicker(cell, type) {
        const row = cell.parentElement;
        if (row) row.dataset.localEditing = '1';

        const rect = cell.getBoundingClientRect();
        let init = cell.textContent.trim();
        if (TIME_PATTERNS.HH_MM_SS.test(init)) init = init.substring(0, 5);

        const def = (type === 'start' ? defaultStart.value : defaultEnd.value) || '09:00';
        const inputVal = TIME_PATTERNS.HH_MM.test(init) ? init : (TIME_PATTERNS.HH_MM.test(def) ? def : '09:00');

        const input = document.createElement('input');
        Object.assign(input, {
            type: 'time',
            step: '60',
            value: inputVal,
            className: 'native-time-input'
        });

        Object.assign(input.style, {
            position: 'absolute',
            zIndex: '9999',
            left: Math.max(8, rect.left) + 'px',
            top: Math.min(window.innerHeight - 56, rect.bottom + 6) + 'px'
        });

        document.body.appendChild(input);
        input.focus();

        if (typeof input.showPicker === 'function') {
            try {
                input.showPicker();
            } catch (err) { /* ignore */
            }
        }

        function applyAndRemove() {
            const v = input.value;
            if (v && TIME_PATTERNS.HH_MM.test(v)) {
                cell.textContent = ensureSeconds(v);
                updateRowMetrics(row);
                autoSaveRow(row);
            }
            if (row && row.dataset.localEditing) delete row.dataset.localEditing;
            input.remove();
        }

        input.addEventListener('change', applyAndRemove);
        input.addEventListener('blur', () => setTimeout(() => {
            if (document.body.contains(input)) applyAndRemove();
        }, 150));
    }

    // DBから祝日を取得（失敗時は外部APIにフォールバック）
    async function fetchHolidays(year) {
        if (holidayCache[year]) return holidayCache[year];
        try {
            // まずDBから取得を試みる
            const res = await fetch(`/api/calendar/holidays?year=${year}`, {credentials: 'same-origin'});
            if (res.ok) {
                const map = await res.json();
                holidayCache[year] = map;
                return map;
            }
            console.warn('祝日取得失敗 (DB):', res.status, '- 外部APIにフォールバック');
        } catch (err) {
            console.warn('祝日取得失敗 (DB):', err, '- 外部APIにフォールバック');
        }

        // フォールバック: 外部APIから取得
        try {
            const extRes = await fetch(`https://date.nager.at/api/v3/PublicHolidays/${year}/JP`);
            if (!extRes.ok) {
                holidayCache[year] = {};
                return {};
            }
            const data = await extRes.json();
            const map = {};
            for (const h of data) {
                map[h.date] = h.localName || h.name || '';
            }
            holidayCache[year] = map;
            return map;
        } catch (err) {
            console.warn('祝日取得失敗 (外部API):', err);
            holidayCache[year] = {};
            return {};
        }
    }

    // Update the populateHolidayInfo function to filter holidays for the selected month
    function populateHolidayInfo(holidayMap, selectedMonth) {
        const holidayInfoDiv = document.getElementById('holidayInfo');
        if (!holidayInfoDiv) return;

        holidayInfoDiv.innerHTML = ''; // Clear existing content
        const fragment = document.createDocumentFragment();

        Object.keys(holidayMap).forEach(date => {
            const holidayDate = new Date(date);
            if (holidayDate.getMonth() + 1 === selectedMonth) {
                const holidayEntry = document.createElement('div');
                holidayEntry.textContent = `${date}: 祝: ${holidayMap[date]}`;
                fragment.appendChild(holidayEntry);
            }
        });

        holidayInfoDiv.appendChild(fragment);
    }

    // 稼働(開始-終了)と実働(休憩差引)を同時に計算して返す。
    function computeDuration(start, end, breakMinutes = 0) {
        if (!start || !end) return {durationMinutes: null, workingMinutes: null};

        const mStart = TIME_PATTERNS.FULL.exec(start);
        const mEnd = TIME_PATTERNS.FULL.exec(end);
        if (!mStart || !mEnd) return {durationMinutes: null, workingMinutes: null};

        const sh = Number(mStart[1]);
        const sm = Number(mStart[2]);
        const ss = mStart[3] ? Number(mStart[3]) : 0;
        const eh = Number(mEnd[1]);
        const em = Number(mEnd[2]);
        const es = mEnd[3] ? Number(mEnd[3]) : 0;

        // 秒は常に00固定運用
        if (ss !== 0 || es !== 0 || [sh, sm, eh, em].some(v => isNaN(v))) {
            return {durationMinutes: null, workingMinutes: null};
        }

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
        const startCell = row.querySelector('.time-cell[data-type="start"]');
        const endCell = row.querySelector('.time-cell[data-type="end"]');
        const breakCell = row.querySelector('.break-cell');
        const durationCell = row.querySelector('.duration-cell');
        const workingCell = row.querySelector('.working-cell');

        if (!startCell || !endCell || !breakCell || !durationCell || !workingCell) return;

        const start = startCell.textContent.trim();
        const end = endCell.textContent.trim();
        const breakValRaw = breakCell.textContent.trim();
        const breakVal = parseInt(breakValRaw || '0', 10) || 0;
        const metrics = computeDuration(start, end, breakVal);

        durationCell.textContent = metrics.durationMinutes != null ? fmtHM(metrics.durationMinutes) : '';
        workingCell.textContent = metrics.workingMinutes != null ? fmtHM(metrics.workingMinutes) : '';

        // 警告チェックを実行
        checkRowWarnings(row);
    }

    /**
     * 行の警告状態をチェックして表示する
     * - 勤務時間が空欄で備考も空欄の場合は備考を点滅
     * - 出勤・退勤・休憩のいずれかが入力されていて、いずれかが未入力の場合は枠線を色付け
     */
    function checkRowWarnings(row) {
        const startCell = row.querySelector('.time-cell[data-type="start"]');
        const endCell = row.querySelector('.time-cell[data-type="end"]');
        const breakCell = row.querySelector('.break-cell');
        const noteCell = row.querySelector('.note-cell');
        const noteSelect = row.querySelector('.note-select');

        if (!startCell || !endCell || !breakCell) return;

        const start = startCell.textContent.trim();
        const end = endCell.textContent.trim();
        const breakVal = breakCell.textContent.trim();
        const note = noteSelect?.value || '';

        // 土日祝の場合は警告をスキップ（休日系の備考がある場合も）
        const iso = row.querySelector('.date-cell')?.dataset?.iso;
        const isWeekend = iso ? isWeekendIso(iso) : false;
        const isHoliday = row.dataset.isHoliday === '1';
        const holidayNotes = ['休日', '祝日', '年休', '会社休', '対象外', '振替休日', '特別休暇', '欠勤'];
        const isHolidayNote = holidayNotes.includes(note);

        // 休日系の場合は警告を解除
        if (isWeekend || isHoliday || isHolidayNote) {
            noteCell?.classList.remove('blink-warning');
            startCell.classList.remove('incomplete-warning');
            endCell.classList.remove('incomplete-warning');
            breakCell.classList.remove('incomplete-warning');
            row.classList.remove('row-incomplete');
            return;
        }

        // 1. 勤務時間が空欄で備考も空欄の場合は備考を点滅
        const hasNoTime = !start && !end && !breakVal;
        const hasNoNote = !note;

        if (hasNoTime && hasNoNote) {
            noteCell?.classList.add('blink-warning');
        } else {
            noteCell?.classList.remove('blink-warning');
        }

        // 2. 出勤・退勤・休憩のいずれかが入力されていて、いずれかが未入力の場合
        const hasAnyTime = start || end || breakVal;
        const hasAllTime = start && end && breakVal;

        if (hasAnyTime && !hasAllTime) {
            // 未入力のセルに警告枠線
            if (!start) startCell.classList.add('incomplete-warning');
            else startCell.classList.remove('incomplete-warning');

            if (!end) endCell.classList.add('incomplete-warning');
            else endCell.classList.remove('incomplete-warning');

            if (!breakVal) breakCell.classList.add('incomplete-warning');
            else breakCell.classList.remove('incomplete-warning');

            row.classList.add('row-incomplete');
        } else {
            startCell.classList.remove('incomplete-warning');
            endCell.classList.remove('incomplete-warning');
            breakCell.classList.remove('incomplete-warning');
            row.classList.remove('row-incomplete');
        }
    }


    applyDefaultsBtn.addEventListener('click', () => {
        const s = ensureSeconds(defaultStart.value);
        const e = ensureSeconds(defaultEnd.value);
        const b = defaultBreak.value.trim();

        console.debug('[TS] applyDefaults clicked', {s, e, b});

        document.querySelectorAll('#tableBody tr').forEach(row => {
            // 休日出勤チェック済みの行はスキップ
            const hs = row.querySelector('.holiday-switch');
            if (hs && hs.checked) return;

            // 日付を取得
            const iso = row.querySelector('.date-cell')?.dataset?.iso;
            if (!iso) return;

            // 土日はスキップ
            if (isWeekendIso(iso)) {
                console.debug('[TS] applyDefaults skip weekend', iso);
                return;
            }

            // 祝日はスキップ
            if (isHolidayIso(iso)) {
                console.debug('[TS] applyDefaults skip holiday', iso);
                return;
            }

            // table-secondaryクラス（休日/祝日表示用）がある行もスキップ
            if (row.classList.contains('table-secondary')) {
                console.debug('[TS] applyDefaults skip table-secondary row', iso);
                return;
            }

            const sc = row.querySelector('.time-cell[data-type="start"]');
            const ec = row.querySelector('.time-cell[data-type="end"]');
            const bc = row.querySelector('.break-cell');

            if (sc.textContent.trim() === '' && TIME_PATTERNS.HH_MM_00.test(s)) {
                console.debug('[TS] applyDefaults set start', iso, s);
                sc.textContent = s;
            }
            if (ec.textContent.trim() === '' && TIME_PATTERNS.HH_MM_00.test(e)) {
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
    elements.workTable.addEventListener('click', e => {
        const cell = e.target.closest('td.time-cell');
        if (!cell) return;

        const row = cell.parentElement;
        const hs = row.querySelector('.holiday-switch');
        if (hs && !hs.checked) return; // not allowed

        document.querySelectorAll('td.time-cell.active').forEach(c => c.classList.remove('active'));
        cell.classList.add('active');
        currentCell = cell;

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

        let parsed = false;
        if (TIME_PATTERNS.FULL.test(init)) {
            const parts = init.split(':');
            hour = Number(parts[0]);
            minute = Number(parts[1]);
            parsed = true;
        } else {
            const def = (type === 'start' ? defaultStart.value : defaultEnd.value);
            if (TIME_PATTERNS.FULL.test(def)) {
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

        if (overlay) overlay.style.display = 'block';
    });

    function updateDisplay() {
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
        const dateCell = row.querySelector('.date-cell');
        const startCell = row.querySelector('.time-cell[data-type="start"]');
        const endCell = row.querySelector('.time-cell[data-type="end"]');
        const breakCell = row.querySelector('.break-cell');
        const locationBtn = row.querySelector('.work-location-btn');
        const noteSelect = row.querySelector('.note-select');

        if (!dateCell) return;

        const iso = dateCell.dataset.iso;
        const start = startCell?.textContent.trim() || '';
        const end = endCell?.textContent.trim() || '';
        const breakVal = breakCell?.textContent.trim() || '';
        const workLocation = locationBtn?.dataset.location || null;
        const note = noteSelect?.value || null;

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
            workLocation: workLocation,
            note: note
        };

        // すべて空欄の場合はforce=trueで明示的にクリアをサーバへ伝える
        if (!start && !end && !breakVal && !workLocation) {
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
                }
            } catch (e) {
                console.error('[TS] 自動保存エラー', e);
            } finally {
                saveTimers.delete(iso);
            }
        }, 300);
        saveTimers.set(iso, t);
    }

    // 休憩セルの入力に対してデバウンス自動保存
    elements.workTable.addEventListener('input', e => {
        const cell = e.target.closest('.break-cell');
        if (!cell) return;
        const row = cell.parentElement;
        updateRowMetrics(row);
        const iso = row.querySelector('.date-cell').dataset.iso;
        if (saveTimers.has(iso)) clearTimeout(saveTimers.get(iso));
        const t = setTimeout(() => autoSaveRow(row), 600);
        saveTimers.set(iso, t);
    });

    // 出社区分ボタンのクリックイベント
    elements.workTable.addEventListener('click', e => {
        const btn = e.target.closest('.work-location-btn');
        if (btn) {
            const currentLocation = btn.dataset.location || '出社';
            let newLocation;
            // 出社 -> 在宅 -> 出社 のサイクル
            if (currentLocation === '出社') {
                newLocation = '在宅';
            } else {
                newLocation = '出社';
            }
            btn.dataset.location = newLocation;
            btn.textContent = newLocation;

            // ボタンのスタイルを更新
            btn.classList.remove('btn-primary', 'btn-success');
            if (newLocation === '出社') {
                btn.classList.add('btn-primary');
            } else {
                btn.classList.add('btn-success');
            }

            // 自動保存
            const row = btn.closest('tr');
            if (row) {
                autoSaveRow(row);
            }

        }
    });

    // 備考プルダウンの変更で自動保存
    elements.workTable.addEventListener('change', e => {
        // 備考プルダウンの変更で自動保存
        const select = e.target.closest('.note-select');
        if (select) {
            const row = select.closest('tr');
            if (row) {
                const noteValue = select.value;
                // 休日・祝日・年休などの場合は入力値をクリアして無効化
                const clearNotes = ['休日', '祝日', '年休', '会社休', '対象外', '振替休日', '特別休暇', '欠勤'];
                if (clearNotes.includes(noteValue)) {
                    const startCell = row.querySelector('.time-cell[data-type="start"]');
                    const endCell = row.querySelector('.time-cell[data-type="end"]');
                    const breakCell = row.querySelector('.break-cell');
                    const durationCell = row.querySelector('.duration-cell');
                    const workingCell = row.querySelector('.working-cell');

                    if (startCell) startCell.textContent = '';
                    if (endCell) endCell.textContent = '';
                    if (breakCell) breakCell.textContent = '';
                    if (durationCell) durationCell.textContent = '';
                    if (workingCell) workingCell.textContent = '';

                    console.debug('[TS] cleared row due to note:', noteValue);

                    // 入力を無効化
                    disableRowInput(row, true);
                } else {
                    // 休日系以外の場合は入力を有効化
                    disableRowInput(row, false);
                }
                // 警告チェック
                checkRowWarnings(row);
                autoSaveRow(row);
            }
        }
    });

    // 行の入力を無効化/有効化する関数
    function disableRowInput(row, disable) {
        const timeCells = row.querySelectorAll('.time-cell');
        timeCells.forEach(cell => {
            if (disable) {
                cell.classList.add('disabled');
                cell.style.pointerEvents = 'none';
                cell.style.opacity = '0.5';
                cell.style.cursor = 'not-allowed';
            } else {
                cell.classList.remove('disabled');
                cell.style.pointerEvents = '';
                cell.style.opacity = '';
                cell.style.cursor = '';
            }
        });

        const breakCell = row.querySelector('.break-cell');
        if (breakCell) {
            breakCell.contentEditable = disable ? 'false' : 'true';
            breakCell.style.opacity = disable ? '0.5' : '';
            breakCell.style.cursor = disable ? 'not-allowed' : '';
        }

        // 出社区分ボタンも無効化
        const locationBtn = row.querySelector('.work-location-btn');
        if (locationBtn) {
            locationBtn.disabled = disable;
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
            tr.innerHTML = `<td class="date-cell" data-iso="${iso}"><span>${dayLabel(date)}</span><span class="holiday" style="display:none;"></span></td>` +
                `<td class="weekday-cell">${shortDay(date)}</td>` +
                `<td class="location-cell"><button class="btn btn-sm btn-primary work-location-btn" type="button" data-location="出社">出社</button></td>` +
                `<td class="note-cell">
                    <select class="form-select form-select-sm note-select">
                        <option value="">---</option>
                        <option value="午前休">午前休</option>
                        <option value="午後休">午後休</option>
                        <option value="年休">年休</option>
                        <option value="振替出勤">振替出勤</option>
                        <option value="振替休日">振替休日</option>
                        <option value="特別休暇">特別休暇</option>
                        <option value="欠勤">欠勤</option>
                        <option value="休日">休日</option>
                        <option value="祝日">祝日</option>
                        <option value="会社休">会社休</option>
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

            // 平日は編集可能、土日は編集不可
            if (isWeekend) {
                setRowEditable(tr, false);
            } else {
                setRowEditable(tr, true);
            }

            // 土曜日・日曜日の場合は備考列のプルダウンをデフォルトで「休日」を設定
            const noteSelect = tr.querySelector('.note-select');
            if (isWeekend && noteSelect) {
                noteSelect.value = '休日';
                // 休日の場合は入力を無効化
                disableRowInput(tr, true);
            }

            applyRowShade(tr);
        }

        (async () => {
            try {
                const monthInput = document.getElementById('monthInput');
                const [selectedYear, selectedMonth] = monthInput.value.split('-').map(Number);
                const holidayMap = holidayCache[selectedYear] || await fetchHolidays(selectedYear);
                populateHolidayInfo(holidayMap, selectedMonth);
                tbody.querySelectorAll('.date-cell').forEach(cell => {
                    const iso = cell.dataset.iso;
                    if (holidayMap[iso]) {
                        const tr = cell.closest('tr');
                        if (tr) {
                            tr.dataset.isHoliday = '1';

                            // 祝日の場合は備考列のプルダウンをデフォルトで「祝日」を設定
                            const noteSelect = tr.querySelector('.note-select');
                            if (noteSelect) {
                                noteSelect.value = '祝日';
                                // 祝日の場合は入力を無効化
                                disableRowInput(tr, true);
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
                    const isWeekend = isWeekendIso(iso);
                    const isHoliday = !!holidayMap[iso];

                    if (!data) {
                        // clear cells
                        console.debug('[TS] loadTimesheetData clear row', iso);
                        row.querySelector('.time-cell[data-type="start"]').textContent = '';
                        row.querySelector('.time-cell[data-type="end"]').textContent = '';
                        row.querySelector('.break-cell').textContent = '';
                        row.querySelector('.duration-cell').textContent = '';
                        row.querySelector('.working-cell').textContent = '';

                        // 出社区分ボタンをデフォルト「出社」に設定
                        const locationBtn = row.querySelector('.work-location-btn');
                        if (locationBtn) {
                            locationBtn.dataset.location = '出社';
                            locationBtn.textContent = '出社';
                            locationBtn.classList.remove('btn-success');
                            locationBtn.classList.add('btn-primary');
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

                    // reflect workLocation (出社区分ボタン)
                    const locationBtn = row.querySelector('.work-location-btn');
                    if (locationBtn) {
                        const loc = data.workLocation || '出社';
                        locationBtn.dataset.location = loc;
                        locationBtn.textContent = loc;
                        locationBtn.classList.remove('btn-primary', 'btn-success');
                        if (loc === '出社') {
                            locationBtn.classList.add('btn-primary');
                        } else {
                            locationBtn.classList.add('btn-success');
                        }
                    }

                    // reflect note data
                    const noteSelect = row.querySelector('.note-select');
                    if (noteSelect && data.note) {
                        noteSelect.value = data.note;

                        // 備考が休日系の場合は入力を無効化
                        const clearNotes = ['休日', '祝日', '年休', '会社休', '対象外', '振替休日', '特別休暇', '欠勤'];
                        if (clearNotes.includes(data.note)) {
                            disableRowInput(row, true);
                        } else {
                            // editable for normal days
                            setRowEditable(row, true);
                        }
                    } else {
                        // editable for normal days without note
                        setRowEditable(row, true);
                    }

                    // ensure weekday shown
                    const date = new Date(iso + 'T00:00:00');
                    const wd = shortDay(date);
                    const wdEl = row.querySelector('.weekday-cell');
                    if (wdEl) wdEl.textContent = wd;
                    // update shading
                    applyRowShade(row);

                    // 警告チェック
                    checkRowWarnings(row);
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


    // クリアボタン（各行）
    document.getElementById('workTable').addEventListener('click', e => {
        const btn = e.target.closest('.clear-row-btn');
        if (!btn) return;
        const row = btn.closest('tr');
        if (!row) return;

        row.dataset.suppressAutoSave = '1';

        const cells = {
            start: row.querySelector('.time-cell[data-type="start"]'),
            end: row.querySelector('.time-cell[data-type="end"]'),
            break: row.querySelector('.break-cell'),
            duration: row.querySelector('.duration-cell'),
            working: row.querySelector('.working-cell')
        };

        Object.values(cells).forEach(cell => {
            if (cell) cell.textContent = '';
        });

        const hs = row.querySelector('.holiday-switch');
        if (hs) {
            hs.checked = false;
            setRowEditable(row, false);
        } else {
            setRowEditable(row, true);
        }

        updateRowMetrics(row);

        setTimeout(() => {
            delete row.dataset.suppressAutoSave;
            autoSaveRow(row);
        }, 0);
    });

    // Define the downloadReport function to handle Excel and PDF downloads
    async function downloadReport(format) {
        const msgEl = document.getElementById('reportMessage');
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
        }
    }

    // UNISS勤務表テンプレートをダウンロードする機能
    async function downloadUnissXlsx() {
        const msgEl = document.getElementById('reportMessage');
        const btn = document.getElementById('downloadUnissXlsx');
        const origHtml = btn ? btn.innerHTML : '';

        try {
            if (btn) {
                btn.disabled = true;
                btn.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>';
            }
            if (msgEl) msgEl.textContent = 'UNISS勤務表を生成しています...';

            const ym = (monthInput && monthInput.value) ? monthInput.value : (new Date().toISOString().substring(0, 7));
            const [y, m] = ym.split('-').map(Number);
            const from = `${y}-${String(m).padStart(2, '0')}-01`;
            const last = new Date(y, m, 0).getDate();
            const to = `${y}-${String(m).padStart(2, '0')}-${String(last).padStart(2, '0')}`;
            const username = (window.currentUserName || 'user1');

            console.log('UNISS Download - Username:', username);
            const url = `/timesheet/report/uniss-xlsx?username=${encodeURIComponent(username)}&from=${from}&to=${to}`;
            console.log('UNISS Download - URL:', url);

            const resp = await fetch(url, {credentials: 'same-origin'});
            if (!resp.ok) {
                const text = await resp.text().catch(() => '');
                if (msgEl) msgEl.textContent = `UNISS勤務表生成失敗 (${resp.status})`;
                throw new Error('UNISS report fetch failed ' + resp.status + ' ' + text);
            }

            const blob = await resp.blob();
            const disposition = resp.headers.get('Content-Disposition') || '';
            const fnMatch = /filename\*=UTF-8''(.+)$/.exec(disposition) || /filename=(.+)$/.exec(disposition);
            const defaultFilename = `${y}年${String(m).padStart(2, '0')}月度UNISS勤務表(${username}).xlsx`;
            const filename = fnMatch ? decodeURIComponent(fnMatch[1].replace(/"/g, '')) : defaultFilename;

            const a = document.createElement('a');
            a.href = URL.createObjectURL(blob);
            a.download = filename;
            document.body.appendChild(a);
            a.click();
            a.remove();

            if (msgEl) msgEl.textContent = 'UNISS勤務表ダウンロード完了';
        } catch (e) {
            console.error('UNISS download error:', e);
            if (msgEl) msgEl.textContent = 'UNISS勤務表ダウンロードに失敗しました';
        } finally {
            if (btn) {
                btn.disabled = false;
                btn.innerHTML = origHtml;
            }
        }
    }

    // UNISS勤務表ダウンロードボタンのイベントリスナー
    const unissBtn = document.getElementById('downloadUnissXlsx');
    if (unissBtn) {
        unissBtn.addEventListener('click', downloadUnissXlsx);
    }

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

        const downloadUnissOption = document.createElement('div');
        downloadUnissOption.textContent = 'UNISS勤務表ダウンロード';
        downloadUnissOption.style.cursor = 'pointer';
        downloadUnissOption.style.padding = '5px';
        downloadUnissOption.style.borderTop = '1px solid #eee';
        downloadUnissOption.style.marginTop = '3px';
        downloadUnissOption.style.paddingTop = '8px';
        downloadUnissOption.addEventListener('click', () => {
            downloadUnissXlsx();
            document.body.removeChild(menu);
        });

        menu.appendChild(downloadExcelOption);
        menu.appendChild(downloadPdfOption);
        menu.appendChild(downloadUnissOption);
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
})();

