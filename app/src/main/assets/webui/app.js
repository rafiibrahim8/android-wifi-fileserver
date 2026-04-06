const config = window.__WFS_CONFIG || {};
const currentPath = typeof config.currentPath === 'string' ? config.currentPath : '';
const uploadsEnabled = Boolean(config.uploadsEnabled);

const uploadBtn = document.getElementById('upload-btn');
const cancelUploadBtn = document.getElementById('cancel-upload');
const deleteToggleBtn = document.getElementById('delete-toggle');
const deleteDock = document.getElementById('delete-dock');
const deleteSummary = document.getElementById('delete-summary');
const deleteConfirmBtn = document.getElementById('delete-confirm');
const fileInput = document.getElementById('file-input');
const createFolderBtn = document.getElementById('create-folder');
const uploadDock = document.getElementById('upload-dock');
const uploadText = document.getElementById('upload-text');
const progressFill = document.getElementById('progress-fill');

let cancelRequested = false;
let activeUploadPath = '';
let activeController = null;
let uploadInProgress = false;
let deleteMode = false;
const selectedPaths = new Map();
const selectableRows = Array.from(document.querySelectorAll('tr[data-selectable="1"]'));

window.addEventListener('beforeunload', (event) => {
  if (!uploadInProgress) return;
  event.preventDefault();
  event.returnValue = '';
});

document.querySelectorAll('.copy-btn').forEach((btn) => {
  btn.addEventListener('click', async (event) => {
    event.stopPropagation();
    if (deleteMode) return;
    const text = btn.dataset.url || '';
    const ok = await copyText(text);
    const old = btn.textContent;
    btn.textContent = ok ? 'Copied' : 'Failed';
    setTimeout(() => {
      btn.textContent = old;
    }, 1100);
  });
});

selectableRows.forEach((row) => {
  row.addEventListener('click', (event) => {
    if (!deleteMode) return;
    if (event.target.closest('.copy-btn')) return;
    event.preventDefault();
    toggleRowSelection(row);
  });

  row.querySelectorAll('a').forEach((link) => {
    link.addEventListener('click', (event) => {
      if (!deleteMode) return;
      event.preventDefault();
      event.stopPropagation();
      toggleRowSelection(row);
    });
  });
});

if (deleteToggleBtn) {
  deleteToggleBtn.addEventListener('click', () => {
    setDeleteMode(!deleteMode);
  });
}

if (deleteConfirmBtn) {
  deleteConfirmBtn.addEventListener('click', async () => {
    if (selectedPaths.size === 0) return;
    const counts = getSelectionCounts();
    const confirmText = 'Delete ' + counts.dirs + ' ' + pluralize(counts.dirs, 'dir', 'dirs') +
      ' and ' + counts.files + ' ' + pluralize(counts.files, 'file', 'files') + '?';
    if (!confirm(confirmText)) return;

    deleteConfirmBtn.disabled = true;
    try {
      for (const path of selectedPaths.keys()) {
        const response = await fetch('/delete/' + encodePath(path), { method: 'POST' });
        if (!response.ok) {
          let detail = '';
          try { detail = await response.text(); } catch (_) {}
          throw new Error(response.status + (detail ? ' ' + detail : ''));
        }
      }
      location.reload();
    } catch (err) {
      alert('Delete failed: ' + (err && err.message ? err.message : 'unknown error'));
    } finally {
      deleteConfirmBtn.disabled = false;
    }
  });
}

if (uploadBtn && fileInput) {
  uploadBtn.addEventListener('click', () => fileInput.click());
  fileInput.addEventListener('change', () => uploadFiles(fileInput.files));
}

if (cancelUploadBtn) {
  cancelUploadBtn.addEventListener('click', async () => {
    cancelRequested = true;
    if (activeController) {
      activeController.abort();
    }
    if (activeUploadPath) {
      await notifyCancel(activeUploadPath);
    }
    cancelUploadBtn.style.display = 'none';
  });
}

