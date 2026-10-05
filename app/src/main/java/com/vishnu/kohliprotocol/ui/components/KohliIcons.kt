package com.vishnu.kohliprotocol.ui.components

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The two Material icons the tab bar needs that aren't in material-icons-core. Defined from
 * Material's own paths so the (very large) extended icon library isn't pulled in.
 */
object KohliIcons {

    /** Material "Shield". */
    val Shield: ImageVector by lazy {
        materialIcon(name = "Kohli.Shield") {
            materialPath {
                moveTo(12f, 1f)
                lineTo(3f, 5f)
                verticalLineToRelative(6f)
                curveToRelative(0f, 5.55f, 3.84f, 10.74f, 9f, 12f)
                curveToRelative(5.16f, -1.26f, 9f, -6.45f, 9f, -12f)
                lineTo(21f, 5f)
                lineToRelative(-9f, -4f)
                close()
            }
        }
    }

    /** Material "AutoAwesome" (sparkles). */
    val AutoAwesome: ImageVector by lazy {
        materialIcon(name = "Kohli.AutoAwesome") {
            materialPath {
                moveTo(19f, 9f)
                lineToRelative(1.25f, -2.75f)
                lineTo(23f, 5f)
                lineToRelative(-2.75f, -1.25f)
                lineTo(19f, 1f)
                lineToRelative(-1.25f, 2.75f)
                lineTo(15f, 5f)
                lineToRelative(2.75f, 1.25f)
                close()
                moveTo(11.5f, 9.5f)
                lineTo(9f, 4f)
                lineTo(6.5f, 9.5f)
                lineTo(1f, 12f)
                lineToRelative(5.5f, 2.5f)
                lineTo(9f, 20f)
                lineToRelative(2.5f, -5.5f)
                lineTo(17f, 12f)
                close()
                moveTo(19f, 15f)
                lineToRelative(-1.25f, 2.75f)
                lineTo(15f, 19f)
                lineToRelative(2.75f, 1.25f)
                lineTo(19f, 23f)
                lineToRelative(1.25f, -2.75f)
                lineTo(23f, 19f)
                lineToRelative(-2.75f, -1.25f)
                close()
            }
        }
    }
}
