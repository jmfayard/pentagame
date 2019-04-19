package app

import io.data2viz.viz.JFxVizRenderer
import javafx.scene.layout.Priority
import PentaViz
import PentaViz.addEvents
import penta.GameState
import tornadofx.*

class CanvasView : View("PentaGame") {
    override val root = borderpane {
        hgrow = Priority.ALWAYS
        vgrow = Priority.ALWAYS
//        this.minHeight = height
        val canvas = canvas(WIDTH, HEIGHT) {
            hgrow = Priority.ALWAYS
            vgrow = Priority.ALWAYS
            minHeight(HEIGHT)
            minWidth(WIDTH)
            val viz = PentaViz.viz
            PentaViz.gameState = GameState(
                listOf("a", "b", "c"),
                mapOf()
            )
            with(viz) {
                width = this@canvas.width
                height = this@canvas.height
                resize(WIDTH, HEIGHT)
            }

            val renderer = JFxVizRenderer(this, viz)
            with(viz) {
                addEvents()
            }
            viz.renderer = renderer
            viz.render()

            widthProperty().onChange { newWidth ->
                println("new width: $newWidth")
                viz.resize(newWidth, height)
                viz.render()
            }
            heightProperty().onChange { newHeight ->
                println("new height: $newHeight")
//                viz.height = newHeight
                viz.resize(width, newHeight)
                viz.render()
            }

        }
        this.center = canvas

        widthProperty().onChange { newWidth ->
            if(newWidth > 250)
                canvas.width = newWidth
        }
        heightProperty().onChange { newHeight ->
            if(newHeight > 250)
                canvas.height = newHeight
        }

    }
}