if (createFolderBtn) {
  createFolderBtn.addEventListener('click', async () => {
    if (deleteMode) return;
    const folderName = prompt('Folder name');
    if (!folderName) return;
    const trimmed = folderName.trim();
    if (!trimmed) return;
    if (trimmed.includes('/')) {
      alert('Folder name cannot contain "/"');
      return;
    }
    try {
      const fullPath = (currentPath ? currentPath + '/' : '') + trimmed;
      const response = await fetch('/mkdir/' + encodePath(fullPath), { method: 'POST' });
      if (!response.ok) {
        let detail = '';
        try { detail = await response.text(); } catch (_) {}
        throw new Error(response.status + (detail ? ' ' + detail : ''));
      }
      location.reload();
    } catch (err) {
      alert('Create folder failed: ' + (err && err.message ? err.message : 'unknown error'));
    }
  });
}

function showUploadDock() {
  if (uploadDock) uploadDock.style.display = 'block';
}

function hideUploadDock() {
  if (uploadDock) uploadDock.style.display = 'none';
}

function showDeleteDock() {
  if (deleteDock) deleteDock.style.display = 'block';
}

function hideDeleteDock() {
  if (deleteDock) deleteDock.style.display = 'none';
}

function setDeleteMode(enabled) {
  deleteMode = enabled;
  document.body.classList.toggle('delete-mode', enabled);
  if (deleteToggleBtn) {
    deleteToggleBtn.textContent = enabled ? 'Cancel Delete' : 'Delete';
  }
  if (!enabled) {
    clearSelection();
    hideDeleteDock();
  } else {
    refreshDeleteSummary();
  }
}

function clearSelection() {
  selectedPaths.clear();
  selectableRows.forEach((row) => row.classList.remove('selected'));
}

function toggleRowSelection(row) {
  const path = row.dataset.path || '';
  const kind = row.dataset.kind || '';
  if (!path || !kind) return;
  if (selectedPaths.has(path)) {
    selectedPaths.delete(path);
    row.classList.remove('selected');
  } else {
    selectedPaths.set(path, kind);
    row.classList.add('selected');
  }
  refreshDeleteSummary();
}

function getSelectionCounts() {
  let dirs = 0;
  let files = 0;
  for (const kind of selectedPaths.values()) {
    if (kind === 'dir') dirs += 1;
    else files += 1;
  }
  return { dirs, files };
}

function refreshDeleteSummary() {
  const counts = getSelectionCounts();
  if (deleteSummary) {
    deleteSummary.textContent =
      counts.dirs + ' ' + pluralize(counts.dirs, 'dir', 'dirs') + ' and ' +
      counts.files + ' ' + pluralize(counts.files, 'file', 'files') + ' selected';
  }
  if (!deleteMode || selectedPaths.size === 0) {
    hideDeleteDock();
  } else {
    showDeleteDock();
  }
}

function pluralize(count, one, many) {
  return count === 1 ? one : many;
}

function setUploadProgress(done, total, label) {
  const percent = total > 0 ? Math.min(100, Math.round((done / total) * 100)) : 0;
  if (progressFill) progressFill.style.width = percent + '%';
  if (uploadText) uploadText.textContent = label + ' (' + percent + '%)';
}

