package com.swordfish.lemuroid.app.mobile.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.game.BaseGameScreenViewModel
import com.swordfish.lemuroid.app.shared.game.screenlayout.ScreenLayoutManager
import com.swordfish.lemuroid.app.shared.game.screenlayout.ScreenLayoutManager.ScreenId

/** Edge a screen can be aligned to, or CENTER for re-centering. */
enum class AlignEdge { TOP, BOTTOM, LEFT, RIGHT, CENTER }

/**
 * Floating "tool strip" for the NDS dual-screen layout editor.
 * A 4×3 grid of design-faithful tile drawables (nds_tile_r1c1 … r3c3) plus a zoom panel.
 *
 * Design source: docs/UI-元素文档.md (Figma NDS-Screen-Editor).
 * All tools operate on the currently selected screen (tap a dashed frame to pick it).
 */
@Composable
fun ScreenLayoutEditorToolbox(
    modifier: Modifier = Modifier,
    viewModel: BaseGameScreenViewModel,
    layoutState: ScreenLayoutManager.ScreenLayoutState,
    selectedScreen: ScreenLayoutManager.ScreenId,
    onScreenSelected: (ScreenLayoutManager.ScreenId) -> Unit,
    isLandscape: Boolean,
    viewPos: androidx.compose.ui.geometry.Rect,
    density: Float,
    onAlignToEdge: (ScreenLayoutManager.ScreenId, AlignEdge) -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xCC1C1C20),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // 4×3 tool grid (left) — icons are the design's own SVG tiles.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in 0 until 3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (col in 0 until 4) {
                            ToolGridButton(
                                row = row,
                                col = col,
                                viewModel = viewModel,
                                selectedScreen = selectedScreen,
                                onAlignToEdge = onAlignToEdge,
                            )
                        }
                    }
                }
            }
            // Zoom panel (right)
            ZoomPanel(
                isLandscape = isLandscape,
                viewModel = viewModel,
                selectedScreen = selectedScreen,
                layoutState = layoutState,
                viewPos = viewPos,
                density = density,
            )
        }
    }
}

