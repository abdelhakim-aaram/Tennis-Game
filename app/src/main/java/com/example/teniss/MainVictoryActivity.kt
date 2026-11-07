
package com.example.teniss

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainVictoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_victory)

        val tvWinner = findViewById<TextView>(R.id.tvWinner)
        val tvFinalScore = findViewById<TextView>(R.id.tvFinalScore)
        val tvSetHistory = findViewById<TextView>(R.id.tvSetHistory)

        val btnReplay = findViewById<MaterialButton>(R.id.btnReplay)
        val btnHome = findViewById<MaterialButton>(R.id.btnHome)

        val winnerName = intent.getStringExtra("WINNER_NAME")
            ?: "Joueur"

        val setsP1 = intent.getIntExtra("SETS_P1", 0)
        val setsP2 = intent.getIntExtra("SETS_P2", 0)

        val history = intent.getStringArrayListExtra("SET_HISTORY")
            ?: arrayListOf()

        tvWinner.text = winnerName

        tvFinalScore.text = "$setsP1  -  $setsP2"

        tvSetHistory.text = if (history.isNotEmpty()) {
            "Score de la manche : ${history.joinToString("  •  ")}"
        } else {
            "Match terminé"
        }

        btnReplay.setOnClickListener {
            val replayIntent = Intent(this, MainActivity::class.java)
            replayIntent.flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK

            startActivity(replayIntent)
            finish()
        }

        btnHome.setOnClickListener {
            finishAffinity()
        }
    }
}
