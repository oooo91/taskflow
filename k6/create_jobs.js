import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 50,
    duration: '15s',
};

export default function () {
    const url = 'http://host.docker.internal:8080/jobs';
    const key = `k6-create-${__VU}-${__ITER}`;

    const payload = JSON.stringify({
        jobKey: key,
        type: "BATCH_SIM",
        payload: JSON.stringify({ sleepMs: 50, failTimes: 0 }),
    });

    const res = http.post(url, payload, {
        headers: { 'Content-Type': 'application/json' },
    });

    check(res, {
        'status is 200/201': (r) => r.status === 200 || r.status === 201,
    });

    sleep(0.1);
}
