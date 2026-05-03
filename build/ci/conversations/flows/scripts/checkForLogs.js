const pattern = PATTERN;
if (!pattern) throw new Error('PATTERN env var is required');

const res = http.get('http://localhost:17777/assert?pattern=' + encodeURIComponent(pattern));
if (res.status === 404) throw new Error('Expected log line not found: ' + pattern);
if (!res.ok) throw new Error('Sidecar error: ' + res.status);

const body = JSON.parse(res.body);
output.matchedLines = body.lines;
body.lines.forEach(function(line) { console.log(line); });
