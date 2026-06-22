package net.akehurst.kotlin.compose.layout.multipane

import androidx.compose.runtime.Composable
import net.akehurst.kotlin.compose.components.SplitOrientation
import net.akehurst.kotlinx.utils.UniqueIdentityGenerator

/**
 * A context marker for the layout DSL.
 */
@DslMarker
annotation class LayoutDsl

/**
 * Creates a layout node using the DSL builder.
 *
 * Example usage:
 * ```
 * val layout = layoutNode {
 *   split(orientation = SplitOrientation.Horizontal) {
 *     child(weight = 1f) {
 *       pane(title = "Pane 1") { /* ... */ }
 *     }
 *     child(weight = 2f) {
 *       tabbed {
 *         pane(title = "Tab 1") { /* ... */ }
 *         pane(title = "Tab 2") { /* ... */ }
 *       }
 *     }
 *   }
 * }
 * ```
 */
fun layoutNode(init: LayoutNodeBuilder.() -> Unit): LayoutNode {
    val builder = LayoutNodeBuilder()
        builder.init()

    return builder.build()
}

/**
 * The main builder for creating a single [LayoutNode].
 * It ensures that only one root node (Pane, Split, or Tabbed) is defined.
 */
@LayoutDsl
class LayoutNodeBuilder {
    private var node: LayoutNode = LayoutNode.Empty

    internal fun build(): LayoutNode {
        return node
    }

    private fun setNode(newNode: LayoutNode) {
        check(node == LayoutNode.Empty) { "Only one root node can be defined in a LayoutNodeBuilder." }
        node = newNode
    }

//    /**
//     * Defines a [LayoutNode.Pane].
//     */
//    fun pane(id: String = LayoutNode.generateID(), title: String, content: @Composable () -> Unit) {
//        setNode(Pane(id, title, content))
//    }

    /**
     * Defines a [LayoutNode.Split].
     */
    fun split(id: String = UniqueIdentityGenerator.GLOBAL.generate("split"), orientation: SplitOrientation, init: SplitBuilder.() -> Unit) {
        val builder = SplitBuilder(id, orientation)
        builder.init()
        setNode(builder.build())
    }

    /**
     * Defines a [LayoutNode.Tabbed].
     */
    fun tabbed(id: String = UniqueIdentityGenerator.GLOBAL.generate("tabbed"), init: TabbedBuilder.() -> Unit) {
        val builder = TabbedBuilder(id)
        builder.init()
        setNode(builder.build())
    }
}

/**
 * A builder for creating a [LayoutNode.Split].
 */
@LayoutDsl
class SplitBuilder(
    private val nodeId: String,
    private val orientation: SplitOrientation
) {
    private val children = mutableListOf<LayoutNode>()
    private val weights = mutableListOf<Float>()

    fun split(weight: Float,id: String = UniqueIdentityGenerator.GLOBAL.generate("split"), orientation: SplitOrientation, init: SplitBuilder.() -> Unit) {
        val builder = SplitBuilder(id, orientation)
        builder.init()
        children.add(builder.build())
        weights.add(weight)
    }

    fun tabbed(weight: Float,id: String = UniqueIdentityGenerator.GLOBAL.generate("tabbed"), init: TabbedBuilder.() -> Unit) {
        val builder = TabbedBuilder(id)
        builder.init()
        children.add(builder.build())
        weights.add(weight)
    }

    /**
     * convenience: adds a single pane inside a tabbed layout
     */
    fun pane(weight: Float, id: String = UniqueIdentityGenerator.GLOBAL.generate("pane"), title: String, update:()->Unit = {}, content: @Composable () -> Unit) {
        children.add(LayoutNode.Tabbed(children = listOf(Pane(id, title, update=update, content=content))))
        weights.add(weight)
    }

    internal fun build(): LayoutNode.Split {
        val totalWeight = weights.sum()
        val normalizedWeights = weights.map { it / totalWeight }
        return LayoutNode.Split(nodeId, orientation, children, normalizedWeights)
    }
}

/**
 * A builder for creating a [LayoutNode.Tabbed].
 */
@LayoutDsl
class TabbedBuilder(private val nodeId: String) {
    private val children = mutableListOf<Pane>()

    /**
     * Adds a [LayoutNode.Pane] as a tab.
     */
    fun pane(id: String = UniqueIdentityGenerator.GLOBAL.generate("pane"), title: String, update:()->Unit = {}, content: @Composable () -> Unit) {
        children.add(Pane(id, title,  update=update, content=content))
    }

//    /**
//     * Adds a [LayoutNode.Split] as a child. While possible, this may result in a complex UI.
//     */
//    fun split(id: String = LayoutNode.generateID(), orientation: SplitOrientation, init: SplitBuilder.() -> Unit) {
//        val builder = SplitBuilder(id, orientation)
//        builder.init()
//        children.add(builder.build())
//    }

//    /**
//     * Adds a nested [LayoutNode.Tabbed] as a child.
//     */
//    fun tabbed(id: String = LayoutNode.generateID(), init: TabbedBuilder.() -> Unit) {
//        val builder = TabbedBuilder(id)
//        builder.init()
//        children.add(builder.build())
//    }

    internal fun build(): LayoutNode.Tabbed {
        return LayoutNode.Tabbed(nodeId, children)
    }
}