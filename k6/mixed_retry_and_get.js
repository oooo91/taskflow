import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '5s', target: 20 },
        { duration: '15s', target: 80 },
        { duration: '5s', target: 0 },
    ],
};

export default function () {
    const createUrl = 'http://host.docker.internal:8080/jobs';

    const failTimes = (__ITER % 3 === 0) ? 2 : 0;
    const key = `k6-mix-${__VU}-${__ITER}`;

    const body = JSON.stringify({
        jobKey: key,
        type: "BATCH_SIM",
        payload: JSON.stringify({ sleepMs: 30, failTimes }),
    });

    const createRes = http.post(createUrl, body, {
        headers: { 'Content-Type': 'application/json' },
    });

    check(createRes, {
        'created ok': (r) => r.status === 200 || r.status === 201,
    });

    if (createRes.status === 200 || createRes.status === 201) {
        const json = createRes.json();
        const jobId = json.id;

        if (__ITER % 2 === 0 && jobId) {
            const getRes = http.get(`http://host.docker.internal:8080/jobs/${jobId}`);
            check(getRes, { 'get ok': (r) => r.status === 200 });
        }
    }

    sleep(0.05);
}
