// EXP K22 krok 2: czy 404 jest per-kanal czy globalne (per IP).
// 3 kanaly, odstep 4 s. Dwa z nich odpowiadaly OK na tablecie, jeden dawal 404.
const CH = [
  ['dzialal na tablecie A', 'UCrXSVX9a1mj8l0CMLwKgMVw'],
  ['dzialal na tablecie B', 'UCCehmsLClWwxA_SDLtff6xA'],
  ['dawal 404 (MrBeast)',   'UCX6xikMVxBvmr3y-pYiMMmg'],
];
const sleep = ms => new Promise(r => setTimeout(r, ms));
(async () => {
  for (const [label, id] of CH) {
    const r = await fetch('https://www.youtube.com/feeds/videos.xml?channel_id=' + id);
    const b = await r.text();
    console.log(`${label.padEnd(24)} ${id}  kod=${r.status}  entry=${(b.match(/<entry>/g)||[]).length}`);
    await sleep(4000);
  }
})();
