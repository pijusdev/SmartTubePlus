// EXP K22: czy User-Agent decyduje o 404 na feeds/videos.xml
// Uruchomienie: node test_ua.js
// UWAGA (K23): TYLKO dwa zapytania, z odstepem - nie zanieczyszczac pomiaru.
const CH = 'UCX6xikMVxBvmr3y-pYiMMmg'; // MrBeast - kanal na pewno istniejacy
const URL = 'https://www.youtube.com/feeds/videos.xml?channel_id=' + CH;
const UAS = [
  ['okhttp (jak SmartTube dzis)', 'okhttp/3.12.13'],
  ['przegladarka (jak NewPipe/PipePipe)', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36'],
];
const sleep = ms => new Promise(r => setTimeout(r, ms));
(async () => {
  for (const [label, ua] of UAS) {
    try {
      const r = await fetch(URL, { headers: { 'User-Agent': ua } });
      const body = await r.text();
      console.log(`${label}\n  UA=${ua}\n  kod=${r.status} dlugosc=${body.length} entry=${(body.match(/<entry>/g)||[]).length}`);
    } catch (e) {
      console.log(`${label}\n  BLAD: ${e.message}`);
    }
    await sleep(3000);
  }
})();
