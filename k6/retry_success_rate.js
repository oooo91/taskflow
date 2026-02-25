import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '5s', target: 50 },
        { duration: '20s', target: 200 },
        { duration: '5s', target: 0 },
    ],
};

const RUN_ID = __ENV.RUN_ID || `${Date.now()}`;
const PREFIX = `k6-retry-${RUN_ID}-`;

export default function () {
    const createUrl = 'http://host.docker.internal:8080/jobs';

    // 60%: failTimes=1 -> 2번째에 성공
    // 40%: failTimes=10 -> maxAttempts=3이라 최종 FAILED
    const r = Math.random();
    const failTimes = (r < 0.6) ? 1 : 10;

    const key = `${PREFIX}${__VU}-${__ITER}`;
    const body = JSON.stringify({
        jobKey: key,
        type: "BATCH_SIM",
        payload: JSON.stringify({ sleepMs: 30, failTimes }),
    });

    const res = http.post(createUrl, body, { headers: { 'Content-Type': 'application/json' } });
    check(res, { 'create ok': (r) => r.status === 200 || r.status === 201 });

    sleep(0.02);
}