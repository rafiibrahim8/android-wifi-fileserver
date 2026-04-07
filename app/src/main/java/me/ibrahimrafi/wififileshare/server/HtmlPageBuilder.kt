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
        entries: List<DirectoryEntry>,
    ): String {
        val filesCount = entries.count { !it.isDirectory }
        val dirsCount = entries.count { it.isDirectory }

        val breadcrumb = buildBreadcrumbTitle(currentPath, serverBase)
        val rows = buildRows(context, currentPath, serverBase, entries)

        val uploadControls = if (allowUploads) {
            "<button id=\"upload-btn\" class=\"btn\">Upload Files</button>"
        } else {
            ""
        }

        val uploadInput = if (allowUploads) {
            "<input type=\"file\" id=\"file-input\" multiple />"
        } else {
            ""
        }

        val cancelUploadButton = if (allowUploads) {
            "<button id=\"cancel-upload\" class=\"dock-icon-btn\" style=\"display:none;\" title=\"Cancel upload\" aria-label=\"Cancel upload\">✕</button>"
        } else {
            ""
        }

        val deleteToggleButton = if (allowDelete) {
            "<button id=\"delete-toggle\" class=\"btn btn-danger\">Delete</button>"
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
            deleteToggleButton = deleteToggleButton,
            createFolderButton = createFolderButton,
            zipSection = zipSection,
        )
    }
}
