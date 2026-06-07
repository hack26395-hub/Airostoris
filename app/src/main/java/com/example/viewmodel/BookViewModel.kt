package com.example.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.BookEntity
import com.example.data.BookPage
import com.example.data.BookRepository
import com.example.network.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.UUID

sealed class GenerateState {
    object Idle : GenerateState()
    data class Loading(val message: String, val progress: Int) : GenerateState()
    data class Success(val book: BookEntity) : GenerateState()
    data class Error(val errorMessage: String) : GenerateState()
}

class BookViewModel(application: Application) : AndroidViewModel(application) {

    private val bookDao = AppDatabase.getDatabase(application).bookDao()
    private val repository = BookRepository(bookDao)

    val allBooks: StateFlow<List<BookEntity>> = repository.allBooks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _generateState = MutableStateFlow<GenerateState>(GenerateState.Idle)
    val generateState: StateFlow<GenerateState> = _generateState

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val bookJsonAdapter = moshi.adapter(GeneratedBookJson::class.java)

    private fun cleanPromptForApi(prompt: String, fallback: String): String {
        // Only keep letters, digits, and spaces, replace everything else with space
        val sb = java.lang.StringBuilder()
        for (char in prompt) {
            if (char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9') {
                sb.append(char)
            } else {
                sb.append(' ')
            }
        }
        val cleaned = sb.toString().replace(Regex("\\s+"), " ").trim()
        return if (cleaned.length < 5) fallback else cleaned
    }

