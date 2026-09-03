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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.game.BaseGameScreenViewModel
import com.swordfish.lemuroid.app.shared.game.screenlayout.ScreenLayoutManager
import com.swordfish.lemuroid.app.shared.game.screenlayout.ScreenLayoutManager.ScreenId

/** Edge a screen can be aligned to, or CENTER for re-centering. */
enum class AlignEdge { TOP, BOTTOM, BOTTOM_DEVICE, LEFT, RIGHT, CENTER }

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
    displayWidthPx: Float,
    displayHeightPx: Float,
    naturalTopWidthPx: Float,
    naturalBottomWidthPx: Float,
    onAlignToEdge: (ScreenLayoutManager.ScreenId, AlignEdge) -> Unit,
    onClose: () -> Unit,
) {
    // No panel background — the tiles and zoom buttons each carry their own white rounded
    // base (design §4.1/§4.2), so the toolbox floats directly over the game picture. A solid
    // dark Surface here used to leave a big empty black block under the shorter zoom panel.
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Tool grid (left) — icons are the design's own SVG tiles. NOTE: the nds_tile_*
            // drawable FILENAMES preserve the ORIGINAL figma-export sheet position (tiles/RxCy.svg);
            // which cell actually uses a tile is decided by the grid functions below. All cell
            // positions in comments are CURRENT-layout coordinates.
            // The two width-percent cells need runtime geometry, so they're built here and
            // injected into the grid layout (remembered on the geometry values).
            val width100 = remember(displayWidthPx, naturalTopWidthPx, naturalBottomWidthPx) {
                // ↔100% glyph (sheet name R2C4, but it is the WIDTH 100% icon).
                ToolCell("宽度 100%", R.drawable.nds_tile_r2c4) { vm, s, _ ->
                    setWidthPercent(vm, s, 1.0f, displayWidthPx, naturalTopWidthPx, naturalBottomWidthPx)
                }
            }
            val width50 = remember(displayWidthPx, naturalTopWidthPx, naturalBottomWidthPx) {
                // ↔50% glyph (sheet name R1C3, but it is the WIDTH 50% icon).
                ToolCell("宽度 50%", R.drawable.nds_tile_r1c3) { vm, s, _ ->
                    setWidthPercent(vm, s, 0.5f, displayWidthPx, naturalTopWidthPx, naturalBottomWidthPx)
                }
            }
            val grid = if (isLandscape) toolGridLandscape(width100, width50) else toolGridPortrait(width100, width50)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in grid.indices) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (col in grid[row].indices) {
                            ToolGridButton(
                                cell = grid[row][col],
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
                displayWidthPx = displayWidthPx,
                displayHeightPx = displayHeightPx,
                naturalTopWidthPx = naturalTopWidthPx,
                naturalBottomWidthPx = naturalBottomWidthPx,
            )
        }
    }
}

/** A single 64dp tool tile. Renders the design's own vector tile for the cell. */
@Composable
private fun ToolGridButton(
    cell: ToolCell?,
    viewModel: BaseGameScreenViewModel,
    selectedScreen: ScreenLayoutManager.ScreenId,
    onAlignToEdge: (ScreenLayoutManager.ScreenId, AlignEdge) -> Unit,
) {
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
                        // Empty slot — keeps the grid shape (design: visibility:hidden).
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (cell != null) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = cell.drawable),
                contentDescription = cell.label,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * A tool grid cell definition. [drawable] is the design's exported tile
 * (figma-export/tiles/RxCy.svg) — the FILENAME records the tile's ORIGINAL sheet position,
 * which does NOT necessarily match the cell it is used in today (see the grid functions).
 */
private class ToolCell(
    val label: String,
    val drawable: Int,
    val action: (BaseGameScreenViewModel, ScreenId, (AlignEdge) -> Unit) -> Unit,
)

/** Shared cell actions — faithful to docs/UI-元素文档.md §4.1. */
private val CELL_HEIGHT_50 = ToolCell(
    "纵向缩放 50%",
    R.drawable.nds_tile_r1c1,
) { vm, s, _ -> vm.setScreenLayoutVerticalScale(s, ScreenLayoutManager.VERTICAL_SCALE_HALF) }

private val CELL_HEIGHT_100 = ToolCell(
    "纵向缩放 100%",
    R.drawable.nds_tile_r1c4,
) { vm, s, _ -> vm.setScreenLayoutVerticalScale(s, ScreenLayoutManager.VERTICAL_SCALE_FULL) }

