const fs = require('fs');
const https = require('https');
const http = require('http');

const schools = JSON.parse(fs.readFileSync('E:/KEchengbiao/app/src/main/assets/schools.json', 'utf8'));

// 常见的域名变体模式
const DOMAIN_VARIANTS = [
    '',           // 原始
    'www.',       // www 前缀
    'jwc.',      // 教务处
    'jwgl.',     // 教务管理
    'jwxt.',     // 教务系统
    'jw.',       // 教务
    'jwsq.',     // 教务社区
];

async function tryUrl(url, timeout = 5000) {
    return new Promise((resolve) => {
        const protocol = url.startsWith('https') ? https : http;
        const req = protocol.request(url, { method: 'HEAD', timeout }, (res) => {
            resolve({ success: res.statusCode < 400, status: res.statusCode });
            req.destroy();
        });
        req.on('error', () => resolve({ success: false, status: 0 }));
        req.on('timeout', () => { resolve({ success: false, status: 0, timeout: true }); req.destroy(); });
        req.end();
    });
}

function extractDomain(url) {
    try {
        const u = new URL(url);
        // 跳过 IP 地址添加 www 前缀
        if (/^\d+\.\d+\.\d+\.\d+$/.test(u.hostname)) {
            return { hostname: u.hostname, protocol: u.protocol, pathname: u.pathname, origin: u.origin };
        }
        return { hostname: u.hostname, protocol: u.protocol, pathname: u.pathname, origin: u.origin };
    } catch { return null; }
}

function generateUrlVariants(originalUrl) {
    const parsed = extractDomain(originalUrl);
    if (!parsed) return [originalUrl];
    
    const variants = new Set();
    const isIpAddress = /^\d+\.\d+\.\d+\.\d+$/.test(parsed.hostname);
    
    // 1. 原始 URL
    variants.add(originalUrl);
    
    // 2. HTTP/HTTPS 互换
    const otherProtocol = parsed.protocol === 'https:' ? 'http:' : 'https:';
    variants.add(`${otherProtocol}//${parsed.hostname}${parsed.pathname}`);
    
    // 3. 不同子域名前缀（仅对非 IP 地址）
    if (!isIpAddress) {
        for (const prefix of DOMAIN_VARIANTS) {
            if (prefix && !parsed.hostname.startsWith(prefix)) {
                const newHost = prefix + parsed.hostname.replace(/^(www\.|jwc\.|jwgl\.|jwxt\.|jw\.)/, '');
                variants.add(`${parsed.protocol}//${newHost}${parsed.pathname}`);
            }
        }
    }
    
    return Array.from(variants);
}

// 已知的正确域名映射（基于实际经验）
const KNOWN_FIXES = {
    'jw.sandau.edu.cn': 'jw.sandau.edu.cn',  // 可能需要 www 或其他子域
    '210.42.121.241': 'jwgl.whu.edu.cn',     // 武大内网 IP → 正式域名
    'bkjws.sdu.edu.cn': 'bkjws.sdu.edu.cn',   // 山大可能需要 https
    'jwgl.ncst.edu.cn': 'jwgl.ncst.edu.cn',    // 华北理工可能需要 https
    'portal.xju.edu.cn': 'jwgl.xju.edu.cn',   // 新疆大学
    'jwgl.ahjzu.edu.cn': 'jwgl.ahjzu.edu.cn', // 安徽建筑大学
    'jwgl.hpu.edu.cn': 'jwgl.hpu.edu.cn',     // 河南理工
    'jwgl.shengda.edu.cn': 'jwgl.shengda.edu.cn', // 升达学院
    'jwgl.zjkjedu.cn': 'jwgl.zjkjedu.cn',     // 湛江科技
    'jwgl.bjczy.edu.cn': 'jwgl.bjczy.edu.cn',  // 北京财贸
    'jwgl.ccu.edu.cn': 'jwgl.ccu.edu.cn',       // 长春大学
    'hsjw.ncwu.edu.cn': 'hsjw.ncwu.edu.cn',     // 华北水利
    'jwgl.hynu.edu.cn': 'jwgl.hynu.edu.cn',     // 衡阳师院
    'jwgl.jhun.edu.cn': 'jwgl.jhun.edu.cn',     // 江汉大学
    'jwgl.hzau.edu.cn': 'jwgl.hzau.edu.cn',     // 华中农大
    'jwgl.imust.edu.cn': 'jwgl.imust.edu.cn',   // 内蒙古科大
    'jwgl.tyust.edu.cn': 'jwgl.tyust.edu.cn',  // 太原科技
    'jwgl.yctu.edu.cn': 'jwgl.yctu.edu.cn',    // 盐城师院
    'jwgl.ysu.edu.cn': 'jwgl.ysu.edu.cn',       // 燕山大学
    'jwgl.zjkju.edu.cn': 'jwgl.zjkju.edu.cn',   // 湛江科技(正方)
    'jwgl.gdcp.edu.cn': 'jwgl.gdcp.edu.cn',     // 广东交通
    'jwgl.gxufl.edu.cn': 'jwgl.gxufl.edu.cn',  // 广西外国语
    'jw.jzist.edu.cn': 'jw.jzist.edu.cn',       // 荆州理工
    'jwgl.qmu.edu.cn': 'jwgl.qmu.edu.cn',       // 齐齐哈尔医
    'jw.whpa.edu.cn': 'jw.whpa.edu.cn',         // 武汉警官
    'jw.eurasia.edu': 'jw.eurasia.edu',         // 西安欧亚
    'jw.nxnu.edu.cn': 'jw.nxnu.edu.cn',         // 宁夏师院
    'jw.qztc.edu.cn': 'jw.qztc.edu.cn',         // 泉州师院
    'jwgl.sdnu.edu.cn': 'jwgl.sdnu.edu.cn',     // 山东师大
    'jw.tzpc.edu.cn': 'jw.tzpc.edu.cn',         // 泰州职院
    'jw.xauat.edu.cn': 'jw.xauat.edu.cn',       // 西安建科
    'jw.zzu.edu.cn': 'jw.zzu.edu.cn',           // 郑州大学
    'ems.sias.edu.cn': 'ems.sias.edu.cn',       // 西亚斯
    'jxgl.ahmu.edu.cn': 'jxgl.ahmu.edu.cn',     // 安徽医科
    'jwgl.sqgxy.edu.cn': 'jwgl.sqgxy.edu.cn',   // 商丘工学院
    'jwgl.ksu.edu.cn': 'jwgl.ksu.edu.cn',         // 喀什大学
    'jwsc.stbu.edu.cn': 'jwsc.stbu.edu.cn',       // 四川工商
    'jw.sddfvc.cn': 'jw.sddfvc.cn',             // 山东药品
    'jw.sxpu.edu.cn': 'jw.sxpu.edu.cn',          // 山西工程
    'jw.cqust.edu.cn': 'jw.cqust.edu.cn',        // 重庆科技
    'jw.gzgs.edu.cn': 'jw.gzgs.edu.cn',          // 广州工商
    'jwgl.hhdu.edu.cn': 'jwgl.hhdu.edu.cn',     // 哈尔滨华德
    'jw.sddfvc.cn': 'jw.sddfvc.cn',             // 山一医大
    'jw.hit.edu.cn': 'jw.hit.edu.cn',             // 哈工大
    'jw.btvt.edu.cn': 'jw.btvt.edu.cn',           // 包头职院
    'jw.whgc.edu.cn': 'jw.whgc.edu.cn',          // 武汉工程
    'jw.gdaib.edu.cn': 'jw.gdaib.edu.cn',        // 广东农工商
    'jxgl.wtc.edu.cn': 'jxgl.wtc.edu.cn',        // 武汉职院
    'jwgl.cuit.edu.cn': 'jwgl.cuit.edu.cn',       // 成信大
    'jwxt.zykj.edu.cn': 'jwxt.zykj.edu.cn',       // 中原科技
};

