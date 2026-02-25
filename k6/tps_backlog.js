import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '5s', target: 200 },
        { duration: '10s', target: 600 },
        { duration: '5s', target: 0 },
    ],
};

const RUN_ID = __ENV.RUN_ID || `${Date.now()}`;
const PREFIX = `k6-backlog-${RUN_ID}-`;

export default function () {
    const url = 'http://host.docker.internal:8080/jobs';
    const key = `${PREFIX}${__VU}-${__ITER}`;

    const body = JSON.stringify({
        jobKey: key,
        type: "BATCH_SIM",
        payload: JSON.stringify({ sleepMs: 50, failTimes: 0 }),
    });

    const res = http.post(url, body, { headers: { 'Content-Type': 'application/json' } });
    check(res, { 'create ok': (r) => r.status === 200 || r.status === 201 });

    sleep(0.01);
}