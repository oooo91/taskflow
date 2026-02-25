import http from 'k6/http';
import { check } from 'k6';

export const options = {
    scenarios: {
        create_burst: {
            executor: 'constant-arrival-rate',
            rate: 200,          // 초당 200건 생성
            timeUnit: '1s',
            duration: '10s',    // 총 2000건 정도 생성
            preAllocatedVUs: 200,
            maxVUs: 400,
        },
    },
};

export default function () {
    const url = 'http://host.docker.internal:8080/jobs';
    const key = `bench-conc-${__VU}-${__ITER}-${Date.now()}`;

    const body = JSON.stringify({
        jobKey: key,
        type: "BATCH_SIM",
        payload: JSON.stringify({ sleepMs: 30000, failTimes: 0 }),
    });

    const res = http.post(url, body, { headers: { 'Content-Type': 'application/json' } });
    check(res, { 'status is 200/201': (r) => r.status === 200 || r.status === 201 });
}