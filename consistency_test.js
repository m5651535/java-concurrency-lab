import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
    parallelism: 10,
    scenarios: {
        // 寫入者：不斷更新使用者名稱
        writers: {
            executor: 'constant-vus',
            vus: 5,
            duration: '10s',
            exec: 'writeTask',
        },
        // 讀取者：不斷讀取使用者
        readers: {
            executor: 'constant-vus',
            vus: 50,
            duration: '10s',
            exec: 'readTask',
        },
    },
};

const BASE_URL = `http://localhost:8081`;

export function writeTask() {
    let payload = JSON.stringify({ name: "Updated_Name_" + Math.random() });
    let params = { headers: { 'Content-Type': 'application/json' } };
    http.put(`${BASE_URL}/mvc/users/1`, payload, params);
    sleep(0.5); // 每 0.5 秒更新一次
}

export function readTask() {
    http.get(`${BASE_URL}/mvc/users/1/resilient`);
    sleep(0.1);
}