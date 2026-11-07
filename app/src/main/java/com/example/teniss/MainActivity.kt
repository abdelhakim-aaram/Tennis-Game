
package com.example.teniss

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import kotlin.math.ceil
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private enum class Phase {
        COUNTDOWN, RALLY, BREAK, FINISHED
    }

    private enum class MatchResult {
        POINT, GAME, SET, MATCH
    }

    private companion object {
        const val TURN_MS = 3000L
        const val COUNTDOWN_FROM = 5
        const val BREAK_MS = 1500L

        const val SETS_TO_WIN = 1
    }

    private lateinit var content: View
    private lateinit var panels: Array<View>
    private lateinit var balls: Array<ImageView>
    private lateinit var tvPoints: Array<TextView>
    private lateinit var tvGames: Array<TextView>
    private lateinit var tvSets: Array<TextView>
    private lateinit var btnPlay: Array<MaterialButton>

    private lateinit var tvStatus: TextView
    private lateinit var tvTimer: TextView
    private lateinit var turnProgress: ProgressBar
    private lateinit var tvHistory: TextView
    private lateinit var btnReset: MaterialButton
    private lateinit var countdownOverlay: FrameLayout
    private lateinit var tvCountdown: TextView
    private lateinit var tvCountdownInfo: TextView

    // Données du match
    private val points = IntArray(2)
    private val games = IntArray(2)
    private val sets = IntArray(2)

    private val setHistory = mutableListOf<String>()

    private var isTieBreak = false
    private var matchWinner = -1
    private var server = 0
    private var tieBreakFirstServer = 0

    private val names = arrayOf("Joueur 1", "Joueur 2")

    // Timer et animations
    private val handler = Handler(Looper.getMainLooper())

    private var phase = Phase.COUNTDOWN
    private var holder = 0
    private var turnDeadline = 0L
    private var countdownValue = COUNTDOWN_FROM

    private var ballAnimator: ObjectAnimator? = null
    private var tone: ToneGenerator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        applyInsets()

        try {
            tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        } catch (e: Exception) {
            tone = null
        }

        btnPlay[0].setOnClickListener {
            onPlayPressed(0, it)
        }

        btnPlay[1].setOnClickListener {
            onPlayPressed(1, it)
        }

        btnReset.setOnClickListener {
            newMatch()
        }

        resetState()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
        stopBallAnimation()
    }

    override fun onResume() {
        super.onResume()

        when (phase) {
            Phase.COUNTDOWN -> startCountdown()
            Phase.RALLY -> startTurn(holder)
            Phase.BREAK -> handler.postDelayed({
                startRally()
            }, BREAK_MS)

            Phase.FINISHED -> Unit
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopBallAnimation()

        tone?.release()
        tone = null

        super.onDestroy()
    }

    //   XML

    private fun bindViews() {
        content = findViewById(R.id.content)

        panels = arrayOf(
            findViewById(R.id.panelP1),
            findViewById(R.id.panelP2)
        )

        balls = arrayOf(
            findViewById(R.id.ballP1),
            findViewById(R.id.ballP2)
        )

        tvPoints = arrayOf(
            findViewById(R.id.pointsP1),
            findViewById(R.id.pointsP2)
        )

        tvGames = arrayOf(
            findViewById(R.id.gamesP1),
            findViewById(R.id.gamesP2)
        )

        tvSets = arrayOf(
            findViewById(R.id.setsP1),
            findViewById(R.id.setsP2)
        )

        btnPlay = arrayOf(
            findViewById(R.id.btnPlayP1),
            findViewById(R.id.btnPlayP2)
        )

        tvStatus = findViewById(R.id.tvStatus)
        tvTimer = findViewById(R.id.tvTimer)
        turnProgress = findViewById(R.id.turnProgress)
        tvHistory = findViewById(R.id.tvHistory)
        btnReset = findViewById(R.id.btnReset)

        countdownOverlay = findViewById(R.id.countdownOverlay)
        tvCountdown = findViewById(R.id.tvCountdown)
        tvCountdownInfo = findViewById(R.id.tvCountdownInfo)
    }

    private fun applyInsets() {
        val l = content.paddingLeft
        val t = content.paddingTop
        val r = content.paddingRight
        val b = content.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(content) { v, windowInsets ->
            val bars: Insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
            )

            v.setPadding(
                l + bars.left,
                t + bars.top,
                r + bars.right,
                b + bars.bottom
            )

            windowInsets
        }

        ViewCompat.requestApplyInsets(content)
    }

    // Rejouer

    private fun resetState() {
        handler.removeCallbacksAndMessages(null)
        stopBallAnimation()

        points.fill(0)
        games.fill(0)
        sets.fill(0)
        setHistory.clear()

        isTieBreak = false
        matchWinner = -1

        server = Random.nextInt(2)
        tieBreakFirstServer = server
        holder = server

        phase = Phase.COUNTDOWN

        refreshScore()
    }

    private fun newMatch() {
        resetState()
        startCountdown()
    }


    // Chrono


    private fun startCountdown() {
        handler.removeCallbacksAndMessages(null)
        stopBallAnimation()

        phase = Phase.COUNTDOWN
        countdownValue = COUNTDOWN_FROM

        countdownOverlay.visibility = View.VISIBLE

        tvCountdownInfo.text = "${names[server]} sert en premier"
        tvStatus.text = "Préparez-vous…"
        tvTimer.text = ""

        turnProgress.progress = turnProgress.max

        setActivePlayer(-1)

        countdownTick()
    }

    private fun countdownTick() {
        if (phase != Phase.COUNTDOWN) return

        if (countdownValue > 0) {
            tvCountdown.text = countdownValue.toString()

            beep(ToneGenerator.TONE_PROP_BEEP, 90)

            countdownValue--

            handler.postDelayed({
                countdownTick()
            }, 1000)

        } else {
            countdownOverlay.visibility = View.GONE

            beep(ToneGenerator.TONE_PROP_BEEP2, 250)

            startRally()
        }
    }

    // star

    private fun startRally() {
        if (matchWinner != -1) return

        phase = Phase.RALLY
        startTurn(server)
    }

    private fun startTurn(player: Int) {
        handler.removeCallbacksAndMessages(null)

        if (phase == Phase.FINISHED) return

        phase = Phase.RALLY
        holder = player

        turnDeadline = SystemClock.elapsedRealtime() + TURN_MS

        tvStatus.text = "${names[player]} : à toi de jouer !"

        setActivePlayer(player)

        handler.post(turnTick)
    }

    private val turnTick = object : Runnable {
        override fun run() {
            if (phase != Phase.RALLY) return

            val left = turnDeadline - SystemClock.elapsedRealtime()

            if (left <= 0L) {
                onTimeout()
                return
            }

            val seconds = ceil(left / 1000.0).toInt()

            tvTimer.text = seconds.toString()

            tvTimer.setTextColor(
                if (left <= 1000L) {
                    Color.parseColor("#FF5252")
                } else {
                    Color.WHITE
                }
            )

            turnProgress.progress =
                (left * turnProgress.max / TURN_MS).toInt()

            handler.postDelayed(this, 40)
        }
    }

    // Joueur Star play

    private fun onPlayPressed(player: Int, view: View) {
        if (phase != Phase.RALLY || player != holder) return

        view.performHapticFeedback(
            HapticFeedbackConstants.KEYBOARD_TAP
        )

        beep(ToneGenerator.TONE_PROP_ACK, 60)

        // paSSer la ball a l'autrec
        startTurn(1 - player)
    }

    // Fin time


    private fun onTimeout() {
        if (phase != Phase.RALLY) return

        val loser = holder
        val winner = 1 - loser

        phase = Phase.BREAK

        handler.removeCallbacksAndMessages(null)

        setActivePlayer(-1)

        tvTimer.text = "0"
        turnProgress.progress = 0

        val result = pointWon(winner)

        refreshScore()
        pulse(tvPoints[winner])

        val message = when (result) {
            MatchResult.POINT ->
                if (isDeuce) {
                    "Égalité"
                } else {
                    "Point pour ${names[winner]}"
                }

            MatchResult.GAME ->
                "Jeu ${names[winner]} !"

            MatchResult.SET ->
                "Manche ${names[winner]} !"

            MatchResult.MATCH ->
                "Match ${names[winner]} !"
        }

        tvStatus.text = "Temps écoulé ! $message"

        beep(ToneGenerator.TONE_PROP_PROMPT, 200)

        if (result == MatchResult.MATCH) {
            finishMatch(winner)
        } else {
            handler.postDelayed({
                startRally()
            }, BREAK_MS)
        }
    }

    // logique de jeux
    private fun pointWon(player: Int): MatchResult {
        check(matchWinner == -1) {
            "Match already finished"
        }

        require(player in 0..1)

        val opponent = 1 - player

        points[player]++

        // Tie-break
        if (isTieBreak) {

            if ((points[0] + points[1]) % 2 == 1) {
                server = 1 - server
            }

            if (
                points[player] >= 7 &&
                points[player] - points[opponent] >= 2
            ) {
                games[player]++
                return endSet(player)
            }

            return MatchResult.POINT
        }

        // ganier
        if (
            points[player] >= 4 &&
            points[player] - points[opponent] >= 2
        ) {
            games[player]++

            points.fill(0)

            server = 1 - server

            // ganier set
            if (
                games[player] >= 6 &&
                games[player] - games[opponent] >= 2
            ) {
                return endSet(player)
            }

            // start tie-break
            if (games[0] == 6 && games[1] == 6) {
                isTieBreak = true
                tieBreakFirstServer = server
            }

            return MatchResult.GAME
        }

        return MatchResult.POINT
    }

    private fun endSet(player: Int): MatchResult {
        sets[player]++

        setHistory.add("${games[0]}-${games[1]}")

        games.fill(0)
        points.fill(0)

        if (isTieBreak) {
            server = 1 - tieBreakFirstServer
        }

        isTieBreak = false

        if (sets[player] >= SETS_TO_WIN) {
            matchWinner = player
            return MatchResult.MATCH
        }

        return MatchResult.SET
    }

    // calcule et afficher les point

    private fun pointLabel(player: Int): String {
        val opponent = 1 - player

        if (isTieBreak) {
            return points[player].toString()
        }

        if (points[player] >= 3 && points[opponent] >= 3) {
            return if (points[player] > points[opponent]) {
                "AV"
            } else {
                "40"
            }
        }

        return when (points[player]) {
            0 -> "0"
            1 -> "15"
            2 -> "30"
            else -> "40"
        }
    }

    private val isDeuce: Boolean
        get() = !isTieBreak &&
                points[0] >= 3 &&
                points[0] == points[1]

    private fun refreshScore() {
        for (p in 0..1) {
            tvPoints[p].text = pointLabel(p)
            tvGames[p].text = games[p].toString()
            tvSets[p].text = sets[p].toString()
        }

        tvHistory.text = when {
            isTieBreak ->
                "TIE-BREAK" + historySuffix()

            setHistory.isEmpty() ->
                "Manche 1 • Premier à 1 manche"

            else ->
                "Manches : " + setHistory.joinToString("  •  ")
        }
    }

    private fun historySuffix(): String {
        return if (setHistory.isEmpty()) {
            ""
        } else {
            "  |  " + setHistory.joinToString("  •  ")
        }
    }


    //   passe à mainvectory

    private fun finishMatch(winner: Int) {
        phase = Phase.FINISHED

        setActivePlayer(-1)

        tvTimer.text = "🏆"
        tvStatus.text = "${names[winner]} remporte le match !"

        beep(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600)

        val intent = Intent(this, MainVictoryActivity::class.java)

        intent.putExtra("WINNER_NAME", names[winner])
        intent.putExtra("PLAYER1_NAME", names[0])
        intent.putExtra("PLAYER2_NAME", names[1])

        intent.putExtra("SETS_P1", sets[0])
        intent.putExtra("SETS_P2", sets[1])

        intent.putExtra("GAMES_P1", games[0])
        intent.putExtra("GAMES_P2", games[1])

        intent.putStringArrayListExtra(
            "SET_HISTORY",
            ArrayList(setHistory)
        )

        startActivity(intent)
        finish()
    }

    // activite player

    private fun setActivePlayer(player: Int) {
        for (p in 0..1) {
            val active = p == player

            panels[p].setBackgroundResource(
                if (active) {
                    R.drawable.panel_bg_active
                } else {
                    R.drawable.panel_bg
                }
            )

            btnPlay[p].isEnabled = active
        }

        stopBallAnimation()

        if (player in 0..1) {
            val anim = ObjectAnimator.ofFloat(
                balls[player],
                View.TRANSLATION_Y,
                0f,
                -10f * resources.displayMetrics.density
            )

            anim.duration = 320
            anim.repeatMode = ObjectAnimator.REVERSE
            anim.repeatCount = ObjectAnimator.INFINITE
            anim.interpolator = AccelerateDecelerateInterpolator()

            anim.start()

            ballAnimator = anim
        }

        val showOn = if (player in 0..1) {
            player
        } else {
            server
        }

        for (p in 0..1) {
            balls[p].visibility =
                if (p == showOn) View.VISIBLE else View.INVISIBLE
        }
    }

    private fun stopBallAnimation() {
        ballAnimator?.cancel()
        ballAnimator = null

        if (this::balls.isInitialized) {
            for (ball in balls) {
                ball.translationY = 0f
            }
        }
    }

    // Animation

    private fun pulse(view: View) {
        view.animate().cancel()

        view.scaleX = 1f
        view.scaleY = 1f

        view.animate()
            .scaleX(1.5f)
            .scaleY(1.5f)
            .setDuration(160)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .start()
            }
            .start()
    }

    // son


    private fun beep(toneType: Int, ms: Int) {
        try {
            tone?.startTone(toneType, ms)
        } catch (e: Exception) {
        }
    }
}
