import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    scenarios: {
        constant: {
            executor: 'constant-arrival-rate',
            rate: 1000,          // 목표 RPS (원하는 값으로 조절)
            timeUnit: '1s',
            duration: '30s',
            preAllocatedVUs: 200,
            maxVUs: 2000,
        },
    },
};

const RUN_ID = __ENV.RUN_ID || `${Date.now()}`;
const PREFIX = `k6-tps-${RUN_ID}-`;

export default function () {
    const url = 'http://host.docker.internal:8080/jobs';
    const key = `${PREFIX}${__VU}-${__ITER}`;

    const body = JSON.stringify({
        jobKey: key,
        type: "BATCH_SIM",
        payload: JSON.stringify({ sleepMs: 10, failTimes: 0 }),
    });

    const res = http.post(url, body, { headers: { 'Content-Type': 'application/json' } });
    check(res, { 'create ok': (r) => r.status === 200 || r.status === 201 });

    sleep(0.001);
}