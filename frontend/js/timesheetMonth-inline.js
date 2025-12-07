// timesheetMonth.html内のインラインJSを外部ファイル化
window.currentUserName = window.currentUserName || 'user1';

// フルスクリーンモーダル用スクリプト
(function () {
    const fullscreenModal = document.getElementById('fullscreenModal');
    if (!fullscreenModal) return;
    const originalContainer = document.querySelector('.timesheet-table-container');
    const fullscreenContainer = document.getElementById('fullscreenTableContainer');
    const monthInput = document.getElementById('monthInput');
    const monthDisplay = document.getElementById('fullscreenMonthDisplay');
    if (!originalContainer || !fullscreenContainer) return;
    window.isFullscreenMode = false;
    fullscreenModal.addEventListener('show.bs.modal', function () {
        window.isFullscreenMode = true;
        if (monthInput && monthDisplay) {
            const [year, month] = monthInput.value.split('-');
            monthDisplay.textContent = year + '年' + parseInt(month) + '月';
        }
        fullscreenContainer.appendChild(originalContainer);
        originalContainer.style.maxHeight = 'calc(100vh - 80px)';
        originalContainer.style.border = 'none';
        originalContainer.style.pointerEvents = 'auto';
    });
    fullscreenModal.addEventListener('shown.bs.modal', function () {
        const modalBody = fullscreenModal.querySelector('.modal-body');
        if (modalBody) {
            modalBody.style.pointerEvents = 'auto';
        }
        fullscreenModal.querySelectorAll('button, select, input, [contenteditable]').forEach(el => {
            el.style.pointerEvents = 'auto';
        });
    });
    fullscreenModal.addEventListener('hidden.bs.modal', function () {
        window.isFullscreenMode = false;
        const reportMessage = document.getElementById('reportMessage');
        if (reportMessage && reportMessage.parentNode) {
            reportMessage.parentNode.insertBefore(originalContainer, reportMessage.nextSibling);
        }
        originalContainer.style.maxHeight = '';
        originalContainer.style.border = '';
        const contextMenu = document.getElementById('contextMenu');
        if (contextMenu) {
            contextMenu.remove();
        }
    });
    fullscreenModal.addEventListener('contextmenu', function (e) {
        e.preventDefault();
        e.stopPropagation();
        const existingMenu = document.getElementById('contextMenu');
        if (existingMenu) {
            existingMenu.remove();
        }
        const contextMenu = document.createElement('div');
        contextMenu.id = 'contextMenu';
        contextMenu.style.cssText = `position: fixed;top: ${e.clientY}px;left: ${e.clientX}px;z-index: 99999;background: #fff;border: 1px solid #ccc;padding: 5px;box-shadow: 0 2px 5px rgba(0, 0, 0, 0.2);`;
        const excelBtn = document.createElement('div');
        excelBtn.textContent = 'Excelダウンロード';
        excelBtn.style.cssText = 'cursor: pointer; padding: 5px 10px;';
        excelBtn.addEventListener('click', function () {
            window.dispatchEvent(new CustomEvent('downloadReport', {detail: 'xlsx'}));
            contextMenu.remove();
        });
        excelBtn.addEventListener('mouseenter', function () {
            this.style.background = '#f0f0f0';
        });
        excelBtn.addEventListener('mouseleave', function () {
            this.style.background = '';
        });
        const pdfBtn = document.createElement('div');
        pdfBtn.textContent = 'PDFダウンロード';
        pdfBtn.style.cssText = 'cursor: pointer; padding: 5px 10px;';
        pdfBtn.addEventListener('click', function () {
            window.dispatchEvent(new CustomEvent('downloadReport', {detail: 'pdf'}));
            contextMenu.remove();
        });
        pdfBtn.addEventListener('mouseenter', function () {
            this.style.background = '#f0f0f0';
        });
        pdfBtn.addEventListener('mouseleave', function () {
            this.style.background = '';
        });
        contextMenu.appendChild(excelBtn);
        contextMenu.appendChild(pdfBtn);
        fullscreenModal.appendChild(contextMenu);
        const closeMenu = function (ev) {
            if (!contextMenu.contains(ev.target)) {
                contextMenu.remove();
                document.removeEventListener('click', closeMenu);
            }
        };
        setTimeout(() => document.addEventListener('click', closeMenu), 0);
    });
})();
window.addEventListener('downloadReport', function (e) {
    const format = e.detail;
    const monthInput = document.getElementById('monthInput');
    const reportMessage = document.getElementById('reportMessage');
    (async function () {
        try {
            if (reportMessage) reportMessage.textContent = 'レポートを生成しています...';
            const monthValue = monthInput?.value || new Date().toISOString().substring(0, 7);
            const [year, month] = monthValue.split('-').map(Number);
            const from = year + '-' + String(month).padStart(2, '0') + '-01';
            const lastDay = new Date(year, month, 0).getDate();
            const to = year + '-' + String(month).padStart(2, '0') + '-' + String(lastDay).padStart(2, '0');
            const username = window.currentUserName || 'user1';
            const url = '/timesheet/report/' + format + '?username=' + encodeURIComponent(username) + '&from=' + from + '&to=' + to;
            const response = await fetch(url, {credentials: 'same-origin'});
            if (!response.ok) {
                if (reportMessage) reportMessage.textContent = 'レポート生成失敗 (' + response.status + ')';
                return;
            }
            const blob = await response.blob();
            const disposition = response.headers.get('Content-Disposition') || '';
            const filenameMatch = /filename\*=UTF-8''(.+)$/.exec(disposition) || /filename=(.+)$/.exec(disposition);
            const filename = filenameMatch ? decodeURIComponent(filenameMatch[1].replace(/"/g, '')) : 'timesheet_' + from + '_to_' + to + '.' + format;
            const a = document.createElement('a');
            a.href = URL.createObjectURL(blob);
            a.download = filename;
            document.body.appendChild(a);
            a.click();
            a.remove();
            if (reportMessage) reportMessage.textContent = 'ダウンロード完了';
        } catch (err) {
            console.error('Download error:', err);
            if (reportMessage) reportMessage.textContent = 'ダウンロードエラー';
        }
    })();
});

