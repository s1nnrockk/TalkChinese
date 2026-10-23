package com.sinrok.talkchinese

import android.os.Bundle
import android.content.Context
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import java.net.URL
import java.io.OutputStreamWriter
import java.util.concurrent.Executors

private val Lacquer = Color(0xFF8E1725)
private val DeepRed = Color(0xFF350B13)
private val Gold = Color(0xFFD8B56D)
private val Paper = Color(0xFFFFF8EB)
private val Ink = Color(0xFF21191A)

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
    var message by remember { mutableStateOf("") }
    var signedInEmail by remember {
        mutableStateOf(context.getSharedPreferences("talkchinese", Context.MODE_PRIVATE).getString("email", null) ?: "")
    }
    val configured = BuildConfig.SUPABASE_URL.startsWith("https://") &&
        BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    MaterialTheme(colorScheme = lightColorScheme(
        primary = Lacquer,
        onPrimary = Paper,
        secondary = Gold,
        background = Paper,
        surface = Color.White,
        onSurface = Ink
    )) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(DeepRed, Lacquer, Color(0xFF641522)))
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.size(88.dp).background(
                        Brush.linearGradient(listOf(Color(0xFFFFE8A3), Gold, Color(0xFF9B6B27))),
                        RoundedCornerShape(28.dp)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("汉", fontSize = 48.sp, fontWeight = FontWeight.Black, color = DeepRed)
                }
                Spacer(Modifier.height(18.dp))
                Text("TalkChinese", fontSize = 31.sp, fontWeight = FontWeight.Bold, color = Paper)
                Text("中文 · Открой мир китайского", color = Gold, fontSize = 14.sp)
                Spacer(Modifier.height(28.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = Paper,
                    shadowElevation = 12.dp
                ) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (signedInEmail.isNotBlank()) {
                            Text("欢迎回来！", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Lacquer)
                            Text("Сессия сохранена для $signedInEmail", color = Ink)
                            Text("Авторизация выполнена. Учебный экран добавим следующим этапом.", color = Ink)
                            Button(
                                onClick = {
                                    context.getSharedPreferences("talkchinese", Context.MODE_PRIVATE).edit().clear().apply()
                                    signedInEmail = ""
                                    message = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Lacquer)
                            ) { Text("Выйти из аккаунта") }
                        } else {
                            Text(
                                if (isLogin) "С возвращением" else "Создай свой аккаунт",
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Bold,
                                color = Lacquer
                            )
                            Text(
                                if (isLogin) "Войди и продолжай изучать китайский."
                                else "Начни путь к свободному китайскому.",
                                color = Color(0xFF6E5A55)
                            )
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it.trim() },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Электронная почта") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                shape = RoundedCornerShape(14.dp)
                            )
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Пароль") },
                                singleLine = true,
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Показать пароль")
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                shape = RoundedCornerShape(14.dp)
                            )
                            if (!isLogin) {
                                OutlinedTextField(
                                    value = confirmPassword,
                                    onValueChange = { confirmPassword = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Повтори пароль") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    shape = RoundedCornerShape(14.dp)
                                )
                            }

                            if (message.isNotBlank()) {
                                Text(message, color = if (message.startsWith("Успех") || message.startsWith("Аккаунт создан.")) Color(0xFF246B43) else Lacquer, fontSize = 13.sp)
                            }
                            if (!configured) {
                                Text(
                                    "Для подключения регистрации добавь SUPABASE_URL и SUPABASE_ANON_KEY в GitHub Secrets или gradle.properties.",
                                    color = Color(0xFF8A5A20), fontSize = 12.sp
                                )
                            }

                            Button(
                                onClick = {
                                    when {
                                        email.isBlank() || !email.contains("@") -> message = "Введи корректную электронную почту."
                                        password.length < 6 -> message = "Пароль должен содержать минимум 6 символов."
                                        !isLogin && password != confirmPassword -> message = "Пароли не совпадают."
                                        !configured -> message = "Supabase ещё не настроен. Добавь URL и публичный ключ проекта."
                                        else -> {
                                            busy = true
                                            message = ""
                                            val loginMode = isLogin
                                            val mail = email
                                            val pass = password
                                            Thread {
                                                val result = runCatching { supabaseAuth(mail, pass, loginMode) }
                                                (context as Activity).runOnUiThread {
                                                    busy = false
                                                    result.onSuccess { authResult ->
                                                        if (authResult.first) {
                                                            context.getSharedPreferences("talkchinese", Context.MODE_PRIVATE)
                                                                .edit().putString("email", mail)
                                                                .putString("access_token", authResult.second).apply()
                                                            signedInEmail = mail
                                                            message = "Успех: вход выполнен."
                                                        } else {
                                                            message = authResult.second
                                                        }
                                                    }.onFailure { e ->
                                                        message = e.message ?: "Не удалось связаться с Supabase."
                                                    }
                                                }
                                            }.start()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                enabled = !busy,
                                colors = ButtonDefaults.buttonColors(containerColor = Lacquer),
                                shape = RoundedCornerShape(15.dp)
                            ) {
                                if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Paper, strokeWidth = 2.dp)
                                else Text(if (isLogin) "Войти" else "Создать аккаунт", fontWeight = FontWeight.Bold)
                            }

                            TextButton(
                                onClick = {
                                    isLogin = !isLogin
                                    message = ""
                                },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text(
                                    if (isLogin) "Нет аккаунта? Зарегистрироваться" else "Уже есть аккаунт? Войти",
                                    color = Lacquer
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("学无止境 · Учёба без границ", color = Gold, fontSize = 13.sp)
            }
        }
    }
}

private fun supabaseAuth(email: String, password: String, login: Boolean): Pair<Boolean, String> {
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
                ?: "HTTP $code"
            return false to "Ошибка авторизации: $detail"
        }
        val token = json?.optString("access_token").orEmpty()
        if (login) {
            if (token.isBlank()) return false to "Supabase не вернул токен сессии."
            return true to token
        }
        if (token.isNotBlank()) return true to token
        return false to "Аккаунт создан. Проверь почту и подтверди адрес, затем войди."
    } finally {
        connection.disconnect()
    }
}
