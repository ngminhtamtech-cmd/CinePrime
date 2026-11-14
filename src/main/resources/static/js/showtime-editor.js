/* One popup to edit, move or cancel a stored showtime, shared by the weekly scheduler, the daily schedule and the showtime list.
   Saves straight away through /api/admin/showtime-batches/showtimes/{id}; the server re-checks every rule. */
(() => {
  'use strict';
  const C = window.CinePrimeCalendar; if (!C) return;
  const names = ['Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7', 'Chủ nhật'];
  const node = (tag, text, cls) => { const el = document.createElement(tag); if (text !== undefined) el.textContent = text; if (cls) el.className = cls; return el; };
  const dmy = day => /^\d{4}-\d\d-\d\d$/.test(day || '') ? `${day.slice(8, 10)}/${day.slice(5, 7)}/${day.slice(0, 4)}` : '';
  const dayName = day => { const d = new Date(`${day}T00:00Z`); return Number.isNaN(d.getTime()) ? day : `${names[(d.getUTCDay() + 6) % 7]} ${day.slice(8, 10)}/${day.slice(5, 7)}`; };
  const hm = value => String(value || '').slice(11, 16);
  async function request(path, options = {}) {
    const headers = new Headers({ Accept: 'application/json', 'Content-Type': 'application/json' });
    const token = document.querySelector('meta[name=csrf-token]')?.content, header = document.querySelector('meta[name=csrf-header]')?.content; if (token && header) headers.set(header, token);
    const response = await fetch(`/api/admin/showtime-batches${path}`, { ...options, headers, cache: 'no-store' }); let body = {};
    try { body = await response.json(); } catch (_) { /* empty body */ }
    if (!response.ok) throw Object.assign(new Error(body.message || 'Không lưu được suất chiếu.'), { status: response.status, body });
    return body;
  }
  const savedRow = e => ({ movieId: String(e.movieId), roomId: String(e.roomId), date: e.startsAt.slice(0, 10), time: hm(e.startsAt), language: e.language || '', format: e.format || '', surcharge: String(e.surcharge ?? 0), status: e.status });
  /* Sends one stored show; resolves with the saved event (with its new token). */
  const put = (e, row) => request(`/showtimes/${e.id}`, { method: 'PUT', body: JSON.stringify({ expected: e.token, ...row }) });
  const cancel = e => request(`/showtimes/${e.id}/cancel`, { method: 'POST', body: JSON.stringify({ expected: e.token }) });

  /* The day of one room as an even grid of time chips. `items`: {startsAt,endsAt,movieTitle,draft?,self?,clash?}. */
  function strip(items, heading) {
    const box = node('section', undefined, 'se-day'), list = [...items].sort((a, b) => String(a.startsAt).localeCompare(String(b.startsAt)));
    const titles = new Set(list.filter(i => !i.self).map(i => i.movieTitle)), single = titles.size === 1 ? [...titles][0] : '';
    const head = node('header'); head.append(node('strong', heading), node('small', list.some(i => !i.self) ? `${list.filter(i => !i.self).length} suất khác${single ? ` · ${single}` : ''}` : 'Chưa có suất khác'));
    box.append(head);
    if (!list.length) return box;
    const ol = node('ol');
    for (const i of list) {
      const li = node('li', undefined, `se-slot${i.self ? ' self' : ''}${i.clash ? ' clash' : ''}${i.draft ? ' draft' : ''}`);
      li.append(node('b', `${hm(i.startsAt)}–${hm(i.endsAt)}`));
      const label = i.self ? (i.clash ? 'Suất này · trùng lịch' : 'Suất này') : i.clash ? 'Trùng với suất này' : i.draft ? `Chưa lưu · ${i.movieTitle}` : single ? '' : i.movieTitle;
      if (label) li.append(node('span', label));
      li.title = `${hm(i.startsAt)}–${hm(i.endsAt)} ${i.movieTitle}${i.draft ? ' (chưa lưu)' : ''}`; ol.append(li);
    }
    box.append(ol); return box;
  }

  let dialog = null, state = null;
  const $ = id => dialog.querySelector(`[data-se="${id}"]`);
  function build() {
    dialog = node('dialog', undefined, 'show-editor'); dialog.setAttribute('aria-labelledby', 'se-title');
    const form = node('form'); form.noValidate = true; dialog.append(form);
    const head = node('div', undefined, 'se-head'), title = node('h2', 'Sửa suất chiếu'); title.id = 'se-title';
    const chips = node('div', undefined, 'se-chips'); chips.append(node('span', '', 'se-chip'), node('span', '', 'se-chip window'));
    chips.children[0].dataset.se = 'state'; chips.children[1].dataset.se = 'window'; head.append(title, chips); form.append(head);
    const lock = node('p', 'Suất đã có vé hoặc lượt giữ ghế. Chỉ đổi được ngôn ngữ và định dạng; không dời, không hủy.', 'se-lock'); lock.dataset.se = 'lock'; form.append(lock);
    const grid = node('div', undefined, 'se-fields');
    const field = (label, el, key) => { const l = node('label', label); el.dataset.se = key; l.append(el); grid.append(l); return el; };
    field('Phim', node('select'), 'movie'); field('Phòng', node('select'), 'room');
    const date = field('Ngày chiếu', node('input'), 'date'); date.type = 'date'; date.required = true;
    const time = field('Giờ bắt đầu', node('input'), 'time'); time.type = 'time'; time.step = '60'; time.required = true;
    const status = field('Trạng thái', node('select'), 'status'); for (const [v, t] of [['OPEN', 'Đang bán vé'], ['DRAFT', 'Nháp · chưa bán vé']]) { const o = node('option', t); o.value = v; status.append(o); }
    const money = field('Phụ thu (VND)', node('input'), 'surcharge'); money.inputMode = 'numeric';
    field('Ngôn ngữ', node('input'), 'language').maxLength = 255; const fmt = field('Định dạng', node('input'), 'format'); fmt.maxLength = 255; fmt.placeholder = 'Theo phòng';
    form.append(grid);
    const end = node('p', '', 'se-end'); end.dataset.se = 'end'; const day = node('div'); day.dataset.se = 'day'; const error = node('p', '', 'se-error'); error.dataset.se = 'error'; error.setAttribute('role', 'alert');
    form.append(end, day, error);
    const actions = node('div', undefined, 'se-actions'); actions.dataset.se = 'actions';
    const drop = node('button', 'Hủy suất', 'button secondary se-danger'); drop.type = 'button'; drop.dataset.se = 'cancel';
    const full = node('a', 'Mở trang sửa đầy đủ', 'se-link'); full.dataset.se = 'full';
    const close = node('button', 'Đóng', 'button secondary'); close.type = 'button'; close.dataset.se = 'close';
    const save = node('button', 'Lưu thay đổi', 'button se-save'); save.type = 'submit'; save.dataset.se = 'save';
    actions.append(drop, full, close, save);
    const confirm = node('div', undefined, 'se-confirm'); confirm.dataset.se = 'confirm'; confirm.hidden = true;
    const ask = node('p', ''); ask.dataset.se = 'ask'; const no = node('button', 'Không hủy', 'button secondary'); no.type = 'button'; no.dataset.se = 'no'; const yes = node('button', 'Hủy suất', 'button se-danger-solid'); yes.type = 'button'; yes.dataset.se = 'yes';
    confirm.append(ask, no, yes); form.append(actions, confirm);
    document.body.append(dialog);
    form.addEventListener('input', update); close.onclick = () => dialog.close(); dialog.addEventListener('close', () => { state = null; });
    form.addEventListener('submit', submit);
    drop.onclick = () => { if (!state) return; $('ask').textContent = `Hủy suất ${state.event.movieTitle} · ${roomName(state.event.roomId)} · ${hm(state.event.startsAt)} ${dayName(state.event.startsAt.slice(0, 10))}? Khách sẽ không đặt được suất này nữa. Không thể hoàn tác.`; $('actions').hidden = true; $('confirm').hidden = false; no.focus(); };
    no.onclick = () => { $('confirm').hidden = true; $('actions').hidden = false; drop.focus(); };
    yes.onclick = doCancel;
  }
  const roomName = id => state?.rooms.find(r => String(r.id) === String(id))?.name || `Phòng #${id}`;
  const fields = () => ({ movieId: $('movie').value, roomId: $('room').value, date: $('date').value, time: $('time').value, language: $('language').value, format: $('format').value, surcharge: $('surcharge').value || '0', status: $('status').value });
  function update() {
    if (!state) return; const e = state.event, row = fields(), movie = state.movies.find(m => String(m.id) === row.movieId), t = C.timing(row, state.durations);
    $('window').textContent = movie?.endDate ? `Phim chiếu đến ${dmy(movie.endDate)}` : ''; $('window').hidden = !movie?.endDate;
    if (movie?.endDate) $('window').title = `Khoảng chiếu của phim: ${dmy(movie.releaseDate)} – ${dmy(movie.endDate)}`;
    if (!e.locked) { $('date').min = [state.today, movie?.releaseDate || ''].sort().at(-1); $('date').max = movie?.endDate || ''; }
    $('end').textContent = t ? `Kết thúc ${hm(t.endsAt)}${t.endsAt.slice(0, 10) !== row.date ? ` ${dayName(t.endsAt.slice(0, 10))}` : ''} · dọn phòng đến ${hm(t.cleanupUntil)}` : 'Chọn phim, ngày và giờ để tính giờ kết thúc.';
    const moved = !e.locked && (row.movieId !== String(e.movieId) || row.roomId !== String(e.roomId) || row.date !== e.startsAt.slice(0, 10) || row.time !== hm(e.startsAt));
    const others = state.events.filter(x => x.id !== e.id), probe = { ...row, rowKey: `saved-${e.id}` }, reason = moved ? C.problem(probe, state.durations, others, state.drafts, state.serverTime) : '';
    const err = $('error'); err.textContent = reason; err.hidden = !reason;
    const items = [], start = t ? C.stamp(t.startsAt) : NaN;
    if (row.date && row.roomId) {
      for (const x of others) if (String(x.roomId) === row.roomId && x.startsAt.slice(0, 10) === row.date) items.push({ ...x, clash: !!t && C.overlaps(t, x) });
      for (const d of state.drafts) { const dt = C.timing(d, state.durations); if (dt && String(d.roomId) === row.roomId && d.date === row.date) items.push({ ...dt, movieTitle: d.movieTitle || state.movies.find(m => String(m.id) === String(d.movieId))?.title || '', draft: true, clash: !!t && C.overlaps(t, dt) }); }
      if (t && Number.isFinite(start)) items.push({ ...t, movieTitle: movie?.title || e.movieTitle, self: true, clash: items.some(i => i.clash) });
    }
    $('day').replaceChildren(row.date && row.roomId ? strip(items, `${roomName(row.roomId)} · ${dayName(row.date)}`) : '');
  }
  function open(options) {
    if (!dialog) build(); const e = options.event, lock = !!e.locked;
    state = { ...options, drafts: options.drafts || [], durations: new Map(options.movies.map(m => [String(m.id), +m.duration])), today: options.today || new Date(Date.parse(options.serverTime) + 7 * 3600000).toISOString().slice(0, 10) };
    const film = $('movie'); film.replaceChildren(); for (const m of options.movies.filter(m => (m.active && m.status !== 'ENDED') || String(m.id) === String(e.movieId))) { const o = node('option', `${m.title} · ${m.duration} phút`); o.value = String(m.id); film.append(o); }
    const room = $('room'); room.replaceChildren(); for (const r of options.rooms.filter(r => r.active || String(r.id) === String(e.roomId))) { const o = node('option', `${r.name}${r.active ? '' : ' · ngừng hoạt động'}`); o.value = String(r.id); room.append(o); }
    const row = savedRow(e); for (const k of ['movie', 'room', 'date', 'time', 'language', 'format', 'surcharge', 'status']) $(k).value = row[k === 'movie' ? 'movieId' : k === 'room' ? 'roomId' : k];
    for (const k of ['movie', 'room', 'date', 'time', 'status', 'surcharge']) $(k).disabled = lock;
    $('lock').hidden = !lock; $('cancel').hidden = lock; $('confirm').hidden = true; $('actions').hidden = false; $('save').disabled = false;
    $('state').textContent = lock ? 'Có vé / giữ ghế' : e.status === 'OPEN' ? 'Đang bán vé' : 'Nháp · chưa bán'; $('state').className = `se-chip ${lock ? 'locked' : e.status === 'OPEN' ? 'open' : 'draft'}`;
    $('full').href = `/admin/manage/showtimes/edit?id=${e.id}&returnTo=${encodeURIComponent(options.returnTo || `/admin/schedule?cinemaId=${options.cinemaId || ''}&date=${e.startsAt.slice(0, 10)}`)}`;
    update(); dialog.showModal();
  }
  async function submit(ev) {
    ev.preventDefault(); if (!state) return; const s = state, row = fields(), err = $('error');
    if (!row.date || !row.time) { err.hidden = false; err.textContent = 'Nhập ngày và giờ bắt đầu.'; return; }
    if (!/^[0-9]+$/.test(row.surcharge)) { err.hidden = false; err.textContent = 'Phụ thu là số nguyên VND không âm.'; return; }
    $('save').disabled = true; err.hidden = true;
    try { const saved = await put(s.event, row); dialog.close(); await s.onSaved?.(saved, s.event); }
    catch (x) { if (x.status === 409) { dialog.close(); await s.onStale?.(x.message); } else { err.hidden = false; err.textContent = x.message; } }
    finally { if (dialog.open) $('save').disabled = false; }
  }
  async function doCancel() {
    if (!state) return; const s = state; $('yes').disabled = true;
    try { await cancel(s.event); dialog.close(); await s.onCancelled?.(s.event); }
    catch (x) { if (x.status === 409) { dialog.close(); await s.onStale?.(x.message); } else { $('confirm').hidden = true; $('actions').hidden = false; $('error').hidden = false; $('error').textContent = x.message; } }
    finally { $('yes').disabled = false; }
  }
  window.CinePrimeShowEditor = { open, put, cancel, strip, savedRow, dayName };

  /* Links marked data-show-edit (daily schedule, showtime list) open the popup; without JS, or for a cancelled show, they still go to the full page. */
  document.addEventListener('click', async ev => {
    const link = ev.target.closest?.('a[data-show-edit]'); if (!link || ev.button !== 0 || ev.ctrlKey || ev.metaKey || ev.shiftKey) return;
    ev.preventDefault(); if (link.dataset.busy) return; link.dataset.busy = '1';
    try {
      const cinema = link.dataset.cinema, day = link.dataset.date, id = Number(link.dataset.showEdit);
      const data = await request(`/calendar?cinemaId=${encodeURIComponent(cinema)}&date=${encodeURIComponent(day)}`), event = data.events.find(e => e.id === id);
      if (!event) { location.href = link.href; return; }
      const back = `${location.pathname}${location.search}`, reload = text => { try { sessionStorage.setItem(NOTICE, text); } catch (_) { /* storage off */ } location.reload(); };
      open({ event, movies: data.movies, rooms: data.rooms, events: data.events, serverTime: data.serverTime, cinemaId: cinema, returnTo: back,
        onSaved: saved => reload(`Đã lưu ${saved.movieTitle} · ${hm(saved.startsAt)} ${dayName(saved.startsAt.slice(0, 10))}.`), onCancelled: e => reload(`Đã hủy suất ${e.movieTitle} · ${hm(e.startsAt)} ${dayName(e.startsAt.slice(0, 10))}.`), onStale: reload });
    } catch (_) { location.href = link.href; } finally { delete link.dataset.busy; }
  });
  /* A short note after the page reloads from a popup save. */
  const NOTICE = 'cineprime.showNotice';
  let note = null; try { note = sessionStorage.getItem(NOTICE); sessionStorage.removeItem(NOTICE); } catch (_) { /* storage off */ }
  if (note) { const box = node('div', note, 'se-toast'); box.setAttribute('role', 'status'); document.body.append(box); setTimeout(() => box.remove(), 6000); }
})();
