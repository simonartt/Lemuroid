package com.swordfish.touchinput.radial.layouts

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.swordfish.touchinput.controller.R
import com.swordfish.touchinput.radial.controls.LemuroidControlButton
import com.swordfish.touchinput.radial.controls.LemuroidControlCross
import com.swordfish.touchinput.radial.controls.LemuroidControlFaceButtons
import com.swordfish.touchinput.radial.layouts.shared.ComposeTouchLayouts
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonL
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonMenu
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonMenuPlaceholder
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonR
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonSelect
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonStart
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager.TouchButtonId
import com.swordfish.touchinput.radial.ui.LemuroidButtonForeground
import gg.padkit.PadKitScope
import gg.padkit.ids.Id
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/**
 * Edit-mode hit target for ONE button group (v1.20.11). TweakableButton reports the group's
 * VISUAL circle here (root px, committed position); the central gesture router in
 * MobileGameScreen hit-tests against these circles and drives [live] during a drag.
 *
 * Why a central router: per-button pointerInput cannot give correct hit areas — the hit area of
 * a layout node is its bounds (the radial SLOT square), which (a) includes the empty corners
 * around the circular visual, and (b) stays at the slot even after the button was free-dragged
 * elsewhere. It also had to live on a moving/scaled node or a wrapper, and a wrapper swallowed
 * the secondary dials' radialPosition ParentDataModifier (v1.20.10 regression: dials collapsed
 * to the default angle while editing). With the router, the button nodes stay plain children of
 * the radial layout and only REPORT their geometry.
 */
class TouchEditTarget(val id: TouchButtonId) {
    /** Live drag delta (px) written by the router; rendered by TweakableButton until the commit lands. */
    val live = mutableStateOf(Offset.Zero)

    // Committed VISUAL circle (root px) — slot center + legacy offset + freeX/freeY. No live.
    var centerX = 0f
    var centerY = 0f
    var radius = 0f

    // Geometry needed by the router to convert a clamped visual position back into freeX/freeY.
    var slotCx = 0f
    var slotCy = 0f
    var legacyX = 0f
    var legacyY = 0f
    var freeX = 0f
    var freeY = 0f

    var dragging = false
    var dragStartFreeX = 0f
    var dragStartFreeY = 0f
}

/**
 * Registry of all [TouchEditTarget]s currently on screen (v1.20.11). Provided via
 * [LocalTouchEditRegistry] only while the touch-controls editor is open.
 */
class TouchEditRegistry {
    val targets = mutableListOf<TouchEditTarget>()

    /** Circle hit test; the smallest containing circle wins (small buttons sit on top). */
    fun findTarget(x: Float, y: Float): TouchEditTarget? =
        targets
            .filter { t ->
                val dx = x - t.centerX
                val dy = y - t.centerY
                dx * dx + dy * dy <= t.radius * t.radius
            }
            .minByOrNull { it.radius }
}

/** Provided (non-null) only while the touch-controls editor is open (v1.20.11). */
val LocalTouchEditRegistry = compositionLocalOf<TouchEditRegistry?>(defaultFactory = { null })

/**
 * Currently SELECTED button group while the touch-controls editor is open (v1.20.12). Since the
 * blue selection ring was removed (v1.20.10) and the pad is fully neutralized while editing, the
 * selected button gets NO press highlight — this drives the alpha highlight instead: the selected
 * button stays bright (90%) while every unselected button dims to 50%, so it's obvious which button
 * the size slider / reset is acting on.
 */
val LocalEditingSelection = compositionLocalOf<TouchButtonId?>(defaultFactory = { null })