async function main() {
    console.log(`\n开始修复 ${schools.length} 所学校的 URL...\n`);
    
    let fixedCount = 0;
    let skippedCount = 0;
    const fixes = [];
    
    for (const school of schools) {
        const originalUrl = school.loginUrl;
        
        // 跳过已经成功的
        const initialCheck = await tryUrl(originalUrl, 3000);
        if (initialCheck.success) {
            process.stdout.write(`⏭️  ${school.id.padEnd(20)} 已正常，跳过\n`);
            skippedCount++;
            continue;
        }
        
        // 生成 URL 变体并测试
        const variants = generateUrlVariants(originalUrl);
        let foundWorking = null;
        
        for (const variant of variants) {
            if (variant === originalUrl) continue;
            
            const result = await tryUrl(variant, 3000);
            if (result.success) {
                foundWorking = variant;
                break;
            }
            
            // 短暂延迟
            await new Promise(r => setTimeout(r, 50));
        }
        
        if (foundWorking) {
            fixedCount++;
            fixes.push({
                id: school.id,
                name: school.name,
                oldUrl: originalUrl,
                newUrl: foundWorking
            });
            process.stdout.write(`✅ ${school.id.padEnd(20)} 修复成功!\n`);
            process.stdout.write(`   旧: ${originalUrl}\n`);
            process.stdout.write(`   新: ${foundWorking}\n\n`);
            
            // 更新内存中的数据
            school.loginUrl = foundWorking;
            // 同时更新 scheduleUrl 的协议
            if (school.scheduleUrl && !school.scheduleUrl.startsWith('http')) {
                // 相对路径不需要改
            } else if (school.scheduleUrl && originalUrl.includes('://')) {
                const oldProto = originalUrl.split('://')[0];
                const newProto = foundWorking.split('://')[0];
                school.scheduleUrl = school.scheduleUrl.replace(oldProto, newProto);
            }
        } else {
            process.stdout.write(`❌ ${school.id.padEnd(20)} 无法自动修复\n`);
        }
        
        // 进度显示
        await new Promise(r => setTimeout(r, 50));
    }
    
    console.log(`\n${'='.repeat(70)}`);
    console.log(`修复结果: ✅ 成功 ${fixedCount} | ⏭️  跳过 ${skippedCount} | ❌ 未修复 ${schools.length - fixedCount - skippedCount}`);
    
    if (fixes.length > 0) {
        // 保存修复后的完整文件
        fs.writeFileSync('E:/KEchengbiao/app/src/main/assets/schools.json', JSON.stringify(schools, null, 2), 'utf8');
        console.log(`\n✅ 已更新 schools.json (${fixes.length} 个 URL 已修复)`);
        
        // 保存修复详情
        fs.writeFileSync('E:/KEchengbiao/scripts/reports/url-fixes.json', JSON.stringify(fixes, null, 2), 'utf8');
        console.log(`修复详情已保存到: scripts/reports/url-fixes.json`);
    }
}

main().catch(console.error);
