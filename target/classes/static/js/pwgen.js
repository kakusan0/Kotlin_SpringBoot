// pwgen.js - externalized Vanilla JS for password generator
// This file contains the optimized JS previously embedded in the dashboard fragment.

function initPwgen() {
  console.log('pwgen.js: initPwgen start');
  const lengthEl = document.getElementById('length');
  const lengthValueEl = document.getElementById('length-value');
  const useLower = document.getElementById('use-lower');
  const useUpper = document.getElementById('use-upper');
  const useNum = document.getElementById('use-num');
  const useSymbol = document.getElementById('use-symbol');
  const generateBtn = document.getElementById('generate-btn');
  const pwgenResult = document.getElementById('pwgen-result');
  const generatedPassword = document.getElementById('generated-password');
  const copyBtn = document.getElementById('copy-btn');
  const showHideBtn = document.getElementById('show-hide-btn');
  const pwStrength = document.getElementById('pw-strength');
  const pwStrengthLabel = document.getElementById('pw-strength-label');

  console.log('pwgen.js: elements', { lengthEl: !!lengthEl, useLower: !!useLower, useUpper: !!useUpper, useNum: !!useNum, useSymbol: !!useSymbol, generateBtn: !!generateBtn });

  let pwVisible = false;

  // helper: toggle ids
  const toggleIds = ['use-lower','use-upper','use-num','use-symbol'];

  function getToggleEl(id) { return document.getElementById(id); }

  // length input
  if (lengthEl && lengthValueEl) {
    lengthEl.addEventListener('input', () => {
      lengthValueEl.textContent = lengthEl.value;
    });
  }

  // wheel handling with minor throttle
  let wheelQueued = false;
  if (lengthEl) {
    lengthEl.addEventListener('wheel', (e) => {
      const ev = e;
      const min = Number(lengthEl.min || 4);
      const max = Number(lengthEl.max || 64);
      const step = ev.shiftKey ? 5 : 1;
      let val = Number(lengthEl.value);
      if (ev.deltaY < 0) val = Math.min(max, val + step);
      else val = Math.max(min, val - step);
      if (val !== Number(lengthEl.value)) {
        e.preventDefault();
        if (!wheelQueued) {
          wheelQueued = true;
          requestAnimationFrame(() => {
            lengthEl.value = val;
            lengthEl.dispatchEvent(new Event('input', { bubbles: true }));
            wheelQueued = false;
          });
        }
      }
    }, { passive: false });
  }

  // toggle helpers
  function setToggleState(el, on) {
    if (!el) return;
    el.setAttribute('aria-pressed', on ? 'true' : 'false');
    el.dataset.checked = on ? 'ON' : 'OFF';
    el.classList.toggle('opt-on', !!on);
  }

  function anyToggleOn() { return toggleIds.some(id => { const el = getToggleEl(id); return el && el.dataset && el.dataset.checked === 'ON'; }); }
  function allTogglesOn() { return toggleIds.every(id => { const el = getToggleEl(id); return el && el.dataset && el.dataset.checked === 'ON'; }); }

  function resetPasswordDisplay() {
    if (generatedPassword) generatedPassword.value = '';
    if (pwStrength) pwStrength.style.width = '0%';
    if (pwStrengthLabel) pwStrengthLabel.textContent = '';
    if (pwgenResult) pwgenResult.style.display = 'none';
  }

  function updateGenerateBtnState() {
    if (!generateBtn) return;
    generateBtn.disabled = !anyToggleOn();
  }

  // Initialize toggle visuals from DOM (if present)
  toggleIds.forEach(id => {
    const el = getToggleEl(id);
    if (el) {
      const init = el.dataset.checked === 'ON';
      setToggleState(el, init);
      console.log('pwgen.js: initialized toggle', id, '->', init);
    }
  });

  // Delegated handler for toggles (covers dynamic/replaced elements)
  // Use a short suppression window so pointerdown+click don't double-toggle
  const recentToggle = new Map();
  const SUPPRESSION_MS = 300;

  function handleDelegatedToggle(id) {
    const now = Date.now();
    const last = recentToggle.get(id) || 0;
    if (now - last < SUPPRESSION_MS) return; // skip duplicate
    recentToggle.set(id, now);
    const el = getToggleEl(id);
    if (el) {
      const current = el.dataset.checked === 'ON';
      setToggleState(el, !current);
      updateGenerateBtnState();
      if (!allTogglesOn()) resetPasswordDisplay();
    }
    // clear after timeout to avoid memory leaks
    setTimeout(() => recentToggle.delete(id), SUPPRESSION_MS + 50);
  }

  document.addEventListener('pointerdown', function(e) {
    const btn = e.target.closest('.opt-btn');
    if (!btn) return;
    const id = btn.id;
    if (!toggleIds.includes(id)) return;
    e.preventDefault();
    console.log('pwgen.js: delegated pointerdown toggle for', id);
    handleDelegatedToggle(id);
  });

  // click fallback for environments without pointer events
  document.addEventListener('click', function(e) {
    const btn = e.target.closest('.opt-btn');
    if (!btn) return;
    const id = btn.id;
    if (!toggleIds.includes(id)) return;
    console.log('pwgen.js: delegated click toggle for', id);
    handleDelegatedToggle(id);
  });

  updateGenerateBtnState();

  // keyboard shortcuts
  document.addEventListener('keydown', (e) => {
    if (e.altKey || e.ctrlKey || e.metaKey) return;
    const active = document.activeElement;
    if (active && (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA' || active.isContentEditable)) return;
    const map = { '1': useLower, '2': useUpper, '3': useNum, '4': useSymbol };
    const target = map[e.key];
    if (target) {
      e.preventDefault();
      setToggleState(target, target.dataset.checked !== 'ON');
      updateGenerateBtnState();
      if (!allTogglesOn()) resetPasswordDisplay();
      try { target.focus(); } catch (_) {}
    }
  });

  // generate
  function doGenerate() {
    const len = Math.max(4, Math.min(64, parseInt(lengthEl.value) || 12));
    const pw = generatePassword(
      len,
      useLower.dataset.checked === 'ON',
      useUpper.dataset.checked === 'ON',
      useNum.dataset.checked === 'ON',
      useSymbol.dataset.checked === 'ON'
    );
    if (anyToggleOn()) {
      if (generatedPassword) {
        generatedPassword.value = pw;
        generatedPassword.type = pwVisible ? 'text' : 'password';
      }
      if (pwgenResult) pwgenResult.style.display = 'block';
    } else {
      resetPasswordDisplay();
    }
    const score = calcStrength(pw);
    const info = strengthLabel(score);
    if (pwStrength) pwStrength.style.width = (score * 20) + '%';
    if (pwStrengthLabel) pwStrengthLabel.textContent = info.label;
  }

  if (generateBtn) {
    generateBtn.addEventListener('click', (e) => { e.preventDefault(); console.log('pwgen.js: generate clicked'); doGenerate(); });
    console.log('pwgen.js: attached generate handler');
  }

  // copy
  if (copyBtn) {
    copyBtn.addEventListener('click', () => {
      const val = generatedPassword.value; if (!val) return;
      console.log('pwgen.js: copy clicked');
      navigator.clipboard.writeText(val).then(() => {
        const tmp = document.createElement('div');
        tmp.className = 'pw-toast';
        tmp.textContent = 'コピーしました';
        document.body.appendChild(tmp);
        setTimeout(() => { tmp.style.transition = 'opacity 0.3s'; tmp.style.opacity = '0'; setTimeout(() => tmp.remove(), 300); }, 1000);
      });
    });
    console.log('pwgen.js: attached copy handler');
  }

  // show/hide
  if (showHideBtn) {
    showHideBtn.addEventListener('click', () => {
      pwVisible = !pwVisible;
      console.log('pwgen.js: showHide clicked ->', pwVisible);
      showHideBtn.textContent = pwVisible ? '非表示' : '表示';
      if (generatedPassword) generatedPassword.type = pwVisible ? 'text' : 'password';
    });
    console.log('pwgen.js: attached show/hide handler');
  }

  if (lengthEl) lengthEl.focus();
}

document.addEventListener('DOMContentLoaded', function () {
  initPwgen();
});

function generatePassword(length, useLower, useUpper, useNum, useSymbol) {
  let chars = '';
  if (useLower) chars += 'abcdefghijklmnopqrstuvwxyz';
  if (useUpper) chars += 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
  if (useNum) chars += '0123456789';
  if (useSymbol) chars += '!@#$%^&*()';
  if (!chars) return '';
  let password = '';
  for (let i = 0; i < length; i++) {
    password += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return password;
}

function calcStrength(pw) {
  let score = 0;
  if (pw.length >= 8) score++;
  if (/[a-z]/.test(pw)) score++;
  if (/[A-Z]/.test(pw)) score++;
  if (/[0-9]/.test(pw)) score++;
  if (/[!@#$%^&*()]/.test(pw)) score++;
  return score;
}

function strengthLabel(score) {
  switch(score) {
    case 5: return {label:'非常に強い'};
    case 4: return {label:'強い'};
    case 3: return {label:'普通'};
    case 2: return {label:'弱い'};
    default: return {label:'とても弱い'};
  }
}
