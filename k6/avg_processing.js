import http from 'k6/http';
import { check } from 'k6';

export const options = {
    scenarios: {
        create_many: {
            executor: 'constant-arrival-rate',
            rate: 300,        // 초당 300건
            timeUnit: '1s',
            duration: '20s',  // 총 6000건 정도
            preAllocatedVUs: 200,
            maxVUs: 400,
        },
    },
};

export default function () {
    const url = 'http://host.docker.internal:8080/jobs';
    const key = `bench-avg-${__VU}-${__ITER}-${Date.now()}`;

    const body = JSON.stringify({
        jobKey: key,
        type: "BATCH_SIM",
        payload: JSON.stringify({ sleepMs: 200, failTimes: 0 }),
    });

    const res = http.post(url, body, { headers: { 'Content-Type': 'application/json' } });
    check(res, { 'status is 200/201': (r) => r.status === 200 || r.status === 201 });
}