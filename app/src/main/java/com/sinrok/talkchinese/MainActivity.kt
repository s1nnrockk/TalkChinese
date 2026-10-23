package com.sinrok.talkchinese

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
    HOME("Главная"), LEARN("Уроки"), SEARCH("Поиск"), ACCOUNTS("Аккаунты"), SETTINGS("Настройки")
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
            when (selectedTab) {
                MainTab.HOME -> HomeScreen(email, onStartLearning = { onTabSelected(MainTab.LEARN) })
                MainTab.LEARN -> LessonsScreen(context)
                MainTab.SEARCH -> SearchScreen(context, searchQuery, onSearchQueryChange)
                MainTab.ACCOUNTS -> AccountsScreen(email, onLogout)
                MainTab.SETTINGS -> SettingsScreen(email, onLogout)
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
                            Color(0xFFFDFBFA).copy(alpha = 0.96f),
                            Color(0xFFF6EAE6).copy(alpha = 0.98f),
                            Color(0xFFFFFDF8)
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
            BrandMark(54)
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
        Text("ТВОЙ ПЛАН", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), "今日", "Сегодня", "Учись в своём темпе")
            StatCard(Modifier.weight(1f), "汉字", "Новые слова", "Пополняй словарь")
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
    LaunchedEffect(context) {
        val result = runCatching { withContext(Dispatchers.IO) { readDictionary(context) } }
        result.onSuccess { entries = it }.onFailure { failed = true }
        loading = false
    }
    val lessons = remember(entries, level) { if (level == 0) entries else entries.filter { it.level == level } }
    val pos = if (lessons.isEmpty()) 0 else Math.floorMod(index, lessons.size)
    val word = lessons.getOrNull(pos)
    val type = pos % 4
    val meaning = word?.russian?.firstOrNull()?.takeIf { it.isNotBlank() } ?: word?.english?.firstOrNull().orEmpty()
    val answer = when (type) { 0 -> meaning; 1 -> word?.pinyin.orEmpty(); 2 -> word?.hanzi.orEmpty(); else -> word?.pinyin.orEmpty() }
    val options = remember(lessons, pos, type) {
        val w = lessons.getOrNull(pos)
        if (w == null || type == 3) emptyList() else {
            fun value(e: DictionaryEntry) = when(type) {
                0 -> e.russian.firstOrNull()?.takeIf { it.isNotBlank() } ?: e.english.firstOrNull().orEmpty()
                1 -> e.pinyin
                else -> e.hanzi
            }
            val right = value(w)
            (listOf(right) + lessons.asSequence().filter { it.hanzi != w.hanzi }.map(::value).filter { it.isNotBlank() && it != right }.distinct().take(3))
                .distinct().sortedBy { it.hashCode() xor w.hanzi.hashCode() }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Text("Уроки и практика", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Объяснения, задания и сохранение прогресса.", color = Muted, fontSize = 13.sp)
        Surface(shape = RoundedCornerShape(24.dp), color = DeepRed) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ТВОЯ ПРОГРАММА", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("${entries.size} уроков по словам", color = Paper, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                Text("Четыре формата практики: перевод, пиньинь, распознавание иероглифа и ввод ответа.", color = Paper, fontSize = 13.sp)
                LinearProgressIndicator(progress = { if (entries.isEmpty()) 0f else (done.size.toFloat()/entries.size).coerceIn(0f,1f) }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = Gold, trackColor = Paper.copy(alpha = .2f))
                Text("Пройдено: ${done.size} из ${entries.size}", color = Gold, fontSize = 12.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (0..6).forEach { n ->
                FilterChip(selected = level == n, onClick = {
                    level = n; index = 0; prefs.edit().putInt("lesson_index", 0).apply()
                    selected = ""; typed = ""; feedback = null; correct = false
                }, label = { Text(if (n == 0) "Все" else "HSK $n", fontSize = 10.sp) }, modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Lacquer, selectedLabelColor = Paper))
            }
        }
        when {
            loading -> CircularProgressIndicator(color = Lacquer)
            failed || entries.isEmpty() -> Text("Словарь уроков не загрузился. Проверь сборку APK через GitHub Actions.", color = Lacquer)
            word != null -> {
                Surface(shape = RoundedCornerShape(25.dp), color = CardWhite, shadowElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("УРОК ${pos+1}", color = Lacquer, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                            Spacer(Modifier.weight(1f)); Text("HSK ${word.level}", color = Muted, fontSize = 11.sp)
                        }
                        Text(word.hanzi, color = Lacquer, fontSize = 46.sp, fontWeight = FontWeight.ExtraBold)
                        Text(word.pinyin, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        Text(meaning.ifBlank { "Перевод не указан" }, color = Lacquer, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        if (word.russian.size > 1) Text("Другие значения: ${word.russian.drop(1).take(4).joinToString(" · ")}", color = Muted, fontSize = 12.sp)
                        HorizontalDivider(color = SoftRose)
                        Text("ОБЪЯСНЕНИЕ", color = Lacquer, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                        Text("Запомни связку: ${word.hanzi} → ${word.pinyin} → ${meaning}. Пиньинь показывает произношение латинскими буквами, а надстрочные знаки отмечают тоны — они важны для правильного китайского произношения.", color = Ink, fontSize = 14.sp)
                        Text("Учись в три шага: узнай иероглиф, вспомни произношение, затем назови значение без подсказки. Упражнение ниже проверит одну из этих связей.", color = Muted, fontSize = 12.sp)
                    }
                }
                Surface(shape = RoundedCornerShape(25.dp), color = CardWhite, shadowElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("ПРОВЕРЬ СЕБЯ", color = Lacquer, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        Text(when(type) {
                            0 -> "Что означает «${word.hanzi}»?"
                            1 -> "Выбери пиньинь для «${word.hanzi}»."
                            2 -> "Какой иероглиф означает «${meaning}»?"
                            else -> "Введи пиньинь слова «${word.hanzi}»."
                        }, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        if (type == 3) {
                            OutlinedTextField(value = typed, onValueChange = { typed = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Пиньинь") }, placeholder = { Text("Например: ni hao") }, shape = RoundedCornerShape(15.dp))
                            Button(onClick = {
                                correct = normalizeLearningAnswer(typed) == normalizeLearningAnswer(answer)
                                feedback = if (correct) "Верно! Ты вспомнил произношение без вариантов." else "Не совпало. Проверь пиньинь в карточке и попробуй ещё раз."
                            }, enabled = typed.isNotBlank() && feedback == null, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Lacquer)) { Text("Проверить ответ") }
                        } else options.forEach { option ->
                            OutlinedButton(onClick = {
                                selected = option; correct = option == answer
                                feedback = if (correct) "Верно! ${word.hanzi} — ${meaning}; пиньинь: ${word.pinyin}." else "Пока нет. Правильный ответ: ${answer}. Перечитай объяснение и попробуй ещё раз."
                            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected == option && correct) Color(0xFFE2F2E5) else if (selected == option) SoftRose else CardWhite, contentColor = Ink)) {
                                Text(option, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                            }
                        }
                        feedback?.let { note ->
                            Surface(shape = RoundedCornerShape(15.dp), color = if (correct) Color(0xFFE5F3E7) else SoftRose) { Text(note, Modifier.fillMaxWidth().padding(13.dp), color = Ink, fontSize = 13.sp) }
                            Button(onClick = {
                                if (correct) {
                                    val id = "${word.hanzi}|${word.pinyin}"
                                    val updated = done + id
                                    done = updated
                                    prefs.edit().putStringSet("completed_lessons", updated).apply()
                                    index = pos + 1; prefs.edit().putInt("lesson_index", index).apply()
                                }
                                selected = ""; typed = ""; feedback = null; correct = false
                            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = if (correct) Lacquer else Muted)) {
                                Text(if (correct) "Следующий урок →" else "Попробовать ещё раз")
                            }
                        }
                    }
                }
                Text("Ошибки не увеличивают прогресс. Повтори слово, пока не вспомнишь его самостоятельно.", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

private data class DictionaryEntry(
    val hanzi: String,
    val pinyin: String,
    val russian: List<String>,
    val english: List<String>,
    val level: Int
)

private fun readDictionary(context: Context): List<DictionaryEntry> {
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

    val normalizedQuery = remember(query) { normalizeDictionaryQuery(query.trim()) }
    val results = remember(entries, normalizedQuery) {
        if (normalizedQuery.isBlank()) emptyList() else entries.asSequence()
            .filter { entry ->
                normalizeDictionaryQuery(entry.hanzi).contains(normalizedQuery) ||
                    normalizeDictionaryQuery(entry.pinyin).contains(normalizedQuery) ||
                    entry.russian.any { normalizeDictionaryQuery(it).contains(normalizedQuery) } ||
                    entry.english.any { normalizeDictionaryQuery(it).contains(normalizedQuery) }
            }
            .sortedWith(
                compareBy<DictionaryEntry> {
                    when {
                        normalizeDictionaryQuery(it.hanzi) == normalizedQuery -> 0
                        normalizeDictionaryQuery(it.pinyin) == normalizedQuery -> 1
                        it.hanzi.startsWith(query.trim()) -> 2
                        else -> 3
                    }
                }.thenBy { it.hanzi.length }
            )
            .take(100)
            .toList()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Словарь", color = Ink, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
        Text("Ищи по иероглифам, пиньиню или русскому переводу.", color = Muted, fontSize = 14.sp)
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("Аккаунт", color = Ink, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
        Surface(shape = RoundedCornerShape(26.dp), color = CardWhite, shadowElevation = 4.dp) {
            Column(
                Modifier.fillMaxWidth().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BrandMark(70)
                Text(email, color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row(
                    Modifier.clip(CircleShape).background(Color(0xFFE5F2E6)).padding(horizontal = 13.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2F7545), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Сессия проверена сервером", color = Color(0xFF2F7545), fontSize = 12.sp)
                }
                Text("Данные аккаунта подтверждаются Supabase. При недействительной сессии приложение попросит войти снова.", color = Muted, fontSize = 13.sp)
            }
        }
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Text("Выйти из аккаунта", color = Lacquer)
        }
    }
}

@Composable
private fun SettingsScreen(email: String, onLogout: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("Настройки", color = Ink, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
        Surface(shape = RoundedCornerShape(23.dp), color = CardWhite, shadowElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Аккаунт и безопасность", color = Lacquer, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                SettingRow(Icons.Default.Person, "Аккаунт", email)
                HorizontalDivider(color = SoftRose)
                SettingRow(Icons.Default.Lock, "Авторизация", "Supabase Auth")
                HorizontalDivider(color = SoftRose)
                SettingRow(Icons.Default.CheckCircle, "Проверка сессии", "При запуске приложения")
            }
        }
        Surface(shape = RoundedCornerShape(23.dp), color = CardWhite, shadowElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("О TalkChinese", color = Lacquer, fontWeight = FontWeight.Bold)
                Text("Версия 0.1.0", color = Ink, fontSize = 14.sp)
                Text("Изучай китайский шаг за шагом.", color = Muted, fontSize = 13.sp)
            }
        }
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Text("Выйти", color = Lacquer)
        }
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