    fun createBook(
        title: String,
        concept: String,
        pagesCount: Int,
        linesPerPage: Int,
        styleIsColor: Boolean,
        imageFrequency: Int,
        language: String
    ) {
        viewModelScope.launch {
            _generateState.value = GenerateState.Loading("جاري نسج فكرة الكتاب وبناء هيكليته...", 15)

            val apiKey = com.example.BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                _generateState.value = GenerateState.Error(
                    "مفتاح API الخاص بجيميناي غير مضبوط! يرجى إدخال GEMINI_API_KEY في لوحة الأسرار (Secrets) لتفعيل كتابة الروايات والكتب."
                )
                return@launch
            }

            // Instruct Gemini to write the pages
            val cartoonStyleText = if (styleIsColor) "color, vibrant child cartoon, cute fairytale" else "black and white outline, high-contrast coloring book, simple line sketch art"
            val prompt = """
                أنشئ كتاباً كاملاً ومقسماً إلى صفحات بناءً على المعايير التالية تماماً:
                اسم الكتاب: $title
                فكرة الكتاب الأساسية: $concept
                عدد الصفحات المطلوب: $pagesCount (يجب توليد $pagesCount صفحة تماماً!)
                عدد الأسطر لكل صفحة: $linesPerPage أسطر تقريباً.
                اللغة المطلوبة للكتابة: $language
                نمط الصور الكرتونية: $cartoonStyleText
                تواتر توزيع الصور: ضع صورة كرتونية ملائمة في الصفحات التي يقبل رقمها القسمة على $imageFrequency (بحيث يكون حقل hasIllustration = true مع كتابة حقل illustrationPrompt ملائم للقصة). الصفحات الأخرى يفضل أن تكون hasIllustration = false.
                
                شروط حاسمة جداً للتأليف والرسوم:
                1. يجب كتابة اسم المؤلف kais في نهاية الصفحة الأخيرة بشكل واضح (مثال: "تأليف: kais" أو "Written by: kais").
                2. صمم وصفاً تفصيلياً لغلاف الكتاب كرسومات كرتونية واكتبه باللغة الإنجليزية فقط في حقل coverIllustrationPrompt، واحرص على أن يكون جذاباً يجسد فكرة الرواية بدون أي إشارة للذكاء الاصطناعي وبدون حروف عربية نهائياً.
                3. يجب أن تكون الصفحات مرتبة بالتسلسل من 1 وحتى الصفحة $pagesCount.
                4. يجب كتابة حقول "illustrationPrompt" باللغة الإنجليزية حصراً (English only) بدون أي حروف عربية، لوصف مشهد الصفحة بطريقة كرتونية طفولية لطيفة ومحفبة.
                
                الرجاء الرد حصراً بتنسيق JSON نظيف وصحيح، ولا تكتب أي كلام خارجي أو شرح غير الـ JSON لتجنب أخطاء برمجية:
                {
                  "coverIllustrationPrompt": "description of the book cover in English only, cartoon illustration style, child-friendly",
                  "pages": [
                    {
                      "pageNumber": 1,
                      "text": "نص الصفحة الأولى بالتفصيل والأسطر الكافية...",
                      "hasIllustration": true,
                      "illustrationPrompt": "scenic cartoon description in English only representing the scene on this page"
                    }
                  ]
                }
            """.trimIndent()

            try {
                _generateState.value = GenerateState.Loading("جاري صياغة قصة مشوقة وكتابة الصفحات بذكاء واحترافية عالية...", 45)
                
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                    generationConfig = GenerationConfig(responseMimeType = "application/json", temperature = 0.7f),
                    systemInstruction = Content(parts = listOf(Part(text = "You are a gold-standard elegant children book maker. You always write beautiful, detailed pages in the user's selected language. Strictly outputs valid JSON format.")))
                )

                val response = RetrofitClient.service.generateContent(apiKey, request)
                val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                
                if (rawJson == null) {
                    _generateState.value = GenerateState.Error("فشل توليد الكتاب. استجابة فارغة من الذكاء الاصطناعي.")
                    return@launch
                }

                _generateState.value = GenerateState.Loading("تمت الكتابة الأولية بنجاح! جاري بدء التدقيق والاختبار النهائي...", 75)
                
                performFinalReviewAndSave(
                    rawJson = rawJson,
                    requestedPages = pagesCount,
                    title = title,
                    concept = concept,
                    linesPerPage = linesPerPage,
                    styleIsColor = styleIsColor,
                    imageFrequency = imageFrequency,
                    language = language,
                    apiKey = apiKey
                )

            } catch (e: Exception) {
                Log.e("BookViewModel", "Error building book", e)
                _generateState.value = GenerateState.Error("حدث خطأ أثناء الاتصال بجيميناي: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun performFinalReviewAndSave(
        rawJson: String,
        requestedPages: Int,
        title: String,
        concept: String,
        linesPerPage: Int,
        styleIsColor: Boolean,
        imageFrequency: Int,
        language: String,
        apiKey: String
    ) {
        withContext(Dispatchers.Default) {
            _generateState.value = GenerateState.Loading("جاري مرحلة الاختبار النهائي: مراجعة النصوص وتصحيح الأخطاء ومطابقة الصفحات وتوقيع kais...", 85)

            val correctionPrompt = """
                أنت مدقق لغوي ومصحح تنسيق كتب محترف ومبدع. أمامك كتاب مولد بصيغة JSON لعنوان '$title' وفكرة '$concept'.
                الرجاء فحص الكتاب كاملاً وتنسيقه وإجراء التعديلات التالية:
                1. تصحيح أي أخطاء إملائية، لغوية، أو نحوية بكل لغات الكتاب المكتوب بها.
                2. التحقق من أن عدد الصفحات هو $requestedPages صفحة تماماً، وتعديل أي نقض أو ترتيب خاطئ لتكون مرتبة من الصفحة 1 وحتى الصفحة $requestedPages.
                3. التأكد من كتابة اسم المؤلف kais في ذيل الصفحة الأخيرة لتكتمل بصمة الكتاب الجاهز بشكل جميل.
                4. يجب مراجعة وضبط حقول الأوصاف الصورية coverIllustrationPrompt و illustrationPrompt لتكون جمل باللغة الإنجليزية الصافية 100% (English only) بدون أي حروف عربية أو تداخل بين اللغات نهائياً لتتمكن خوادم الرسم والجيل من معالجتها.
                5. لا تذكر أي عبارات تشير إلى الذكاء الاصطناعي أو "تم التوليد بواسطة" بل صف المشهد الكرتوني برسوماته الخيالية الرائعة فقط.
                6. لا تغير بنية الـ JSON ومفاتيحها على الإطلاق.
                
                الـ JSON الحالي لمسودة الكتاب:
                $rawJson
                
                المطلوب: توليد نسخة نهائية مصححة ومثالية بنسبة 100% بنفس هيكل الـ JSON المعتمد، لا تكتب أي نص شارح فقط الـ JSON المصحح.
            """.trimIndent()

            try {
                val reviewRequest = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = correctionPrompt)))),
                    generationConfig = GenerationConfig(responseMimeType = "application/json", temperature = 0.2f),
                    systemInstruction = Content(parts = listOf(Part(text = "You are a perfectionist senior spelling and structural book corrector. Review and formatting output strictly as valid JSON with English-only illustration prompts.")))
                )

                val response = RetrofitClient.service.generateContent(apiKey, reviewRequest)
                val correctedJsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: rawJson
                
                val finalBookJson: GeneratedBookJson = try {
                    bookJsonAdapter.fromJson(correctedJsonText) ?: bookJsonAdapter.fromJson(rawJson)!!
                } catch (e: Exception) {
                    bookJsonAdapter.fromJson(rawJson) ?: throw Exception("تعذر تحليل بيانات الكتاب المدققة.")
                }

                _generateState.value = GenerateState.Loading("جاري تصميم غلاف القصة والرسومات الكرتونية وبدء النشر...", 95)

                val randomSeed = UUID.randomUUID().hashCode()
                
                val rawCoverPrompt = finalBookJson.coverIllustrationPrompt ?: "children cartoon book cover"
                val cleanedCoverPrompt = cleanPromptForApi(rawCoverPrompt, "cute kids fairytale cartoon book cover")
                val coverStyle = if (styleIsColor) {
                    "vibrant color fairytale cartoon style child friendly cute vector illustration bright colors detailed background"
                } else {
                    "simple children coloring book outline clean bold black lines on perfect white paper minimalist design"
                }
                
                val encodedCoverPrompt = URLEncoder.encode("$cleanedCoverPrompt $coverStyle", "UTF-8").replace("+", "%20")
                val coverUrl = "https://image.pollinations.ai/prompt/$encodedCoverPrompt?width=512&height=768&nologo=true&seed=$randomSeed&model=flux-anime"

                val finalPagesList = finalBookJson.pages.map { page ->
                    val pageImageUrl = if (page.hasIllustration && !page.illustrationPrompt.isNullOrEmpty()) {
                        val cleanedPagePrompt = cleanPromptForApi(page.illustrationPrompt, "cute children fairytale cartoon scene")
                        val pageStyle = if (styleIsColor) {
                            "charming vibrant fairytale cartoon illustration page for children flat retro colors highly detailed"
                        } else {
                            "neat children coloring book draft pure black and white line art on clean white background easy bold contours"
                        }
                        val encodedPagePrompt = URLEncoder.encode("$cleanedPagePrompt $pageStyle", "UTF-8").replace("+", "%20")
                        "https://image.pollinations.ai/prompt/$encodedPagePrompt?width=512&height=512&nologo=true&seed=${randomSeed + page.pageNumber}&model=flux-anime"
                    } else {
                        null
                    }
                    BookPage(
                        pageNumber = page.pageNumber,
                        text = page.text,
                        hasIllustration = page.hasIllustration,
                        illustrationPrompt = page.illustrationPrompt,
                        illustrationUrl = pageImageUrl
                    )
                }

                val pagesJsonString = moshi.adapter<List<BookPage>>(
                    Types.newParameterizedType(List::class.java, BookPage::class.java)
                ).toJson(finalPagesList)

                val bookEntity = BookEntity(
                    title = title,
                    concept = concept,
                    requestedPages = requestedPages,
                    requestedLines = linesPerPage,
                    styleIsColor = styleIsColor,
                    imageFrequency = imageFrequency,
                    language = language,
                    coverImageUrl = coverUrl,
                    pagesJson = pagesJsonString
                )

                val insertedId = repository.insertBook(bookEntity)
                val completeInsertedBook = bookEntity.copy(id = insertedId.toInt())

                withContext(Dispatchers.Main) {
                    _generateState.value = GenerateState.Success(completeInsertedBook)
                }

            } catch (e: Exception) {
                Log.e("BookViewModel", "Review validation failed, saving raw draft with standard check", e)
                try {
                    val parsedDraft = bookJsonAdapter.fromJson(rawJson)!!
                    val randomSeed = UUID.randomUUID().hashCode()
                    val fallbackCover = "https://image.pollinations.ai/prompt/${URLEncoder.encode("children fairytale story book cover cartoon design colorful", "UTF-8").replace("+", "%20")}?width=512&height=768&nologo=true&model=flux-anime"
                    
                    val fallbackPages = parsedDraft.pages.map { page ->
                        val pageImg = if (page.hasIllustration && !page.illustrationPrompt.isNullOrEmpty()) {
                            val cleanedPagePrompt = cleanPromptForApi(page.illustrationPrompt, "cute child cartoon illustration scene")
                            "https://image.pollinations.ai/prompt/${URLEncoder.encode("$cleanedPagePrompt fairytale cartoon style", "UTF-8").replace("+", "%20")}?width=512&height=512&nologo=true&seed=${randomSeed + page.pageNumber}&model=flux-anime"
                        } else null
                        BookPage(page.pageNumber, page.text, page.hasIllustration, page.illustrationPrompt, pageImg)
                    }
                    
                    val pagesJsonFallback = moshi.adapter<List<BookPage>>(
                        Types.newParameterizedType(List::class.java, BookPage::class.java)
                    ).toJson(fallbackPages)

                    val draftEntity = BookEntity(
                        title = title,
                        concept = concept,
                        requestedPages = requestedPages,
                        requestedLines = linesPerPage,
                        styleIsColor = styleIsColor,
                        imageFrequency = imageFrequency,
                        language = language,
                        coverImageUrl = fallbackCover,
                        pagesJson = pagesJsonFallback
                    )
                    val insertedId = repository.insertBook(draftEntity)
                    
                    withContext(Dispatchers.Main) {
                        _generateState.value = GenerateState.Success(draftEntity.copy(id = insertedId.toInt()))
                    }
                } catch (inner: Exception) {
                    withContext(Dispatchers.Main) {
                        _generateState.value = GenerateState.Error("فشل التدقيق وقراءة رد الرواية: ${inner.localizedMessage}")
                    }
                }
            }
        }
    }

    private suspend fun refinePromptWithGemini(userPrompt: String): String {
        val apiKey = com.example.BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return userPrompt
        }
        
        val systemMessage = "You are a senior specialized Prompt Engineer that creates perfect, detailed cartoon story illustration descriptions in English. If the user input is in Arabic, translate it to English. Enrich the description with magical, fairytale details, cartoon style, cute characters, and vivid colors, but KEEP it concise (around 20-35 words). NO prefix, NO introductory text, NO quotes. Output ONLY the English description itself."
        
        val prompt = "Refine the following to a perfect, high-quality English prompt for kids cartoon AI image generator: \n$userPrompt"
        
        try {
            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(temperature = 0.6f),
                systemInstruction = Content(parts = listOf(Part(text = systemMessage)))
            )
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val output = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            if (!output.isNullOrBlank()) {
                return output
            }
        } catch (e: Exception) {
            Log.e("BookViewModel", "Failed to refine prompt with Gemini, using raw", e)
        }
        return userPrompt
    }

    fun regenerateCoverIllustration(book: BookEntity, newPrompt: String? = null, isColor: Boolean? = null, model: String? = "flux") {
        viewModelScope.launch {
            val styleColor = isColor ?: book.styleIsColor
            val rawCoverPrompt = newPrompt ?: book.concept
            val refinedPrompt = refinePromptWithGemini(rawCoverPrompt)
            val cleanedCoverPrompt = cleanPromptForApi(refinedPrompt, "cute kids fairytale cartoon book cover")
            val coverStyle = if (styleColor) {
                "vibrant color fairytale cartoon style child friendly cute vector illustration bright colors detailed background"
            } else {
                "simple children coloring book outline clean bold black lines on perfect white paper minimalist design"
            }
            val randomSeed = UUID.randomUUID().hashCode()
            val encodedCoverPrompt = URLEncoder.encode("$cleanedCoverPrompt $coverStyle", "UTF-8").replace("+", "%20")
            val selectedModel = model ?: "flux"
            val newCoverUrl = "https://image.pollinations.ai/prompt/$encodedCoverPrompt?width=512&height=768&nologo=true&seed=$randomSeed&model=$selectedModel"
            
            val updatedBook = book.copy(
                coverImageUrl = newCoverUrl,
                styleIsColor = styleColor
            )
            repository.insertBook(updatedBook)
        }
    }

    fun regeneratePageIllustration(book: BookEntity, pageNumber: Int, newPrompt: String? = null, isColor: Boolean? = null, model: String? = "flux") {
        viewModelScope.launch {
            val styleColor = isColor ?: book.styleIsColor
            
            val listType = Types.newParameterizedType(List::class.java, BookPage::class.java)
            val adapter = moshi.adapter<List<BookPage>>(listType)
            val pages: List<BookPage> = try {
                adapter.fromJson(book.pagesJson) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            
            val updatedPages = pages.map { page ->
                if (page.pageNumber == pageNumber) {
                    val rawPrompt = newPrompt ?: page.illustrationPrompt ?: "cute child cartoon illustration scene"
                    val refinedPrompt = refinePromptWithGemini(rawPrompt)
                    val cleanedPagePrompt = cleanPromptForApi(refinedPrompt, "cute children fairytale cartoon scene")
                    val pageStyle = if (styleColor) {
                        "charming vibrant fairytale cartoon illustration page for children flat retro colors highly detailed"
                    } else {
                        "neat children coloring book draft pure black and white line art on clean white background easy bold contours"
                    }
                    val randomSeed = UUID.randomUUID().hashCode()
                    val encodedPagePrompt = URLEncoder.encode("$cleanedPagePrompt $pageStyle", "UTF-8").replace("+", "%20")
                    val selectedModel = model ?: "flux"
                    val newPageImageUrl = "https://image.pollinations.ai/prompt/$encodedPagePrompt?width=512&height=512&nologo=true&seed=$randomSeed&model=$selectedModel"
                    
                    page.copy(
                        hasIllustration = true,
                        illustrationPrompt = rawPrompt,
                        illustrationUrl = newPageImageUrl
                    )
                } else {
                    page
                }
            }
            
            val updatedBook = book.copy(
                pagesJson = adapter.toJson(updatedPages),
                styleIsColor = styleColor
            )
            repository.insertBook(updatedBook)
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch {
            repository.deleteBook(book)
        }
    }

    fun resetGenerateState() {
        _generateState.value = GenerateState.Idle
    }
}
