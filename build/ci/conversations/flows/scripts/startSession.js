const res = http.post('http://localhost:17777/session/start', {body: ''})
if (!res.ok) throw new Error('Failed to start session: HTTP ' + res.status);

const body = JSON.parse(res.body);
if (body.status !== 'started') throw new Error('Unexpected session start response: ' + res.body);

output.timestamp = body.timestamp;
