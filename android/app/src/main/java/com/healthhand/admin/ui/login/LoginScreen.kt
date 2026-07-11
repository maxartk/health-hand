package com.healthhand.admin.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.healthhand.admin.BuildConfig
import com.healthhand.admin.R
import com.healthhand.admin.data.ApiClient
import com.healthhand.admin.ui.theme.EmptyStateCard
import com.healthhand.admin.ui.theme.MetricCard
import com.healthhand.admin.ui.theme.PremiumBackdrop
import com.healthhand.admin.ui.theme.ScreenHeader
import com.healthhand.admin.ui.theme.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokenStore = remember { ApiClient.tokenStore() }

    var token by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf(tokenStore.getBaseUrl() ?: BuildConfig.BASE_URL) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!tokenStore.getToken().isNullOrBlank()) {
            onLoggedIn()
        }
    }

    PremiumBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ScreenHeader(
                    title = stringResource(R.string.login_title),
                    subtitle = stringResource(R.string.login_subtitle),
                )

                SectionCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricCard(label = "Доступ", value = "Secure", caption = "API token", modifier = Modifier.weight(1f), accent = MaterialTheme.colorScheme.primary)
                        MetricCard(label = "Режим", value = "Native", caption = "Compose UI", modifier = Modifier.weight(1f), accent = MaterialTheme.colorScheme.tertiary)
                    }
                }

                SectionCard {
                    Text(
                        text = "Увійдіть у внутрішню панель керування Health Hand.",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Токен зберігається локально на пристрої, а додаток підхоплює ваш API endpoint перед перевіркою доступу.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text(stringResource(R.string.login_token)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text(stringResource(R.string.login_base_url)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                    )

                    Spacer(Modifier.height(16.dp))

                    if (loading) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        Button(
                            onClick = {
                                if (token.isBlank()) return@Button
                                scope.launch {
                                    loading = true
                                    error = null
                                    try {
                                        ApiClient.rebuild(baseUrl)
                                        tokenStore.saveToken(token.trim())
                                        tokenStore.saveBaseUrl(ApiClient.baseUrl)
                                        val resp = withContext(Dispatchers.IO) {
                                            ApiClient.api().getEventsSummary(days = 1)
                                        }
                                        if (resp.ok) {
                                            onLoggedIn()
                                        } else {
                                            tokenStore.clearToken()
                                            error = "Невірний токен (${resp.error ?: "403"})"
                                        }
                                    } catch (e: Exception) {
                                        tokenStore.clearToken()
                                        error = "Помилка: ${e.message ?: e.javaClass.simpleName}"
                                    } finally {
                                        loading = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.login_button))
                        }
                    }

                    error?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                SectionCard {
                    Text("Що це дає", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilterChip(selected = true, onClick = {}, label = { Text("Швидкий доступ") })
                        FilterChip(selected = true, onClick = {}, label = { Text("Без пароля в UI") })
                        FilterChip(selected = true, onClick = {}, label = { Text("Власний base URL") })
                    }
                }
            }
        }
    }
}
