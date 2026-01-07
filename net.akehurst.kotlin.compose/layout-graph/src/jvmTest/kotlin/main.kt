import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.singleWindowApplication
import net.akehurst.kotlin.components.layout.graph.EdgeRouting
import net.akehurst.kotlin.components.layout.graph.GraphLayoutGraphState
import net.akehurst.kotlin.components.layout.graph.GraphLayoutView
import net.akehurst.kotlin.components.layout.graph.GraphLayoutViewState
import net.akehurst.kotlin.compose.components.flowHolder.mutableStateFlowHolderOf
import kotlin.test.Test


class test_MultiPaneLayout {

    fun GraphLayoutGraphState.addNode(id:String, color: Color, width:Int, height:Int) = this.addNode(id) {
        Column(
            modifier = Modifier
                .size(width.dp, height.dp)
                .background(color)
        ) {
            Text(id)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun main() {
        // initial layout
        val graph = GraphLayoutGraphState("g")
        graph.addNode("A",Color.Red,50,50)
        graph.addNode("B",Color.Blue,150, 150)
        graph.addNode("C",Color.Yellow,50, 50)

        graph.addEdge("e1", "A", "C")
        graph.addEdge("e2", "B", "C")

        //println(graph.asString(""))
        val graphStateHolder = mutableStateFlowHolderOf(GraphLayoutGraphState(""))
        graphStateHolder.update { graph }

        val graphViewState = mutableStateFlowHolderOf(GraphLayoutViewState())
        val graphRoutingState = mutableStateFlowHolderOf(EdgeRouting.DIRECT)

        singleWindowApplication(
            title = "Demo GraphLayout",
        ) {
            val vs = graphViewState.collectAsState()
            val gs = graphStateHolder.collectAsState()
            val rs = graphRoutingState.collectAsState()
            GraphLayoutView(
                viewState = vs.value,
                graphState = gs.value,
                routing = rs.value,
                updateRouting = { r-> graphRoutingState.update { r } },
                updateView = { o, z -> graphViewState.update {
                    it.copy(offset = o, zoom = z)
                } },
                routingSelectorsColor = Color.Red,
                modifier = Modifier
                    //.fillMaxSize()
                    .border(color = Color.Red, width = 1.dp)
                    .background(Color.Green),
            )
        }
    }
}


