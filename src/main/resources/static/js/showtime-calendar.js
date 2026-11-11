/* Floating Vietnamese local dates; independent of the browser time zone. */
(() => {
  'use strict';
  const stamp = value => {
    if (!/^\d{4}-\d\d-\d\dT\d\d:\d\d(?::\d\d)?$/.test(value || '')) return NaN;
    const parsed = Date.parse(`${value}Z`);
    return Number.isFinite(parsed) && new Date(parsed).toISOString().slice(0, value.length) === value ? parsed : NaN;
  };
  const iso = value => new Date(value).toISOString().slice(0, 16);
  const addDays = (day, count) => iso(Date.parse(`${day}T00:00Z`) + count * 86400000).slice(0, 10);
  const monday = day => { const d = new Date(`${day}T00:00Z`); return addDays(day, -(d.getUTCDay() + 6) % 7); };
  function timing(row, durations) {
    const start = stamp(`${row.date}T${row.time}`), duration = Number(durations.get(String(row.movieId)));
    if (!Number.isFinite(start) || !duration) return null;
    return { startsAt: iso(start), endsAt: iso(start + duration * 60000), cleanupUntil: iso(start + (duration + 15) * 60000), roomId: String(row.roomId) };
  }
  const overlaps = (a, b) => String(a.roomId) === String(b.roomId) && stamp(a.startsAt) < stamp(b.cleanupUntil) && stamp(a.cleanupUntil) > stamp(b.startsAt);
  function available(row, durations, events, rows, serverTime) {
    return !problem(row, durations, events, rows, serverTime);
  }
  function problem(row, durations, events, rows, serverTime) {
    const t=timing(row,durations);
    if(!t||!row.movieId||!row.roomId)return 'Chọn phim, phòng và giờ hợp lệ.';
    if(stamp(t.startsAt)<=Date.parse(serverTime)+7*3600000)return 'Giờ bắt đầu đã qua. Chỉ thêm suất trong tương lai.';
    const saved=events.find(e=>overlaps(t,e));
    if(saved)return `Phòng đang có ${saved.movieTitle} lúc ${saved.startsAt.slice(11,16)}, gồm 15 phút dọn phòng.`;
    const selected=rows.find(other=>other.rowKey!==row.rowKey&&timing(other,durations)&&overlaps(t,timing(other,durations)));
    return selected?`Trùng suất đang xếp lúc ${selected.time}, gồm 15 phút dọn phòng.`:'';
  }
  function source(rows) { const excel = rows.some(r => r.sourceRow != null), manual = rows.some(r => r.sourceRow == null); return excel ? manual ? 'MIXED' : 'EXCEL' : 'MANUAL'; }
  function merge(rows, incoming) { if (rows.length + incoming.length > 500) throw new Error('Tổng vượt 500 suất. File chưa được bổ sung; các lựa chọn trước vẫn được giữ.'); return [...rows, ...incoming]; }
  class Draft {
    constructor() { this.rows = []; this.revision = 0; this.preview = null; }
    invalidate() { this.revision++; this.preview = null; }
    replace(rows) { if (rows.length > 500) throw new Error('Tối đa 500 suất mỗi đợt.'); this.rows = rows; this.invalidate(); }
    accept(revision, preview) { if (revision !== this.revision) return false; this.rows = preview.rows.map(r => r.input); this.preview = preview; return true; }
    ready() { return this.rows.length > 0 && this.preview?.canCommit === true; }
  }
  const minutes = time => /^\d\d:\d\d/.test(time || '') ? Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5)) : NaN;
  const clock = minute => `${String(Math.floor(minute / 60)).padStart(2, '0')}:${String(minute % 60).padStart(2, '0')}`;
  const roundUp = (minute, step = 5) => Math.ceil(minute / step) * step;
  /* Occupied spans of one room, in minutes from the day's midnight; cleanup is part of each span. */
  function busy(day, roomId, durations, events, rows, exceptKey) {
    const base = stamp(`${day}T00:00`), list = [];
    const add = (t, extra) => { if (t && String(t.roomId) === String(roomId)) list.push({ from: (stamp(t.startsAt) - base) / 60000, end: (stamp(t.endsAt) - base) / 60000, until: (stamp(t.cleanupUntil) - base) / 60000, ...extra }); };
    for (const e of events) add(e, { saved: true, title: e.movieTitle });
    for (const r of rows) if (r.rowKey !== exceptKey) add(timing(r, durations), { saved: false, rowKey: r.rowKey });
    return list.filter(i => i.until > 0 && i.from < 2880).sort((a, b) => a.from - b.from);
  }
  /* Where a show of `length` minutes (movie + cleanup) lands for a pointer at `minute`: sticks to the show before it, or backs up to fit the gap. */
  function snap(minute, length, spans, step = 5) {
    const before = spans.filter(s => s.from <= minute).reduce((last, s) => !last || s.until > last.until ? s : last, null), floor = before ? roundUp(before.until, step) : 0;
    let start = Math.max(0, Math.min(1440 - step, Math.floor(minute / step) * step)), snapped = '';
    if (before && start - floor <= 20) { start = Math.min(1440 - step, Math.max(0, floor)); snapped = 'after'; }
    const next = spans.find(s => s.from >= start && s.until > start && s !== before);
    if (next && start + length > next.from) {
      const back = Math.floor((next.from - length) / step) * step;
      if (back >= Math.max(0, floor)) { start = back; snapped = 'before'; }
      else return { minute: start, time: clock(start), snapped, reason: `Khoảng trống trước suất ${clock(Math.max(0, Math.round(next.from)) % 1440)} chỉ có ${Math.max(0, Math.round(next.from - Math.max(0, before ? before.until : 0)))} phút; phim cần ${length} phút gồm dọn phòng.` };
    }
    return { minute: start, time: clock(start), snapped, reason: '' };
  }
  /* Earliest minute at or after `from` that is not inside an occupied span. */
  function firstFree(from, spans, step = 5) { let minute = from; for (const s of spans) if (s.from <= minute && s.until > minute) minute = roundUp(s.until, step); return Math.min(minute, 1440 - step); }
  /* Back-to-back shows from seed.time until the last allowed start; stops at the first conflict instead of skipping it. */
  function chain(seed, lastStart, durations, events, rows, serverTime, key, step = 5) {
    const duration = Number(durations.get(String(seed.movieId))), made = []; let minute = minutes(seed.time), stopped = '';
    if (!duration || !Number.isFinite(minute)) return { rows: made, stopped: 'Chọn phim, phòng và giờ hợp lệ.' };
    while (minute <= lastStart && minute < 1440) {
      if (rows.length + made.length >= 500) { stopped = 'Đã đạt 500 suất mỗi đợt.'; break; }
      const row = { ...seed, rowKey: key(), sourceRow: null, time: clock(minute) }, reason = problem(row, durations, events, [...rows, ...made], serverTime);
      if (reason) { stopped = reason; break; }
      made.push(row); minute = roundUp(minute + duration + 15, step);
    }
    return { rows: made, stopped };
  }
  /* Clones unsaved rows onto other days; conflicting clones are reported, never added. */
  function copyDay(sourceRows, days, durations, events, rows, serverTime, key) {
    const added = [], skipped = [];
    for (const day of days) for (const from of sourceRows) {
      const row = { ...from, rowKey: key(), sourceRow: null, date: day };
      const reason = rows.length + added.length >= 500 ? 'Đã đạt 500 suất mỗi đợt.' : problem(row, durations, events, [...rows, ...added], serverTime);
      if (reason) skipped.push({ row, reason }); else added.push(row);
    }
    return { added, skipped };
  }
  /* Back-to-back shows from seed.time until the last allowed start; jumps over occupied spans and carries on after them, never into the past. */
  function fillDay(seed, lastStart, durations, events, rows, serverTime, key, step = 5) {
    const duration = Number(durations.get(String(seed.movieId))), added = [], skipped = [], base = stamp(`${seed.date}T00:00`); let minute = minutes(seed.time);
    if (!duration || !Number.isFinite(minute) || !Number.isFinite(base) || !seed.roomId) return { added, skipped: [{ row: { ...seed }, reason: 'Chọn phim, phòng và giờ hợp lệ.' }] };
    const length = duration + 15, spans = busy(seed.date, seed.roomId, durations, events, rows), soon = roundUp((Date.parse(serverTime) + 7 * 3600000 - base) / 60000 + 1, step);
    if (soon > minute) minute = soon;
    while (minute <= lastStart && minute < 1440) {
      const hit = spans.find(s => s.from < minute + length && s.until > minute);
      if (hit) { minute = roundUp(hit.until, step); continue; }
      const row = { ...seed, rowKey: key(), sourceRow: null, time: clock(minute) };
      const reason = rows.length + added.length >= 500 ? 'Đã đạt 500 suất mỗi đợt.' : problem(row, durations, events, [...rows, ...added], serverTime);
      if (reason) { skipped.push({ row, reason }); break; }
      added.push(row); spans.push({ from: minute, until: minute + length }); minute = roundUp(minute + length, step);
    }
    if (!added.length && !skipped.length) skipped.push({ row: { ...seed, time: clock(Math.min(minute, 1435)) }, reason: soon > lastStart ? 'Giờ suất cuối đã qua. Chỉ thêm suất trong tương lai.' : 'Phòng không còn chỗ trống trước giờ suất cuối.' });
    return { added, skipped };
  }
  /* One show per day at seed.time (lastStart == null) or a filled day, across several days; conflicts are reported, never added. */
  function plan(seed, days, lastStart, durations, events, rows, serverTime, key) {
    const added = [], skipped = [];
    for (const day of days) {
      if (lastStart == null) {
        const row = { ...seed, rowKey: key(), sourceRow: null, date: day };
        const reason = rows.length + added.length >= 500 ? 'Đã đạt 500 suất mỗi đợt.' : problem(row, durations, events, [...rows, ...added], serverTime);
        if (reason) skipped.push({ row, reason }); else added.push(row);
      } else { const result = fillDay({ ...seed, date: day }, lastStart, durations, events, [...rows, ...added], serverTime, key); added.push(...result.added); skipped.push(...result.skipped); }
    }
    return { added, skipped };
  }
  const api = { stamp, iso, addDays, monday, timing, overlaps, available, problem, source, merge, Draft, minutes, clock, roundUp, busy, snap, firstFree, chain, copyDay, fillDay, plan };
  if (typeof module !== 'undefined' && module.exports) module.exports = api; else window.CinePrimeCalendar = api;
})();
