import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
    stages: [
        { duration: '1s', target: 500 },   // 🚀 核心重點：1 秒內強行拉起 500 個虛擬執行緒
        { duration: '10s', target: 500 },  // 維持壓力，確保大家都擠進那個 200ms 的 Thread.sleep 窗口
        { duration: '5s', target: 0 },
    ],
};

// 環境變數讀取
const port = __ENV.TARGET_PORT || '8081';
// TEST_TYPE 可選值: 'simple' (無鎖版) 或 'resilient' (分散式鎖版)
const testType = __ENV.TEST_TYPE || 'simple';
// 集中火力攻擊同一個熱點 ID，模擬擊穿場景
const userId = '1';

const url = `http://localhost:${port}/mvc/user/${userId}/${testType}`;

export default function () {
    let res = http.get(url);

    check(res, {
        [`Status is 200 (${testType})`]: (r) => r.status === 200,
        'Response time < 1000ms': (r) => r.timings.duration < 1000,
    });

    // 擊穿實驗通常不加 sleep 或加極短 sleep，模擬最極端的競爭
    sleep(0.01);
}