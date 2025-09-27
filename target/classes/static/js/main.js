// メインクライアントスクリプト（初期化はアイドル時またはDOMContentLoaded後に実行）
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
  const on = (selector, evt, handler, ns = '') => {
    const evtName = ns ? `${evt}.${ns}` : evt;
    $(document).off(evtName, selector).on(evtName, selector, handler);
  };

  // PC: サイドバー折りたたみ切替
  on('#pcSidebarToggle', 'click', () => $('#sidebarMenu, .main-content').toggleClass('is-collapsed'));

  // サイドバーの Offcanvas を初期化
  const $sidebar = $('#sidebarMenu');
  if ($sidebar.length) bootstrap.Offcanvas.getOrCreateInstance($sidebar[0]);

  // ビューポート高さをCSSカスタムプロパティに反映
  const setAppHeight = () => $('html').css('--app-height', `${window.innerHeight}px`);
  on(window, 'resize', setAppHeight, 'app');
  setAppHeight();

  // トースト表示
  on('#liveToastBtn', 'click', () => bootstrap.Toast.getOrCreateInstance($('#liveToast')[0]).show(), 'toast');

  // モーダル別にバックドロップの色を切替
  on('#errorModal', 'shown.bs.modal', () => $('.modal-backdrop').last().addClass('backdrop-error'), 'modal');
  on('#scrollableModal', 'shown.bs.modal', () => $('.modal-backdrop').last().addClass('backdrop-select'), 'modal');

  // モーダル内のアイテム選択で /content をAJAX遷移（失敗時は通常遷移）
  on('.content-item', 'click', function (e) {
    e.preventDefault();
    const screenName = $(this).data('screen-name');
    if (!screenName) return;
    const url = new URL(window.location.href);
    url.pathname = '/content';
    url.searchParams.set('screenName', screenName);

    $.get(url.toString()).done(function (response) {
      try {
        const parsed = $($.parseHTML(response));
        const newMain = parsed.find('main.main-content');
        const newSelectedName = parsed.find('#selectedItemName').text() || screenName;
        if (newMain.length) {
          $('main.main-content').replaceWith(newMain);
          $('#selectedItemName').text(newSelectedName);
          const modalEl = $('#scrollableModal')[0];
          if (modalEl) {
            const modalInst = bootstrap.Modal.getInstance(modalEl) || bootstrap.Modal.getOrCreateInstance(modalEl);
            modalInst.hide();
          }
          // 履歴を置き換え（リロード無し）
          history.pushState({ screenName }, '', url.toString());
          return;
        }
      } catch (err) {
        // パース失敗時はフル遷移
      }
      window.location.href = url.toString();
    }).fail(function () {
      window.location.href = url.toString();
    });
  }, 'content');

  // 戻る/進むで main コンテンツを再取得
  window.addEventListener('popstate', function (event) {
    const state = event.state || {};
    const screenName = state.screenName || null;
    if (!screenName) return;
    const url = new URL(window.location.href);
    url.pathname = '/content';
    url.searchParams.set('screenName', screenName);
    $.get(url.toString()).done(function (response) {
      const parsed = $($.parseHTML(response));
      const newMain = parsed.find('main.main-content');
      const newSelectedName = parsed.find('#selectedItemName').text() || screenName;
      if (newMain.length) {
        $('main.main-content').replaceWith(newMain);
        $('#selectedItemName').text(newSelectedName);
      }
    });
  });
});