/** Wrapper that applies per-button offset & scale, and reports geometry to the edit router */
@Composable
fun PadKitScope.TweakableButton(
    id: TouchButtonId,
    settings: TouchControllerSettingsManager.Settings,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val bs = settings.getButtonSettings(id)
    val registry = LocalTouchEditRegistry.current
    val isEditing = registry != null
    val isHidden = settings.isButtonHidden(id)
    // v1.20.12: alpha highlight for the selected button group (replaces the removed blue ring).
    val isSelected = isEditing && LocalEditingSelection.current == id

    // Skip rendering if hidden (but still show in edit mode)
    if (isHidden && !isEditing) return

    val target = remember(id) { TouchEditTarget(id) }
    if (registry != null) {
        DisposableEffect(registry, id) {
            registry.targets.add(target)
            onDispose { registry.targets.remove(target) }
        }
    }

    // Slot rect = the button node's LAYOUT position (own graphicsLayer translation is NOT
    // included in these coordinates — same semantics PadKit's controls rely on), so it stays the
    // untranslated anchor even while the visual floats elsewhere via freeX/freeY/live.
    val slotRect = remember { mutableStateOf<Rect?>(null) }

    // LIVE drag delta (v1.20.8): the button follows the finger via a local pixel offset; the
    // Settings store is written ONCE on finger-up (per-event VM writes = full JSON encode + SP
    // write + pad rebuild at ~120Hz → the ghosting the user saw). Absorbed = the commit has
    // landed in bs.freeX/freeY → the live delta hands the position over seamlessly (no
    // snap-back frame, no double-move frame). v1.20.11: the delta lives on the registry target;
    // the central router writes it (see TouchEditTarget).
    // Alpha (v1.20.12): in edit mode the SELECTED button stays bright (0.9) while every other
    // button dims to 0.5 so the active edit target is obvious; hidden buttons stay extra dim so
    // they can still be told apart from normal dimmed ones. Outside edit mode: 1.0 (0.4 when hidden).
    val targetAlpha =
        when {
            !isEditing -> if (isHidden) 0.4f else 1f
            isSelected -> 0.9f
            isHidden -> 0.4f
            else -> 0.5f
        }

    // v1.20.13 (drag-follow fix): in edit mode render through the graphicsLayer BLOCK form so the
    // layer's draw pass reads target.live directly — a per-frame draw subscription (like an
    // animation) that follows the finger. v1.20.11 moved the live delta from the button's own
    // remember state to the central router's registry target; in practice the button stopped
    // following during the drag and only snapped to place after the commit landed — because the
    // cross-composition subscribe on target.live wasn't triggering recomposition. Reading it in
    // the draw scope invalidates the LAYER (not the composition), so it tracks every frame.
    // Outside edit mode the button never drags, so keep the cheap conditional layer (no live).
    val baseMod =
        if (isEditing) {
            Modifier.graphicsLayer {
                val absorbedInDraw =
                    bs.freeX != target.dragStartFreeX || bs.freeY != target.dragStartFreeY
                val effLx = if (absorbedInDraw) 0f else target.live.value.x
                val effLy = if (absorbedInDraw) 0f else target.live.value.y
                translationX = TouchControllerSettingsManager.MAX_MARGINS * bs.offsetX + bs.freeX + effLx
                translationY = TouchControllerSettingsManager.MAX_MARGINS * bs.offsetY + bs.freeY + effLy
                scaleX = bs.scale
                scaleY = bs.scale
                alpha = targetAlpha
            }
        } else {
            val hasLayer =
                bs.scale != 1.0f || bs.offsetX != 0f || bs.offsetY != 0f ||
                    bs.freeX != 0f || bs.freeY != 0f
            if (hasLayer) {
                val ox = TouchControllerSettingsManager.MAX_MARGINS * bs.offsetX + bs.freeX
                val oy = TouchControllerSettingsManager.MAX_MARGINS * bs.offsetY + bs.freeY
                Modifier.graphicsLayer(
                    translationX = ox,
                    translationY = oy,
                    scaleX = bs.scale,
                    scaleY = bs.scale,
                    alpha = targetAlpha,
                )
            } else if (targetAlpha < 1f) {
                Modifier.graphicsLayer(alpha = targetAlpha)
            } else {
                Modifier
            }
        }

    val trackMod =
        if (isEditing) {
            Modifier.onGloballyPositioned { slotRect.value = it.boundsInRoot() }
        } else {
            Modifier
        }

    // Report the committed visual circle to the router after every recomposition. NOTE: no
    // wrapper node here — TweakableButton emits the content node directly so the secondary
    // dials' radialPosition ParentDataModifier keeps reaching LayoutRadial (a wrapper broke it
    // in v1.20.10: every secondary dial collapsed to the default angle while editing).
    SideEffect {
        val rect = slotRect.value
        if (rect != null) {
            val half = minOf(rect.width, rect.height) / 2f
            val legacyX = TouchControllerSettingsManager.MAX_MARGINS * bs.offsetX
            val legacyY = TouchControllerSettingsManager.MAX_MARGINS * bs.offsetY
            val slotCx = rect.left + rect.width / 2f
            val slotCy = rect.top + rect.height / 2f
            target.centerX = slotCx + legacyX + bs.freeX
            target.centerY = slotCy + legacyY + bs.freeY
            target.radius = half * bs.scale
            target.slotCx = slotCx
            target.slotCy = slotCy
            target.legacyX = legacyX
            target.legacyY = legacyY
            target.freeX = bs.freeX
            target.freeY = bs.freeY
        }
    }

    content(modifier.then(trackMod).then(baseMod))
}

