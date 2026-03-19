import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
    // 門檻值：如果 95% 的請求超過 1 秒或失敗率 > 1%，壓測視為不合格
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<1000'],
    },
    stages: [
        { duration: '10s', target: 500 },
        { duration: '30s', target: 1000 },  // 拉長讓 cache 熱起來
        { duration: '10s', target: 0 },
    ],
};

const port = __ENV.TARGET_PORT || '8081';
const testType = __ENV.TEST_TYPE || 'simple';
const userId = '1';

// 自動判定路徑：8081 是 MVC，其餘預設為 WebFlux
const path = (port === '8081')
    ? `/mvc/user/${userId}/${testType}`
    : `/flux/user/${userId}`;

const url = `http://localhost:${port}${path}`;

export default function () {
    const userId = Math.floor(Math.random() * 10000) + 1;  // 1~10000 隨機
    const path = `/flux/user/${userId}`;
    const url = `http://localhost:${port}${path}`;

    let res = http.get(url);

    check(res, {
        'Status is 200': (r) => r.status === 200,
        'No Cache Breakdown Delay': (r) => r.timings.duration < 300,
    });

    sleep(0.05);
}