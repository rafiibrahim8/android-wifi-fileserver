package me.ibrahimrafi.wififileshare.server

import android.content.Context

object HtmlPageBuilder {
    fun build(
        context: Context,
        currentPath: String,
        serverBase: String,
        assetBasePath: String,
        allowUploads: Boolean,
        allowCreateFolder: Boolean,
        allowDelete: Boolean,
        allowZipDownload: Boolean,
        dropBoxMode: Boolean,
        entries: List<DirectoryEntry>,
    ): String {
        val filesCount = entries.count { !it.isDirectory }
        val dirsCount = entries.count { it.isDirectory }

        val breadcrumb = buildBreadcrumbTitle(currentPath, serverBase)
        val rows = buildRows(context, currentPath, serverBase, entries)

        val uploadControls = if (allowUploads) {
            """
            <button id="upload-btn" class="btn">Upload Files</button>
            <button id="upload-folder-btn" class="btn">Upload Folder</button>
            """.trimIndent()
        } else {
            ""
        }

        val uploadInput = if (allowUploads) {
            """
            <input type="file" id="file-input" multiple />
            <input type="file" id="folder-input" webkitdirectory directory multiple />
            """.trimIndent()
        } else {
            ""
        }

        val cancelUploadButton = if (allowUploads) {
            "<button id=\"cancel-upload\" class=\"dock-icon-btn\" style=\"display:none;\" title=\"Cancel upload\" aria-label=\"Cancel upload\">✕</button>"
        } else {
            ""
        }

        val canSelectAnything = allowDelete || allowZipDownload
        val selectToggleButton = if (canSelectAnything) {
            "<button id=\"select-toggle\" class=\"btn\">Select</button>"
        } else {
            ""
        }
        val deleteConfirmInDock = if (allowDelete) {
            "<button id=\"delete-confirm\" class=\"btn btn-danger\">Delete</button>"
        } else {
            ""
        }

        val createFolderButton = if (allowCreateFolder) {
            "<button id=\"create-folder\" class=\"btn\">New Folder</button>"
        } else {
            ""
        }

        val zipSection = if (allowZipDownload) {
            val zipPath = if (currentPath.isBlank()) "" else "/${percentEncodePath(currentPath)}"
            "<a class=\"btn\" href=\"$serverBase/zip$zipPath\">Download ZIP</a>"
        } else {
            ""
        }

        val bulkZipButton = if (allowZipDownload) {
            "<button id=\"download-zip\" class=\"btn\">Download ZIP</button>"
        } else {
            ""
        }

        return buildWebUiTemplate(
            currentPath = currentPath,
            allowUploads = allowUploads,
            assetBasePath = assetBasePath,
            dirsCount = dirsCount,
            filesCount = filesCount,
            breadcrumb = breadcrumb,
            rows = rows,
            uploadControls = uploadControls,
            uploadInput = uploadInput,
            cancelUploadButton = cancelUploadButton,
            selectToggleButton = selectToggleButton,
            createFolderButton = createFolderButton,
            zipSection = zipSection,
            bulkZipButton = bulkZipButton,
            deleteConfirmButton = deleteConfirmInDock,
            dropBoxMode = dropBoxMode,
        )
    }
}