// The four directional-align cells surround the center cell in both grids (design logic).
// Their glyphs are the sheet's 上/下/左/右移 tiles; filenames keep the ORIGINAL sheet position.
private val CELL_ALIGN_TOP = ToolCell("上对齐", R.drawable.nds_tile_r1c2) { _, _, align ->
    align(AlignEdge.TOP)
}

private val CELL_ALIGN_BOTTOM = ToolCell("下对齐", R.drawable.nds_tile_r3c2) { _, _, align ->
    align(AlignEdge.BOTTOM_DEVICE)
}

private val CELL_ALIGN_LEFT = ToolCell("左对齐", R.drawable.nds_tile_r2c1) { _, _, align ->
    align(AlignEdge.LEFT)
}

private val CELL_ALIGN_RIGHT = ToolCell("右对齐", R.drawable.nds_tile_align_right) { _, _, align ->
    align(AlignEdge.RIGHT)
}

private val CELL_FREE_MOVE = ToolCell("居中", R.drawable.nds_tile_r2c2) { _, _, align ->
    align(AlignEdge.CENTER)
}

/**
 * The single screen-gap button (design sheet R3C3 "Screen Gap", glyph `nds_tile_r3c3`:
 * two bars with a 10px vertical arrow). There is exactly ONE gap button in the toolbox;
 * tapping cycles the gap: 0 → 16 → 32 → 48 → 0 … device px.
 */
private val CELL_GAP = ToolCell("屏幕间距", R.drawable.nds_tile_r3c3) { vm, s, _ -> cycleGap(vm, s) }

private val CELL_ORIGINAL_SIZE = ToolCell("Original Size", R.drawable.nds_tile_r3c1) { vm, s, _ ->
    vm.resetScreenLayoutScreen(s)
}

/**
 * Landscape arrangement — 4 columns × 3 rows. All positions below are CURRENT-layout
 * coordinates. Design logic (user-pinned): the four directional arrows (↑←→↓) directly
 * surround the center button, and there is exactly ONE gap button.
 *
 * | R1C1 高度50%  | R1C2 上对齐↑ | R1C3 高度100% | R1C4 宽度100% ↔100% |
 * | R2C1 左对齐← | R2C2 居中✛   | R2C3 右对齐→  | R2C4 宽度50%  ↔50%  |
 * | R3C1 原始尺寸 | R3C2 下对齐↓ | R3C3 间距     | (空位)              |
 */
private fun toolGridLandscape(width100: ToolCell, width50: ToolCell): Array<Array<ToolCell?>> = arrayOf(
    arrayOf(CELL_HEIGHT_50, CELL_ALIGN_TOP, CELL_HEIGHT_100, width100),
    arrayOf(CELL_ALIGN_LEFT, CELL_FREE_MOVE, CELL_ALIGN_RIGHT, width50),
    arrayOf(CELL_ORIGINAL_SIZE, CELL_ALIGN_BOTTOM, CELL_GAP, null),
)

/**
 * Portrait arrangement — 4 rows × 3 columns. All positions below are CURRENT-layout
 * coordinates. User-pinned: R1C2 = 宽度100%, R2C3 = 宽度50%, R3C3 = 右对齐,
 * R4C2 = 底部对齐（贴设备屏底）, R4C3 = 间距（唯一间距按钮，图标 tiles/R3C3.svg）.
 * The four directional arrows (↑ R2C2, ← R3C1, → R3C3, ↓ R4C2) surround the center R3C2.
 *
 * | R1C1 高度100% | R1C2 宽度100% | (空位)     |
 * | R2C1 高度50%  | R2C2 上对齐↑  | R2C3 宽度50% |
 * | R3C1 左对齐←  | R3C2 居中✛    | R3C3 右对齐→ |
 * | R4C1 原始尺寸 | R4C2 底部对齐↓ | R4C3 间距   |
 */
private fun toolGridPortrait(
    width100: ToolCell,
    width50: ToolCell,
): Array<Array<ToolCell?>> = arrayOf(
    arrayOf(CELL_HEIGHT_100, width100, null),
    arrayOf(CELL_HEIGHT_50, CELL_ALIGN_TOP, width50),
    arrayOf(CELL_ALIGN_LEFT, CELL_FREE_MOVE, CELL_ALIGN_RIGHT),
    arrayOf(CELL_ORIGINAL_SIZE, CELL_ALIGN_BOTTOM, CELL_GAP),
)

/** Gap-cycle step / wrap point for the single gap button (device px). */
private const val GAP_CYCLE_STEP = 16f
private const val GAP_CYCLE_MAX = 48f

