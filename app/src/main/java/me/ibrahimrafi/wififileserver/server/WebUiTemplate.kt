package me.ibrahimrafi.wififileserver.server

internal fun buildWebUiTemplate(
    currentPath: String,
    allowUploads: Boolean,
    assetBasePath: String,
    dirsCount: Int,
    filesCount: Int,
    breadcrumb: String,
    rows: String,
    uploadControls: String,
    uploadInput: String,
    cancelUploadButton: String,
    selectToggleButton: String,
    createFolderButton: String,
    zipSection: String,
    bulkZipButton: String,
    deleteConfirmButton: String,
    dropBoxMode: Boolean,
): String {
    val dropBoxBanner = if (dropBoxMode) {
        """<div class="dropbox-banner">Drop-box mode — anyone on this network can upload files here. Browsing is disabled.</div>"""
    } else {
        ""
    }
    val listingBlock = if (dropBoxMode) {
        ""
    } else {
        """
      <div class="listing">
        <table aria-describedby="summary">
          <thead>
            <tr>
              <th></th>
              <th>Name</th>
              <th class="center">Size</th>
              <th class="hideable center">Last Modified</th>
              <th class="hideable"></th>
            </tr>
          </thead>
          <tbody>
            $rows
          </tbody>
        </table>
      </div>
        """.trimIndent()
    }
    return """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="color-scheme" content="light dark" />
  <title>Index of /${escapeHtml(currentPath.trim('/'))}</title>
  <link rel="icon" href="${escapeHtml("$assetBasePath/favicon.ico")}" type="image/x-icon">
  <link rel="stylesheet" href="${escapeHtml("$assetBasePath/style.css")}">
</head>
<body>
  <header>
    <div class="wrapper">
      <div class="breadcrumbs">Folder Path</div>
      <h1>$breadcrumb</h1>
    </div>
  </header>

  <div class="wrapper">
    <main>
      $dropBoxBanner
      <div class="meta">
        <div id="summary">
          <span class="meta-item"><b>$dirsCount</b> directories</span>
          <span class="meta-item"><b>$filesCount</b> files</span>
        </div>
        <div class="actions">
          $uploadControls
          $selectToggleButton
          $createFolderButton
          $zipSection
        </div>
        $uploadInput
      </div>

      $listingBlock
    </main>
  </div>

  <div id="drop-overlay" class="drop-overlay" aria-hidden="true">
    <div class="drop-overlay-inner">
      <div class="drop-overlay-icon">↓</div>
      <div class="drop-overlay-text">Drop here to upload</div>
    </div>
  </div>

  <div id="upload-dock" class="upload-dock" aria-live="polite">
    <div id="upload-text" class="upload-text">Uploading...</div>
    <div class="upload-progress-row">
      <div class="progress-track">
        <div id="progress-fill" class="progress-fill"></div>
      </div>
      $cancelUploadButton
    </div>
  </div>

  <div id="delete-dock" class="upload-dock" aria-live="polite">
    <div id="delete-summary" class="upload-text">No items selected</div>
    <div class="delete-dock-actions">
      $bulkZipButton
      $deleteConfirmButton
    </div>
  </div>

  <script>
    window.__WFS_CONFIG = {
      currentPath: ${jsString(currentPath)},
      uploadsEnabled: ${if (allowUploads) "true" else "false"}
    };
  </script>
  <script src="${escapeHtml("$assetBasePath/app.js")}"></script>
</body>
</html>
"""
}