async function uploadFiles(fileList) {
  if (!uploadsEnabled || !fileList || fileList.length === 0) return;
  const files = Array.from(fileList);
  const existingNames = getExistingFileNameSet();
  const conflicts = Array.from(
    new Set(
      files
        .map((file) => file && file.name ? file.name : '')
        .filter((name) => name && existingNames.has(name.toLowerCase()))
    )
  );
  if (conflicts.length > 0) {
    const shown = conflicts.slice(0, 8).map((name) => '• ' + name).join('\n');
    const more = conflicts.length > 8 ? '\n...and ' + (conflicts.length - 8) + ' more' : '';
    const ok = confirm(
      'These files already exist on the server and will be replaced:\n\n' +
      shown +
      more +
      '\n\nContinue upload?'
    );
    if (!ok) {
      if (fileInput) fileInput.value = '';
      return;
    }
  }
  const totalBytes = files.reduce((sum, file) => sum + (file.size || 0), 0);
  let uploadedBytes = 0;

  cancelRequested = false;
  uploadInProgress = true;
  showUploadDock();
  setUploadProgress(0, totalBytes, 'Preparing upload');
  if (cancelUploadBtn) cancelUploadBtn.style.display = 'inline-flex';

  try {
    for (let i = 0; i < files.length; i += 1) {
      const file = files[i];
      if (cancelRequested) throw new Error('canceled');
      await uploadFile(file, (sentForFile) => {
        const filePosition = '(' + (i + 1) + '/' + files.length + ') ';
        setUploadProgress(uploadedBytes + sentForFile, totalBytes, 'Uploading ' + filePosition + file.name);
      });
      uploadedBytes += file.size || 0;
    }
    setUploadProgress(totalBytes, totalBytes, 'Upload complete');
    setTimeout(() => location.reload(), 350);
  } catch (err) {
    const message = err && err.message ? err.message : 'unknown error';
    if (uploadText) {
      uploadText.textContent = message === 'canceled' ? 'Upload canceled' : ('Upload failed: ' + message);
    }
    if (progressFill && message === 'canceled') {
      progressFill.style.width = '0%';
    }
  } finally {
    uploadInProgress = false;
    activeController = null;
    activeUploadPath = '';
    if (cancelUploadBtn) cancelUploadBtn.style.display = 'none';
    cancelRequested = false;
    setTimeout(() => {
      if (!uploadInProgress) hideUploadDock();
    }, 1400);
  }
}

async function uploadFile(file, onProgress) {
  const CHUNK_SIZE = 1024 * 1024;
  let offset = 0;
  const fullPath = (currentPath ? currentPath + '/' : '') + file.name;
  activeUploadPath = fullPath;

  while (offset < file.size) {
    if (cancelRequested) {
      await notifyCancel(fullPath);
      throw new Error('canceled');
    }

    const chunk = file.slice(offset, offset + CHUNK_SIZE);
    const end = Math.min(offset + CHUNK_SIZE, file.size) - 1;
    const controller = new AbortController();
    activeController = controller;

    let response;
    try {
      response = await fetch('/upload/' + encodePath(fullPath), {
        method: 'POST',
        headers: {
          'Content-Range': 'bytes ' + offset + '-' + end + '/' + file.size,
          'Content-Type': 'application/octet-stream'
        },
        body: chunk,
        signal: controller.signal
      });
    } catch (err) {
      if (cancelRequested) throw new Error('canceled');
      throw err;
    }

    if (!response.ok) {
      let detail = '';
      try { detail = await response.text(); } catch (_) {}
      throw new Error(response.status + (detail ? ' ' + detail : ''));
    }

    offset = end + 1;
    onProgress(offset);
  }

  activeController = null;
}

async function notifyCancel(path) {
  try {
    await fetch('/upload-cancel/' + encodePath(path), { method: 'POST' });
  } catch (_) {
  }
}

function encodePath(path) {
  return path.split('/').map(encodeURIComponent).join('/').replace(/\+/g, '%20');
}

function getExistingFileNameSet() {
  const names = new Set();
  document.querySelectorAll('tr[data-kind="file"][data-name]').forEach((row) => {
    const name = (row.dataset.name || '').trim().toLowerCase();
    if (name) names.add(name);
  });
  return names;
}

async function copyText(text) {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      return true;
    }
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.left = '-9999px';
    document.body.appendChild(ta);
    ta.focus();
    ta.select();
    const ok = document.execCommand('copy');
    ta.remove();
    return ok;
  } catch (_) {
    return false;
  }
}
