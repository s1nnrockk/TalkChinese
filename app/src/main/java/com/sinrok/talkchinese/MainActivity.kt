package com.sinrok.talkchinese

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.net.HttpURLConnection
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.io.OutputStreamWriter
import java.util.concurrent.Executors

private val Lacquer = Color(0xFF8E1725)
private val DeepRed = Color(0xFF350B13)
private val Gold = Color(0xFFD8B56D)
private val Paper = Color(0xFFFFF8EB)
private val Ink = Color(0xFF21191A)
private val Muted = Color(0xFF806E69)
private val CardWhite = Color(0xFFFFFDF8)
private val SoftRose = Color(0xFFF4E2DD)
private val AuthExecutor = Executors.newSingleThreadExecutor()

private enum class MainTab(val title: String) {
    HOME("Главная"), LEARN("Уроки"), DIALOGUES("Диалоги"), SEARCH("Поиск"), ACCOUNTS("Аккаунты"), SETTINGS("Настройки")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TalkChineseApp(this) }
    }
}

@Composable
private fun TalkChineseApp(context: Context) {
    var isLogin by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var checkingSession by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }
    var signedInEmail by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(MainTab.HOME) }
    var searchQuery by remember { mutableStateOf("") }

    val prefs = remember { context.getSharedPreferences("talkchinese", Context.MODE_PRIVATE) }
    val configured = BuildConfig.SUPABASE_URL.startsWith("https://") &&
        BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    LaunchedEffect(Unit) {
        val savedToken = prefs.getString("access_token", null)
        if (savedToken.isNullOrBlank() || !configured) {
            checkingSession = false
        } else {
            AuthExecutor.execute {
                val validEmail = runCatching { validateSession(savedToken) }.getOrNull()
                (context as Activity).runOnUiThread {
                    if (!validEmail.isNullOrBlank()) {
                        signedInEmail = validEmail
                        prefs.edit().putString("email", validEmail).apply()
                    } else {
                        prefs.edit().clear().apply()
                    }
                    checkingSession = false
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Lacquer,
            onPrimary = Paper,
            secondary = Gold,
            background = Paper,
            surface = CardWhite,
            onSurface = Ink
        )
    ) {
        when {
            checkingSession -> LoadingScreen()
            signedInEmail.isBlank() -> AuthScreen(
                isLogin = isLogin,
                onLoginModeChange = {
                    isLogin = it
                    message = ""
                },
                email = email,
                onEmailChange = { email = it.trim() },
                password = password,
                onPasswordChange = { password = it },
                confirmPassword = confirmPassword,
                onConfirmPasswordChange = { confirmPassword = it },
                showPassword = showPassword,
                onTogglePassword = { showPassword = !showPassword },
                busy = busy,
                message = message,
                configured = configured,
                onSubmit = {
                    val normalizedEmail = email.trim()
                    val validEmail = android.util.Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()
                    when {
                        !validEmail -> message = "Проверь адрес электронной почты."
                        password.length < 8 -> message = "Пароль должен содержать минимум 8 символов."
                        !isLogin && password != confirmPassword -> message = "Пароли не совпадают."
                        !configured -> message = "Подключение Supabase не настроено. Проверь GitHub Secrets."
                        else -> {
                            busy = true
                            message = ""
                            val mail = normalizedEmail
                            val pass = password
                            val loginMode = isLogin
                            AuthExecutor.execute {
                                val result = runCatching { supabaseAuth(mail, pass, loginMode) }
                                (context as Activity).runOnUiThread {
                                    busy = false
                                    result.onSuccess { auth ->
                                        if (auth.success && !auth.accessToken.isNullOrBlank()) {
                                            prefs.edit()
                                                .putString("email", auth.email ?: mail)
                                                .putString("access_token", auth.accessToken)
                                                .apply()
                                            signedInEmail = auth.email ?: mail
                                            selectedTab = MainTab.HOME
                                            password = ""
                                            confirmPassword = ""
                                            message = ""
                                        } else {
                                            message = auth.message
                                        }
                                    }.onFailure { error ->
                                        message = error.message ?: "Не удалось связаться с сервером. Попробуй ещё раз."
                                    }
                                }
                            }
                        }
                    }
                }
            )
            else -> MainShell(
                context = context,
                email = signedInEmail,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onLogout = {
                    prefs.edit().clear().apply()
                    signedInEmail = ""
                    selectedTab = MainTab.HOME
                    email = ""
                    password = ""
                    confirmPassword = ""
                    message = ""
                }
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(DeepRed, Lacquer))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            BrandMark(72)
            CircularProgressIndicator(color = Gold, strokeWidth = 3.dp)
            Text("Проверяем защищённую сессию…", color = Paper, fontSize = 14.sp)
        }
    }
}

@Composable
private fun AuthScreen(
    isLogin: Boolean,
    onLoginModeChange: (Boolean) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    showPassword: Boolean,
    onTogglePassword: () -> Unit,
    busy: Boolean,
    message: String,
    configured: Boolean,
    onSubmit: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF25070D), DeepRed, Color(0xFF751A2A), Lacquer))
        )
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BrandMark(78)
            Spacer(Modifier.height(16.dp))
            Text("TalkChinese", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Paper)
            Text("中文 · Твой путь в китайский", color = Gold, fontSize = 14.sp)
            Spacer(Modifier.height(25.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                color = Paper.copy(alpha = 0.98f),
                shadowElevation = 18.dp
            ) {
                Column(
                    Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Text(
                        if (isLogin) "С возвращением" else "Начни своё путешествие",
                        fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Lacquer
                    )
                    Text(
                        if (isLogin) "Войди, чтобы продолжить обучение."
                        else "Один аккаунт — новые слова каждый день.",
                        fontSize = 14.sp, color = Muted
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = onEmailChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Электронная почта") },
                        leadingIcon = { Icon(Icons.Default.Person, null, tint = Lacquer) },
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(16.dp)
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Пароль · от 8 символов") },
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = Lacquer) },
                        trailingIcon = {
                            IconButton(onClick = onTogglePassword, enabled = !busy) {
                                Icon(
                                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassword) "Скрыть пароль" else "Показать пароль",
                                    tint = Lacquer
                                )
                            }
                        },
                        singleLine = true,
                        enabled = !busy,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(16.dp)
                    )
                    if (!isLogin) {
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = onConfirmPasswordChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Повтори пароль") },
                            leadingIcon = { Icon(Icons.Default.CheckCircle, null, tint = Lacquer) },
                            singleLine = true,
                            enabled = !busy,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            isError = confirmPassword.isNotEmpty() && password != confirmPassword,
                            supportingText = {
                                if (confirmPassword.isNotEmpty() && password != confirmPassword) {
                                    Text("Пароли пока не совпадают")
                                }
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                    }

                    AnimatedVisibility(visible = message.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
                        Surface(
                            color = if (message.startsWith("Проверь почту")) Color(0xFFE8F3E8) else SoftRose,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                message,
                                Modifier.fillMaxWidth().padding(12.dp),
                                color = if (message.startsWith("Проверь почту")) Color(0xFF27613B) else Lacquer,
                                fontSize = 13.sp
                            )
                        }
                    }
                    if (!configured) {
                        Text(
                            "Авторизация временно недоступна: конфигурация Supabase не прошла проверку.",
                            color = Lacquer, fontSize = 12.sp
                        )
                    }
                    Button(
                        onClick = onSubmit,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        enabled = !busy && configured,
                        shape = RoundedCornerShape(17.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Lacquer)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = Paper, strokeWidth = 2.dp)
                        } else {
                            Text(if (isLogin) "Войти в аккаунт" else "Создать аккаунт", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.ArrowForward, null)
                        }
                    }
                    TextButton(
                        onClick = { onLoginModeChange(!isLogin) },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        enabled = !busy
                    ) {
                        Text(
                            if (isLogin) "Впервые здесь? Создать аккаунт" else "Уже есть аккаунт? Войти",
                            color = Lacquer
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("学无止境  ·  Учёба без границ", color = Gold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun BrandMark(size: Int) {
    Box(
        Modifier.size(size.dp).background(
            Brush.linearGradient(listOf(Color(0xFFFFE9A8), Gold, Color(0xFF9B6B27))),
            RoundedCornerShape((size * 0.30f).dp)
        ).border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape((size * 0.30f).dp)),
        contentAlignment = Alignment.Center
    ) {
        Text("汉", fontSize = (size * 0.53f).sp, fontWeight = FontWeight.Black, color = DeepRed)
    }
}


@Composable
private fun DragonMascot(size: Int) {
    val motion = rememberInfiniteTransition(label = "dragonMascotMotion")
    val bob by motion.animateFloat(
        initialValue = -4.5f, targetValue = 4.5f,
        animationSpec = infiniteRepeatable(animation = tween(1850), repeatMode = RepeatMode.Reverse),
        label = "dragonFloat"
    )
    // Two motion tracks keep the mascot lively without running four perpetual animations.
    val yaw by motion.animateFloat(
        initialValue = -4f, targetValue = 4f,
        animationSpec = infiniteRepeatable(animation = tween(3200), repeatMode = RepeatMode.Reverse),
        label = "dragonPerspective"
    )
    val glow = 0.58f
    Box(
        Modifier.size(size.dp)
            .graphicsLayer {
                translationY = bob
                rotationZ = yaw * 0.38f
                rotationY = yaw
                rotationX = -yaw * 0.42f
                cameraDistance = 18f * density
                shadowElevation = 12.dp.toPx()
                shape = RoundedCornerShape((size * 0.34f).dp)
                clip = true
            }
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFFFFF0C5).copy(alpha = 0.58f), Color(0xFFB72B3A), Color(0xFF350B13))
                ),
                RoundedCornerShape((size * 0.34f).dp)
            )
            .border(1.4.dp, Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.96f), Gold.copy(alpha = 0.68f), Color(0xFF8D5B2A))
            ), RoundedCornerShape((size * 0.34f).dp))
            .padding(3.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.talkchinese_dragon),
            contentDescription = "Китайский дракон TalkChinese с объёмной анимацией",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
                .graphicsLayer {
                    rotationY = -yaw * 0.32f
                    scaleX = 1.015f
                    scaleY = 1.015f
                }
                .clip(RoundedCornerShape((size * 0.30f).dp))
        )
        // Moving specular highlight gives the glass frame a polished, liquid-glass finish.
        Box(
            Modifier.fillMaxWidth(0.72f).height((size * 0.14f).dp)
                .align(Alignment.TopCenter)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.68f), Color.Transparent)
                ))
        )
    }
}

