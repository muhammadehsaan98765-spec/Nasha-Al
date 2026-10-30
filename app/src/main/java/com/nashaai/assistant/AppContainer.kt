package com.nashaai.assistant

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.nashaai.assistant.accessibility.NashaAccessibilityService
import com.nashaai.assistant.data.actions.ActionLogRepository
import com.nashaai.assistant.data.chat.ChatRepository
import com.nashaai.assistant.data.network.NetworkMonitor
import com.nashaai.assistant.data.search.SearchProvider
import com.nashaai.assistant.data.settings.SecureKeyStore
import com.nashaai.assistant.data.settings.SettingsRepository
import com.nashaai.assistant.data.settings.ShortcutRepository
import com.nashaai.assistant.data.workspace.WorkspaceRepository
import com.nashaai.assistant.domain.actions.ActionEngine
import com.nashaai.assistant.domain.actions.ActionExecutionStatus
import com.nashaai.assistant.domain.actions.ActionExecutor
import com.nashaai.assistant.domain.actions.ActionParams
import com.nashaai.assistant.domain.actions.ActionRequest
import com.nashaai.assistant.domain.actions.ActionResult
import com.nashaai.assistant.domain.actions.ActionType
import com.nashaai.assistant.domain.actions.CommandParser
import com.nashaai.assistant.util.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Process-wide dependency container.
 *
 * Block 11 changes (vs Block 10):
 *   - Adds [ttsManager] (constructed on Main thread — NashaApplication.
 *     onCreate runs on Main; TextToSpeech requires a Looper thread).
 *   - Passes [ttsManager] into [AndroidActionExecutor] so
 *     ActionType.READ_ALOUD actually speaks instead of returning
 *     NOT_IMPLEMENTED.
 *
 * Everything else (repositories, providers, action engine, settings
 * snapshot binding, application scope) is unchanged from Block 7/10.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    // ---- Data layer --------------------------------------------------

    val settingsRepository: SettingsRepository = SettingsRepository(appContext)

    val secureKeyStore: SecureKeyStore = SecureKeyStore(appContext)

    val shortcutRepository: ShortcutRepository = ShortcutRepository(appContext)

    val networkMonitor: NetworkMonitor = NetworkMonitor(appContext)

    val chatRepository: ChatRepository = ChatRepository(
        context = appContext,
        settingsRepository = settingsRepository,
        secureKeyStore = secureKeyStore
    )

    val workspaceRepository: WorkspaceRepository = WorkspaceRepository(appContext)

    val actionLogRepository: ActionLogRepository = ActionLogRepository(appContext)

    val searchProvider: SearchProvider = SearchProvider()

    // ---- TTS ---------------------------------------------------------
    // Constructed eagerly on Main. If the device has no TTS engine,
    // TtsManager tracks that internally (hasFailed) and speak() returns
    // false; no crash.

    val ttsManager: TtsManager = TtsManager(appContext)

    // ---- Domain layer ------------------------------------------------

    val commandParser: CommandParser = CommandParser()

    val actionExecutor: ActionExecutor = AndroidActionExecutor(
        appContext = appContext,
        workspaceRepository = workspaceRepository,
        ttsManager = ttsManager
    )

    val actionEngine: ActionEngine = ActionEngine(
        executor = actionExecutor,
        logRepository = actionLogRepository
    )

    // ---- Application scope -------------------------------------------

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ---- Init --------------------------------------------------------

    init {
        // Block 7 refinement — bind the executor's settings source once.
        SettingsSnapshotFlow.bind(settingsRepository.settings)
    }

    fun launchBackground(block: suspend CoroutineScope.() -> Unit) {
        appScope.launch { block() }
    }
}

// ---------------------------------------------------------------------
// AndroidActionExecutor — private implementation detail of AppContainer.
// Not exposed as a public API; screens must go through ActionEngine.
// ---------------------------------------------------------------------