@Composable
fun PadKitScope.MelonDSLeft(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    BaseLayoutLeft(
        settings = settings,
        modifier = modifier,
        primaryDial = {
            TweakableButton(id = TouchButtonId.DPAD, settings = settings) { mod ->
                LemuroidControlCross(modifier = mod, id = Id.DiscreteDirection(ComposeTouchLayouts.MOTION_SOURCE_DPAD))
            }
        },
        secondaryDials = {
            TweakableButton(id = TouchButtonId.L, settings = settings) { mod -> SecondaryButtonL(modifier = mod) }
            TweakableButton(id = TouchButtonId.SELECT, settings = settings) { mod -> SecondaryButtonSelect(position = 2, modifier = mod) }
            // v1.20.9 bug2 fix: the placeholder used to be wrapped in TweakableButton(MENU), so
            // selecting 全局菜单 drew a SECOND blue ring around this invisible slot — which sits at
            // -120° exactly where the mic button is — and the size slider scaled both rings. The
            // placeholder is a geometry reservation, never an editable button: call it bare.
            SecondaryButtonMenuPlaceholder(settings)
            TweakableButton(id = TouchButtonId.L2, settings = settings) { mod ->
                LemuroidControlButton(
                    modifier = mod.then(Modifier.radialPosition(-120f)),
                    id = Id.Key(KeyEvent.KEYCODE_BUTTON_L2),
                    icon = R.drawable.button_mic,
                )
            }
            TweakableButton(id = TouchButtonId.THUMBL, settings = settings) { mod ->
                LemuroidControlButton(
                    modifier = mod.then(Modifier.radialPosition(-60f)),
                    id = Id.Key(KeyEvent.KEYCODE_BUTTON_THUMBL),
                    icon = R.drawable.button_close_screen,
                )
            }
        },
    )
}

@Composable
fun PadKitScope.MelonDSRight(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    BaseLayoutRight(
        settings = settings,
        modifier = modifier,
        primaryDial = {
            TweakableButton(id = TouchButtonId.FACE, settings = settings) { mod ->
                LemuroidControlFaceButtons(
                    modifier = mod,
                    ids =
                        persistentListOf(
                            Id.Key(KeyEvent.KEYCODE_BUTTON_A),
                            Id.Key(KeyEvent.KEYCODE_BUTTON_B),
                            Id.Key(KeyEvent.KEYCODE_BUTTON_Y),
                            Id.Key(KeyEvent.KEYCODE_BUTTON_X),
                        ),
                    idsForegrounds =
                        persistentMapOf<Id.Key, @Composable (State<Boolean>) -> Unit>(
                            Id.Key(KeyEvent.KEYCODE_BUTTON_A) to { LemuroidButtonForeground(pressed = it, label = "A") },
                            Id.Key(KeyEvent.KEYCODE_BUTTON_B) to { LemuroidButtonForeground(pressed = it, label = "B") },
                            Id.Key(KeyEvent.KEYCODE_BUTTON_Y) to { LemuroidButtonForeground(pressed = it, label = "Y") },
                            Id.Key(KeyEvent.KEYCODE_BUTTON_X) to { LemuroidButtonForeground(pressed = it, label = "X") },
                        ),
                )
            }
        },
        secondaryDials = {
            TweakableButton(id = TouchButtonId.R, settings = settings) { mod -> SecondaryButtonR(modifier = mod) }
            TweakableButton(id = TouchButtonId.START, settings = settings) { mod -> SecondaryButtonStart(position = 2, modifier = mod) }
            TweakableButton(id = TouchButtonId.MENU, settings = settings) { mod -> SecondaryButtonMenu(settings, modifier = mod) }
            TweakableButton(id = TouchButtonId.THUMBR, settings = settings) { mod ->
                LemuroidControlButton(
                    modifier = mod.then(Modifier.radialPosition(-120f)),
                    id = Id.Key(KeyEvent.KEYCODE_BUTTON_THUMBR),
                    icon = R.drawable.button_swap_screens,
                )
            }
        },
    )
}