private fun configureChineseVoice(tts: TextToSpeech): Boolean {
    val chineseVoices = runCatching {
        tts.voices.orEmpty().filter { it.locale.language == "zh" && it.locale.country in setOf("CN", "") }
    }.getOrDefault(emptyList())
    val preferredVoice = chineseVoices.filterNot { it.isNetworkConnectionRequired }.maxByOrNull { it.quality }
        ?: chineseVoices.maxByOrNull { it.quality }
    if (preferredVoice != null) runCatching { tts.voice = preferredVoice }
    val result = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
    return result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
}

@Composable
private fun ChineseAudioButton(hanzi: String, pinyin: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val ttsHolder = remember(context) { arrayOfNulls<TextToSpeech>(1) }
    val tts = remember(context) {
        TextToSpeech(context.applicationContext) { status ->
            val engine = ttsHolder[0]
            val voiceReady = status == TextToSpeech.SUCCESS && engine != null && configureChineseVoice(engine)
            ready = voiceReady
            if (!voiceReady) failed = true
        }.also { ttsHolder[0] = it }
    }
    DisposableEffect(tts) {
        onDispose { tts.stop(); tts.shutdown() }
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.70f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.clickable {
                if (ready) {
                    if (configureChineseVoice(tts)) {
                        tts.setPitch(1.0f)
                        tts.setSpeechRate(0.92f)
                        tts.speak(hanzi, TextToSpeech.QUEUE_FLUSH, null, "talkchinese_${hanzi}_${pinyin}")
                    } else failed = true
                } else failed = true
            }.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.VolumeUp, contentDescription = "Прослушать китайское произношение", tint = Lacquer, modifier = Modifier.size(21.dp))
            Text(if (failed) "Аудио недоступно" else if (ready) "听 · Слушать" else "Загрузка…", color = Lacquer, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}



private data class DialogueLine(val speaker: Int, val hanzi: String, val pinyin: String, val russian: String)
private data class ChineseDialogue(val title: String, val lines: List<DialogueLine>)
private val starterDialogues = listOf(
    ChineseDialogue("Знакомство · 认识新朋友", listOf(
        DialogueLine(0,"你好！你叫什么名字？","Nǐ hǎo! Nǐ jiào shénme míngzi?","Привет! Как тебя зовут?"),
        DialogueLine(1,"我叫安娜。你呢？","Wǒ jiào Ānnà. Nǐ ne?","Меня зовут Анна. А тебя?"),
        DialogueLine(0,"我叫大卫。很高兴认识你。","Wǒ jiào Dàwèi. Hěn gāoxìng rènshi nǐ.","Меня зовут Давэй. Рад познакомиться."),
        DialogueLine(1,"我也很高兴！","Wǒ yě hěn gāoxìng!","Я тоже рада!")
    )),
    ChineseDialogue("В кафе · 在咖啡馆", listOf(
        DialogueLine(0,"你想喝什么？","Nǐ xiǎng hē shénme?","Что хотите выпить?"),
        DialogueLine(1,"我想喝一杯茶。","Wǒ xiǎng hē yì bēi chá.","Я хочу чашку чая."),
        DialogueLine(0,"还要别的吗？","Hái yào bié de ma?","Что-нибудь ещё?"),
        DialogueLine(1,"不用了，谢谢！","Bú yòng le, xièxie!","Нет, спасибо!")
    )),
    ChineseDialogue("Как пройти? · 问路", listOf(
        DialogueLine(0,"地铁站在哪儿？","Dìtiě zhàn zài nǎr?","Где станция метро?"),
        DialogueLine(1,"一直走，然后左转。","Yìzhí zǒu, ránhòu zuǒ zhuǎn.","Идите прямо, затем налево."),
        DialogueLine(0,"远不远？","Yuǎn bu yuǎn?","Это далеко?"),
        DialogueLine(1,"走五分钟就到。","Zǒu wǔ fēnzhōng jiù dào.","Пешком пять минут.")
    )),
    ChineseDialogue("Покупки · 买东西", listOf(
        DialogueLine(0,"这个多少钱？","Zhège duōshao qián?","Сколько это стоит?"),
        DialogueLine(1,"三十五块。","Sānshíwǔ kuài.","Тридцать пять юаней."),
        DialogueLine(0,"可以便宜一点儿吗？","Kěyǐ piányi yìdiǎnr ma?","Можно немного дешевле?"),
        DialogueLine(1,"好吧，三十块。","Hǎo ba, sānshí kuài.","Ладно, тридцать юаней.")
    ))
)

@Composable
private fun DialoguesScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var ready by remember { mutableStateOf(false) }
    var audioError by remember { mutableStateOf(false) }
    var expanded by remember { mutableIntStateOf(0) }
    val dialogueTtsHolder = remember(context) { arrayOfNulls<TextToSpeech>(1) }
    val tts = remember(context) {
        TextToSpeech(context.applicationContext) { status ->
            val engine = dialogueTtsHolder[0]
            val voiceReady = status == TextToSpeech.SUCCESS && engine != null && configureChineseVoice(engine)
            ready = voiceReady
            if (!voiceReady) audioError = true
        }.also { dialogueTtsHolder[0] = it }
    }
    DisposableEffect(tts) { onDispose { tts.stop(); tts.shutdown() } }
    fun speakLine(item: DialogueLine, queue: Int = TextToSpeech.QUEUE_FLUSH) {
        if (!ready) { audioError = true; return }
        if (!configureChineseVoice(tts)) { audioError = true; return }
        // Keep both dialogue roles at a natural pitch; artificial pitch shifts sound comic.
        tts.setPitch(1.0f)
        tts.setSpeechRate(0.90f)
        tts.speak(item.hanzi, queue, null, "dialog_" + item.speaker + "_" + item.hanzi.hashCode() + "_" + System.nanoTime())
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .background(Brush.verticalGradient(listOf(Paper, Color(0xFFFFF1E2), Color(0xFFF8E9DF))))
        .padding(horizontal = 18.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("对话 · Диалоги", color = Ink, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
                Text("Озвучка обеих ролей · 双人会话", color = Muted, fontSize = 13.sp)
            }
            DragonMascot(60)
        }
        Surface(shape = RoundedCornerShape(26.dp), color = DeepRed.copy(alpha = 0.96f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = 0.65f)), shadowElevation = 7.dp) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("TWO VOICES · A + B", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                Text("Живой китайский — реплика за репликой", color = Paper, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("Слушай весь диалог или каждую реплику отдельно. Роли различаются высотой голоса; для настоящих разных тембров нужен установленный китайский TTS-движок с несколькими голосами.", color = Paper.copy(alpha = 0.84f), fontSize = 12.sp)
            }
        }
        if (audioError) Surface(shape = RoundedCornerShape(14.dp), color = SoftRose) {
            Text("Китайский голос недоступен. Проверь настройки Text-to-Speech и установи голос 简体中文.", Modifier.padding(12.dp), color = Lacquer, fontSize = 12.sp)
        }
        starterDialogues.forEachIndexed { index, dialogue ->
            val isExpanded = expanded == index
            Surface(shape = RoundedCornerShape(24.dp), color = Color.White.copy(alpha = 0.72f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.95f)),
                shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(dialogue.title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                            Text("4 реплики · pinyin + перевод", color = Muted, fontSize = 11.sp)
                        }
                        Surface(shape = RoundedCornerShape(15.dp), color = Lacquer,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = 0.75f)),
                            modifier = Modifier.clickable {
                                expanded = index
                                dialogue.lines.forEachIndexed { n, item ->
                                    speakLine(item, if (n == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD)
                                }
                            }) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VolumeUp, null, tint = Gold, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(if (ready) "Слушать всё" else "Загрузка…", color = Paper, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (isExpanded) dialogue.lines.forEach { item ->
                        val roleColor = if (item.speaker == 0) Lacquer else Color(0xFF8A6426)
                        Surface(shape = RoundedCornerShape(18.dp),
                            color = if (item.speaker == 0) Color.White.copy(alpha = 0.84f) else Color(0xFFFFF0D4).copy(alpha = 0.84f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, roleColor.copy(alpha = 0.18f))) {
                            Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (item.speaker == 0) "角色 A · Роль A" else "角色 B · Роль B", color = roleColor, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                                    Text(item.hanzi, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                    Text(item.pinyin, color = Lacquer, fontSize = 12.sp)
                                    Text(item.russian, color = Muted, fontSize = 12.sp)
                                }
                                IconButton(onClick = { speakLine(item) }) { Icon(Icons.Default.VolumeUp, contentDescription = "Озвучить реплику", tint = roleColor) }
                            }
                        }
                    }
                    TextButton(onClick = { expanded = if (isExpanded) -1 else index }) {
                        Text(if (isExpanded) "Свернуть ↑" else "Открыть реплики ↓", color = Lacquer, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Text("Совет: прослушай диалог целиком, затем повторяй каждую реплику отдельно.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun MainShell(
    context: Context,
    email: String,
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onLogout: () -> Unit
) {
    Scaffold(
        containerColor = Paper,
        bottomBar = {
            LiquidBottomBar(selected = selectedTab, onSelect = onTabSelected)
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .background(Brush.verticalGradient(listOf(Paper, Color(0xFFFFF2E4))))
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val movingForward = targetState.ordinal >= initialState.ordinal
                    (fadeIn(animationSpec = tween(360, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(
                            animationSpec = tween(460, easing = FastOutSlowInEasing),
                            initialOffsetX = { fullWidth -> if (movingForward) fullWidth / 9 else -fullWidth / 9 }
                        ) +
                        slideInVertically(
                            animationSpec = tween(420, easing = FastOutSlowInEasing),
                            initialOffsetY = { height -> height / 70 }
                        ) +
                        scaleIn(animationSpec = tween(420, easing = FastOutSlowInEasing), initialScale = 0.94f))
                        .togetherWith(
                            fadeOut(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                                slideOutHorizontally(
                                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                                    targetOffsetX = { fullWidth -> if (movingForward) -fullWidth / 16 else fullWidth / 16 }
                                ) +
                                slideOutVertically(
                                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                                    targetOffsetY = { height -> -height / 90 }
                                ) +
                                scaleOut(animationSpec = tween(300, easing = FastOutSlowInEasing), targetScale = 1.015f)
                        )
                },
                label = "liquidTabTransition"
            ) { tab ->
                when (tab) {
                    MainTab.HOME -> HomeScreen(email, onStartLearning = { onTabSelected(MainTab.LEARN) })
                    MainTab.LEARN -> LessonsScreen(context)
                    MainTab.DIALOGUES -> DialoguesScreen()
                    MainTab.SEARCH -> SearchScreen(context, searchQuery, onSearchQueryChange)
                    MainTab.ACCOUNTS -> AccountsScreen(email, onLogout)
                    MainTab.SETTINGS -> SettingsScreen(context, email, onLogout)
                }
            }
        }
    }
}

@Composable
private fun LiquidBottomBar(selected: MainTab, onSelect: (MainTab) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        shadowElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFFDFBFA).copy(alpha = 0.78f),
                            Color(0xFFF6EAE6).copy(alpha = 0.86f),
                            Color(0xFFFFFDF8).copy(alpha = 0.91f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.95f), Color(0xFFDCC6BD).copy(alpha = 0.7f))
                    ),
                    shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
                )
        ) {
            // Glossy reflection along the upper edge gives the bar a glass-like finish.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.95f),
                                Color(0xFFFFF5D9).copy(alpha = 0.9f),
                                Color.White.copy(alpha = 0.95f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 9.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MainTab.values().forEach { tab ->
                    val active = selected == tab
                    val scale by animateFloatAsState(
                        targetValue = if (active) 1f else 0.94f,
                        animationSpec = spring(dampingRatio = 0.38f, stiffness = 520f),
                        label = "liquidTabScale"
                    )
                    val lift by animateDpAsState(
                        targetValue = if (active) (-3).dp else 0.dp,
                        animationSpec = spring(dampingRatio = 0.34f, stiffness = 440f),
                        label = "liquidTabLift"
                    )
                    val pillHeight by animateDpAsState(
                        targetValue = if (active) 57.dp else 49.dp,
                        animationSpec = spring(dampingRatio = 0.36f, stiffness = 380f),
                        label = "liquidTabHeight"
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .height(62.dp)
                            .offset(y = lift)
                            .scale(scale)
                            .clip(RoundedCornerShape(21.dp))
                            .clickable { onSelect(tab) }
                            .padding(horizontal = 1.dp, vertical = 1.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(pillHeight)
                                .clip(RoundedCornerShape(19.dp))
                                .background(
                                    if (active) Brush.linearGradient(
                                        listOf(
                                            Color(0xFFFFE5D6).copy(alpha = 0.98f),
                                            Color(0xFFF1C7B7).copy(alpha = 0.92f),
                                            Color(0xFFE8B8A9).copy(alpha = 0.96f)
                                        )
                                    ) else Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.58f),
                                            Color(0xFFFFFDF8).copy(alpha = 0.20f)
                                        )
                                    )
                                )
                                .border(
                                    width = if (active) 1.dp else 0.7.dp,
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = if (active) 1f else 0.82f),
                                            if (active) Color(0xFFCB8F7D).copy(alpha = 0.78f)
                                            else Color(0xFFDCCBC5).copy(alpha = 0.46f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(19.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            // A soft specular highlight makes the selected capsule feel like a curved drop.
                            Box(
                                Modifier
                                    .fillMaxWidth(0.62f)
                                    .height(10.dp)
                                    .align(Alignment.TopCenter)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.0f),
                                                Color.White.copy(alpha = if (active) 0.78f else 0.48f),
                                                Color.White.copy(alpha = 0.0f)
                                            )
                                        )
                                    )
                            )
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                val iconSize by animateDpAsState(
                                    targetValue = if (active) 24.dp else 21.dp,
                                    animationSpec = spring(dampingRatio = 0.30f, stiffness = 320f),
                                    label = "liquidIconSize"
                                )
                                val iconTint by animateColorAsState(
                                    targetValue = if (active) Lacquer else Muted,
                                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 460f),
                                    label = "liquidIconTint"
                                )
                                val iconBounce by animateFloatAsState(
                                    targetValue = if (active) 1.08f else 1f,
                                    animationSpec = spring(dampingRatio = 0.24f, stiffness = 300f),
                                    label = "liquidIconBounce"
                                )
                                Icon(
                                    imageVector = tabIcon(tab),
                                    contentDescription = tab.title,
                                    tint = iconTint,
                                    modifier = Modifier.size(iconSize).scale(iconBounce)
                                )
                                AnimatedVisibility(
                                    visible = active,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    Text(
                                        tab.title,
                                        fontSize = 9.sp,
                                        color = DeepRed,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun tabIcon(tab: MainTab) = when (tab) {
    MainTab.HOME -> Icons.Default.Home
    MainTab.LEARN -> Icons.Default.Book
    MainTab.DIALOGUES -> Icons.Default.VolumeUp
    MainTab.SEARCH -> Icons.Default.Search
    MainTab.ACCOUNTS -> Icons.Default.Person
    MainTab.SETTINGS -> Icons.Default.Settings
}

@Composable
private fun HomeScreen(email: String, onStartLearning: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("你好， привет!", color = Lacquer, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Продолжим учиться?", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                Text(email, color = Muted, fontSize = 12.sp)
            }
            DragonMascot(70)
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = Lacquer,
            shadowElevation = 9.dp
        ) {
            Box(Modifier.background(Brush.linearGradient(listOf(Color(0xFF9F2635), DeepRed))).padding(22.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(Icons.Default.Whatshot, null, tint = Gold)
                        Text("ТВОЙ КИТАЙСКИЙ ПУТЬ", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    }
                    Text("Маленький шаг.\nБольшой прогресс.", color = Paper, fontSize = 27.sp, lineHeight = 33.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Осваивай слова, фразы и тона в своём темпе.", color = Paper.copy(alpha = 0.82f), fontSize = 14.sp)
                    Row(
                        Modifier.padding(top = 4.dp).clip(RoundedCornerShape(15.dp))
                            .background(Gold).clickable(onClick = onStartLearning).padding(horizontal = 15.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Начать обучение", color = DeepRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowForward, null, tint = DeepRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        val context = androidx.compose.ui.platform.LocalContext.current
        val learningPrefs = remember(context) { context.getSharedPreferences("talkchinese_learning", Context.MODE_PRIVATE) }
        var dailyCount by remember { mutableIntStateOf(0) }
        var streak by remember { mutableIntStateOf(0) }
        var completedCount by remember { mutableIntStateOf(0) }
        var xp by remember { mutableIntStateOf(0) }
        val todayKey = remember { java.time.LocalDate.now().toString() }
        LaunchedEffect(context) {
            dailyCount = if (learningPrefs.getString("daily_date", "") == todayKey) learningPrefs.getInt("daily_count", 0) else 0
            streak = learningPrefs.getInt("learning_streak", 0)
            completedCount = learningPrefs.getStringSet("completed_lessons", emptySet())?.size ?: 0
            xp = learningPrefs.getInt("learning_xp", completedCount * 10)
        }
        Text("ТВОЙ ПРОГРЕСС", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Modifier.weight(1f), "$dailyCount", "Сегодня", "ответов")
            StatCard(Modifier.weight(1f), "🔥 $streak", "Серия дней", "не теряй ритм")
        }
        GlassLessonCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Твой путь", color = Lacquer, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Text("$completedCount слов изучено · $xp XP", color = Muted, fontSize = 12.sp)
                }
                BrandMark(48)
            }
            val goal = 10
            val progress = (dailyCount.toFloat() / goal).coerceIn(0f, 1f)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = Lacquer, trackColor = SoftRose)
            Text("Цель дня: $dailyCount из $goal ответов", color = Muted, fontSize = 12.sp)
        }
        Text("ДОСТИЖЕНИЯ", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { AchievementChip("🌱", "Первый шаг", completedCount >= 1) }
            Box(Modifier.weight(1f)) { AchievementChip("🔥", "3 дня подряд", streak >= 3) }
            Box(Modifier.weight(1f)) { AchievementChip("🏆", "100 XP", xp >= 100) }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onStartLearning),
            shape = RoundedCornerShape(24.dp),
            color = CardWhite,
            shadowElevation = 3.dp
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(SoftRose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Book, null, tint = Lacquer, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Первый урок", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Приветствия и первые фразы", color = Muted, fontSize = 13.sp)
                }
                Icon(Icons.Default.ArrowForward, null, tint = Lacquer)
            }
        }
        Text("Каждое слово словаря становится отдельным уроком с объяснением и практикой.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun AchievementChip(emoji: String, title: String, unlocked: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (unlocked) Color(0xFFFFF0D1).copy(alpha = 0.88f) else Color.White.copy(alpha = 0.52f),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (unlocked) Gold.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.9f))
    ) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(if (unlocked) emoji else "🔒", fontSize = 20.sp)
            Text(title, color = if (unlocked) Lacquer else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(if (unlocked) "Открыто" else "Впереди", color = Muted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, headline: String, title: String, detail: String) {
    Surface(modifier, shape = RoundedCornerShape(22.dp), color = CardWhite, shadowElevation = 3.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(headline, color = Lacquer, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text(title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = Muted, fontSize = 11.sp)
        }
    }
}


private fun normalizeLearningAnswer(value: String): String =
    Normalizer.normalize(value.trim().lowercase().replace('ü', 'v'), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace("u:", "v").replace(Regex("[\\s'-]+"), "")


@Composable
private fun LessonsScreen(context: Context) {
    val uiPrefs = remember(context) { context.getSharedPreferences("talkchinese_ui_settings", Context.MODE_PRIVATE) }
    val showPinyin = remember { uiPrefs.getBoolean("show_pinyin", true) }
    val prefs = remember(context) { context.getSharedPreferences("talkchinese_learning", Context.MODE_PRIVATE) }
    var entries by remember { mutableStateOf<List<DictionaryEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var level by remember { mutableIntStateOf(0) }
    var index by remember { mutableIntStateOf(prefs.getInt("lesson_index", 0)) }
    var done by remember { mutableStateOf(prefs.getStringSet("completed_lessons", emptySet())?.toSet() ?: emptySet()) }
    var selected by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }
    var correct by remember { mutableStateOf(false) }
    var reviewOnly by remember { mutableStateOf(false) }
    var reviewRefresh by remember { mutableIntStateOf(0) }
    var dailyCount by remember { mutableIntStateOf(0) }
    val lessonScrollState = rememberScrollState()
    val today = remember { java.time.LocalDate.now().toString() }

    LaunchedEffect(index, level, reviewOnly) {
        lessonScrollState.animateScrollTo(0)
    }

    LaunchedEffect(context) {
        val result = runCatching { withContext(Dispatchers.IO) { readDictionary(context) } }
        result.onSuccess { entries = it }.onFailure { failed = true }
        dailyCount = if (prefs.getString("daily_date", "") == today) prefs.getInt("daily_count", 0) else 0
        loading = false
    }

    val lessons = remember(entries, level, reviewOnly, reviewRefresh) {
        val base = if (level == 0) entries else entries.filter { it.level == level }
        if (!reviewOnly) base else {
            val now = System.currentTimeMillis()
            base.filter { entry ->
                val raw = prefs.getString("srs_${entry.hanzi}_${entry.pinyin}", null)
                val due = raw?.substringAfter("|", "")?.toLongOrNull() ?: 0L
                raw != null && due <= now
            }
        }
    }
    val pos = if (lessons.isEmpty()) 0 else if (reviewOnly) index.coerceIn(0, lessons.lastIndex) else Math.floorMod(index, lessons.size)
    val word = lessons.getOrNull(pos)
    val type = pos % 4
    val meaning = word?.russian?.firstOrNull()?.takeIf { it.isNotBlank() } ?: word?.english?.firstOrNull().orEmpty()
    val answer = when (type) { 0 -> meaning; 1 -> word?.pinyin.orEmpty(); 2 -> word?.hanzi.orEmpty(); else -> word?.pinyin.orEmpty() }
    val options = remember(lessons, pos, type) {
        val w = lessons.getOrNull(pos)
        if (w == null || type == 3) emptyList() else {
            fun value(e: DictionaryEntry) = when (type) {
                0 -> e.russian.firstOrNull()?.takeIf { it.isNotBlank() } ?: e.english.firstOrNull().orEmpty()
                1 -> e.pinyin
                else -> e.hanzi
            }
            val right = value(w)
            (listOf(right) + lessons.asSequence().filter { it.hanzi != w.hanzi }.map(::value)
                .filter { it.isNotBlank() && it != right }.distinct().take(3))
                .distinct().sortedBy { it.hashCode() xor w.hanzi.hashCode() }
        }
    }
    val completedInLevel = remember(lessons, done, reviewOnly, index) { if (reviewOnly) index.coerceIn(0, lessons.size) else lessons.count { "${it.hanzi}|${it.pinyin}" in done } }
    val progress = if (lessons.isEmpty()) 0f else completedInLevel.toFloat() / lessons.size.toFloat()

    Column(
        Modifier.fillMaxSize().verticalScroll(lessonScrollState)
            .background(Brush.verticalGradient(listOf(Paper, Color(0xFFFFF1E2), Color(0xFFF8E9DF))))
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("学习 · Уроки", color = Ink, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
                Text("Китайский в твоём ритме", color = Muted, fontSize = 13.sp)
            }
            DragonMascot(62)
        }

        Surface(shape = RoundedCornerShape(28.dp), color = DeepRed, border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = .55f)), shadowElevation = 5.dp) {
            Box {
                Box(Modifier.align(Alignment.TopEnd).size(130.dp).background(Brush.radialGradient(listOf(Gold.copy(alpha = .25f), Color.Transparent)), CircleShape))
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("每日进步 · ТВОЙ ПРОГРЕСС", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                    Text(if (reviewOnly) "Время повторить" else "Маленький шаг — большой результат", color = Paper, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                    Text(if (reviewOnly) "Повторяй слова, которые пора закрепить." else "Слова, произношение и активное вспоминание — в одном уроке.", color = Paper.copy(alpha = .86f), fontSize = 13.sp)
                    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = Gold, trackColor = Paper.copy(alpha = .18f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$completedInLevel / ${lessons.size} слов закреплено", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text("Сегодня: $dailyCount / 10", color = Paper, fontSize = 11.sp)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModePill("新词 · Учить", !reviewOnly, Modifier.weight(1f)) {
                reviewOnly = false; index = 0; selected = ""; typed = ""; feedback = null; correct = false
            }
            ModePill("复习 · Повторить", reviewOnly, Modifier.weight(1f)) {
                reviewOnly = true; index = 0; selected = ""; typed = ""; feedback = null; correct = false; reviewRefresh++
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (0..6).forEach { n ->
                FilterChip(selected = level == n, onClick = {
                    level = n; index = 0; selected = ""; typed = ""; feedback = null; correct = false; reviewRefresh++
                }, label = { Text(if (n == 0) "Все" else "HSK $n", fontSize = 10.sp) }, modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Lacquer, selectedLabelColor = Paper))
            }
        }

        when {
            loading -> Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Lacquer) }
            failed || entries.isEmpty() -> GlassLessonCard {
                Text("Не удалось загрузить словарь", color = Lacquer, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Словарь HSK загружается при сборке APK. Проверь последний запуск GitHub Actions и пересобери приложение.", color = Muted, fontSize = 13.sp)
            }
            reviewOnly && (lessons.isEmpty() || index >= lessons.size) -> GlassLessonCard {
                Text(if (lessons.isEmpty()) "复习完成 · Всё повторено" else "本轮完成 · Раунд завершён", color = Lacquer, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                Text(if (lessons.isEmpty()) "Сейчас нет слов, которые пора повторить. Новые повторы появятся по расписанию." else "Ты прошёл все слова, доступные для повторения сейчас. Отличная работа!", color = Muted, fontSize = 14.sp)
                Button(onClick = { reviewOnly = false; index = 0; feedback = null }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Lacquer)) { Text("Учить новые слова") }
            }
            word != null -> {
                GlassLessonCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (reviewOnly) "复习 · ПОВТОРЕНИЕ" else "第 ${pos + 1} 课 · УРОК", color = Lacquer, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                        Spacer(Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(10.dp), color = Gold.copy(alpha = .22f)) {
                            Text("HSK ${word.level}", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Lacquer, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                        Text(word.hanzi, color = Lacquer, fontSize = 54.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    if (showPinyin) Text(word.pinyin.ifBlank { "Пиньинь не указан" }, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(meaning.ifBlank { "Перевод не указан" }, modifier = Modifier.weight(1f), color = Lacquer, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        ChineseAudioButton(hanzi = word.hanzi, pinyin = word.pinyin)
                    }
                    Text("听一听 · Нажми на динамик, чтобы услышать произношение на китайском.", color = Muted, fontSize = 11.sp)
                    if (word.russian.size > 1) Text("Другие значения: ${word.russian.drop(1).take(4).joinToString(" · ")}", color = Muted, fontSize = 12.sp)
                    HorizontalDivider(color = Gold.copy(alpha = .45f))
                    Text("КАК ЗАПОМНИТЬ", color = Lacquer, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 1.2.sp)
                    Text("Свяжи иероглиф, звучание и смысл. В китайском четыре основных тона и нейтральный: ровный, восходящий, сначала вниз-вверх и резкий нисходящий. Слушай пример и повторяй вслух.", color = Ink, fontSize = 14.sp)
                    Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = .55f), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .85f))) {
                        Text("Сначала посмотри на 汉字, затем попробуй вспомнить произношение и перевод, не глядя на ответ.", Modifier.fillMaxWidth().padding(13.dp), color = Muted, fontSize = 12.sp)
                    }
                }

                GlassLessonCard {
                    Text("练习 · ПРАКТИКА", color = Lacquer, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.1.sp)
                    Text(when (type) {
                        0 -> "Что означает «${word.hanzi}»?"
                        1 -> "Выбери пиньинь для «${word.hanzi}»."
                        2 -> "Какой иероглиф означает «$meaning»?"
                        else -> "Введи пиньинь слова «${word.hanzi}»."
                    }, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)

                    if (type == 3) {
                        OutlinedTextField(value = typed, onValueChange = { typed = it; feedback = null }, modifier = Modifier.fillMaxWidth(),
                            singleLine = true, label = { Text("Пиньинь") }, placeholder = { Text("Например: ni hao") }, shape = RoundedCornerShape(16.dp), enabled = feedback == null)
                        Button(onClick = {
                            correct = normalizeLearningAnswer(typed) == normalizeLearningAnswer(answer)
                            feedback = if (correct) "Верно! Ты вспомнил произношение самостоятельно." else "Пока не совпало. Попробуй ещё раз — тоны при проверке не обязательны."
                        }, enabled = typed.isNotBlank() && feedback == null, modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Lacquer), shape = RoundedCornerShape(16.dp)) { Text("Проверить ответ") }
                    } else options.forEach { option ->
                        OutlinedButton(onClick = {
                            selected = option; correct = option == answer
                            feedback = if (correct) "Верно! ${word.hanzi} — $meaning; пиньинь: ${word.pinyin}." else "Пока нет. Правильный ответ: $answer. Запомни связку и попробуй снова."
                        }, enabled = feedback == null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected == option && correct) Color(0xFFDDEFE0) else if (selected == option) SoftRose else Color.White.copy(alpha = .65f), contentColor = Ink)) {
                            Text(option, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                        }
                    }

                    feedback?.let { note ->
                        Surface(shape = RoundedCornerShape(16.dp), color = if (correct) Color(0xFFDDEFE0) else SoftRose,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (correct) Color(0xFF9CC9A3) else Color(0xFFE3B8B8))) {
                            Text(note, Modifier.fillMaxWidth().padding(13.dp), color = Ink, fontSize = 13.sp)
                        }
                        Button(onClick = {
                            val cardKey = "srs_${word.hanzi}_${word.pinyin}"
                            val previous = prefs.getString(cardKey, null)?.substringBefore("|")?.toIntOrNull() ?: 0
                            val now = System.currentTimeMillis()
                            val intervalDays = if (correct) when ((previous + 1).coerceAtMost(5)) {
                                1 -> 1; 2 -> 3; 3 -> 7; 4 -> 14; else -> 30
                            } else 0
                            val dueAt = if (correct) now + intervalDays * 24L * 60L * 60L * 1000L else now + 10L * 60L * 1000L
                            prefs.edit().putString(cardKey, "${if (correct) (previous + 1).coerceAtMost(5) else 0}|$dueAt").apply()
                            if (correct) {
                                done = done + "${word.hanzi}|${word.pinyin}"
                                prefs.edit().putStringSet("completed_lessons", done).apply()
                                val count = if (prefs.getString("daily_date", "") == today) prefs.getInt("daily_count", 0) else 0
                                dailyCount = count + 1
                                val yesterday = java.time.LocalDate.now().minusDays(1).toString()
                                val previousDay = prefs.getString("last_learning_day", "")
                                val currentStreak = prefs.getInt("learning_streak", 0)
                                val nextStreak = when (previousDay) {
                                    today -> currentStreak.coerceAtLeast(1)
                                    yesterday -> currentStreak + 1
                                    else -> 1
                                }
                                val xp = prefs.getInt("learning_xp", 0) + 10
                                prefs.edit()
                                    .putString("daily_date", today)
                                    .putInt("daily_count", dailyCount)
                                    .putString("last_learning_day", today)
                                    .putInt("learning_streak", nextStreak)
                                    .putInt("learning_xp", xp)
                                    .apply()
                            }
                            index = pos + 1
                            prefs.edit().putInt("lesson_index", index).apply()
                            selected = ""; typed = ""; feedback = null; correct = false; reviewRefresh++
                        }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (correct) Lacquer else Muted)) {
                            Text(if (correct) "Дальше · 继续 →" else "Следующее слово")
                        }
                    }
                }
                Text("Повторение по интервалам: 1, 3, 7, 14 и 30 дней. Ошибка возвращает слово в короткий цикл повторения через 10 минут.", color = Muted, fontSize = 11.sp)
            }
        }
        Text("HSK 1–6 · офлайн-словарь · локальный прогресс", Modifier.fillMaxWidth(), color = Muted, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun ModePill(title: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp), color = if (selected) Lacquer.copy(alpha = .95f) else Color.White.copy(alpha = .58f),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Gold.copy(alpha = .8f) else Color.White.copy(alpha = .9f)),
        shadowElevation = if (selected) 4.dp else 0.dp) {
        Text(title, Modifier.fillMaxWidth().padding(vertical = 13.dp, horizontal = 8.dp), color = if (selected) Paper else Lacquer, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun GlassLessonCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(280)), shape = RoundedCornerShape(27.dp), color = Color.White.copy(alpha = .76f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .95f)), shadowElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(19.dp), verticalArrangement = Arrangement.spacedBy(11.dp), content = content)
    }
}

