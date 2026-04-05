const fs = require('fs');
const https = require('https');
const http = require('http');

const schools = JSON.parse(fs.readFileSync('E:/KEchengbiao/app/src/main/assets/schools.json', 'utf8'));

async function checkUrl(url, schoolId) {
    return new Promise((resolve) => {
        const protocol = url.startsWith('https') ? https : http;
        const req = protocol.request(url, { method: 'HEAD', timeout: 8000 }, (res) => {
            resolve({
                schoolId,
                url,
                status: res.statusCode,
                success: res.statusCode < 400,
                error: null
            });
            req.destroy();
        });
        req.on('error', (e) => {
            resolve({
                schoolId,
                url,
                status: 0,
                success: false,
                error: e.code || e.message
            });
        });
        req.on('timeout', () => {
            resolve({
                schoolId,
                url,
                status: 0,
                success: false,
                error: 'TIMEOUT'
            });
            req.destroy();
        });
        req.end();
    });
}

async function main() {
    console.log(`\n开始检测 ${schools.length} 所学校的 URL...\n`);
    
    const results = [];
    let passed = 0;
    let failed = 0;
    
    for (const school of schools) {
        const result = await checkUrl(school.loginUrl, school.id);
        results.push(result);
        
        if (result.success) {
            passed++;
            process.stdout.write(`✅ ${school.id.padEnd(20)} ${result.status}\n`);
        } else {
            failed++;
            process.stdout.write(`❌ ${school.id.padEnd(20)} ${result.error || 'FAILED'}\n`);
        }
        
        // 小延迟避免被封
        await new Promise(r => setTimeout(r, 100));
    }
    
    console.log(`\n${'='.repeat(60)}`);
    console.log(`检测结果: 总计 ${schools.length} | ✅ 通过 ${passed} | ❌ 失败 ${failed} (${(failed/schools.length*100).toFixed(1)}%)`);
    
    // 输出失败详情
    const failures = results.filter(r => !r.success);
    if (failures.length > 0) {
        console.log(`\n❌ 失败的 URL 详情:`);
        console.log('-'.repeat(80));
        for (const f of failures) {
            const school = schools.find(s => s.id === f.schoolId);
            console.log(`\n[${f.schoolId}] ${school?.name}`);
            console.log(`  URL: ${f.url}`);
            console.log(`  错误: ${f.error}`);
        }
        
        // 保存失败列表到文件
        fs.writeFileSync('E:/KEchengbiao/scripts/reports/failed-urls.json', JSON.stringify(failures, null, 2));
        console.log(`\n失败列表已保存到: scripts/reports/failed-urls.json`);
    }
}

main().catch(console.error);
