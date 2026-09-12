function sleep(ms) {
    const end = Date.now() + ms;
    while (Date.now() < end) { /* busy-wait */ }
}

function findMatchingLines(pattern) {
    const res = http.get('http://localhost:17777/assert?pattern=' + encodeURIComponent(pattern));
    if (res.status === 404) return null;
    if (!res.ok) throw new Error('Sidecar error: ' + res.status);
    return JSON.parse(res.body).lines;
}

function pollForMatchingLines(pattern, maxAttempts, delayMs) {
    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
        const lines = findMatchingLines(pattern);
        if (lines) return lines;
        if (attempt < maxAttempts) sleep(delayMs);
    }
    return null;
}

const pattern = typeof PATTERN !== 'undefined' ? PATTERN : undefined;
if (!pattern) throw new Error('PATTERN env var is required');

const maxAttempts = typeof MAX_ATTEMPTS !== 'undefined' ? parseInt(MAX_ATTEMPTS, 10) : 1;
const delayMs = typeof DELAY_MS !== 'undefined' ? parseInt(DELAY_MS, 10) : 1000;

const matchedLines = pollForMatchingLines(pattern, maxAttempts, delayMs);
if (!matchedLines) throw new Error('Expected log line not found: ' + pattern);

output.matchedLines = matchedLines;
matchedLines.forEach(function(line) { console.log(line); });
