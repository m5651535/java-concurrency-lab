import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
    stages: [
        { duration: '30s', target: 500 }, // 30秒內爬升到 500 VUs
        { duration: '1m', target: 2000 }, // 維持 1分鐘 2000 VUs
        { duration: '10s', target: 0 },    // 降溫
    ],
};

export default function () {
    // 測試 MVC 模組
    let resMvc = http.get('http://localhost:8081/mvc/test');
    check(resMvc, { 'MVC status is 200': (r) => r.status === 200 });

    // 測試 WebFlux 模組
    let resFlux = http.get('http://localhost:8082/flux/test');
    check(resFlux, { 'Flux status is 200': (r) => r.status === 200 });

    sleep(0.1); // 每個虛擬用戶休息 100ms
}