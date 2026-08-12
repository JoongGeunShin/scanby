package com.example.scanby.feature.sendtopc.utils

import android.content.Context
import fi.iki.elonen.NanoHTTPD

/**
 * 선택된 스캔 이미지들을 같은 Wi-Fi(LAN)의 PC 브라우저가 내려받을 수 있게 서빙하는 초경량
 * 로컬 HTTP 서버. `/`는 파일 목록을 보여주는 HTML, `/file/{id}`는 실제 이미지 바이트를
 * 반환한다.
 *
 * 인증이 전혀 없다 — 같은 네트워크에 있는 사람이면 누구나 URL만 알면 접근 가능하므로,
 * 사용자가 "PC로 전송"을 켜둔 짧은 시간 동안만(신뢰하는 개인 Wi-Fi에서) 쓰는 걸 전제로
 * 한다. 공용 Wi-Fi 경고 같은 건 이번 범위 밖.
 */
class LocalFileServer(
    private val context: Context,
    private val files: List<ScannedImage>,
) : NanoHTTPD(0) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        return when {
            uri == "/" -> newFixedLengthResponse(
                Response.Status.OK,
                "text/html; charset=utf-8",
                buildIndexHtml(),
            )
            uri.startsWith("/file/") -> serveFile(uri.removePrefix("/file/").toLongOrNull())
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun buildIndexHtml(): String {
        val items = files.joinToString(separator = "\n") { file ->
            "<li><a href=\"/file/${file.id}\">${file.displayName}</a></li>"
        }
        return """
            <!DOCTYPE html>
            <html><head><meta charset="utf-8"><title>scanby</title></head>
            <body>
                <h3>scanby &middot; ${files.size}장</h3>
                <ul>$items</ul>
            </body></html>
        """.trimIndent()
    }

    private fun serveFile(fileId: Long?): Response {
        val target = files.firstOrNull { it.id == fileId }
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        val stream = context.contentResolver.openInputStream(target.uri)
            ?: return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Failed to open file")
        return newChunkedResponse(Response.Status.OK, "image/jpeg", stream).apply {
            addHeader("Content-Disposition", "attachment; filename=\"${target.displayName}\"")
        }
    }
}
