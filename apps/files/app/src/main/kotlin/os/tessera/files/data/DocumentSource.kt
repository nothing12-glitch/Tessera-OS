package os.tessera.files.data

import android.content.Intent
import android.content.UriPermission
import android.net.Uri
import android.provider.DocumentsContract
import os.tessera.files.logic.FileNode

/**
 * Робота з дерева документів через Storage Access Framework.
 *
 * Нам не потрібне `MANAGE_EXTERNAL_STORAGE`: користувач один раз обирає
 * папку, Android видає persistable-дозвіл, і далі ми ходимо лише всередині
 * неї. Поза обраним деревом застосунок не бачить нічого — це й є мета.
 */
class DocumentSource(private val resolver: android.content.ContentResolver) {

    private val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_FLAGS,
    )

    fun grantedTrees(): List<UriPermission> =
        resolver.persistedUriPermissions.filter {
            it.isReadPermission && it.uri.scheme == "content" &&
                DocumentsContract.isTreeUri(it.uri)
        }

    fun grant(treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { resolver.takePersistableUriPermission(treeUri, flags) }
    }

    fun release(treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { resolver.releasePersistableUriPermission(treeUri, flags) }
    }

    fun documentUri(tree: Uri, documentId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, documentId)

    fun children(tree: Uri, documentId: String): List<FileNode> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val out = ArrayList<FileNode>()
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val flagsCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_FLAGS)

            while (cursor.moveToNext()) {
                val mime = cursor.getString(mimeCol) ?: ""
                val flags = cursor.getInt(flagsCol)
                out += FileNode(
                    documentId = cursor.getString(idCol),
                    name = cursor.getString(nameCol) ?: "",
                    mimeType = mime,
                    sizeBytes = cursor.getLong(sizeCol),
                    lastModifiedMillis = cursor.getLong(dateCol),
                    isDirectory = FileNode.isDirectoryMime(mime),
                    isWriteable = flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0,
                )
            }
        }
        return out
    }

    /** Ім'я самого каталогу — для «хлібних крихт». */
    fun displayName(tree: Uri, documentId: String): String =
        runCatching {
            val uri = documentUri(tree, documentId)
            resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) c.getString(0) ?: DocumentsContract.getTreeDocumentId(uri) else ""
                }.orEmpty()
        }.getOrElse { "" }

    fun createFolder(tree: Uri, parentId: String, name: String): String? = runCatching {
        val created = DocumentsContract.createDocument(
            resolver,
            documentUri(tree, parentId),
            DocumentsContract.Document.MIME_TYPE_DIR,
            name,
        )
        created?.let { DocumentsContract.getDocumentId(it) }
    }.getOrNull()

    fun rename(tree: Uri, documentId: String, newName: String): Boolean = runCatching {
        DocumentsContract.renameDocument(resolver, documentUri(tree, documentId), newName) != null
    }.getOrDefault(false)

    fun delete(tree: Uri, documentId: String): Boolean = runCatching {
        DocumentsContract.deleteDocument(resolver, documentUri(tree, documentId))
    }.getOrDefault(false)

    fun viewIntent(uri: Uri, mimeType: String): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
}
