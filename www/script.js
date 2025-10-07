// Ocean服务器交互脚本
document.addEventListener('DOMContentLoaded', function() {
    console.log('🌊 Ocean服务器页面已加载');
    
    // 添加页面加载动画
    const container = document.querySelector('.container');
    if (container) {
        container.style.opacity = '0';
        container.style.transform = 'translateY(30px)';
        
        setTimeout(() => {
            container.style.transition = 'all 0.8s ease-out';
            container.style.opacity = '1';
            container.style.transform = 'translateY(0)';
        }, 100);
    }
    
    // 创建服务器状态检查功能
    createStatusChecker();
    
    // 添加交互按钮
    createInteractiveButtons();
    
    // 添加动态时间显示
    createTimeDisplay();
    
    // 添加点击特效
    addClickEffects();
});

// 服务器状态检查
function createStatusChecker() {
    const statusIndicator = document.querySelector('.status-indicator');
    if (statusIndicator) {
        // 模拟服务器状态检查
        setInterval(() => {
            const isOnline = Math.random() > 0.1; // 90% 概率在线
            statusIndicator.style.background = isOnline ? '#27ae60' : '#e74c3c';
            statusIndicator.title = isOnline ? '服务器运行正常' : '服务器连接异常';
        }, 3000);
    }
}

// 创建交互按钮
function createInteractiveButtons() {
    const container = document.querySelector('.container');
    if (!container) return;
    
    // 创建按钮容器
    const buttonContainer = document.createElement('div');
    buttonContainer.className = 'button-container';
    buttonContainer.style.marginTop = '2rem';
    
    // 服务器信息按钮
    const infoBtn = document.createElement('button');
    infoBtn.className = 'btn';
    infoBtn.textContent = '📊 查看服务器信息';
    infoBtn.onclick = showServerInfo;
    
    // 测试连接按钮
    const testBtn = document.createElement('button');
    testBtn.className = 'btn';
    testBtn.textContent = '🔗 测试连接';
    testBtn.onclick = testConnection;
    
    // 刷新页面按钮
    const refreshBtn = document.createElement('button');
    refreshBtn.className = 'btn';
    refreshBtn.textContent = '🔄 刷新页面';
    refreshBtn.onclick = () => window.location.reload();
    
    buttonContainer.appendChild(infoBtn);
    buttonContainer.appendChild(testBtn);
    buttonContainer.appendChild(refreshBtn);
    
    container.appendChild(buttonContainer);
}

// 显示服务器信息
function showServerInfo() {
    const info = `
        <div class="info-box">
            <h4>🌊 Ocean服务器信息</h4>
            <ul style="list-style: none; padding: 0;">
                <li><strong>服务器名称:</strong> Ocean Web Server</li>
                <li><strong>版本:</strong> 1.0.0</li>
                <li><strong>运行时间:</strong> ${getUptime()}</li>
                <li><strong>当前时间:</strong> ${new Date().toLocaleString()}</li>
                <li><strong>用户代理:</strong> ${navigator.userAgent}</li>
            </ul>
        </div>
    `;
    
    showModal('服务器信息', info);
}

// 测试连接
function testConnection() {
    const button = event.target;
    const originalText = button.textContent;
    
    button.textContent = '🔄 测试中...';
    button.disabled = true;
    
    // 模拟连接测试
    setTimeout(() => {
        const isConnected = Math.random() > 0.2; // 80% 成功率
        const result = isConnected ? 
            '<p style="color: #27ae60;">✅ 连接测试成功！服务器响应正常。</p>' :
            '<p style="color: #e74c3c;">❌ 连接测试失败，请检查网络连接。</p>';
        
        showModal('连接测试结果', result);
        
        button.textContent = originalText;
        button.disabled = false;
    }, 1500);
}

