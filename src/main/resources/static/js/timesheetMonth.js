(function () {
    // 既存変数取得
    const defaultStart = document.getElementById('defaultStart');
    const defaultEnd = document.getElementById('defaultEnd');
    const defaultBreak = document.getElementById('defaultBreak');
    const applyDefaultsBtn = document.getElementById('applyDefaults');
    // saveButton 削除: テンプレートから削除されたため参照しない
    const picker = document.getElementById('timePicker');
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

    // 秒付き(常に00)へ正規化するヘルパ
    function ensureSeconds(t) {
        if (!t) return t;
        if (/^\d\d:\d\d$/.test(t)) return t + ':00';
        if (/^\d\d:\d\d:00$/.test(t)) return t;
        // 想定外フォーマットはそのまま返す
        return t;
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

    (async function init() {
        const year = new Date().getFullYear();
        const holidayMap = await fetchHolidays(year);

        document.querySelectorAll('td.date-cell').forEach(cell => {
            const iso = cell.dataset.iso;
            if (holidayMap[iso]) {
                const hspan = cell.querySelector('.holiday');
                if (hspan) {
                    hspan.textContent = `祝: ${holidayMap[iso]}`;
                    hspan.style.display = '';
                }
            }
        });
    })();

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
        document.querySelectorAll('#tableBody tr').forEach(row => {
            const sc = row.querySelector('.time-cell[data-type="start"]');
            const ec = row.querySelector('.time-cell[data-type="end"]');
            const bc = row.querySelector('.break-cell');
            if (sc.textContent.trim() === '' && /^\d\d:\d\d(:00)?$/.test(s)) sc.textContent = s;
            if (ec.textContent.trim() === '' && /^\d\d:\d\d(:00)?$/.test(e)) ec.textContent = e;
            if (bc.textContent.trim() === '' && b !== '') bc.textContent = b;
            updateRowMetrics(row);
        });
    });

    function getCsrf() {
        const t = document.querySelector('meta[name="_csrf"]');
        const h = document.querySelector('meta[name="_csrf_header"]');
        return t && h ? {token: t.content, header: h.content} : null;
    }

    async function saveBatch() {
        const entries = [];
        document.querySelectorAll('#tableBody tr').forEach(row => {
            const iso = row.querySelector('.date-cell').dataset.iso;
            const startTime = row.querySelector('.time-cell[data-type="start"]').textContent.trim();
            const endTime = row.querySelector('.time-cell[data-type="end"]').textContent.trim();
            const breakMinutes = row.querySelector('.break-cell').textContent.trim();
            entries.push({
                workDate: iso,
                startTime: startTime || null,
                endTime: endTime || null,
                breakMinutes: breakMinutes || null
            });
        });

        const csrf = getCsrf();
        const headers = {'Content-Type': 'application/json'};
        if (csrf) headers[csrf.header] = csrf.token;
        try {
            const resp = await fetch('/timesheet/api/batch', {
                method: 'POST',
                headers,
                body: JSON.stringify({entries}),
                credentials: 'same-origin'
            });
            if (!resp.ok) {
                console.warn('一括保存失敗', resp.status);
                alert('保存に失敗しました。');
            } else {
                alert('保存しました。');
                loadTimesheetData();
            }
        } catch (e) {
            console.error('一括保存エラー', e);
            alert('保存中にエラーが発生しました。');
        }
    }

    // 時刻セルクリック
    document.getElementById('workTable').addEventListener('click', e => {
        const cell = e.target.closest('td.time-cell');
        if (!cell) return;
        document.querySelectorAll('td.time-cell.active').forEach(c => c.classList.remove('active'));
        cell.classList.add('active');
        currentCell = cell;
        const type = cell.dataset.type;
        const row = cell.parentElement;
        const dateCell = row.querySelector('.date-cell');
        const iso = dateCell ? dateCell.dataset.iso : '';
        selectedDateLabel.textContent = '日付: ' + iso;
        cellTypeLabel.textContent = '種類: ' + (type === 'start' ? '出勤' : '退勤');
        holidayLabel.style.display = 'none';
        const init = cell.textContent.trim();
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
        if (currentCell) {
            currentCell.textContent = display.textContent;
            updateRowMetrics(currentCell.parentElement);
        }
    }

    function onDrop() {
        document.removeEventListener('mousemove', onDrag);
        document.removeEventListener('mouseup', onDrop);
        if (selectingHour) {
            selectingHour = false;
            modeLabel.textContent = '分を設定してください (0-59)';
            setHand();
            return;
        }
        if (currentCell) {
            currentCell.textContent = display.textContent;
            currentCell.classList.remove('active');
            updateRowMetrics(currentCell.parentElement);
            // 自動保存: ドロップ後にサーバへ保存
            autoSaveRow(currentCell.parentElement);
        }
        picker.style.display = 'none';
        picker.setAttribute('aria-hidden', 'true');
    }

    // デバウンス用 map
    const saveTimers = new Map();

    function autoSaveRow(row) {
        const iso = row.querySelector('.date-cell').dataset.iso;
        const start = row.querySelector('.time-cell[data-type="start"]').textContent.trim();
        const end = row.querySelector('.time-cell[data-type="end"]').textContent.trim();
        const breakVal = row.querySelector('.break-cell').textContent.trim();
        // 小さな保護: 両方空なら保存しない
        if (!start && !end && !breakVal) return;
        // 既存のタイマーがあればクリア
        if (saveTimers.has(iso)) clearTimeout(saveTimers.get(iso));
        // CSRF ヘッダ
        const csrf = getCsrf();
        // 即時保存（短い遅延でバッチ化）
        const t = setTimeout(async () => {
            try {
                const headers = {'Content-Type': 'application/json'};
                if (csrf) headers[csrf.header] = csrf.token;
                const resp = await fetch('/timesheet/api/entry', {
                    method: 'POST',
                    headers,
                    credentials: 'same-origin',
                    body: JSON.stringify({
                        workDate: iso,
                        startTime: start || null,
                        endTime: end || null,
                        breakMinutes: breakVal || null
                    })
                });
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
                    const row = document.querySelector(`#tableBody tr .date-cell[data-iso="${iso}"]`);
                    if (!row) return;
                    const tr = row.closest('tr');
                    tr.querySelector('.time-cell[data-type="start"]').textContent = data.startTime ? ensureSeconds(data.startTime) : '';
                    tr.querySelector('.time-cell[data-type="end"]').textContent = data.endTime ? ensureSeconds(data.endTime) : '';
                    tr.querySelector('.break-cell').textContent = data.breakMinutes != null ? data.breakMinutes : '';
                    tr.querySelector('.duration-cell').textContent = data.durationMinutes != null ? fmtHM(data.durationMinutes) : '';
                    tr.querySelector('.working-cell').textContent = data.workingMinutes != null ? fmtHM(data.workingMinutes) : '';
                } catch (err) {
                    console.warn('SSE parse error', err);
                }
            });
            es.addEventListener('break', e => {
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

    // 月初期化
    (function () {
        const now = new Date();
        const y = now.getFullYear();
        const m = now.getMonth() + 1;
        monthInput.value = `${y}-${String(m).padStart(2, '0')}`;
    })();

    function dayLabel(date) {
        const wd = ['日', '月', '火', '水', '木', '金', '土'][date.getDay()];
        const mm = String(date.getMonth() + 1).padStart(2, '0');
        const dd = String(date.getDate()).padStart(2, '0');
        return `${mm}/${dd} (${wd})`;
    }

    function calcLastDay(year, month1) {
        return new Date(year, month1, 0).getDate();
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
            tr.innerHTML = `<td class="date-cell" data-iso="${iso}"><span>${dayLabel(date)}</span><span class="holiday" style="display:none;"></span></td>` +
                `<td class="time-cell" data-type="start"></td>` +
                `<td class="time-cell" data-type="end"></td>` +
                `<td class="break-cell" contenteditable="true"></td>` +
                `<td class="duration-cell"></td>` +
                `<td class="working-cell"></td>`;
            tbody.appendChild(tr);
        }
        (async () => {
            try {
                const holidayMap = holidayCache[yNum] || await fetchHolidays(yNum);
                tbody.querySelectorAll('.date-cell').forEach(cell => {
                    const iso = cell.dataset.iso;
                    if (holidayMap[iso]) {
                        const h = cell.querySelector('.holiday');
                        h.textContent = `祝: ${holidayMap[iso]}`;
                        h.style.display = '';
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
            entries.forEach(e => map[e.workDate] = e);
            document.querySelectorAll('#tableBody tr').forEach(row => {
                const iso = row.querySelector('.date-cell').dataset.iso;
                const data = map[iso];
                if (!data) {
                    row.querySelector('.time-cell[data-type="start"]').textContent = '';
                    row.querySelector('.time-cell[data-type="end"]').textContent = '';
                    row.querySelector('.break-cell').textContent = '';
                    row.querySelector('.duration-cell').textContent = '';
                    row.querySelector('.working-cell').textContent = '';
                    return;
                }
                row.querySelector('.time-cell[data-type="start"]').textContent = data.startTime ? ensureSeconds(data.startTime) : '';
                row.querySelector('.time-cell[data-type="end"]').textContent = data.endTime ? ensureSeconds(data.endTime) : '';
                row.querySelector('.break-cell').textContent = data.breakMinutes != null ? data.breakMinutes : '';
                row.querySelector('.duration-cell').textContent = data.durationMinutes != null ? fmtHM(data.durationMinutes) : '';
                row.querySelector('.working-cell').textContent = data.workingMinutes != null ? fmtHM(data.workingMinutes) : '';
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
})();
