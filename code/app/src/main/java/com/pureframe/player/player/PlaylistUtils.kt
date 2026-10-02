package com.pureframe.player.player

import java.io.File
import java.text.Collator
import java.util.Locale

/**
 * 同目录视频队列发现工具（PR7）
 *
 * 给定一个本地视频文件路径，扫描其同目录里其它视频文件，按文件名自然顺序返回。
 * 用于构造 ExoPlayer 的播放队列，从而实现：
 * - 通知栏 / 锁屏的「上一首 / 下一首」按钮天然可用（Media3 默认从 playlist 推导）
 * - 用户连续点击下一首时保持连续剧 / 系列片连贯
 *
 * 设计取舍：
 * - 仅按扩展名白名单过滤，不依赖 MediaStore 时序 → 简单可控，新文件立即可见
 * - 文件名自然排序（Collator + 自然数字比较）→ "S01E10.mp4" 排在 "S01E02.mp4" 之后，符合剧集心智模型
 *
 * 扩展名列表与 [com.pureframe.player.ui.screens.player.PlayerViewModel] 中边下边播的
 * largestFile 视频识别保持一致，保证 stream 任务文件夹的视频文件也能被识别（虽然 PR7 暂不在 stream 模式用）。
 */
object PlaylistUtils {

    /**
     * 同目录下视频文件扩展名（小写，含点）
     */
    val VIDEO_EXTENSIONS: Set<String> = setOf(
        "mp4", "mkv", "avi", "webm", "ts", "mov",
        "m4v", "flv", "wmv", "mpg", "mpeg", "3gp", "m3u8"
    )

    /**
     * 列出与 [videoPath] 同目录的视频文件（自然排序）。
     *
     * @param videoPath 当前正在播放的视频绝对路径
     * @return 同目录视频文件列表（含自己），按文件名自然顺序；父目录不存在或不可读返回仅含自身的列表
     */
    fun listVideoSiblings(videoPath: String): List<File> {
        val current = try {
            File(videoPath)
        } catch (e: Exception) {
            return emptyList()
        }
        if (!current.exists() || !current.canRead()) return listOf(current)

        val parent = current.parentFile ?: return listOf(current)

        // 子目录无读取权限时 listFiles() == null（IO 异常被吞），此时降级为只含自身
        val all = parent.listFiles() ?: return listOf(current)

        return all.asSequence()
            .filter { it.isFile && it.canRead() }
            .filter { f -> VIDEO_EXTENSIONS.contains(f.extension.lowercase(Locale.ROOT)) }
            .sortedWith(NATURAL_FILENAME_COMPARATOR)
            .toList()
    }

    /**
     * 自然文件名排序比较器（locale-aware）
     *
     * 中文 / 英文 / 数字混排都能给出符合直觉的顺序：
     * - S01E01.mp4 < S01E02.mp4 < S01E10.mp4 （自然数字）
     * - a.mp4 < B.mp4 （忽略大小写但 locale-aware 排序）
     * - 中.mp4 < 英.mp4 （按 Locale.CHINA 默认顺序）
     */
    private val NATURAL_FILENAME_COMPARATOR: Comparator<File> = Comparator { a, b ->
        val nameA = a.name
        val nameB = b.name
        // 先按自然数字比较（保证 S01E10 排在 S01E02 后），用 locale-aware collator 兜底
        NATURAL_COMPARATOR.compare(nameA, nameB).takeIf { it != 0 }
            ?: Collator.getInstance(Locale.CHINA).compare(nameA, nameB)
    }

    /**
     * 自然数字比较器：把字符串拆成"数字片段 vs 非数字片段"逐段比较
     *
     * 实现简洁版"NaturalOrder"算法：digit run 之间按数值大小比较，其它 run 按 String.compareTo
     * 优点：纯 JDK，无依赖；缺点：性能略低（对小目录可忽略，对几千文件仍毫秒级）
     */
    private val NATURAL_COMPARATOR: Comparator<String> = Comparator { a, b ->
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                // 比较两段连续数字（按数值大小，跳过前导 0）
                var ai = i
                var bj = j
                while (ai < a.length && a[ai] == '0') ai++
                while (bj < b.length && b[bj] == '0') bj++
                var aEnd = ai
                var bEnd = bj
                while (aEnd < a.length && a[aEnd].isDigit()) aEnd++
                while (bEnd < b.length && b[bEnd].isDigit()) bEnd++
                // 长度不同：数字多的更大；长度相同：按字典序
                val aLen = aEnd - ai
                val bLen = bEnd - bj
                if (aLen != bLen) return@Comparator aLen - bLen
                while (ai < aEnd && bj < bEnd) {
                    val diff = a[ai].code - b[bj].code
                    if (diff != 0) return@Comparator diff
                    ai++
                    bj++
                }
                i = aEnd
                j = bEnd
            } else {
                val diff = ca.lowercaseChar().code - cb.lowercaseChar().code
                if (diff != 0) return@Comparator diff
                i++
                j++
            }
        }
        // 走到末尾的更小
        a.length - i - (b.length - j)
    }
}