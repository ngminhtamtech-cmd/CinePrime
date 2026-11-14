/* Lịch chiếu — shared behaviour of the three tabs: keyboard shortcuts, filters that apply on change,
   click on the empty part of the day axis to add a show there, and bulk open / cancel in the list.
   Every rule stays on the server; this file only reads the page and calls the existing JSON API. */
(() => {
  'use strict';
  /* The CSP allows no inline style attributes, so positions on the time axis and sales meters arrive as data-left / data-width. */
  document.querySelectorAll('[data-left],[data-width]').forEach(el => { if (el.dataset.left) el.style.left = el.dataset.left; if (el.dataset.width) el.style.width = el.dataset.width; });
  const typing = el => !!el?.closest?.('input,select,textarea,[contenteditable="true"]');
  /* N, T, ←, → click the element marked data-hotkey, unless the admin is typing or a dialog is open. */
  document.addEventListener('keydown', e => {
    if (e.ctrlKey || e.metaKey || e.altKey || typing(e.target) || document.querySelector('dialog[open]')) return;
    const key = e.key.length === 1 ? e.key.toLowerCase() : e.key, target = document.querySelector(`[data-hotkey="${key}"]`);
    if (!target || target.disabled || target.closest('[hidden]')) return;
    e.preventDefault(); target.click();
  });
  document.querySelectorAll('[data-autosubmit]').forEach(el => el.addEventListener('change', () => {
    const form = el.form; if (!form) return; if (el.name === 'date' || el.name === 'cinemaId') form.querySelectorAll('select[name=movieId],select[name=roomId]').forEach(s => { if (el.name === 'cinemaId' || s.name === 'movieId') s.value = ''; });
    form.requestSubmit();
  }));

  /* Day axis: a click on empty space opens the weekly planner with room, day and the clicked time filled in. */
  document.querySelectorAll('.tl-track[data-add]').forEach(track => {
    const from = Number(track.dataset.from), to = Number(track.dataset.to);
    const at = x => { const box = track.getBoundingClientRect(), minute = from + (x - box.left) / box.width * (to - from); const m = Math.max(0, Math.min(1435, Math.round(minute / 15) * 15)); return `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`; };
    track.addEventListener('mousemove', e => { if (e.target === track || e.target.classList.contains('tl-line')) track.title = `Thêm suất lúc ${at(e.clientX)}`; else track.removeAttribute('title'); });
    track.addEventListener('click', e => { if (e.target !== track && !e.target.classList.contains('tl-line')) return; location.href = `${track.dataset.add}&time=${at(e.clientX)}`; });
  });

  /* Showtime list: choose rows, then open every chosen draft or cancel every chosen show in one all-or-nothing request. */
  const list = document.querySelector('[data-bulk]'); if (!list) return;
  const q = s => list.querySelector(s), picks = () => [...list.querySelectorAll('.st-pick')], chosen = () => picks().filter(p => p.checked);
  function sync() {
    const all = chosen(), drafts = all.filter(p => p.dataset.draft === 'true');
    q('.st-bulk').hidden = !all.length; q('.st-count').textContent = `Đã chọn ${all.length} suất`;
    q('.st-detail').textContent = `${drafts.length} nháp · ${all.length - drafts.length} đang mở bán`;
    q('.st-open').textContent = `Mở bán ${drafts.length} suất nháp`; q('.st-open').disabled = !drafts.length;
    q('.st-cancel').textContent = `Huỷ ${all.length} suất`;
    picks().forEach(p => p.closest('tr').classList.toggle('st-on', p.checked));
    const box = q('.st-all'); box.checked = !!picks().length && all.length === picks().length; box.indeterminate = !!all.length && !box.checked; box.disabled = !picks().length;
  }
  list.addEventListener('change', e => { if (e.target.classList.contains('st-all')) picks().forEach(p => { p.checked = e.target.checked; }); sync(); });
  q('.st-clear').onclick = () => { picks().forEach(p => { p.checked = false; }); sync(); };
  const say = (text, bad) => { const m = q('.st-message'); m.hidden = !text; m.textContent = text; m.className = `st-message${bad ? ' bad' : ''}`; };
  async function run(action, items, question) {
    if (!items.length || !window.confirm(question)) return;
    const headers = new Headers({ Accept: 'application/json', 'Content-Type': 'application/json' });
    const token = document.querySelector('meta[name=csrf-token]')?.content, header = document.querySelector('meta[name=csrf-header]')?.content; if (token && header) headers.set(header, token);
    list.querySelectorAll('.st-bulk button').forEach(b => { b.disabled = true; }); say(action === 'OPEN' ? 'Đang mở bán…' : 'Đang huỷ…');
    try {
      const response = await fetch('/api/admin/showtime-batches/showtimes/bulk', { method: 'POST', headers, cache: 'no-store', body: JSON.stringify({ action, items: items.map(p => ({ id: Number(p.value), expected: p.dataset.token })) }) });
      let body = {}; try { body = await response.json(); } catch (_) { /* empty */ }
      if (!response.ok) throw new Error(body.message || 'Chưa thực hiện được. Không suất nào bị thay đổi.');
      try { sessionStorage.setItem('cineprime.showNotice', action === 'OPEN' ? `Đã mở bán ${body.count} suất.` : `Đã huỷ ${body.count} suất.`); } catch (_) { /* storage off */ }
      location.reload();
    } catch (x) { say(x.message, true); sync(); }
  }
  q('.st-open').onclick = () => { const d = chosen().filter(p => p.dataset.draft === 'true'); run('OPEN', d, `Mở bán ${d.length} suất nháp? Khách đặt được vé ngay sau khi mở bán.`); };
  q('.st-cancel').onclick = () => { const all = chosen(); run('CANCEL', all, `Huỷ ${all.length} suất đã chọn? Khách sẽ không đặt được các suất này nữa. Không thể hoàn tác.`); };
  sync();
})();
