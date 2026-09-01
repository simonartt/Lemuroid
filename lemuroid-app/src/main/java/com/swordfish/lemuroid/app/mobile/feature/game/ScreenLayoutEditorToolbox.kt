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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.app.shared.game.BaseGameScreenViewModel
import com.swordfish.lemuroid.app.shared.game.screenlayout.ScreenLayoutManager
import com.swordfish.lemuroid.app.shared.game.screenlayout.ScreenLayoutManager.ScreenId

/** Edge a screen can be aligned to, or CENTER for re-centering. */
enum class AlignEdge { TOP, BOTTOM, LEFT, RIGHT, CENTER }

/**
 * Floating "tool strip" for the NDS dual-screen layout editor.
 * Replaces the old bottom-card editor. Centers a 4×3 tool grid plus a zoom panel.
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
            // 4×3 tool grid (left)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in 0 until 3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (col in 0 until 4) {
                            ToolGridButton(
                                row = row,
                                col = col,
                                viewModel = viewModel,
                                selectedScreen = selectedScreen,
                                onScreenSelected = onScreenSelected,
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
            )
        }
    }
}

/** A single 64dp tool tile in the 4×3 grid. */
@Composable
private fun ToolGridButton(
    row: Int,
    col: Int,
    viewModel: BaseGameScreenViewModel,
    selectedScreen: ScreenLayoutManager.ScreenId,
    onScreenSelected: (ScreenLayoutManager.ScreenId) -> Unit,
    onAlignToEdge: (ScreenLayoutManager.ScreenId, AlignEdge) -> Unit,
) {
    val cell = TOOL_GRID[row][col]
    val enabled = cell != null

    Box(
        modifier =
            Modifier
                .size(64.dp)
                .background(Color.White, RoundedCornerShape(4.dp))
                .border(2.dp, Color(0xFF48DAFF), RoundedCornerShape(4.dp))
                .let { base ->
                    if (enabled) {
                        base.clickable {
                            cell!!.action(viewModel, selectedScreen) { edge ->
                                onAlignToEdge(selectedScreen, edge)
                            }
                        }
                    } else {
                        base
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        if (enabled) {
            val spec = cell!!
            if (spec.icon != null) {
                Icon(
                    imageVector = spec.icon,
                    contentDescription = spec.label,
                    tint = Color(0xFF202020),
                    modifier = Modifier.size(36.dp),
                )
            } else {
                Text(
                    text = spec.label,
                    color = Color(0xFF202020),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    lineHeight = 13.sp,
                )
            }
        }
    }
}

/** A tool grid cell definition; null = empty placeholder (R3C4). */
private class ToolCell(
    val label: String,
    val icon: ImageVector? = null,
    val action: (BaseGameScreenViewModel, ScreenId, (AlignEdge) -> Unit) -> Unit,
)

/**
 * Tool grid button semantics — faithful to docs/UI-元素文档.md §4.1:
 *
 * | cell | function                                        | data field  |
 * |------|-------------------------------------------------|-------------|
 * | R1C1 | height → 50% of default                        | scaleY = 0.5 |
 * | R1C2 | align top                                       | offset → top |
 * | R1C3 | width → 50% of default                         | scaleX = 0.5 |
 * | R1C4 | height → 100% of default                       | scaleY = 1.0 |
 * | R2C1 | align left                                      | offset → left|
 * | R2C2 | center (move to screen center)                  | offset = 0   |
 * | R2C3 | align right                                     | offset → right|
 * | R2C4 | width → 100% of default                        | scaleX = 1.0 |
 * | R3C1 | original size (reset this screen)               | reset        |
 * | R3C2 | align bottom                                    | offset → bottom|
 * | R3C3 | screen gap (push the two screens apart)         | gap += delta |
 * | R3C4 | empty                                           | —            |
 */
private val TOOL_GRID: Array<Array<ToolCell?>> = arrayOf(
    // Row 1: 高度50% / 顶部对齐 / 宽度50% / 高度100%
    arrayOf(
        ToolCell("高 50%") { vm, s, _ -> vm.setScreenLayoutVerticalScale(s, ScreenLayoutManager.VERTICAL_SCALE_HALF) },
        ToolCell("顶部对齐", Icons.Filled.ArrowUpward) { _, _, align -> align(AlignEdge.TOP) },
        ToolCell("宽 50%") { vm, s, _ -> vm.setScreenLayoutHorizontalScale(s, ScreenLayoutManager.HORIZONTAL_SCALE_HALF) },
        ToolCell("高 100%") { vm, s, _ -> vm.setScreenLayoutVerticalScale(s, ScreenLayoutManager.VERTICAL_SCALE_FULL) },
    ),
    // Row 2: 左对齐 / 居中 / 右对齐 / 宽度100%
    arrayOf(
        ToolCell("左对齐", Icons.Filled.ArrowBack) { _, _, align -> align(AlignEdge.LEFT) },
        ToolCell("居中", Icons.Filled.OpenInFull) { _, _, align -> align(AlignEdge.CENTER) },
        ToolCell("右对齐", Icons.Filled.ArrowForward) { _, _, align -> align(AlignEdge.RIGHT) },
        ToolCell("宽 100%") { vm, s, _ -> vm.setScreenLayoutHorizontalScale(s, ScreenLayoutManager.HORIZONTAL_SCALE_FULL) },
    ),
    // Row 3: 原始尺寸 / 底部对齐 / 屏幕间距 / 空位
    arrayOf(
        ToolCell("还原") { vm, s, _ -> vm.resetScreenLayoutScreen(s) },
        ToolCell("底部对齐", Icons.Filled.ArrowDownward) { _, _, align -> align(AlignEdge.BOTTOM) },
        ToolCell("间距") { vm, s, _ -> vm.setScreenLayoutGap(s, vm.currentScreenLayoutState().transformOf(s).gap + ScreenLayoutManager.GAP_DELTA) },
        null, // R3C4 empty slot
    ),
)

/** Zoom panel: stepped uniform scale of the selected screen. */
@Composable
private fun ZoomPanel(
    isLandscape: Boolean,
    viewModel: BaseGameScreenViewModel,
    selectedScreen: ScreenLayoutManager.ScreenId,
    layoutState: ScreenLayoutManager.ScreenLayoutState,
) {
    val steps = if (isLandscape) intArrayOf(1, 5, 2, 6, 3, 7, 4, -1) else intArrayOf(1, 5, 2, 3, 4, -1)
    val currentScale = layoutState.transformOf(selectedScreen).scale

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
                    val active = currentScale == step.toFloat()
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
                                .clickable { viewModel.setScreenLayoutScale(selectedScreen, step.toFloat()) },
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
 * Bottom action bar: 菜单 / 重设回默认 / 关闭工具箱 / 完成.
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
            BottomBarItem("关闭工具箱", enabled = true) { onCloseToolbox() }
            BottomBarItem("完成", enabled = true) { viewModel.toggleEditScreenLayout(false) }
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