private class AndroidActionExecutor(
    private val appContext: Context,
    private val workspaceRepository: WorkspaceRepository,
    private val ttsManager: TtsManager
) : ActionExecutor {

    override suspend fun execute(request: ActionRequest): ActionResult {
        return when (request.type) {

            // ---- Accessibility delegation ------------------------------
            ActionType.ACCESSIBILITY_HOME,
            ActionType.ACCESSIBILITY_BACK,
            ActionType.ACCESSIBILITY_RECENTS -> {
                val svc = NashaAccessibilityService.instance
                    ?: return failure(request, "accessibility_disabled")
                svc.execute(request)
            }

            // ---- Browser / search --------------------------------------
            ActionType.OPEN_URL -> {
                val url = (request.params as? ActionParams.OpenUrl)?.url
                    ?: return failure(request, "missing_url")
                launchView(url, request)
            }

            ActionType.WEB_SEARCH -> {
                val query = (request.params as? ActionParams.WebSearch)?.query
                    ?: return failure(request, "missing_query")
                val settings = settingsCache.first()
                val url = SearchProvider.buildBrowserUrl(
                    engine = settings.searchEngine,
                    query = query,
                    customTemplate = settings.customSearchUrl
                )
                launchView(url, request)
            }

            // ---- System settings ---------------------------------------
            ActionType.OPEN_SETTINGS ->
                launchIntent(Intent(android.provider.Settings.ACTION_SETTINGS), request)

            ActionType.OPEN_NOTIFICATION_SETTINGS -> {
                val intent = Intent(
                    android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                ).putExtra(
                    android.provider.Settings.EXTRA_APP_PACKAGE,
                    appContext.packageName
                )
                launchIntent(intent, request)
            }

            // ---- Open another app --------------------------------------
            ActionType.OPEN_APP -> {
                val pkg = (request.params as? ActionParams.OpenApp)?.packageName
                    ?: return failure(request, "missing_package")
                val launch = try {
                    appContext.packageManager.getLaunchIntentForPackage(pkg)
                } catch (_: Exception) {
                    null
                } ?: return failure(request, "app_not_installed")
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                launchIntent(launch, request)
            }

            // ---- Clipboard ---------------------------------------------
            ActionType.COPY_TEXT -> {
                val text = (request.params as? ActionParams.CopyText)?.text
                    ?: return failure(request, "missing_text")
                return try {
                    val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE)
                        as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Nasha AI", text))
                    success(request, "copied")
                } catch (_: Exception) {
                    failure(request, "clipboard_failed")
                }
            }

            // ---- Notes → stored in workspace as language="note" --------
            ActionType.CREATE_NOTE -> {
                val note = request.params as? ActionParams.CreateNote
                    ?: return failure(request, "missing_note")
                return try {
                    workspaceRepository.save(
                        name = note.title,
                        language = "note",
                        code = note.body
                    ).fold(
                        onSuccess = { success(request, "note_saved") },
                        onFailure = { failure(request, "note_save_failed") }
                    )
                } catch (_: Exception) {
                    failure(request, "note_save_failed")
                }
            }

            // ---- Share / prepare message (never auto-sent) -------------
            ActionType.SHARE_TEXT -> {
                val text = (request.params as? ActionParams.ShareText)?.text
                    ?: return failure(request, "missing_text")
                launchShare(text, request)
            }

            ActionType.PREPARE_MESSAGE -> {
                val text = (request.params as? ActionParams.PrepareMessage)?.text
                    ?: return failure(request, "missing_text")
                launchShare(text, request)
            }

            // ---- Coding workspace --------------------------------------
            ActionType.SAVE_CODE -> {
                val payload = request.params as? ActionParams.SaveCode
                    ?: return failure(request, "missing_code")
                return try {
                    workspaceRepository.save(
                        name = payload.suggestedName ?: "code-${System.currentTimeMillis()}",
                        language = payload.language,
                        code = payload.code
                    ).fold(
                        onSuccess = { success(request, "code_saved") },
                        onFailure = { failure(request, "code_save_failed") }
                    )
                } catch (_: Exception) {
                    failure(request, "code_save_failed")
                }
            }

            // ---- Read aloud (BLOCK 11 — now actually speaks) -----------
            ActionType.READ_ALOUD -> {
                val text = (request.params as? ActionParams.ReadAloud)?.text
                    ?: return failure(request, "missing_text")
                return try {
                    val language = try {
                        settingsCache.first().language
                    } catch (_: Exception) {
                        "en"
                    }
                    if (!ttsManager.isReady()) {
                        return ActionResult(
                            type = request.type,
                            risk = request.risk,
                            status = ActionExecutionStatus.FAILURE,
                            detail = "tts_unavailable"
                        )
                    }
                    val queued = ttsManager.speak(text, language)
                    if (queued) {
                        success(request, "tts_queued")
                    } else {
                        ActionResult(
                            type = request.type,
                            risk = request.risk,
                            status = ActionExecutionStatus.FAILURE,
                            detail = "tts_failed"
                        )
                    }
                } catch (_: Exception) {
                    ActionResult(
                        type = request.type,
                        risk = request.risk,
                        status = ActionExecutionStatus.FAILURE,
                        detail = "tts_failed"
                    )
                }
            }

            // ---- Policy fallback ---------------------------------------
            ActionType.UNKNOWN -> ActionResult(
                type = request.type,
                risk = request.risk,
                status = ActionExecutionStatus.BLOCKED,
                detail = "unknown_action"
            )
        }
    }

    // ---- Intent helpers -------------------------------------------------

    private fun launchView(url: String, request: ActionRequest): ActionResult {
        val parsed = try { Uri.parse(url) } catch (_: Exception) { null }
            ?: return failure(request, "invalid_url")
        val intent = Intent(Intent.ACTION_VIEW, parsed)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launchIntent(intent, request)
    }

    private fun launchShare(text: String, request: ActionRequest): ActionResult {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launchIntent(chooser, request)
    }

    private fun launchIntent(intent: Intent, request: ActionRequest): ActionResult {
        return try {
            appContext.startActivity(intent)
            success(request, "intent_dispatched")
        } catch (_: android.content.ActivityNotFoundException) {
            failure(request, "no_handler")
        } catch (_: Exception) {
            failure(request, "intent_failed")
        }
    }

    // Settings flow — bound by AppContainer.init via SettingsSnapshotFlow.
    private val settingsCache: kotlinx.coroutines.flow.Flow<com.nashaai.assistant.data.settings.AppSettings>
        get() = SettingsSnapshotFlow.flow
}

/**
 * Holds the real settings flow so AndroidActionExecutor can read it
 * without taking a constructor dependency on SettingsRepository.
 *
 * AppContainer.init calls [bind] exactly once during construction. Before
 * binding, the default flowOf(AppSettings()) keeps the executor safe in
 * tests that construct it in isolation.
 */
private object SettingsSnapshotFlow {
    @Volatile
    var flow: kotlinx.coroutines.flow.Flow<com.nashaai.assistant.data.settings.AppSettings> =
        kotlinx.coroutines.flow.flowOf(com.nashaai.assistant.data.settings.AppSettings())

    fun bind(
        source: kotlinx.coroutines.flow.Flow<com.nashaai.assistant.data.settings.AppSettings>
    ) {
        flow = source
    }
}

// ActionResult helpers used by AndroidActionExecutor.
private fun success(request: ActionRequest, detail: String): ActionResult =
    ActionResult(
        type = request.type,
        risk = request.risk,
        status = ActionExecutionStatus.SUCCESS,
        detail = detail
    )

private fun failure(request: ActionRequest, detail: String): ActionResult =
    ActionResult(
        type = request.type,
        risk = request.risk,
        status = ActionExecutionStatus.FAILURE,
        detail = detail
    )
