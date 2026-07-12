package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.remote.GoogleCredentialClient
import com.example.myapplication.ui.LoginUiState
import com.example.myapplication.ui.EmailAvailabilityUiState
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    state: LoginUiState,
    emailAvailabilityState: EmailAvailabilityUiState,
    onLogin: (String, String) -> Unit,
    onSignup: (String, String, String, String) -> Unit,
    onCheckEmail: (String) -> Unit,
    onEmailChanged: () -> Unit,
    onGoogleLogin: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirmation by remember { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var signupMode by rememberSaveable { mutableStateOf(false) }
    var showEmailLogin by rememberSaveable { mutableStateOf(false) }
    var googleError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val googleClient = remember(context) { GoogleCredentialClient(context) }
    val loading = state == LoginUiState.Loading
    val normalizedEmail = email.trim().lowercase()
    val validEmail = android.util.Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()
    val validPassword = password.length in 8..72 && password.any(Char::isLetter) && password.any(Char::isDigit)
    val emailAvailable = emailAvailabilityState is EmailAvailabilityUiState.Available &&
        emailAvailabilityState.email == normalizedEmail
    val passwordsMatch = passwordConfirmation.isNotEmpty() && password == passwordConfirmation
    val canSubmit = validEmail && validPassword && !loading && if (signupMode) {
        displayName.trim().length >= 2 && emailAvailable && passwordsMatch
    } else true

    fun submit() {
        if (canSubmit) {
            if (signupMode) onSignup(email, password, passwordConfirmation, displayName)
            else onLogin(email, password)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).background(
                    MaterialTheme.colorScheme.primary,
                    CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text("♪", color = MaterialTheme.colorScheme.onPrimary)
            }
            Text(
                "MELODY BUBBLE",
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(28.dp))
        Text("취향이 닿는 순간,", style = MaterialTheme.typography.displaySmall)
        Text(
            "새로운 음악 친구를 만나보세요.",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Google 계정으로 가입하면 별도의 비밀번호 없이 바로 시작할 수 있어요.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                googleError = null
                scope.launch {
                    runCatching { googleClient.getIdToken(context) }
                        .onSuccess(onGoogleLogin)
                        .onFailure { error ->
                            googleError = when {
                                error is IllegalStateException -> "Google 로그인이 아직 설정되지 않았습니다."
                                error::class.simpleName?.contains("Cancellation") == true -> null
                                else -> "Google 계정을 불러오지 못했습니다. 잠시 후 다시 시도해주세요."
                            }
                        }
                }
            },
            enabled = !loading && GoogleCredentialClient.isConfigured,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.background,
            ),
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Text("G", fontWeight = FontWeight.Bold)
                Text("Google로 가입 또는 로그인", modifier = Modifier.padding(start = 12.dp))
            }
        }

        if (!GoogleCredentialClient.isConfigured) {
            Spacer(Modifier.height(8.dp))
            Text(
                "개발 설정에 Google Web Client ID가 필요합니다.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        googleError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
            Text(
                "또는",
                modifier = Modifier.padding(horizontal = 14.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
        }

        if (!showEmailLogin) {
            OutlinedButton(
                onClick = { showEmailLogin = true },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("이메일로 로그인")
            }
        } else {
            if (signupMode) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading,
                    singleLine = true,
                    label = { Text("프로필 이름") },
                )
                Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; onEmailChanged() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
                singleLine = true,
                label = { Text("이메일") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
            )
            if (signupMode) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onCheckEmail(normalizedEmail) },
                    enabled = validEmail && !loading && emailAvailabilityState != EmailAvailabilityUiState.Loading,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    if (emailAvailabilityState == EmailAvailabilityUiState.Loading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else Text("이메일 중복 확인")
                }
                val availabilityMessage = when (emailAvailabilityState) {
                    is EmailAvailabilityUiState.Available -> "사용할 수 있는 이메일입니다."
                    is EmailAvailabilityUiState.Unavailable -> "이미 가입된 이메일입니다."
                    is EmailAvailabilityUiState.Error -> emailAvailabilityState.message
                    else -> null
                }
                availabilityMessage?.let {
                    Text(
                        it,
                        color = if (emailAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
                singleLine = true,
                label = { Text("비밀번호") },
                supportingText = if (signupMode) ({ Text("영문과 숫자를 포함한 8자 이상") }) else null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (signupMode) ImeAction.Next else ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            if (signupMode) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = passwordConfirmation,
                    onValueChange = { passwordConfirmation = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading,
                    singleLine = true,
                    isError = passwordConfirmation.isNotEmpty() && !passwordsMatch,
                    label = { Text("비밀번호 확인") },
                    supportingText = if (passwordConfirmation.isNotEmpty() && !passwordsMatch) {
                        ({ Text("비밀번호가 일치하지 않습니다.") })
                    } else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
            }
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = ::submit,
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (signupMode) "회원가입" else "로그인")
            }
            androidx.compose.material3.TextButton(
                onClick = { signupMode = !signupMode },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text(if (signupMode) "이미 계정이 있어요 · 로그인" else "계정이 없어요 · 회원가입") }
        }

        if (state is LoginUiState.Error) {
            Spacer(Modifier.height(12.dp))
            Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "계속하면 서비스 이용약관과 개인정보 처리방침에 동의하는 것으로 간주됩니다.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
