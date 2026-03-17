import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
    scenarios: {
        writers: {
            executor: 'per-vu-iterations',
            vus: 5,
            iterations: 10, // 每個 Writer 只寫 10 次就停止
            exec: 'writeTask',
        },
        readers: {
            executor: 'constant-vus',
            vus: 200,       // 大量讀取者
            duration: '15s', // 讀取時間比寫入長，確保最後是讀取在洗版
            exec: 'readTask',
        },
    },
};

const BASE_URL = `http://localhost:8081`;

export function writeTask() {
    // 確保包含所有必要欄位，避免 PUT 導致資料遺失
    let payload = JSON.stringify({
        "id": 1,
        "username": "Updated_" + Math.random().toString(36).substring(7),
        "email": "user_1@example.com" // 保持原始 email
    });

    let params = { headers: { 'Content-Type': 'application/json' } };

    // 呼叫你的 MVC 更新接口
    let res = http.put(`${BASE_URL}/mvc/user/1`, payload, params);

    check(res, {
        'write status is 200': (r) => r.status === 200,
    });

    // 每次更新後稍微停頓，讓讀取者有機會在「雙刪」的空隙間切入
    sleep(0.5);
}

export function readTask() {
    let res = http.get(`${BASE_URL}/mvc/user/1/simple`);

    check(res, {
        'read status is 200': (r) => r.status === 200,
    });
    sleep(0.1);
}