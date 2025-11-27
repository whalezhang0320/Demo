package com.example.star.aiwork.data.database

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

class LocalRAGService(private val context: Context, private val dao: KnowledgeDao) {

    init {
        try {
            PDFBoxResourceLoader.init(context)
        } catch (e: Exception) {
            Log.e("LocalRAGService", "Failed to init PDFBox", e)
        }
    }

    // 1. 解析 PDF 并切片
    suspend fun indexPdf(uri: Uri) = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e("LocalRAGService", "Cannot open input stream for URI: $uri")
                return@withContext
            }

            // 使用 PDFBox 加载
            val document = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            // 提取全文
            val fullText = stripper.getText(document)
            document.close()

            if (fullText.isBlank()) {
                Log.w("LocalRAGService", "PDF content is empty")
                return@withContext
            }

            // 切片
            val chunks = splitTextIntoChunks(fullText, chunkSize = 500)
            
            // 存入数据库
            val fileName = getFileName(uri)
            val entities = chunks.map { 
                KnowledgeChunk(sourceFilename = fileName, content = it) 
            }
            dao.insertChunks(entities)
            Log.d("LocalRAGService", "Indexed ${entities.size} chunks from $fileName")
            
        } catch (e: Exception) {
            Log.e("LocalRAGService", "Error indexing PDF", e)
        } finally {
            try {
                inputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun splitTextIntoChunks(text: String, chunkSize: Int): List<String> {
        // 改进的切片策略：先按段落（双换行）分割，再组合或切割
        val paragraphs = text.split(Regex("\\n\\s*\\n"))
        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()

        for (paragraph in paragraphs) {
            val cleanedPara = paragraph.trim()
            if (cleanedPara.isEmpty()) continue

            // 如果加上当前段落会超过限制
            if (currentChunk.length + cleanedPara.length > chunkSize) {
                // 先保存当前累积的 Chunk
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString().trim())
                    currentChunk = StringBuilder()
                }
                
                // 如果单个段落本身就比 chunkSize 大，则强制切割
                if (cleanedPara.length > chunkSize) {
                     cleanedPara.chunked(chunkSize).forEach { 
                         chunks.add(it)
                     }
                } else {
                    // 否则开始新的 Chunk
                    currentChunk.append(cleanedPara).append("\n\n")
                }
            } else {
                // 累积到当前 Chunk
                currentChunk.append(cleanedPara).append("\n\n")
            }
        }
        // 添加最后一个 Chunk
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }
        
        // 如果上面的逻辑导致空列表（极端情况），回退到简单切分
        if (chunks.isEmpty() && text.isNotEmpty()) {
            return text.chunked(chunkSize)
        }
        
        return chunks
    }
    
    private fun getFileName(uri: Uri): String {
        // 简单处理，实际可能需要查询 ContentResolver 获取真实文件名
        // 为了演示，直接取最后的 path segment
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "unknown_file.pdf"
    }

    // 2. 检索
    suspend fun retrieve(query: String): String {
        if (query.isBlank()) return ""
        try {
            // 处理查询词，将 "杭州 天气" 转换为 FTS 语法 "杭州* OR 天气*"
            // 这里假设中文分词由 SQLite 的 simple tokenizer 处理（通常按字或空格）
            // Android Room 默认 FTS4 simple tokenizer 只能按空格或符号分词。
            // 对于中文，更好的做法是在存入前进行手动分词（如 "杭 州 天 气"），或者使用 ICU tokenizer (Android 自带需特定版本)。
            // 这里为了简化，我们假设用户输入的 Query 包含空格，或者我们强制把每个字拆开（如果需要更细粒度）。
            // 下面这种实现主要针对英文或已空格分隔的中文。
            
            val ftsQuery = formatFtsQuery(query) 
            Log.d("LocalRAGService", "Searching with FTS query: $ftsQuery")
            
            val results = dao.search(ftsQuery)
            
            if (results.isEmpty()) {
                 Log.d("LocalRAGService", "No results found.")
                 return ""
            }
            
            // 拼接上下文，去重
            val context = results.distinctBy { it.content }.joinToString("\n\n---\n\n") { it.content }
            return context
        } catch (e: Exception) {
            Log.e("LocalRAGService", "Error retrieving context", e)
            return ""
        }
    }
    
    private fun formatFtsQuery(query: String): String {
         // 将查询字符串拆分为单词，并为每个单词添加通配符 '*'
         // 移除特殊字符以防 SQL 注入或语法错误
        val sanitized = query.replace(Regex("[^\\w\\s\\u4e00-\\u9fa5]"), " ")
        val words = sanitized.trim().split("\\s+".toRegex())
        return words.filter { it.isNotBlank() }.joinToString(" OR ") { "$it*" }
    }
    
    suspend fun clearAll() {
        dao.clearAll()
    }
}