// 创建时间显示
function createTimeDisplay() {
    const container = document.querySelector('.container');
    if (!container) return;
    
    const timeDisplay = document.createElement('div');
    timeDisplay.className = 'time-display';
    timeDisplay.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        background: rgba(0, 0, 0, 0.8);
        color: white;
        padding: 0.5rem 1rem;
        border-radius: 20px;
        font-family: monospace;
        font-size: 0.9rem;
        z-index: 1000;
    `;
    
    function updateTime() {
        const now = new Date();
        timeDisplay.textContent = now.toLocaleString();
    }
    
    updateTime();
    setInterval(updateTime, 1000);
    
    document.body.appendChild(timeDisplay);
}

// 添加点击特效
function addClickEffects() {
    const buttons = document.querySelectorAll('.btn');
    buttons.forEach(button => {
        button.addEventListener('click', function(e) {
            // 创建涟漪效果
            const ripple = document.createElement('span');
            const rect = this.getBoundingClientRect();
            const size = Math.max(rect.width, rect.height);
            const x = e.clientX - rect.left - size / 2;
            const y = e.clientY - rect.top - size / 2;
            
            ripple.style.cssText = `
                position: absolute;
                width: ${size}px;
                height: ${size}px;
                left: ${x}px;
                top: ${y}px;
                background: rgba(255, 255, 255, 0.6);
                border-radius: 50%;
                transform: scale(0);
                animation: ripple 0.6s linear;
                pointer-events: none;
            `;
            
            this.style.position = 'relative';
            this.style.overflow = 'hidden';
            this.appendChild(ripple);
            
            setTimeout(() => {
                ripple.remove();
            }, 600);
        });
    });
    
    // 添加CSS动画
    const style = document.createElement('style');
    style.textContent = `
        @keyframes ripple {
            to {
                transform: scale(4);
                opacity: 0;
            }
        }
    `;
    document.head.appendChild(style);
}

// 显示模态框
function showModal(title, content) {
    // 移除已存在的模态框
    const existingModal = document.querySelector('.modal');
    if (existingModal) {
        existingModal.remove();
    }
    
    const modal = document.createElement('div');
    modal.className = 'modal';
    modal.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(0, 0, 0, 0.5);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 2000;
        opacity: 0;
        transition: opacity 0.3s ease;
    `;
    
    const modalContent = document.createElement('div');
    modalContent.style.cssText = `
        background: white;
        padding: 2rem;
        border-radius: 15px;
        max-width: 500px;
        width: 90%;
        max-height: 80vh;
        overflow-y: auto;
        transform: translateY(-20px);
        transition: transform 0.3s ease;
    `;
    
    modalContent.innerHTML = `
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">
            <h3 style="margin: 0; color: #2c3e50;">${title}</h3>
            <button onclick="this.closest('.modal').remove()" style="
                background: none;
                border: none;
                font-size: 1.5rem;
                cursor: pointer;
                color: #999;
            ">&times;</button>
        </div>
        <div>${content}</div>
    `;
    
    modal.appendChild(modalContent);
    document.body.appendChild(modal);
    
    // 显示动画
    setTimeout(() => {
        modal.style.opacity = '1';
        modalContent.style.transform = 'translateY(0)';
    }, 10);
    
    // 点击背景关闭
    modal.addEventListener('click', function(e) {
        if (e.target === modal) {
            modal.remove();
        }
    });
}

// 获取运行时间（模拟）
function getUptime() {
    const startTime = new Date(Date.now() - Math.random() * 86400000); // 随机运行时间
    const uptime = Date.now() - startTime.getTime();
    const hours = Math.floor(uptime / 3600000);
    const minutes = Math.floor((uptime % 3600000) / 60000);
    return `${hours}小时 ${minutes}分钟`;
}

// 页面可见性变化处理
document.addEventListener('visibilitychange', function() {
    if (document.hidden) {
        console.log('🌊 Ocean页面已隐藏');
    } else {
        console.log('🌊 Ocean页面已显示');
    }
});

// 键盘快捷键
document.addEventListener('keydown', function(e) {
    if (e.ctrlKey || e.metaKey) {
        switch(e.key) {
            case 'i':
                e.preventDefault();
                showServerInfo();
                break;
            case 't':
                e.preventDefault();
                testConnection();
                break;
            case 'r':
                e.preventDefault();
                window.location.reload();
                break;
        }
    }
});

console.log('🌊 Ocean服务器脚本初始化完成');
console.log('💡 提示: 使用 Ctrl+I 查看服务器信息, Ctrl+T 测试连接, Ctrl+R 刷新页面');
