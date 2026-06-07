package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.Coil
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import com.example.data.BookEntity
import com.example.data.BookPage
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.BookViewModel
import com.example.viewmodel.GenerateState
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Build a highly reliable custom ImageLoader for Coil with 60-second timeouts & browser headers
        val customImageLoader = ImageLoader.Builder(applicationContext)
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                            .build()
                        chain.proceed(request)
                    }
                    .build()
            }
            .crossfade(true)
            .build()
        Coil.setImageLoader(customImageLoader)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F121F) // Premium Deep dark canvas (Cosmic Midnight Slate)
                ) {
                    AirostorisApp()
                }
            }
        }
    }
}

// Global Moshi Parser Helper for local Page list extraction
fun parsePagesJson(json: String): List<BookPage> {
    return try {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val type = Types.newParameterizedType(List::class.java, BookPage::class.java)
        val adapter = moshi.adapter<List<BookPage>>(type)
        adapter.fromJson(json) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirostorisApp(viewModel: BookViewModel = viewModel()) {
    val context = LocalContext.current
    val books by viewModel.allBooks.collectAsStateWithLifecycle()
    val generateState by viewModel.generateState.collectAsStateWithLifecycle()

    var isFormOpen by remember { mutableStateOf(false) }
    var activeBook by remember { mutableStateOf<BookEntity?>(null) }

    // Forms State Localized
    var bookTitle by remember { mutableStateOf("") }
    var bookConcept by remember { mutableStateOf("") }
    var pagesCount by remember { mutableStateOf(10f) } // Slider 1 to 300
    var linesCount by remember { mutableStateOf(10) } // Dropdown or row select
    var styleIsColor by remember { mutableStateOf(true) } // color vs B&W
    var imageFrequency by remember { mutableStateOf(2) } // image every 1, 2, 3, or 4 pages
    var selectedLanguage by remember { mutableStateOf("العربية") }

    val languages = listOf("العربية", "English", "Français", "Español", "Deutsch")

    // Navigation and screen management
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0C0F1D),
                        Color(0xFF14182E)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (activeBook != null) {
            val currentActiveBook = books.find { it.id == activeBook!!.id } ?: activeBook!!
            // Book Reader View Override
            BookReaderView(
                book = currentActiveBook,
                onClose = {
                    activeBook = null
                },
                onDelete = {
                    viewModel.deleteBook(currentActiveBook)
                    Toast.makeText(context, "تم حذف الكتاب بنجاح", Toast.LENGTH_SHORT).show()
                    activeBook = null
                }
            )
        } else if (isFormOpen) {
            // Form Creator screen
            BookCreatorForm(
                title = bookTitle,
                onTitleChange = { bookTitle = it },
                concept = bookConcept,
                onConceptChange = { bookConcept = it },
                pages = pagesCount,
                onPagesChange = { pagesCount = it },
                lines = linesCount,
                onLinesChange = { linesCount = it },
                isColor = styleIsColor,
                onIsColorChange = { styleIsColor = it },
                freq = imageFrequency,
                onFreqChange = { imageFrequency = it },
                lang = selectedLanguage,
                onLangChange = { selectedLanguage = it },
                langOptions = languages,
                onBack = { isFormOpen = false },
                onSubmit = {
                    if (bookTitle.isBlank() || bookConcept.isBlank()) {
                        Toast.makeText(context, "يرجى تعبئة اسم وفكرة الكتاب أولاً!", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.createBook(
                            title = bookTitle,
                            concept = bookConcept,
                            pagesCount = pagesCount.toInt(),
                            linesPerPage = linesCount,
                            styleIsColor = styleIsColor,
                            imageFrequency = imageFrequency,
                            language = selectedLanguage
                        )
                    }
                }
            )
        } else {
            // Main bookcase shelf/library view
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    CenterAlignedTopAppBar(
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = Color(0xFFF1C40F) // Gold Accent
                        ),
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFF1C40F),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = "Airostoris",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "أيروستوريس",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = {
                            // Reset state for new book creation
                            bookTitle = ""
                            bookConcept = ""
                            pagesCount = 8f
                            linesCount = 8
                            styleIsColor = true
                            imageFrequency = 2
                            viewModel.resetGenerateState()
                            isFormOpen = true
                        },
                        containerColor = Color(0xFFF1C40F),
                        contentColor = Color(0xFF0F121F),
                        modifier = Modifier
                            .padding(bottom = 16.dp, end = 8.dp)
                            .testTag("create_book_fab"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "ألف جديد")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "تأليف كتاب جديد",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp)
                ) {
                    // Header banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF231A43),
                                        Color(0xFF14182E)
                                    )
                                )
                            )
                            .border(1.dp, Color(0xFF33206D), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Text(
                                text = "مستشار التأليف جاهز لك 🧙‍♂️",
                                color = Color(0xFFF1C40F),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "اصنع كتباً وروايات متكاملة الأركان بلمح البصر، مدعمة بالرسومات الكرتونية الفريدة ومراجعة لغوياً بنسبة 100%! بإشراف كاتبك kais.",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "مكتبتك والكتب الجاهزة (${books.size})",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )

                    if (books.isEmpty()) {
                        // Empty states illustration UI
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .background(Color(0xFF1B203E), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = Color(0xFF5D658C),
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "الرفوف فارغة حالياً! ✨",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "ابدأ بكتابة اسم الفكرة لترى قصتك كاملة ومصممة وموقعة باسم المؤلف kais.",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp),
                                lineHeight = 20.sp
                            )
                        }
                    } else {
                        // Library Bookshelf grid view
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(bottom = 80.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(books) { book ->
                                BookCard(
                                    book = book,
                                    onClick = { activeBook = book }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Overlay modal for loading, reviewing and testing states during generation
        AnimatedVisibility(
            visible = generateState !is GenerateState.Idle,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE60A0C16))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                when (val state = generateState) {
                    is GenerateState.Loading -> {
                        GenerationLoaderView(
                            message = state.message,
                            progress = state.progress
                        )
                    }
                    is GenerateState.Success -> {
                        LaunchedEffect(state) {
                            delay(1000)
                            activeBook = state.book
                            isFormOpen = false
                            viewModel.resetGenerateState()
                        }
                        SuccessPublishCompletedView(title = state.book.title)
                    }
                    is GenerateState.Error -> {
                        ErrorStateDialogue(
                            errorMessage = state.errorMessage,
                            onClose = { viewModel.resetGenerateState() }
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}

// Gorgeous loading view mapping with the final verification testing phase requested
@Composable
fun GenerationLoaderView(message: String, progress: Int) {
    var rotationAngle by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            rotationAngle += 4f
            delay(16)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("verify_and_save_anim"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161A30)),
        border = BorderStroke(1.dp, Color(0xFF332A57)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer rotating mystical circle
                Canvas(modifier = Modifier
                    .fillMaxSize()
                    .rotate(rotationAngle)) {
                    drawArc(
                        color = Color(0xFFF1C40F),
                        startAngle = 0f,
                        sweepAngle = 280f,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 6.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                }
                
                // Icons mapping custom statuses
                Icon(
                    imageVector = if (progress >= 75) Icons.Default.Check else Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFF1C40F),
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "أيروستوريس يكتب بريقاً ✨",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Color(0xFFF1C40F),
                trackColor = Color(0xFF20264A),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "$progress%",
                color = Color(0xFFF1C40F),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Current subtask logging in Arabic
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            if (progress >= 75) {
                // Interactive Review Checks visualization
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = Color(0xFF2B3050))
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("تدقيق القواعد والصفحات:", fontSize = 12.sp, color = Color.White.copy(0.6f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("نشط ✅", fontSize = 12.sp, color = Color(0xFF2ECC71), fontWeight = FontWeight.Bold)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("دمج ترويسة kais وتنسيق الأسطر:", fontSize = 12.sp, color = Color.White.copy(0.6f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("جارٍ... ✍️", fontSize = 12.sp, color = Color(0xFFF1C40F), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SuccessPublishCompletedView(title: String) {
    Card(
        modifier = Modifier.fillMaxWidth(0.9f),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2C24)),
        border = BorderStroke(1.dp, Color(0xFF1E523C)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color(0xFF2ECC71),
                modifier = Modifier
                    .size(60.dp)
                    .background(Color(0xFF153F32), CircleShape)
                    .padding(12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "اكتملت المراجعة والنشر! 🎉",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "روايتك الجديدة '$title' جاهزة الآن وحليفة التدقيق والمصادقة باسم kais جاهزة للقراءة الفورية!",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun ErrorStateDialogue(errorMessage: String, onClose: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(0.9f),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C161E)),
        border = BorderStroke(1.dp, Color(0xFF5E202B)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFE74C3C),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "حدث خطأ ما",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("رجوع لتعديل الخيارات", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Visual Book Display Card on shelves
@Composable
fun BookCard(book: BookEntity, onClick: () -> Unit) {
    val totalIllustrationsCount = remember(book) {
        parsePagesJson(book.pagesJson).count { it.hasIllustration }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("book_shelf_item_${book.id}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161A30)),
        border = BorderStroke(1.dp, Color(0xFF252A4A)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(Color(0xFF0F121F))
            ) {
                // Book Cover Rendering using Coil
                coil.compose.SubcomposeAsyncImage(
                    model = book.coverImageUrl,
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFF1C40F),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color(0xFF0F121F)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFE74C3C).copy(alpha = 0.5f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                )
                // Overlay Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color(0xE60F121F), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${book.requestedPages} ص",
                        color = Color(0xFFF1C40F),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = book.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "بصمة الكاتب: ${book.author}",
                    color = Color(0xFF2ECC71),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (book.styleIsColor) "ملونة 🎨" else "أبيض/أسود ✏️",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                    Text(
                        text = "صور: $totalIllustrationsCount",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

// Gorgeous creation input panels
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookCreatorForm(
    title: String,
    onTitleChange: (String) -> Unit,
    concept: String,
    onConceptChange: (String) -> Unit,
    pages: Float,
    onPagesChange: (Float) -> Unit,
    lines: Int,
    onLinesChange: (Int) -> Unit,
    isColor: Boolean,
    onIsColorChange: (Boolean) -> Unit,
    freq: Int,
    onFreqChange: (Int) -> Unit,
    lang: String,
    onLangChange: (String) -> Unit,
    langOptions: List<String>,
    onBack: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        // Top action row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .background(Color(0xFF1B203E), CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "رجوع",
                    tint = Color.White
                )
            }
            Text(
                "منصة تأليف الروايات",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Title textfield
        Text(
            "اسم الكتاب / الرواية 📖",
            color = Color.White.copy(0.9f),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            placeholder = { Text("مثال: مغامرة كحيلان في الجزيرة المفقودة", textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("book_title_input"),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF161A30),
                unfocusedContainerColor = Color(0xFF161A30),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedIndicatorColor = Color(0xFFF1C40F),
                unfocusedIndicatorColor = Color(0xFF252A4A)
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Concept textfield
        Text(
            "فكرة أو محور القصة 💡",
            color = Color.White.copy(0.9f),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = concept,
            onValueChange = onConceptChange,
            placeholder = { Text("مثال: قرية سعيدة يزورها تنين نائم يحتاج لمساعدة الأصدقاء ليتعلم الطيران من جديد والتحليق للأعلى", textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .testTag("book_concept_input"),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF161A30),
                unfocusedContainerColor = Color(0xFF161A30),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedIndicatorColor = Color(0xFFF1C40F),
                unfocusedIndicatorColor = Color(0xFF252A4A)
            ),
            maxLines = 4
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Language block
        Text(
            "لغة كتابة الرواية 🌐",
            color = Color.White.copy(0.9f),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            langOptions.forEach { l ->
                val isSelected = lang == l
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Color(0xFFF1C40F) else Color(0xFF161A30))
                        .border(1.dp, if (isSelected) Color(0xFFF1C40F) else Color(0xFF252A4A), RoundedCornerShape(10.dp))
                        .clickable { onLangChange(l) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = l,
                        color = if (isSelected) Color(0xFF0F121F) else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Page count sliders (from 1 to 300)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161A30), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF252A4A), RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${pages.toInt()} صفحة",
                    color = Color(0xFFF1C40F),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "حجم الكتاب (عدد الصفحات) 🗂️",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = pages,
                onValueChange = onPagesChange,
                valueRange = 1f..300f,
                steps = 299,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFF1C40F),
                    activeTrackColor = Color(0xFFF1C40F),
                    inactiveTrackColor = Color(0xFF2E345D)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pages_slider")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("300 صفحة", fontSize = 11.sp, color = Color.White.copy(0.4f))
                Text("الأفضل للعرض السريع: 5 - 12 صفحة", fontSize = 11.sp, color = Color(0xFFE2A345))
                Text("صفحة 1", fontSize = 11.sp, color = Color.White.copy(0.4f))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Lines count row selector
        Text(
            "عدد الأسطر في كل صفحة 📏",
            color = Color.White.copy(0.9f),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val linesOptions = listOf(5, 8, 12, 15)
            linesOptions.forEach { opt ->
                val isSelected = lines == opt
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Color(0xFFF1C40F) else Color(0xFF161A30))
                        .border(1.dp, if (isSelected) Color(0xFFF1C40F) else Color(0xFF252A4A), RoundedCornerShape(10.dp))
                        .clickable { onLinesChange(opt) }
                        .padding(vertical = 10.dp)
                        .testTag("lines_option_$opt"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$opt أسطر",
                        color = if (isSelected) Color(0xFF0F121F) else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Draw style (Color vs B&W)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161A30), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF252A4A), RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Visual Swapper
            Row(
                modifier = Modifier
                    .weight(0.6f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F121F))
                    .padding(2.dp)
            ) {
                // Color button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isColor) Color(0xFFE2A345) else Color.Transparent)
                        .clickable { onIsColorChange(true) }
                        .padding(vertical = 6.dp)
                        .testTag("color_style_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("ملونة 🎨", color = if (isColor) Color(0xFF0F121F) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                // B&W button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (!isColor) Color(0xFFE2A345) else Color.Transparent)
                        .clickable { onIsColorChange(false) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("أبيض وأسود ✏️", color = if (!isColor) Color(0xFF0F121F) else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "نمط الرسومات ค✏️",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Frequency selector
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161A30), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF252A4A), RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                "توزيع الرسومات الكرتونية بالرواية 🖼️",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val freqs = listOf(1, 2, 3, 4)
                freqs.forEach { f ->
                    val isSelected = freq == f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0xFFF1C40F) else Color(0xFF0F121F))
                            .border(1.dp, if (isSelected) Color(0xFFF1C40F) else Color(0xFF252A4A), RoundedCornerShape(10.dp))
                            .clickable { onFreqChange(f) }
                            .padding(vertical = 10.dp)
                            .testTag("image_frequency_option_$f"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "كل $f صفحات",
                            color = if (isSelected) Color(0xFF0F121F) else Color.White.copy(0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Submit publishing buttons
        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("generate_button"),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1C40F)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF0F121F))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "صناعة الرواية ومراجعتها الآن 🚀",
                    color = Color(0xFF0F121F),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

// Full interactive digital book reader screen
@Composable
fun BookReaderView(
    book: BookEntity,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    viewModel: com.example.viewmodel.BookViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentPageIndex by remember { mutableStateOf(-1) } // -1 indicates Cover Page

    val pages = remember(book) { parsePagesJson(book.pagesJson) }
    val totalElements = pages.size

    // PDF generation states
    var isPdfGenerating by remember { mutableStateOf(false) }
    var pdfProgressText by remember { mutableStateOf("") }
    var pdfProgressPercent by remember { mutableStateOf(0) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showIllustrationVerifier by remember { mutableStateOf(false) }

    // PDF generation progress dialog popup
    if (isPdfGenerating) {
        AlertDialog(
            onDismissRequest = {}, // strict modal status
            confirmButton = {},
            dismissButton = {},
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "جاري تصدير روايتك الـ PDF...",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(
                        progress = { pdfProgressPercent / 100f },
                        color = Color(0xFFF1C40F),
                        trackColor = Color(0xFF1B203E),
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = pdfProgressText,
                        color = Color.White.copy(0.81f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    LinearProgressIndicator(
                        progress = { pdfProgressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFFF1C40F),
                        trackColor = Color(0xFF1B203E)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "%$pdfProgressPercent",
                        color = Color(0xFFF1C40F),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            containerColor = Color(0xFF161A30),
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Share Options Selection Dialogue popup
    if (showShareDialog) {
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            confirmButton = {
                TextButton(onClick = { showShareDialog = false }) {
                    Text("إلغاء", color = Color(0xFFE74C3C), fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Text(
                    text = "خيارات تصدير ومشاركة الرواية 🌟",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Option 1: Standard text copy sharing
                    Card(
                        onClick = {
                            showShareDialog = false
                            val shareText = buildString {
                                append("رواية: ${book.title}\n")
                                append("فكرة: ${book.concept}\n")
                                append("تأليف: kais ✍️\n\n")
                                pages.forEach {
                                    append("الصفحة ${it.pageNumber}:\n")
                                    append("${it.text}\n\n")
                                }
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, "مشاركة الرواية كـ نص"))
                        },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B203E)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📝",
                                fontSize = 24.sp,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "مشاركة كـ نص بسيط",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "توليد ومشاركة نص الرواية كاملة سريعاً",
                                    color = Color.White.copy(0.6f),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // Option 2: Beautiful rich graphical PDF compilation sharing
                    Card(
                        onClick = {
                            showShareDialog = false
                            scope.launch {
                                isPdfGenerating = true
                                pdfProgressPercent = 0
                                pdfProgressText = "جاري تحضير الطابعة الإلكترونية..."
                                val pdfUri = com.example.utils.PdfGenerator.generateBookPdf(
                                    context = context,
                                    book = book,
                                    onProgress = { status, percentage ->
                                        pdfProgressText = status
                                        pdfProgressPercent = percentage
                                    }
                                )
                                isPdfGenerating = false
                                if (pdfUri != null) {
                                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(pdfUri, "application/pdf")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                                    }
                                    try {
                                        context.startActivity(viewIntent)
                                    } catch (e: Exception) {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(Intent.EXTRA_STREAM, pdfUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "فتح ومشاركة ملف الـ PDF"))
                                    }
                                } else {
                                    Toast.makeText(context, "فشل تصدير ملف الـ PDF. يرجى المحاولة لاحقاً.", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C1E19)),
                        border = BorderStroke(1.5.dp, Color(0xFFF1C40F)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📄✨",
                                fontSize = 24.sp,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "تصدير كتاب PDF جاهز للطباعة",
                                    color = Color(0xFFF1C40F),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "توليد ملف منسّق ببطانة غلاف ورسومات ونصوص واضحة",
                                    color = Color.White.copy(0.7f),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            },
            containerColor = Color(0xFF101222),
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showIllustrationVerifier) {
        val defaultPrompt = if (currentPageIndex == -1) {
            "A fairytale kids cartoon book cover of a story about ${book.concept} titled ${book.title}"
        } else {
            pages.getOrNull(currentPageIndex)?.illustrationPrompt ?: "A cartoon kids fairytale drawing representing the story..."
        }
        var localPromptText by remember(showIllustrationVerifier, currentPageIndex) { mutableStateOf(defaultPrompt) }
        var localIsColor by remember(showIllustrationVerifier) { mutableStateOf(book.styleIsColor) }
        var selectedModel by remember(showIllustrationVerifier) { mutableStateOf("flux-anime") } // default to beautiful flux-anime / nano banana style
        var isRegenerating by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isRegenerating) showIllustrationVerifier = false },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isRegenerating = true
                            if (currentPageIndex == -1) {
                                viewModel.regenerateCoverIllustration(book, localPromptText, localIsColor, selectedModel)
                            } else {
                                val curPageNum = pages[currentPageIndex].pageNumber
                                viewModel.regeneratePageIllustration(book, curPageNum, localPromptText, localIsColor, selectedModel)
                            }
                            kotlinx.coroutines.delay(2000)
                            isRegenerating = false
                            showIllustrationVerifier = false
                            Toast.makeText(context, "تم تصحيح وتوليد الصورة بنجاح بنظام المدقق الذكي! جاري التحميل خلال لحظات...", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isRegenerating,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1C40F))
                ) {
                    if (isRegenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black)
                    } else {
                        Text("حفظ وتوليد الصورة فوراً 🔄", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showIllustrationVerifier = false },
                    enabled = !isRegenerating
                ) {
                    Text("إلغاء", color = Color.White.copy(0.6f))
                }
            },
            title = {
                Text(
                    text = if (currentPageIndex == -1) "🎨 مدقق ومصحح غلاف الرواية" else "🎨 مدقق ومصحح رسم حدث صفحة ${currentPageIndex + 1}",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "يقوم هذا المدقق الصوري الذكي بفحص أوصاف الصور وإعادة صياغتها بالإنجليزية وترجمتها بشكل تلقائي من العربية وحل مشكلة عدم التحميل عبر ربطها بمحرك نانو بنانا الذكي.",
                        color = Color.White.copy(0.7f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                        Text(
                            text = "وصف المشهد للرسم (يدعم العربية أو الإنجليزية) 📝",
                            color = Color(0xFFF1C40F),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        OutlinedTextField(
                            value = localPromptText,
                            onValueChange = { localPromptText = it },
                            placeholder = { Text("مثال: ولد يركب فيل طائر ملون أو سحر الغابة...", color = Color.Gray) },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 4,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFF1C40F),
                                unfocusedBorderColor = Color(0xFF1B203E),
                                focusedContainerColor = Color(0xFF1B203E),
                                unfocusedContainerColor = Color(0xFF1B203E)
                            )
                        )
                    }

                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                        Text(
                            text = "اختر محرك الذكاء الاصطناعي وباقة السحب 🦾",
                            color = Color(0xFFF1C40F),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val chunkedModels = listOf(
                                listOf("flux-anime" to "أنمي كرتوني (Nano Banana) 🌸", "flux" to "الرسم الافتراضي 🎨"),
                                listOf("flux-3d" to "ثلاثي الأبعاد 🧸", "flux-realism" to "واقعي سريالي 📸")
                            )
                            chunkedModels.forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    row.forEach { (modelId, modelName) ->
                                        val isSelected = selectedModel == modelId
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSelected) Color(0xFFF1C40F) else Color(0xFF1B203E))
                                                .clickable { selectedModel = modelId }
                                                .padding(vertical = 10.dp, horizontal = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = modelName,
                                                color = if (isSelected) Color.Black else Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                        Text(
                            text = "تلوين الرسمة 🌈",
                            color = Color(0xFFF1C40F),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { localIsColor = false },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (!localIsColor) Color(0xFFE74C3C) else Color(0xFF1B203E)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("📓 أبيض وأسود", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { localIsColor = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (localIsColor) Color(0xFF2ECC71) else Color(0xFF1B203E)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🎨 رسومات ملونة", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            containerColor = Color(0xFF101222),
            shape = RoundedCornerShape(24.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C16))
    ) {
        // Reader top bar controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Delete action
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .background(Color(0xFF2C161E), CircleShape)
                    .size(40.dp)
                    .testTag("delete_book_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "حذف الكتاب",
                    tint = Color(0xFFE74C3C)
                )
            }

            // Share text / PDF action
            IconButton(
                onClick = { showShareDialog = true },
                modifier = Modifier
                    .background(Color(0xFF1B203E), CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "مشاركة",
                    tint = Color.White
                )
            }

            // Book details
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = book.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (currentPageIndex == -1) "الغلاف الخارجي" else "الصفحة ${currentPageIndex + 1} من $totalElements",
                    color = Color(0xFFF1C40F),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Close back to shelf
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .background(Color(0xFF1B203E), CircleShape)
                    .size(40.dp)
                    .testTag("close_reader_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "إغلاق",
                    tint = Color.White
                )
            }
        }

        // Active page area (with swipe animations)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (currentPageIndex == -1) {
                // Book Cover Display page
                Card(
                    modifier = Modifier
                        .fillMaxSize(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161A30)),
                    border = BorderStroke(2.dp, Color(0xFFF1C40F)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "📖 غلاف رواية متكاملة",
                            color = Color(0xFFF1C40F),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Big stylized cover illustration
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(310.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF0A0C16))
                        ) {
                            coil.compose.SubcomposeAsyncImage(
                                model = book.coverImageUrl,
                                contentDescription = "غلاف الرواية كرتوني",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                loading = {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = Color(0xFFF1C40F),
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                },
                                error = {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(Color(0xFF0F121F)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = Color(0xFFE74C3C),
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("تعذر تحميل الغلاف", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                                        }
                                    }
                                }
                            )
                        }

                        // Artwork validation / verification button
                        androidx.compose.material3.OutlinedButton(
                            onClick = { showIllustrationVerifier = true },
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFF1C40F)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1C40F).copy(0.4f)),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Text("🎨 مدقق ومصحح غلاف الرواية", color = Color(0xFFF1C40F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = book.title,
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                lineHeight = 30.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "فكرة القصة: ${book.concept}",
                                color = Color.White.copy(0.6f),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        // Author stamp
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1F2B1E))
                                .border(1.dp, Color(0xFF2ECC71), RoundedCornerShape(12.dp))
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "تأليف المبدع: ${book.author} ✍️",
                                color = Color(0xFF2ECC71),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                // Individual story page
                val currentPage = pages.getOrNull(currentPageIndex)
                if (currentPage != null) {
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161A30)),
                        border = BorderStroke(1.dp, Color(0xFF252A4A)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (currentPage.hasIllustration && !currentPage.illustrationUrl.isNullOrEmpty()) {
                                // Event illustrations
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .padding(bottom = 8.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    coil.compose.SubcomposeAsyncImage(
                                        model = currentPage.illustrationUrl,
                                        contentDescription = "رسم المشهد كرتونياً",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        loading = {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    color = Color(0xFFF1C40F),
                                                    modifier = Modifier.size(30.dp)
                                                )
                                            }
                                        },
                                        error = {
                                            Box(
                                                modifier = Modifier.fillMaxSize().background(Color(0xFF0F121F)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(
                                                        imageVector = Icons.Default.Warning,
                                                        contentDescription = null,
                                                        tint = Color(0xFFE74C3C),
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text("تعذر تحميل الرسمة", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    )
                                }

                                // Artwork modifier button
                                androidx.compose.material3.OutlinedButton(
                                    onClick = { showIllustrationVerifier = true },
                                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFFF1C40F)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1C40F).copy(0.4f)),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(42.dp).padding(bottom = 8.dp)
                                ) {
                                    Text("🎨 مدقق ومصحح رسوم الحدث", color = Color(0xFFF1C40F), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                // Default subtle spacer/filler for text pages, but with addition option
                                androidx.compose.material3.OutlinedButton(
                                    onClick = { showIllustrationVerifier = true },
                                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color.White.copy(0.6f)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.2f)),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(42.dp).padding(bottom = 12.dp)
                                ) {
                                    Text("➕ إضافة رسم كرتوني لهذا الحدث", color = Color.White.copy(0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Story content paragraph
                            Text(
                                text = currentPage.text,
                                color = Color.White.copy(alpha = 0.95f),
                                fontSize = 16.sp,
                                lineHeight = 28.sp,
                                textAlign = TextAlign.Right,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                            )

                            Spacer(modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.height(16.dp))

                            // If this is the final page, we MUST showcase final signed author name
                            if (currentPageIndex == totalElements - 1) {
                                Box(
                                    modifier = Modifier
                                        .padding(vertical = 12.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF231F33))
                                        .border(1.dp, Color(0xFF8E44AD), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "المؤلف والكاتب الحصري: kais ✍️",
                                        color = Color(0xFF9E5ED4),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            Text(
                                text = "صفحة رقم ${currentPage.pageNumber}",
                                color = Color.White.copy(0.4f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                    }
                }
            }
        }

        // Reader navigation bottom footer indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous button (Right because of Arabic RTF alignment)
            Button(
                onClick = {
                    if (currentPageIndex > -1) {
                        currentPageIndex--
                    }
                },
                enabled = currentPageIndex > -1,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B203E), disabledContainerColor = Color(0xFF101222)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = if (currentPageIndex > -1) Color.White else Color.White.copy(0.3f))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("السابق", color = if (currentPageIndex > -1) Color.White else Color.White.copy(0.3f), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Next button (Left because of Arabic alignment)
            Button(
                onClick = {
                    if (currentPageIndex < totalElements - 1) {
                        currentPageIndex++
                    }
                },
                enabled = currentPageIndex < totalElements - 1,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1C40F), disabledContainerColor = Color(0xFF101222)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (currentPageIndex == -1) "ابدأ القراءة 📖" else "التالي",
                        color = if (currentPageIndex < totalElements - 1) Color.Black else Color.White.copy(0.3f),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = if (currentPageIndex < totalElements - 1) Color.Black else Color.White.copy(0.3f))
                }
            }
        }
    }
}