private data class DictionaryEntry(
    val hanzi: String,
    val pinyin: String,
    val russian: List<String>,
    val english: List<String>,
    val level: Int
)

@Volatile
private var cachedDictionary: List<DictionaryEntry>? = null

private fun readDictionary(context: Context): List<DictionaryEntry> {
    cachedDictionary?.let { return it }
    return synchronized(DictionaryEntry::class.java) {
        cachedDictionary?.let { return@synchronized it }
        readDictionaryUncached(context).also { cachedDictionary = it }
    }
}

private fun readDictionaryUncached(context: Context): List<DictionaryEntry> {
    val raw = context.assets.open("hsk.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
    val array = org.json.JSONArray(raw)
    return buildList(array.length()) {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val translations = item.optJSONObject("translations")
            fun readTerms(key: String): List<String> {
                val value = translations?.opt(key) ?: return emptyList()
                return when (value) {
                    is org.json.JSONArray -> (0 until value.length()).mapNotNull { i ->
                        value.optString(i).takeIf { it.isNotBlank() }
                    }
                    is String -> listOf(value).filter { it.isNotBlank() }
                    else -> emptyList()
                }
            }
            val hanzi = item.optString("hanzi").trim()
            if (hanzi.isBlank()) continue
            add(
                DictionaryEntry(
                    hanzi = hanzi,
                    pinyin = item.optString("pinyin").trim(),
                    russian = readTerms("rus"),
                    english = readTerms("eng"),
                    level = item.optInt("level", 0)
                )
            )
        }
    }
}

