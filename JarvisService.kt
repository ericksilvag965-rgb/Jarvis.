package com.jarvis.app

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.*
import android.speech.*
import android.speech.tts.*
import java.text.SimpleDateFormat
import java.util.*

class JarvisService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private var sr: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var speaking = false
    private var awake = 0L
    private val br = Locale("pt", "BR")
    private val wake = Regex("\\b(jarvis|jarbas|járvis|jarves|jarbis|charvis|garvis)\\b[,.!?:]?\\s*(.*)", RegexOption.IGNORE_CASE)
    private val jokes = listOf(
        "Existem dez tipos de pessoas: as que entendem binário e as que não entendem.",
        "Meu código funciona, mas não sei por quê. O outro não funciona, e também não sei por quê."
    )
    private val listenRun = Runnable { listen() }
    private fun now() = System.currentTimeMillis()

    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("jarvis", "Jarvis", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "jarvis").setContentTitle("Jarvis ativo")
            .setContentText("Diga “Jarvis” para falar comigo")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, n)
        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) {
                tts?.language = br; tts?.setPitch(0.6f); tts?.setSpeechRate(0.94f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(u: String?) {}
                    override fun onDone(u: String?) { speaking = false; if (awake > 0) awake = now() + 9000; again(400) }
                    override fun onError(u: String?) { speaking = false; again(400) }
                })
                again(0)
            }
        }
    }

    override fun onDestroy() {
        h.removeCallbacks(listenRun); sr?.destroy(); tts?.shutdown(); super.onDestroy()
    }

    private fun again(ms: Long) { h.removeCallbacks(listenRun); if (!speaking) h.postDelayed(listenRun, ms) }

    private fun listen() {
        if (speaking || !SpeechRecognizer.isRecognitionAvailable(this)) return
        sr?.destroy()
        sr = SpeechRecognizer.createSpeechRecognizer(this).also { r ->
            r.setRecognitionListener(object : RecognitionListener {
                override fun onResults(b: Bundle?) {
                    b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { handle(it) }
                    again(350)
                }
                override fun onError(e: Int) { again(350) }
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(b: Bundle?) {}
                override fun onEvent(t: Int, b: Bundle?) {}
            })
            r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR"))
        }
    }

    private fun say(t: String) {
        speaking = true; sr?.cancel()
        tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "j" + now())
    }

    private fun go(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { startActivity(i) } catch (e: Exception) { say("Não consegui abrir isso, senhor.") }
    }

    private fun handle(tx: String) {
        val m = wake.find(tx)
        if (m != null) {
            val rest = m.groupValues[2].trim()
            if (rest.isNotEmpty()) { awake = 0; run(rest) } else { awake = now() + 12000; say("Pois não, senhor?") }
            return
        }
        if (awake > now()) { awake = 0; run(tx) }
    }

    private fun run(raw: String) {
        val t = raw.lowercase(br).trim()
        val timer = Regex("(\\d+)\\s*(segundo|minuto|hora)").find(t)
        val open = Regex("abr(?:a|ir|e)\\s+(?:o |a )?(.+)").find(t)
        val search = Regex("(?:pesquis(?:e|ar|a)|busque|procure)\\s+(?:por |sobre )?(.+)").find(t)
        when {
            Regex("que horas|me diga a hora").containsMatchIn(t) ->
                say("São " + SimpleDateFormat("HH:mm", br).format(Date()) + ".")
            Regex("que dia|data de hoje").containsMatchIn(t) ->
                say("Hoje é " + SimpleDateFormat("EEEE, d 'de' MMMM", br).format(Date()) + ".")
            Regex("piada").containsMatchIn(t) -> say(jokes.random())
            Regex("desligar|encerrar|tchau").containsMatchIn(t) -> { say("Encerrando. Até logo, senhor."); h.postDelayed({ stopSelf() }, 2500) }
            timer != null -> {
                val n = timer.groupValues[1].toLong()
                val ms = n * when (timer.groupValues[2]) { "segundo" -> 1000L; "minuto" -> 60000L; else -> 3600000L }
                h.postDelayed({ say("Senhor, o tempo de $n ${timer.groupValues[2]} acabou.") }, ms)
                say("Timer de $n ${timer.groupValues[2]} iniciado.")
            }
            open != null -> {
                val name = open.groupValues[1]
                val pm = packageManager
                val app = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .firstOrNull { it.loadLabel(pm).toString().lowercase(br).contains(name) }
                val li = app?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
                if (li != null) { say("Abrindo $name."); go(li) } else say("Não encontrei $name no seu celular.")
            }
            search != null -> { say("Pesquisando."); go(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(search.groupValues[1])))) }
            else -> { say("Não tenho esse comando, então vou pesquisar."); go(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(t)))) }
        }
    }
}
