package com.example.utils

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.data.BookEntity
import com.example.data.BookPage
import com.example.parsePagesJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object PdfGenerator {

    /**
     * Downloads Coil images and builds a beautifully formatted PDF.
     * Keeps track of progress via the [onProgress] callback.
     */
    suspend fun generateBookPdf(
        context: Context,
        book: BookEntity,
        onProgress: (String, Int) -> Unit
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            onProgress("جاري تهيئة المحرر والصفحات للطباعة...", 10)
            
            val pdfDocument = PdfDocument()
            val pagesList = parsePagesJson(book.pagesJson)
            val totalPages = pagesList.size
            
            // Standard A4 Size: 595 x 842 point units (at 72 dpi)
            val pageWidth = 595
            val pageHeight = 842
            var physicalPageNum = 1

            // 1. GENERATE THE COVER PAGE
            onProgress("جاري تحميل غلاف القصة الكرتوني...", 25)
            val coverBitmap = fetchBitmapFromUrl(context, book.coverImageUrl)
            
            val coverPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, physicalPageNum++).create()
            val coverPage = pdfDocument.startPage(coverPageInfo)
            val canvas = coverPage.canvas
            
            if (coverBitmap != null) {
                // Draw cover image full size to cover the entire page!
                val coverDest = RectF(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat())
                drawBitmapCentered(canvas, coverBitmap, coverDest, 0f)
                
                // Draw a beautiful dark banner overlay at the bottom for readability
                val overlayPaint = Paint().apply {
                    color = Color.parseColor("#F20F121F") // Semi-transparent very dark slate blue
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                // Draw banner in the lower 35% of the cover page
                canvas.drawRect(0f, 530f, pageWidth.toFloat(), 842f, overlayPaint)
                
                // Draw golden top border divider for banner
                val accentPaint = Paint().apply {
                    color = Color.parseColor("#F1C40F") // Gold
                    strokeWidth = 3f
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                }
                canvas.drawLine(0f, 530f, pageWidth.toFloat(), 530f, accentPaint)
            } else {
                // Fallback elegant solid background
                canvas.drawColor(Color.parseColor("#0F121F"))
            }

            // Header stamping (Cleaned, no AI reference as requested)
            val headerPaint = TextPaint().apply {
                color = if (coverBitmap != null) Color.parseColor("#BDC3C7") else Color.GRAY
                textSize = 10f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            drawCenteredText(canvas, "قصة ورواية مصورة حصرية للكاتب kais ✍️", (pageWidth / 2).toFloat(), 50f, headerPaint)

            // Book Title Text
            val titlePaint = TextPaint().apply {
                color = Color.parseColor("#F1C40F") // Large Gold Title
                textSize = 26f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            drawCenteredText(canvas, book.title, (pageWidth / 2).toFloat(), 575f, titlePaint)

            // Splitter
            val dotPaint = Paint().apply {
                color = Color.parseColor("#CBD5E1")
                strokeWidth = 2f
                style = Paint.Style.FILL
            }
            canvas.drawCircle((pageWidth / 2).toFloat(), 605f, 4f, dotPaint)
            canvas.drawCircle((pageWidth / 2 - 15).toFloat(), 605f, 2.5f, dotPaint)
            canvas.drawCircle((pageWidth / 2 + 15).toFloat(), 605f, 2.5f, dotPaint)

            // Concept layout
            val conceptPaint = TextPaint().apply {
                color = Color.parseColor("#EDF2F7") // Readable light text
                textSize = 12f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                isAntiAlias = true
            }
            val conceptLayoutWidth = pageWidth - 100 // margin of 50 inside
            val conceptLayout = StaticLayout.Builder.obtain(book.concept, 0, book.concept.length, conceptPaint, conceptLayoutWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.25f)
                .build()
            
            canvas.save()
            canvas.translate(50f, 625f)
            conceptLayout.draw(canvas)
            canvas.restore()

            // Author stamp badge
            val badgeGold = Paint().apply {
                color = Color.parseColor("#1F3A24") // Solid elegant green
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val badgeBorder = Paint().apply {
                color = Color.parseColor("#2ECC71")
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
                isAntiAlias = true
            }
            val badgeRect = RectF((pageWidth / 2 - 130).toFloat(), 745f, (pageWidth / 2 + 130).toFloat(), 785f)
            canvas.drawRoundRect(badgeRect, 10f, 10f, badgeGold)
            canvas.drawRoundRect(badgeRect, 10f, 10f, badgeBorder)

            val badgeTextPaint = TextPaint().apply {
                color = Color.parseColor("#2ECC71")
                textSize = 13f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            drawCenteredText(canvas, "تأليف الكاتب الحصري: ${book.author} 🖋️", (pageWidth / 2).toFloat(), 770f, badgeTextPaint)

            pdfDocument.finishPage(coverPage)


            // 2. GENERATE EACH INNER STORY PAGE
            val textPaint = TextPaint().apply {
                color = Color.parseColor("#1C1E21")
                textSize = 15f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                isAntiAlias = true
            }
            
            val isRtl = (book.language == "العربية")
            val textAlignment = if (isRtl) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL

            for (index in 0 until totalPages) {
                val pageData = pagesList[index]
                val currentProgress = 30 + ((index + 1) * 60 / totalPages)
                onProgress("جاري طباعة ومراجعة الصفحة ${index + 1} وتنزيل الصور...", currentProgress)

                // First handle image if hasIllustration is true
                if (pageData.hasIllustration && !pageData.illustrationUrl.isNullOrEmpty()) {
                    val illustrationBitmap = fetchBitmapFromUrl(context, pageData.illustrationUrl)
                    if (illustrationBitmap != null) {
                        // Create dedicated FULL-PAGE illustration print plate!
                        val imgPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, physicalPageNum++).create()
                        val imgPage = pdfDocument.startPage(imgPageInfo)
                        val imgCanvas = imgPage.canvas
                        
                        imgCanvas.drawColor(Color.WHITE)
                        
                        // Thin elegant gold inner border margin
                        val borderPaint = Paint().apply {
                            color = Color.parseColor("#E5E8E8")
                            style = Paint.Style.STROKE
                            strokeWidth = 2f
                            isAntiAlias = true
                        }
                        imgCanvas.drawRect(20f, 20f, (pageWidth - 20).toFloat(), (pageHeight - 20).toFloat(), borderPaint)
                        
                        // Crop and draw the illustration to take the full page layout (with beautiful borders)
                        val imgDest = RectF(30f, 30f, (pageWidth - 30).toFloat(), (pageHeight - 30).toFloat())
                        drawBitmapCentered(imgCanvas, illustrationBitmap, imgDest, 16f)
                        
                        // Outer frame border around illustration
                        val borderGold = Paint().apply {
                            color = Color.parseColor("#F1C40F")
                            style = Paint.Style.STROKE
                            strokeWidth = 1.5f
                            isAntiAlias = true
                        }
                        imgCanvas.drawRoundRect(imgDest, 16f, 16f, borderGold)
                        
                        pdfDocument.finishPage(imgPage)
                    }
                }

                // Create dedicated clean text page
                val textPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, physicalPageNum++).create()
                val textPage = pdfDocument.startPage(textPageInfo)
                val pgCanvas = textPage.canvas

                // Pure paper background
                pgCanvas.drawColor(Color.WHITE)

                // Page frame lines
                val pageEdgeBorder = Paint().apply {
                    color = Color.parseColor("#E5E8E8")
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                }
                pgCanvas.drawRect(30f, 30f, (pageWidth - 30).toFloat(), (pageHeight - 30).toFloat(), pageEdgeBorder)

                // Top Header line
                val pageHeaderPaint = TextPaint().apply {
                    color = Color.parseColor("#7F8C8D")
                    textSize = 9f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    isAntiAlias = true
                }
                val headerY = 48f
                pgCanvas.drawText("عنوان الرواية: ${book.title}", 50f, headerY, pageHeaderPaint)
                
                val statusText = "مراجعة وتوقيع: kais • صفحة ${index + 1} من $totalPages"
                val textBounds = Rect()
                pageHeaderPaint.getTextBounds(statusText, 0, statusText.length, textBounds)
                pgCanvas.drawText(statusText, (pageWidth - 50 - textBounds.width()).toFloat(), headerY, pageHeaderPaint)

                // Tiny header divider line
                pgCanvas.drawLine(50f, 56f, (pageWidth - 50).toFloat(), 56f, pageEdgeBorder)

                // Dedicated text starting position (highly readable spacious text layout)
                val textStartY = 85f

                // Draw Story text wrapped cleanly matching line margins and lines constraint
                val textUsableWidth = pageWidth - 120 // Spacious margin: 60 on each side
                val cleanSpacingText = pageData.text.trim()
                
                val storyLayout = StaticLayout.Builder.obtain(cleanSpacingText, 0, cleanSpacingText.length, textPaint, textUsableWidth)
                    .setAlignment(textAlignment)
                    .setLineSpacing(0f, 1.45f)
                    .build()

                pgCanvas.save()
                pgCanvas.translate(60f, textStartY)
                storyLayout.draw(pgCanvas)
                pgCanvas.restore()

                // If last page, print the kais author signature stamp (removed "AI" references as requested)
                if (index == totalPages - 1) {
                    val stampBoxPaint = Paint().apply {
                        color = Color.parseColor("#F4ECF7")
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                    val stampBorder = Paint().apply {
                        color = Color.parseColor("#8E44AD")
                        style = Paint.Style.STROKE
                        strokeWidth = 1f
                        isAntiAlias = true
                    }
                    val stampRect = RectF((pageWidth / 2 - 160).toFloat(), 720f, (pageWidth / 2 + 160).toFloat(), 750f)
                    pgCanvas.drawRoundRect(stampRect, 6f, 6f, stampBoxPaint)
                    pgCanvas.drawRoundRect(stampRect, 6f, 6f, stampBorder)

                    val stampTextPaint = TextPaint().apply {
                        color = Color.parseColor("#8E44AD")
                        textSize = 10f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                        isAntiAlias = true
                    }
                    drawCenteredText(pgCanvas, "المؤلف والكاتب الحصري: kais ✍️", (pageWidth / 2).toFloat(), 739f, stampTextPaint)
                }

                // Footer page numbering
                val footerPaint = TextPaint().apply {
                    color = Color.parseColor("#95A5A6")
                    textSize = 10f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    isAntiAlias = true
                }
                drawCenteredText(pgCanvas, "صفحة رقم ${pageData.pageNumber}", (pageWidth / 2).toFloat(), 805f, footerPaint)

                pdfDocument.finishPage(textPage)
            }

            // 3. WRITE THE DOCUMENT TO APP FILE CACHE STORAGE
            onProgress("جاري دمج الملف وحفظ الـ PDF النهائي...", 95)
            val sharedDir = File(context.cacheDir, "shared_pdfs")
            if (!sharedDir.exists()) {
                sharedDir.mkdirs()
            }
            
            // Clean sanitize file title for file naming
            val sanitizedTitle = book.title.replace(Regex("[^\\w\\dأ-ي]"), "_")
            val pdfFile = File(sharedDir, "airostoris_${sanitizedTitle}_${System.currentTimeMillis()}.pdf")
            
            var fos: FileOutputStream? = null
            try {
                fos = FileOutputStream(pdfFile)
                pdfDocument.writeTo(fos)
            } catch (io: IOException) {
                io.printStackTrace()
                return@withContext null
            } finally {
                fos?.close()
                pdfDocument.close()
            }

            // Share via FileProvider URI
            val authority = "com.aistudio.airostoris.ndvqr.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, pdfFile)
            
            onProgress("تم التصدير للـ PDF بنجاح!", 100)
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun fetchBitmapFromUrl(context: Context, urlString: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(urlString)
                    .allowHardware(false) // Safe for hardware bitmap conversions/drawings on canvases
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val drawable = result.drawable
                    if (drawable is android.graphics.drawable.BitmapDrawable) {
                        drawable.bitmap
                    } else {
                        val bitmap = Bitmap.createBitmap(
                            drawable.intrinsicWidth.takeIf { it > 0 } ?: 1,
                            drawable.intrinsicHeight.takeIf { it > 0 } ?: 1,
                            Bitmap.Config.ARGB_8888
                        )
                        val canvas = Canvas(bitmap)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        bitmap
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun drawBitmapCentered(
        canvas: Canvas,
        bitmap: Bitmap,
        destRect: RectF,
        cornerRadius: Float = 0f
    ) {
        val srcWidth = bitmap.width.toFloat()
        val srcHeight = bitmap.height.toFloat()
        val destWidth = destRect.width()
        val destHeight = destRect.height()

        val srcRatio = srcWidth / srcHeight
        val destRatio = destWidth / destHeight

        val srcRect = if (srcRatio > destRatio) {
            val newWidth = srcHeight * destRatio
            val offset = (srcWidth - newWidth) / 2
            Rect(offset.toInt(), 0, (offset + newWidth).toInt(), srcHeight.toInt())
        } else {
            val newHeight = srcWidth / destRatio
            val offset = (srcHeight - newHeight) / 2
            Rect(0, offset.toInt(), srcWidth.toInt(), (offset + newHeight).toInt())
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
        }

        if (cornerRadius > 0f) {
            val path = Path().apply {
                addRoundRect(destRect, cornerRadius, cornerRadius, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(path)
            canvas.drawBitmap(bitmap, srcRect, destRect, paint)
            canvas.restore()
        } else {
            canvas.drawBitmap(bitmap, srcRect, destRect, paint)
        }
    }

    private fun drawCenteredText(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint) {
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        val textX = x - (bounds.width() / 2f)
        canvas.drawText(text, textX, y, paint)
    }
}
