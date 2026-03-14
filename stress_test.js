import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
    stages: [
        { duration: '10s', target: 50 },  // 快速爬升
        { duration: '1m', target: 200 },  // 穩定維持（建議先用 200 測斷路器，太高怕你電腦先卡死）
        { duration: '10s', target: 0 },   // 降溫
    ],
};

// 從環境變數讀取目標埠號，預設為 8081
const port = __ENV.TARGET_PORT || '8081';
// 根據埠號決定路徑：8081 走 /mvc/user/1，其他走 /flux/user/1
const path = (port === '8081') ? '/mvc/user/1' : '/flux/user/1';
const url = `http://localhost:${port}${path}`;

export default function () {
    let res = http.get(url);

    // 這裡的 check 會動態顯示是哪個 Port 在跑
    check(res, {
        [`Status is 200 (Port ${port})`]: (r) => r.status === 200,
        'Response time < 500ms': (r) => r.timings.duration < 500,
    });

    sleep(0.1);
}