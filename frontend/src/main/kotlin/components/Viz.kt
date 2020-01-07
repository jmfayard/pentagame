package components

import io.data2viz.viz.Viz
import io.data2viz.viz.bindRendererOn
import kotlinx.css.margin
import kotlinx.html.id
import mu.KotlinLogging
import onHtmlRendered
import react.RBuilder
import react.RClass
import react.RComponent
import react.RProps
import react.RState
import react.createRef
import react.invoke
import react.redux.rConnect
import redux.RAction
import redux.WrapperAction
import styled.css
import styled.styledCanvas
import kotlin.browser.window
import kotlin.math.min
import kotlin.math.max
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.EventListener
import react.dom.div

class EmptyAction: RAction

interface VizProps: VizStateProps, VizDispatchProps

class VizComponent(props: VizProps): RComponent<VizProps, RState>(props) {
    companion object {
        private val logger = KotlinLogging.logger {}
    }
    val canvasRef = createRef<HTMLCanvasElement>()
    val divRef = createRef<HTMLDivElement>()
    override fun RBuilder.render() {
        div {
            styledCanvas {
                css {
                    margin = "0px"
                }
                attrs.id = props.id ?: "vizCanvas"
                attrs.width = "100"
                attrs.height = "100"
                ref = canvasRef
            }
            ref = divRef
        }

        onHtmlRendered.add {
            logger.info { "canvasRef.current: ${canvasRef.current}" }
            val canvas = canvasRef.current!!
            val div = divRef.current!!
            props.viz.bindRendererOn(canvas)

            window.addEventListener("resize",
                EventListener { event ->
                    val rect = div.getBoundingClientRect()
                    logger.debug { "rect width: ${rect.width} height: ${rect.height}" }
                    logger.debug { "canvas width: ${canvas.width} height: ${canvas.height}" }
                    val size = max(200,
                        min(
                            min(rect.height, rect.width).toInt(),
                            window.document.documentElement!!.clientHeight
                        )
                    )
                    canvas.width = size
                    canvas.height = size
                    with(props.viz) {
                        height = canvas.height.toDouble()
                        width = canvas.width.toDouble()
                        resize(canvas.width.toDouble(), canvas.height.toDouble())
                        render()
                    }
                })
        }
    }
}
interface VizStateProps: RProps {
    var viz: Viz
    var id: String?
}
interface VizDispatchProps : RProps {

}
val vizCanvas = rConnect<EmptyAction, WrapperAction, VizProps, VizProps>(
    {
            dispatch, configProps ->
        // any kind of interactivity is linked to dispatching state changes here
        println("VizComponent.dispatch")
        println("dispatch: $dispatch ")
        println("configProps: $configProps ")
    }
)(VizComponent::class.js.unsafeCast<RClass<VizProps>>())