private data class DictionarySearchItem(
    val entry: DictionaryEntry,
    val hanziKey: String,
    val pinyinKey: String,
    val russianKey: String,
    val englishKey: String
)

private fun normalizeDictionaryQuery(value: String): String {
    return Normalizer.normalize(value.lowercase().replace('ü', 'v'), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace("u:", "v")
        .replace(Regex("[\\s'-]+"), "")
}

@Composable
private fun SearchScreen(context: Context, query: String, onQueryChange: (String) -> Unit) {
    var entries by remember { mutableStateOf<List<DictionaryEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }

    LaunchedEffect(context) {
        val loaded = withContext(Dispatchers.IO) { runCatching { readDictionary(context) } }
        entries = loaded.getOrDefault(emptyList())
        loadError = loaded.isFailure
        loading = false
    }

    var debouncedQuery by remember { mutableStateOf("") }
    LaunchedEffect(query) {
        kotlinx.coroutines.delay(140)
        debouncedQuery = query
    }
    val normalizedQuery = remember(debouncedQuery) { normalizeDictionaryQuery(debouncedQuery.trim()) }
    // Build normalized keys once, then search in the background so typing stays responsive.
    val searchIndex = remember(entries) {
        entries.map { entry ->
            DictionarySearchItem(
                entry = entry,
                hanziKey = normalizeDictionaryQuery(entry.hanzi),
                pinyinKey = normalizeDictionaryQuery(entry.pinyin),
                russianKey = normalizeDictionaryQuery(entry.russian.joinToString(" ")),
                englishKey = normalizeDictionaryQuery(entry.english.joinToString(" "))
            )
        }
    }
    var results by remember { mutableStateOf<List<DictionaryEntry>>(emptyList()) }
    LaunchedEffect(searchIndex, normalizedQuery, debouncedQuery) {
        if (normalizedQuery.isBlank()) {
            results = emptyList()
        } else {
            val rawQuery = debouncedQuery.trim()
            results = withContext(Dispatchers.Default) {
                searchIndex.asSequence()
                    .filter { item ->
                        item.hanziKey.contains(normalizedQuery) ||
                            item.pinyinKey.contains(normalizedQuery) ||
                            item.russianKey.contains(normalizedQuery) ||
                            item.englishKey.contains(normalizedQuery)
                    }
                    .sortedWith(
                        compareBy<DictionarySearchItem> {
                            when {
                                it.hanziKey == normalizedQuery -> 0
                                it.pinyinKey == normalizedQuery -> 1
                                it.entry.hanzi.startsWith(rawQuery) -> 2
                                else -> 3
                            }
                        }.thenBy { it.entry.hanzi.length }
                    )
                    .take(100)
                    .map { it.entry }
                    .toList()
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Словарь", color = Ink, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
                Text("Ищи иероглифы и значения", color = Muted, fontSize = 13.sp)
            }
            DragonMascot(60)
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Lacquer) },
            placeholder = { Text("你好 · nǐ hǎo · привет") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp)
        )

        when {
            loading -> Row(
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(color = Lacquer, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Загружаем словарь…", color = Muted, fontSize = 13.sp)
            }
            loadError -> Surface(shape = RoundedCornerShape(20.dp), color = CardWhite) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Не удалось открыть словарь", color = Lacquer, fontWeight = FontWeight.Bold)
                    Text("Проверь, что сборка содержит словарь HSK. Пересобери APK через GitHub Actions с подключением к интернету.", color = Muted, fontSize = 13.sp)
                }
            }
            query.isBlank() -> Surface(shape = RoundedCornerShape(22.dp), color = CardWhite, shadowElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Китайско-русский словарь HSK", color = Lacquer, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("В базе — слова всех 6 уровней HSK с пиньинем и переводом на русский.", color = Ink, fontSize = 14.sp)
                    Text("Попробуй: 爱, 学习, 你好, ài или xuexi.", color = Muted, fontSize = 13.sp)
                    Text("${entries.size} слов доступно офлайн", color = Lacquer, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Источник данных: LiudmilaLV/json_hsk · см. лицензию источника.", color = Muted, fontSize = 10.sp)
                }
            }
            results.isEmpty() -> Surface(shape = RoundedCornerShape(20.dp), color = CardWhite) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ничего не найдено", color = Lacquer, fontWeight = FontWeight.Bold)
                    Text("Попробуй другой иероглиф, пиньинь без тонов или перевод.", color = Muted, fontSize = 13.sp)
                }
            }
            else -> {
                Text("Найдено: ${results.size}${if (results.size == 100) "+" else ""}", color = Muted, fontSize = 12.sp)
                results.forEach { entry ->
                    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = CardWhite, shadowElevation = 2.dp) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(entry.hanzi, color = Lacquer, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(entry.pinyin.ifBlank { "Пиньинь не указан" }, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    if (entry.level > 0) Text("HSK ${entry.level}", color = Muted, fontSize = 10.sp)
                                }
                            }
                            if (entry.russian.isNotEmpty()) Text(entry.russian.joinToString(" · "), color = Ink, fontSize = 14.sp)
                            if (entry.english.isNotEmpty()) Text(entry.english.take(3).joinToString(" · "), color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        Text("Словарь работает без интернета после установки. Это полный набор слов HSK в источнике, а не все существующие китайские слова.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun AccountsScreen(email: String, onLogout: () -> Unit) {
    val displayName = email.substringBefore("@").replace(".", " ").replace("_", " ")
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Аккаунт", Modifier.weight(1f), color = Ink, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            DragonMascot(62)
        }
        Surface(shape = RoundedCornerShape(30.dp), color = CardWhite, shadowElevation = 4.dp) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                Box(Modifier.size(76.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFFFE6D8), Color(0xFFD7A9A0)))), contentAlignment = Alignment.Center) {
                    Text(displayName.take(1).uppercase().ifBlank { "你" }, color = DeepRed, fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(displayName.ifBlank { "Ученик TalkChinese" }, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(email, color = Muted, fontSize = 12.sp)
                    Text("Участник TalkChinese", color = Lacquer, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text("АККАУНТ", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Surface(shape = RoundedCornerShape(22.dp), color = CardWhite) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SettingRow(Icons.Default.Person, "Профиль", "Вход через Supabase")
                HorizontalDivider(Modifier.padding(vertical = 13.dp), color = SoftRose)
                SettingRow(Icons.Default.CheckCircle, "Защита аккаунта", "Сессия проверяется сервером")
            }
        }
        Surface(shape = RoundedCornerShape(22.dp), color = CardWhite) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Учебный профиль", color = Lacquer, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Прогресс уроков хранится на этом устройстве. Не удаляй данные приложения, если хочешь сохранить локальную статистику.", color = Muted, fontSize = 13.sp)
            }
        }
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) {
            Text("Выйти из аккаунта", color = Lacquer, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SettingsScreen(context: Context, email: String, onLogout: () -> Unit) {
    val prefs = remember(context) { context.getSharedPreferences("talkchinese_ui_settings", Context.MODE_PRIVATE) }
    var soundEnabled by remember { mutableStateOf(prefs.getBoolean("sound", true)) }
    var dailyReminder by remember { mutableStateOf(prefs.getBoolean("daily_reminder", false)) }
    var showPinyin by remember { mutableStateOf(prefs.getBoolean("show_pinyin", true)) }
    var haptics by remember { mutableStateOf(prefs.getBoolean("haptics", true)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Настройки", color = Ink, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                Text("Персонализируй обучение", color = Muted, fontSize = 13.sp)
            }
            DragonMascot(62)
        }
        Text("ОБУЧЕНИЕ", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Surface(shape = RoundedCornerShape(24.dp), color = CardWhite, shadowElevation = 2.dp) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                PreferenceRow("Показывать пиньинь", "Подсказка под иероглифами", showPinyin) { showPinyin = it; prefs.edit().putBoolean("show_pinyin", it).apply() }
                HorizontalDivider(color = SoftRose)
                PreferenceRow("Звуки", "Звуковые сигналы приложения", soundEnabled) { soundEnabled = it; prefs.edit().putBoolean("sound", it).apply() }
                HorizontalDivider(color = SoftRose)
                PreferenceRow("Тактильный отклик", "Вибрация при взаимодействии", haptics) { haptics = it; prefs.edit().putBoolean("haptics", it).apply() }
                HorizontalDivider(color = SoftRose)
                PreferenceRow("Ежедневные напоминания", "Настройка сохраняется на устройстве", dailyReminder) { dailyReminder = it; prefs.edit().putBoolean("daily_reminder", it).apply() }
            }
        }
        Text("АККАУНТ И БЕЗОПАСНОСТЬ", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Surface(shape = RoundedCornerShape(24.dp), color = CardWhite, shadowElevation = 2.dp) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                SettingRow(Icons.Default.Person, "Аккаунт", email)
                HorizontalDivider(color = SoftRose)
                SettingRow(Icons.Default.Lock, "Авторизация", "Supabase Auth")
                HorizontalDivider(color = SoftRose)
                SettingRow(Icons.Default.CheckCircle, "Проверка сессии", "При запуске приложения")
            }
        }
        Surface(shape = RoundedCornerShape(24.dp), color = CardWhite) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("О TalkChinese", color = Lacquer, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Версия 0.2 · Китайский каждый день", color = Ink, fontSize = 14.sp)
                Text("Настройки сохраняются на этом устройстве. Напоминания пока являются переключателем предпочтения, а не системным уведомлением.", color = Muted, fontSize = 12.sp)
            }
        }
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) { Text("Выйти из аккаунта", color = Lacquer) }
    }
}


