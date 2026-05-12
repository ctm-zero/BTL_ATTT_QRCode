// =============================================
// THEME SWITCHER
// =============================================
const THEMES = {
    pinky:  {
        file:      'css/style-pinky.css',
        icon:      '🌙',
        bodyClass: 'light-theme'
    },
    ubuntu: {
        file:      'css/style-techno.css',
        icon:      '🌸',
        bodyClass: 'dark-theme'
    },
};

let currentTheme = localStorage.getItem('theme') || 'pinky';
applyTheme(currentTheme);

function toggleTheme() {
    currentTheme = currentTheme === 'pinky' ? 'ubuntu' : 'pinky';
    applyTheme(currentTheme);
    localStorage.setItem('theme', currentTheme);
}

function applyTheme(name) {
    const t = THEMES[name];
    document.getElementById('theme-css').href = t.file;
    document.getElementById('theme-toggle').dataset.icon = t.icon;
    document.body.classList.remove('theme-pinky', 'theme-ubuntu');
    document.body.classList.add(t.bodyClass);
}

// =============================================

// Toggle active cho EC level buttons
document.querySelectorAll('#ec-group .seg-btn').forEach(btn => {
    btn.addEventListener('click', () => {
        document.querySelectorAll('#ec-group .seg-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
    });
});

// Toggle active cho version buttons
document.querySelectorAll('#version-group .v-btn').forEach(btn => {
    btn.addEventListener('click', () => {
        document.querySelectorAll('#version-group .v-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
    });
});

// Lấy giá trị đang active
function getActiveValue(groupId) {
    const active = document.querySelector(`#${groupId} .active`);
    return active ? active.dataset.value : 'auto';
}

// Gọi API khi bấm Generate
document.getElementById('generate-btn').addEventListener('click', async () => {
    const data = document.getElementById('data').value.trim();

    if (!data) {
        alert('Vui lòng nhập dữ liệu!');
        return;
    }

    const version = document.getElementById('version-select').value;
    const errorCorrectionLevel = getActiveValue('ec-group');

    try {
        const response = await fetch('/api/generate', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ data, version, errorCorrectionLevel })
        });

        if (!response.ok) {
            const error = await response.text();
            alert('Lỗi: ' + error);
            return;
        }

        const result = await response.json();

        document.getElementById('meta-version').textContent = result.version;
        document.getElementById('meta-ec').textContent = result.ecLevel;

        const placeholder = document.getElementById('placeholder');
        placeholder.textContent = '✓ Bitstream ready (' + result.bitstream.length + ' bits)';

        console.log('Bitstream:', result.bitstream);
        console.log('Version:', result.version);
        console.log('EC Level:', result.ecLevel);
        console.log('Mode:', result.mode);

    } catch (err) {
        alert('Không thể kết nối đến server: ' + err.message);
    }
});