package me.ibrahimrafi.wififileshare.server

object HtmlPageBuilder {
    fun build(
        currentPath: String,
        serverBase: String,
        allowUploads: Boolean,
        allowCreateFolder: Boolean,
        allowZipDownload: Boolean,
        anonymousAccess: Boolean,
        entries: List<DirectoryEntry>,
    ): String {
        val pathLabel = if (currentPath.isBlank()) "/" else "/${currentPath.trim('/')}"
        val breadcrumb = buildBreadcrumb(currentPath, serverBase)
        val upRow = buildUpRow(currentPath, serverBase)
        val rows = buildString {
            append(upRow)
            for (entry in entries) {
                val encoded = percentEncodePath(entry.relativePath)
                if (entry.isDirectory) {
                    val href = "$serverBase/$encoded"
                    append("<tr data-name=\"")
                    append(escapeHtml(entry.name.lowercase()))
                    append("\" data-size=\"0\" data-mod=\"")
                    append(entry.modified)
                    append("\"><td>📁 <a href=\"")
                    append(escapeHtml(href))
                    append("\">")
                    append(escapeHtml(entry.name))
                    append("</a></td><td>—</td><td>")
                    append(formatTime(entry.modified))
                    append("</td></tr>")
                } else {
                    val downloadUrl = "$serverBase/files/$encoded"
                    append("<tr data-name=\"")
                    append(escapeHtml(entry.name.lowercase()))
                    append("\" data-size=\"")
                    append(entry.size)
                    append("\" data-mod=\"")
                    append(entry.modified)
                    append("\"><td>📄 <a href=\"")
                    append(escapeHtml(downloadUrl))
                    append("\" download=\"")
                    append(escapeHtml(entry.name))
                    append("\">")
                    append(escapeHtml(entry.name))
                    append("</a><button class=\"copy\" data-url=\"")
                    append(escapeHtml(downloadUrl))
                    append("\" title=\"Copy link\" aria-label=\"Copy link\">⧉</button></td><td>")
                    append(formatBytes(entry.size))
                    append("</td><td>")
                    append(formatTime(entry.modified))
                    append("</td></tr>")
                }
            }
        }

        val uploadSection = if (allowUploads) {
            """
            <div id="upload-zone">Drop files here or click to upload</div>
            <input type="file" id="file-input" multiple />
            <button id="cancel-upload" style="display:none;">Cancel upload</button>
            <div id="upload-progress"></div>
            """.trimIndent()
        } else {
            ""
        }

        val createFolderButton = if (allowCreateFolder) {
            "<button id=\"create-folder\" class=\"mkdir\">New folder</button>"
        } else {
            ""
        }

        val zipSection = if (allowZipDownload) {
            val zipPath = if (currentPath.isBlank()) "" else "/${percentEncodePath(currentPath)}"
            "<a class=\"zip\" href=\"$serverBase/zip$zipPath\">Download as ZIP</a>"
        } else {
            ""
        }

        val authHint = if (anonymousAccess) {
            ""
        } else {
            "<div class=\"hint\">Auth enabled: wget --user=USER --password=PASS \"URL\"</div>"
        }

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <title>WiFi File Share — ${escapeHtml(pathLabel)}</title>
              <style>
                *, *::before, *::after { box-sizing: border-box; }
                :root { color-scheme: light dark; --bg:#0d1f22; --surface:#1a2f33; --text:#d8eef1; --accent:#4dd8e0; }
                @media (prefers-color-scheme: light) { :root { --bg:#f4fafb; --surface:#ffffff; --text:#103238; --accent:#006469; } }
                body { margin:0; font-family: ui-sans-serif, system-ui; background: radial-gradient(circle at 20% 0%, #20454c, var(--bg)); color: var(--text); }
                main { width:100%; max-width: 1100px; margin: 0 auto; padding: 20px; }
                .header { display:flex; gap:12px; align-items:center; justify-content:space-between; flex-wrap:wrap; }
                .actions { display:flex; align-items:center; gap:8px; flex-wrap:wrap; }
                #filter { min-width:220px; }
                .breadcrumb { margin-top:10px; padding:10px 12px; border-radius:12px; background:var(--surface); font-size:14px; }
                .breadcrumb a { color:var(--accent); text-decoration:none; }
                .breadcrumb a:hover { text-decoration:underline; }
                #upload-zone { margin-top:14px; border:1px dashed var(--accent); border-radius:12px; padding:16px; text-align:center; cursor:pointer; }
                #cancel-upload { margin-top:10px; padding:8px 12px; border:none; border-radius:8px; background:#ff6b6b; color:#ffffff; cursor:pointer; }
                input[type=file] { display:none; }
                table { width:100%; border-collapse:collapse; margin-top:16px; background:var(--surface); border-radius:16px; overflow:hidden; }
                th, td { padding:12px; text-align:left; border-bottom:1px solid rgba(255,255,255,0.06); }
                tr:hover { background:rgba(77,216,224,0.08); }
                .zip, .mkdir { font-size:12px; padding:6px 10px; border:none; border-radius:8px; background:var(--accent); color:#002c33; cursor:pointer; }
                .copy { margin-left:8px; padding:0; border:none; background:transparent; color:var(--accent); cursor:pointer; font-size:2rem; line-height:1; vertical-align:middle; }
                .up-icon { font-size:1em; line-height:1; vertical-align:middle; margin-right:6px; }
                .hint { margin-top:10px; opacity:0.85; }

                @media (max-width: 700px) {
                  main { padding:12px; }
                  .header { align-items:stretch; }
                  .actions { width:100%; }
                  #filter { width:100%; min-width:0; }
                  table { font-size:14px; table-layout:fixed; }
                  th, td { padding:10px 8px; }
                  th:nth-child(3), td:nth-child(3) { display:none; }
                  th:nth-child(1), td:nth-child(1) { width:72%; }
                  th:nth-child(2), td:nth-child(2) { width:28%; white-space:nowrap; text-align:right; }
                  td:first-child { word-break:break-word; }
                  .copy { font-size:1.25rem; }
                }
              </style>
            </head>
            <body>
              <main>
                <div class="header">
                  <div>
                    <h2>WiFi File Share</h2>
                    <nav class="breadcrumb">$breadcrumb</nav>
                  </div>
                  <div class="actions">
                    <input type="search" id="filter" placeholder="Filter files...">
                    $createFolderButton
                    $zipSection
                  </div>
                </div>
                $uploadSection
                $authHint
                <table id="file-table">
                  <thead><tr><th>Name</th><th>Size</th><th>Modified</th></tr></thead>
                  <tbody>$rows</tbody>
                </table>
              </main>
              <script>
                const currentPath = ${jsString(currentPath)};
                const uploadsEnabled = ${if (allowUploads) "true" else "false"};
                const fileInput = document.getElementById('file-input');
                const uploadZone = document.getElementById('upload-zone');
                const cancelUploadBtn = document.getElementById('cancel-upload');
                const uploadProgress = document.getElementById('upload-progress');
                const createFolderBtn = document.getElementById('create-folder');
                const filter = document.getElementById('filter');
                const rows = Array.from(document.querySelectorAll('#file-table tbody tr'));
                let cancelRequested = false;
                let activeUploadPath = '';
                let activeController = null;

                document.querySelectorAll('.copy').forEach(btn => {
                  btn.addEventListener('click', async () => {
                    const text = btn.dataset.url || '';
                    const ok = await copyText(text);
                    btn.textContent = ok ? '✓' : '!';
                    setTimeout(() => { btn.textContent = '⧉'; }, 1200);
                  });
                });

                if (uploadZone && fileInput) {
                  uploadZone.addEventListener('click', () => fileInput.click());
                  uploadZone.addEventListener('dragover', (e) => { e.preventDefault(); });
                  uploadZone.addEventListener('drop', (e) => {
                    e.preventDefault();
                    uploadFiles(e.dataTransfer.files);
                  });
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

                filter.addEventListener('input', () => {
                  const q = filter.value.toLowerCase();
                  rows.forEach(row => {
                    row.style.display = row.dataset.name.includes(q) ? '' : 'none';
                  });
                });

                function encodePath(path) {
                  return path.split('/').map(encodeURIComponent).join('/').replace(/\+/g, '%20');
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

                async function uploadFiles(fileList) {
                  if (!uploadsEnabled || !fileList) return;
                  cancelRequested = false;
                  if (cancelUploadBtn) cancelUploadBtn.style.display = 'inline-block';
                  try {
                    for (const file of fileList) {
                      if (cancelRequested) throw new Error('canceled');
                      await uploadFile(file);
                    }
                    if (uploadProgress) {
                      uploadProgress.textContent = 'Upload complete';
                    }
                    location.reload();
                  } catch (err) {
                    if (uploadProgress) {
                      const message = err && err.message ? err.message : 'unknown error';
                      uploadProgress.textContent = message === 'canceled' ? 'Upload canceled' : ('Upload failed: ' + message);
                    }
                  } finally {
                    if (cancelUploadBtn) cancelUploadBtn.style.display = 'none';
                    activeController = null;
                    activeUploadPath = '';
                    cancelRequested = false;
                  }
                }

                async function uploadFile(file) {
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
                      if (cancelRequested) {
                        throw new Error('canceled');
                      }
                      throw err;
                    }
                    if (!response.ok) {
                      let detail = '';
                      try { detail = await response.text(); } catch (_) {}
                      throw new Error(response.status + (detail ? ' ' + detail : ''));
                    }
                    offset = end + 1;
                    if (uploadProgress) {
                      uploadProgress.textContent = file.name + ' ' + Math.round((offset / file.size) * 100) + '%';
                    }
                  }
                  activeController = null;
                }

                async function notifyCancel(path) {
                  try {
                    await fetch('/upload-cancel/' + encodePath(path), { method: 'POST' });
                  } catch (_) {
                  }
                }
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun escapeHtml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun jsString(value: String): String {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }

    private fun buildBreadcrumb(currentPath: String, serverBase: String): String {
        val segments = currentPath.trim('/').split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) {
            return "<a href=\"${escapeHtml("$serverBase/")}\">Home</a>"
        }

        val parts = mutableListOf<String>()
        parts += "<a href=\"${escapeHtml("$serverBase/")}\">Home</a>"

        var accumulated = ""
        for (segment in segments) {
            accumulated = if (accumulated.isEmpty()) segment else "$accumulated/$segment"
            val href = "$serverBase/${percentEncodePath(accumulated)}"
            parts += "<a href=\"${escapeHtml(href)}\">${escapeHtml(segment)}</a>"
        }
        return parts.joinToString(" / ")
    }

    private fun buildUpRow(currentPath: String, serverBase: String): String {
        val trimmed = currentPath.trim('/')
        if (trimmed.isBlank()) return ""

        val parent = trimmed.substringBeforeLast('/', "")
        val href = if (parent.isBlank()) "$serverBase/" else "$serverBase/${percentEncodePath(parent)}"
        return "<tr data-name=\"..\" data-size=\"0\" data-mod=\"0\"><td><span class=\"up-icon\">↩</span><a href=\"${escapeHtml(href)}\">..</a></td><td>—</td><td>—</td></tr>"
    }
}
