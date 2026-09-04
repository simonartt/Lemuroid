package com.swordfish.lemuroid.app.mobile.feature.game

import androidx.compose.runtime.Composable
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.GameMenuActivity
import com.swordfish.lemuroid.app.shared.game.BaseGameActivity
import com.swordfish.lemuroid.app.shared.game.BaseGameScreenViewModel

class GameActivity : BaseGameActivity() {
    @Composable
    override fun GameScreen(viewModel: BaseGameScreenViewModel) {
        MobileGameScreen(viewModel)
    }

    // v1.20.9 (bug3/bug4): while an NDS game runs, the MANUAL layout mode now LOCKS the activity
    // orientation to itself (portrait layout = portrait phone). Rotating the phone changes
    // nothing at all — no anchor reflow, no picture jump — so "gravity keeps switching the
    // layout" is gone by construction, and the per-screen enable switch can't be masked by a
    // rotated anchor. TV keeps sensor-driven behavior (override stays false there).
    override fun shouldFollowLayoutOrientation(): Boolean = true

    override fun getDialogClass() = GameMenuActivity::class.java
}