/** Single gap button action: cycle the shared inter-screen gap 0 → 16 → 32 → 48 → 0 … */
private fun cycleGap(
    vm: BaseGameScreenViewModel,
    screen: ScreenId,
) {
    val current = vm.currentScreenLayoutState().transformOf(screen).gap
    val next = if (current + GAP_CYCLE_STEP > GAP_CYCLE_MAX) 0f else current + GAP_CYCLE_STEP
    vm.setScreenLayoutGap(ScreenId.TOP, next)
    vm.setScreenLayoutGap(ScreenId.BOTTOM, next)
}

/**
 * Sets the selected screen's rendered width to [percent] of the device screen width.
 * Rendered width = naturalWidth × scale, so the required uniform scale is
 * percent × displayWidth / naturalWidth. Uniform scale keeps the 4:3 ratio intact.
 */
private fun setWidthPercent(
    vm: BaseGameScreenViewModel,
    screen: ScreenId,
    percent: Float,
    displayWidthPx: Float,
    naturalTopWidthPx: Float,
    naturalBottomWidthPx: Float,
) {
    val naturalWidth = if (screen == ScreenId.TOP) naturalTopWidthPx else naturalBottomWidthPx
    if (naturalWidth <= 0f || displayWidthPx <= 0f) return
    val targetScale = (percent * displayWidthPx / naturalWidth).coerceIn(
        ScreenLayoutManager.MIN_SCALE,
        ScreenLayoutManager.MAX_SCALE,
    )
    vm.setScreenLayoutScale(screen, targetScale)
}

/** Zoom panel: stepped uniform scale of the selected screen. */
@Composable
private fun ZoomPanel(
    isLandscape: Boolean,
    viewModel: BaseGameScreenViewModel,
    selectedScreen: ScreenLayoutManager.ScreenId,
    layoutState: ScreenLayoutManager.ScreenLayoutState,
    displayWidthPx: Float,
    displayHeightPx: Float,
    naturalTopWidthPx: Float,
    naturalBottomWidthPx: Float,
) {
    // Sequential left-to-right, top-to-bottom: [1x,2x] / [3x,4x] / [5x,…].
    val steps = if (isLandscape) intArrayOf(1, 2, 3, 4, 5, 6, 7, -1) else intArrayOf(1, 2, 3, 4, 5, -1)
    val currentScale = layoutState.transformOf(selectedScreen).scale

    // Zoom buttons are NATIVE-RESOLUTION multiples: Nx renders one screen at N × 256×192
    // device px. The default look (scale=1.0) is the anchor-fit size, which on a typical phone
    // is several times the native width — so scale=1.0 never equals an integer step and no
    // button is highlighted until one is pressed.
    val naturalWidth =
        if (selectedScreen == ScreenId.TOP) naturalTopWidthPx else naturalBottomWidthPx
    val baseScale = nativeResolutionScale(naturalWidth)

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
                    val targetScale = (step * baseScale).coerceIn(
                        ScreenLayoutManager.MIN_SCALE,
                        ScreenLayoutManager.MAX_SCALE,
                    )
                    // Highlight only the button whose own value equals the current scale.
                    val active = currentScale == targetScale
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
                                .clickable { viewModel.setScreenLayoutScale(selectedScreen, targetScale) },
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
 * Bottom action bar — four items (v1.20.1):
 * 菜单(弹下级菜单) / 重设回默认 / 关闭工具箱·打开工具箱(切换) /
 * 调整屏幕大小 ⇄ 返回(缩放模式开关，第一次点击进缩放模式、文案变"返回"，再点退出).
 * The old disabled "编辑全局布局" grey placeholder was removed (never had a function);
 * "调整屏幕大小" no longer exits the editor — exiting moved into the 菜单 sub-menu
 * ("返回游戏菜单" = close editor + open game menu).
 */
@Composable
fun ScreenLayoutBottomBar(
    modifier: Modifier = Modifier,
    viewModel: BaseGameScreenViewModel,
    isLandscape: Boolean,
    toolboxVisible: Boolean,
    onToggleToolbox: () -> Unit,
    resizeMode: Boolean,
    onToggleResizeMode: () -> Unit,
    onMenu: () -> Unit,
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
            BottomBarItem("菜单", enabled = true) { onMenu() }
            BottomBarItem("重设回默认", enabled = true) { viewModel.resetScreenLayoutToDefault() }
            BottomBarItem(
                label = if (toolboxVisible) "关闭工具箱" else "打开工具箱",
                enabled = true,
            ) { onToggleToolbox() }
            BottomBarItem(
                label = if (resizeMode) "返回" else "调整屏幕大小",
                enabled = true,
            ) { onToggleResizeMode() }
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
