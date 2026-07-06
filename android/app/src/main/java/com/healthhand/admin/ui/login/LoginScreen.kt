package com.healthhand.admin.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.healthhand.admin.BuildConfig
import com.healthhand.admin.R
import com.healthhand.admin.data.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokenStore = remember { ApiClient.tokenStore() }

    var token by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf(BuildConfig.BASE_URL) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // If a token is already stored, skip straight in.
    LaunchedEffect(Unit) {
        if (!tokenStore.getToken().isNullOrBlank()) {
            onLoggedIn()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text(stringResource(R.string.login_token)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxSize(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text(stringResource(R.string.login_base_url)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxSize(),
        )
        Spacer(Modifier.height(16.dp))

        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
        } else {
            Button(
                onClick = {
                    if (token.isBlank()) return@Button
                    scope.launch {
                        loading = true
                        error = null
                        try {
                            // Save the token + url first so the auth interceptor uses it.
                            ApiClient.rebuild(baseUrl)
                            tokenStore.saveToken(token.trim())
                            tokenStore.saveBaseUrl(ApiClient.baseUrl)
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    ApiClient.api().getEventsSummary(days = 1)
                                }
                            }
                            val resp = result.getOrNull()
                            if (resp?.ok == true) {
                                onLoggedIn()
                            } else {
                                // 403 already triggered force-logout flow; show message.
                                tokenStore.clearToken()
                                error = context.getString(R.string.login_error_invalid)
                            }
                        } catch (e: Exception) {
                            tokenStore.clearToken()
                            error = context.getString(R.string.login_error_generic, e.message ?: "?")
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                Text(stringResource(R.string.login_button))
            }
        }

        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}