/** A single 64dp tool tile in the 4×3 grid. Renders the design's own vector tile. */
@Composable
private fun ToolGridButton(
    row: Int,
    col: Int,
    viewModel: BaseGameScreenViewModel,
    selectedScreen: ScreenLayoutManager.ScreenId,
    onAlignToEdge: (ScreenLayoutManager.ScreenId, AlignEdge) -> Unit,
) {
    val cell = TOOL_GRID[row][col]

    Box(
        modifier =
            Modifier
                .size(64.dp)
                .then(
                    if (cell != null) {
                        Modifier.clickable {
                            cell.action(viewModel, selectedScreen) { edge ->
                                onAlignToEdge(selectedScreen, edge)
                            }
                        }
                    } else {
                        // R3C4: empty slot — keeps the 4×3 grid shape (design: visibility:hidden).
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (cell != null) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = tileDrawable(row, col)),
                contentDescription = cell.label,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Maps a grid position to the design's exported tile drawable (figma-export/tiles/RxCy.svg). */
private fun tileDrawable(row: Int, col: Int): Int =
    when {
        row == 0 && col == 0 -> R.drawable.nds_tile_r1c1
        row == 0 && col == 1 -> R.drawable.nds_tile_r1c2
        row == 0 && col == 2 -> R.drawable.nds_tile_r1c3
        row == 0 && col == 3 -> R.drawable.nds_tile_r1c4
        row == 1 && col == 0 -> R.drawable.nds_tile_r2c1
        row == 1 && col == 1 -> R.drawable.nds_tile_r2c2
        row == 1 && col == 2 -> R.drawable.nds_tile_r2c3
        row == 1 && col == 3 -> R.drawable.nds_tile_r2c4
        row == 2 && col == 0 -> R.drawable.nds_tile_r3c1
        row == 2 && col == 1 -> R.drawable.nds_tile_r3c2
        else -> R.drawable.nds_tile_r3c3
    }

/** A tool grid cell definition. */
private class ToolCell(
    val label: String,
    val action: (BaseGameScreenViewModel, ScreenId, (AlignEdge) -> Unit) -> Unit,
)

/**
 * Tool grid button semantics — faithful to docs/UI-元素文档.md §4.1:
 *
 * | cell | function                                        | data field  |
 * |------|-------------------------------------------------|-------------|
 * | R1C1 | height → 50% of default                        | scaleY = 0.5 |
 * | R1C2 | align top (上移)                                | offset → top |
 * | R1C3 | horizontal gap nudge (水平间距调节, −)          | gap -= delta |
 * | R1C4 | height → 100% of default                       | scaleY = 1.0 |
 * | R2C1 | align left (左移)                               | offset → left|
 * | R2C2 | free move / re-center (自由移动)                | offset = 0   |
 * | R2C3 | align right (右移)                              | offset → right|
 * | R2C4 | gap nudge (间距调节, +)                          | gap += delta |
 * | R3C1 | original size (reset this screen)               | reset        |
 * | R3C2 | align bottom (下移)                             | offset → bottom|
 * | R3C3 | screen gap (屏幕间距调节)                        | gap += delta |
 * | R3C4 | empty placeholder                               | —            |
 */
private val TOOL_GRID: Array<Array<ToolCell?>> = arrayOf(
    // Row 1: 高度50% / 上移 / 水平间距调节 / 高度100%
    arrayOf(
        ToolCell("纵向缩放 50%") { vm, s, _ -> vm.setScreenLayoutVerticalScale(s, ScreenLayoutManager.VERTICAL_SCALE_HALF) },
        ToolCell("上移") { _, _, align -> align(AlignEdge.TOP) },
        ToolCell("水平间距调节") { vm, s, _ -> nudgeGap(vm, s, -ScreenLayoutManager.GAP_DELTA) },
        ToolCell("纵向缩放 100%") { vm, s, _ -> vm.setScreenLayoutVerticalScale(s, ScreenLayoutManager.VERTICAL_SCALE_FULL) },
    ),
    // Row 2: 左移 / 自由移动 / 右移 / 间距调节
    arrayOf(
        ToolCell("左移") { _, _, align -> align(AlignEdge.LEFT) },
        ToolCell("自由移动") { _, _, align -> align(AlignEdge.CENTER) },
        ToolCell("右移") { _, _, align -> align(AlignEdge.RIGHT) },
        ToolCell("间距调节 100%") { vm, s, _ -> nudgeGap(vm, s, +ScreenLayoutManager.GAP_DELTA) },
    ),
    // Row 3: 原始尺寸 / 下移 / 屏幕间距 / 空位
    arrayOf(
        ToolCell("Original Size") { vm, s, _ -> vm.resetScreenLayoutScreen(s) },
        ToolCell("下移") { _, _, align -> align(AlignEdge.BOTTOM) },
        ToolCell("Screen Gap") { vm, s, _ -> nudgeGap(vm, s, +ScreenLayoutManager.GAP_DELTA) },
        null, // R3C4 empty slot (visibility:hidden in the design)
    ),
)

/** Applies a gap delta to both screens (the gap is shared between them). */
private fun nudgeGap(
    vm: BaseGameScreenViewModel,
    screen: ScreenId,
    delta: Float,
) {
    val current = vm.currentScreenLayoutState().transformOf(screen).gap
    val next = (current + delta).coerceAtLeast(0f)
    vm.setScreenLayoutGap(ScreenId.TOP, next)
    vm.setScreenLayoutGap(ScreenId.BOTTOM, next)
}

/** Zoom panel: stepped uniform scale of the selected screen. */
@Composable
private fun ZoomPanel(
    isLandscape: Boolean,
    viewModel: BaseGameScreenViewModel,
    selectedScreen: ScreenLayoutManager.ScreenId,
    layoutState: ScreenLayoutManager.ScreenLayoutState,
    viewPos: androidx.compose.ui.geometry.Rect,
    density: Float,
) {
    val steps = if (isLandscape) intArrayOf(1, 5, 2, 6, 3, 7, 4, -1) else intArrayOf(1, 5, 2, 3, 4, -1)
    val currentScale = layoutState.transformOf(selectedScreen).scale
    val currentTransform = layoutState.transformOf(selectedScreen)
    // Dynamic cap: the largest uniform scale that keeps the screen inside the usable area,
    // given its current independent width/height scales. Stepped labels above this cap are hidden.
    val maxScale = maxOnScreenScale(viewPos, density, currentTransform.scaleX, currentTransform.scaleY)

    Column(verticalArrangement = Arrangement.spacedBy(if (isLandscape) 5.dp else 6.dp)) {
        for (i in steps.indices step 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 5.dp else 6.dp)) {
                for (j in 0..1) {
                    val idx = i + j
                    if (idx >= steps.size) continue
                    val step = steps[idx]
                    if (step == -1) {
                        Spacer(modifier = Modifier.size(if (isLandscape) 38.dp else 44.dp))
                        continue
                    }
                    val stepFloat = step.toFloat()
                    val clampedScale = stepFloat.coerceAtMost(maxScale)
                    val active = currentScale == clampedScale
                    Box(
                        modifier =
                            Modifier
                                .size(if (isLandscape) 38.dp else 44.dp)
                                .background(
                                    if (active) Color(0xFF35b5e8) else Color.White,
                                    RoundedCornerShape(4.dp),
                                )
                                .border(
                                    2.dp,
                                    Color(0xFF35b5e8),
                                    RoundedCornerShape(4.dp),
                                )
                                .clickable { viewModel.setScreenLayoutScale(selectedScreen, clampedScale) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${step}x",
                            color = if (active) Color.White else Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bottom action bar — design §5, exactly five items:
 * 菜单 / 重设回默认 / 编辑全局布局(禁用) / 关闭工具箱 / 调整屏幕大小.
 */
@Composable
fun ScreenLayoutBottomBar(
    modifier: Modifier = Modifier,
    viewModel: BaseGameScreenViewModel,
    isLandscape: Boolean,
    onCloseToolbox: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF252528),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (isLandscape) 8.dp else 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            BottomBarItem("菜单", enabled = true) { viewModel.showGameMenu() }
            BottomBarItem("重设回默认", enabled = true) { viewModel.resetScreenLayoutToDefault() }
            // Reserved global-layout entry — disabled per design (rgba(255,255,255,0.4)).
            BottomBarItem("编辑全局布局", enabled = false) {}
            BottomBarItem("关闭工具箱", enabled = true) { onCloseToolbox() }
            BottomBarItem("调整屏幕大小", enabled = true) { viewModel.toggleEditScreenLayout(false) }
        }
    }
}

@Composable
private fun RowScope.BottomBarItem(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f)
    Box(
        modifier =
            Modifier
                .weight(1f)
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .then(
                    if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
