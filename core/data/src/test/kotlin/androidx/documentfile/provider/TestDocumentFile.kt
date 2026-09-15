package androidx.documentfile.provider

import android.net.Uri

class TestDocumentFile(
    var parent: DocumentFile?,
    private val docName: String,
    private val isDir: Boolean,
    var content: String = "",
    val children: MutableList<DocumentFile> = mutableListOf(),
    private val docUri: Uri = Uri.parse("content://test.provider/document/${System.nanoTime()}"),
) : DocumentFile(parent) {
    override fun getParentFile(): DocumentFile? = parent

    override fun createFile(
        mimeType: String,
        displayName: String,
    ): DocumentFile? = null

    override fun createDirectory(displayName: String): DocumentFile? = null

    override fun getUri(): Uri = docUri

    override fun getName(): String = docName

    override fun getType(): String? = if (isDir) null else "text/markdown"

    override fun isDirectory(): Boolean = isDir

    override fun isFile(): Boolean = !isDir

    override fun isVirtual(): Boolean = false

    override fun lastModified(): Long = 1726488000000L

    override fun length(): Long = content.length.toLong()

    override fun canRead(): Boolean = true

    override fun canWrite(): Boolean = true

    override fun delete(): Boolean = true

    override fun exists(): Boolean = true

    override fun listFiles(): Array<DocumentFile> = children.toTypedArray()

    override fun renameTo(displayName: String): Boolean = true

    fun moveTo(newParent: TestDocumentFile) {
        (parent as? TestDocumentFile)?.children?.remove(this)
        parent = newParent
        newParent.children.add(this)
    }
}