@Composable
private fun PreferenceRow(title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Muted, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Paper, checkedTrackColor = Lacquer))
    }
}

@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(SoftRose), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Lacquer)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(detail, color = Muted, fontSize = 12.sp)
        }
    }
}

private data class AuthResult(
    val success: Boolean,
    val message: String,
    val accessToken: String? = null,
    val email: String? = null
)

private fun supabaseAuth(email: String, password: String, login: Boolean): AuthResult {
    val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    val endpoint = if (login) "/auth/v1/token?grant_type=password" else "/auth/v1/signup"
    val connection = (URL(base + endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 15000
        readTimeout = 15000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
    }
    try {
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(JSONObject().put("email", email).put("password", password).toString())
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        val json = runCatching { JSONObject(body) }.getOrNull()
        if (code !in 200..299) {
            val detail = json?.optString("msg")?.takeIf { it.isNotBlank() }
                ?: json?.optString("message")?.takeIf { it.isNotBlank() }
                ?: json?.optString("error_description")?.takeIf { it.isNotBlank() }
                ?: json?.optString("error")?.takeIf { it.isNotBlank() }
                ?: "HTTP " + code
            return AuthResult(false, "Ошибка авторизации: " + detail)
        }

        val token = json?.optString("access_token").orEmpty()
        val returnedEmail = json?.optJSONObject("user")?.optString("email")
            ?.takeIf { it.isNotBlank() } ?: email

        if (login) {
            if (token.isBlank()) return AuthResult(false, "Вход не завершён: сервер не вернул токен сессии.")
            val verifiedEmail = validateSession(token)
            if (verifiedEmail.isNullOrBlank()) {
                return AuthResult(false, "Сессию не удалось подтвердить. Попробуй войти ещё раз.")
            }
            return AuthResult(true, "Вход выполнен.", token, verifiedEmail)
        }

        if (token.isBlank()) {
            return AuthResult(
                false,
                "Проверь почту: Supabase запросил подтверждение адреса. После подтверждения войди в аккаунт."
            )
        }
        val verifiedEmail = validateSession(token)
        if (verifiedEmail.isNullOrBlank()) {
            return AuthResult(false, "Регистрация получена, но сессию не удалось проверить. Попробуй войти.")
        }
        return AuthResult(true, "Аккаунт создан.", token, verifiedEmail)
    } finally {
        connection.disconnect()
    }
}

private fun validateSession(accessToken: String): String? {
    val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    val connection = (URL(base + "/auth/v1/user").openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 12000
        readTimeout = 12000
        setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
        setRequestProperty("Authorization", "Bearer " + accessToken)
    }
    try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) return null
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        return json.optString("email").takeIf { it.isNotBlank() }
    } finally {
        connection.disconnect()
    }
}
