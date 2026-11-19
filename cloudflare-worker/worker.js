/** JARVIS NEXUS Gemini proxy — Cloudflare Workers (Module syntax)
 * Set secrets: GEMINI_API_KEY (required), JARVIS_ACCESS_TOKEN (recommended).
 * Deploy with: npx wrangler deploy
 */
const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization',
  'Access-Control-Max-Age': '86400'
};
const json = (body, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { ...cors, 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' }
});
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers: cors });
    if (url.pathname === '/health' && request.method === 'GET') return json({ ok: true, service: 'jarvis-nexus-gemini-proxy' });
    if (url.pathname !== '/chat' || request.method !== 'POST') return json({ error: 'Not found' }, 404);
    if (!env.GEMINI_API_KEY) return json({ error: 'کلید Gemini روی Worker تنظیم نشده است.' }, 503);
    if (env.JARVIS_ACCESS_TOKEN) {
      const supplied = request.headers.get('Authorization') || '';
      if (supplied !== `Bearer ${env.JARVIS_ACCESS_TOKEN}`) return json({ error: 'کد دسترسی معتبر نیست.' }, 401);
    }
    let body;
    try { body = await request.json(); } catch { return json({ error: 'درخواست JSON معتبر نیست.' }, 400); }
    const contents = Array.isArray(body.contents) ? body.contents.slice(-12) : [];
    if (!contents.length || contents.some(x => !['user', 'model'].includes(x?.role) || !Array.isArray(x.parts))) return json({ error: 'ساختار گفتگو معتبر نیست.' }, 400);
    const total = JSON.stringify(contents).length;
    if (total > 24000) return json({ error: 'متن گفتگو بیش از حد طولانی است.' }, 413);
    const safeContents = contents.map(x => ({ role: x.role, parts: x.parts.slice(0, 1).map(p => ({ text: String(p.text || '').slice(0, 8000) })) }));
    const upstream = await fetch('https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent', {
      method: 'POST', headers: { 'Content-Type': 'application/json', 'x-goog-api-key': env.GEMINI_API_KEY },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: 'تو جارویس نکسوس هستی؛ دستیار فارسی‌زبان، دقیق، محترمانه و صادق. پاسخ‌ها را به فارسی روان بده مگر کاربر زبان دیگری بخواهد. اگر مطمئن نیستی، شفاف بگو. ادعا نکن کاری را روی گوشی انجام داده‌ای مگر ابزار آن واقعاً اجرا شده باشد. برای کارهای حساس و پرخطر راهنمایی عملی نده.' }] },
        contents: safeContents,
        generationConfig: { temperature: 0.7, maxOutputTokens: 900 }
      })
    });
    let result;
    try { result = await upstream.json(); } catch { return json({ error: 'پاسخ نامعتبر از سرویس Gemini.' }, 502); }
    if (!upstream.ok) return json({ error: result?.error?.message || `Gemini API error ${upstream.status}` }, upstream.status === 429 ? 429 : 502);
    const text = (result?.candidates?.[0]?.content?.parts || []).map(p => p.text || '').join('').trim();
    if (!text) return json({ error: 'Gemini پاسخی تولید نکرد.' }, 502);
    return json({ text });
  }
};
