// ==================== 모달 공통 ====================

function openModal(modalId) {
    document.getElementById(modalId).classList.remove('hidden');
    document.body.style.overflow = 'hidden';
}

function closeModal(modalId) {
    document.getElementById(modalId).classList.add('hidden');
    document.body.style.overflow = '';
}

function showModalError(errorElId, message) {
    const el = document.getElementById(errorElId);
    el.textContent = message;
    el.classList.remove('hidden');
}

function hideModalError(errorElId) {
    document.getElementById(errorElId).classList.add('hidden');
}

// ==================== 버튼 로딩 ====================

function startLoading(button) {
    button.disabled = true;
    button.dataset.originalText = button.textContent;
    button.innerHTML = `
        <svg class="animate-spin h-4 w-4 inline-block mr-1" viewBox="0 0 24 24" fill="none">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
        </svg>
        처리중...`;
    button.classList.add('opacity-70', 'cursor-not-allowed');
}

function stopLoading(button) {
    button.disabled = false;
    button.textContent = button.dataset.originalText;
    button.classList.remove('opacity-70', 'cursor-not-allowed');
}

function completeLoading(button) {
    button.innerHTML = `
        <svg class="h-4 w-4 inline-block mr-1" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
            <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7"/>
        </svg>
        완료`;
    button.classList.remove('opacity-70');
    button.classList.add('opacity-90');
    return new Promise(resolve => setTimeout(() => {
        stopLoading(button);
        resolve();
    }, 1000));
}

// ==================== 드래그앤드롭 ====================

function setupDropZone(zoneId, fileInputId) {
    const zone = document.getElementById(zoneId);
    if (!zone) return;

    zone.addEventListener('dragover', (e) => {
        e.preventDefault();
        zone.classList.add('border-docu-primary', 'bg-docu-primary-light/30');
    });
    zone.addEventListener('dragleave', () => {
        zone.classList.remove('border-docu-primary', 'bg-docu-primary-light/30');
    });
    zone.addEventListener('drop', (e) => {
        e.preventDefault();
        zone.classList.remove('border-docu-primary', 'bg-docu-primary-light/30');
        const fileInput = document.getElementById(fileInputId);
        if (e.dataTransfer.files.length > 0) {
            fileInput.files = e.dataTransfer.files;
            fileInput.dispatchEvent(new Event('change'));
        }
    });
}

// ==================== 파일 수정 모달 공통 ====================

function toggleEditFile() {
    const area = document.getElementById('editFileArea');
    if (document.getElementById('editFileToggle').checked) {
        area.classList.remove('hidden');
    } else {
        area.classList.add('hidden');
        clearEditFile();
    }
}

function onEditFileSelected(input) {
    if (input.files.length > 0) {
        document.getElementById('editFileName').textContent = input.files[0].name;
        document.getElementById('editFileInfo').classList.remove('hidden');
        document.getElementById('editDropZone').classList.add('hidden');
    }
}

function clearEditFile() {
    document.getElementById('editFile').value = '';
    document.getElementById('editFileInfo').classList.add('hidden');
    document.getElementById('editDropZone').classList.remove('hidden');
}

// ==================== 카테고리 칩 선택 ====================

const CHIP_INACTIVE = ['border-divider', 'text-docu-secondary', 'bg-surface-card'];
const CHIP_ACTIVE = ['border-docu-primary', 'bg-docu-primary', 'text-white', 'font-medium', 'shadow-sm'];

function selectCategory(chipEl, inputId) {
    const input = document.getElementById(inputId);
    const container = chipEl.closest('.flex.flex-wrap');
    container.querySelectorAll('.category-chip').forEach(btn => {
        btn.classList.remove(...CHIP_ACTIVE);
        btn.classList.add(...CHIP_INACTIVE);
    });
    chipEl.classList.remove(...CHIP_INACTIVE);
    chipEl.classList.add(...CHIP_ACTIVE);
    input.value = chipEl.textContent.trim();
}
