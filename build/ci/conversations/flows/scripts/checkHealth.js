const MAX_ATTEMPTS = 3;
const DELAY_MS = 500;

function sleep(ms) {
    const end = Date.now() + ms;
    while (Date.now() < end) { /* busy-wait */ }
}

let healthy = false;
let lastError = 'Health check failed after ' + MAX_ATTEMPTS + ' attempts';

for (let i = 0; i < MAX_ATTEMPTS; i++) {
    if (i > 0) sleep(DELAY_MS);
    try {
        const res = http.get('http://localhost:17777/health');
        if (res.ok) { healthy = true; break; }
        lastError = 'Health check returned HTTP ' + res.status;
    } catch (e) {
        lastError = 'Health check request failed: ' + (e.message || String(e));
    }
}

if (!healthy) throw new Error(lastError);
