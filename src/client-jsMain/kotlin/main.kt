import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.features.websocket.WebSockets

actual val client: HttpClient = HttpClient(Js).config {
    install(WebSockets)
    install(JsonFeature) {
        serializer = KotlinxSerializer()
    }
}

//suspend fun main(): Unit = coroutineScope {
//    val canvasId = "viz"
//    val canvas = requireNotNull(document.getElementById(canvasId) as HTMLCanvasElement?)
//    { "No canvas in the document corresponding to $canvasId" }
//
//    val url = URL(document.URL)
//    val hash = url.hash.ifEmpty { null }?.substringAfter('#')
//    println("hash: $hash")
//    val playerCount = hash?.toInt() ?: 2
//    require(playerCount in (2..4)) { "player number must be within 2..4" }
//
//    window.addEventListener("resize",
//        EventListener { event ->
//            val size = min(document.documentElement!!.clientHeight, document.documentElement!!.clientWidth)
//            canvas.height = size
//            canvas.width = size
//            with(PentaViz.viz) {
//                height = canvas.height.toDouble()
//                width = canvas.width.toDouble()
//                resize(width, height)
//                render()
//            }
//        }
//    )
//
//    val size = min(document.documentElement!!.clientHeight, document.documentElement!!.clientWidth)
//    canvas.height = size
//    canvas.width = size
//
//    val playerSymbols = listOf("triangle", "square", "cross", "circle")
//
//    with(PentaViz.viz) {
//        height = canvas.height.toDouble()
//        width = canvas.width.toDouble()
//
////        PentaViz.gameState = ClientGameState(
////            playerSymbols.subList(0, playerCount)
////        )
//
//        resize(width, height)
//        bindRendererOn(canvas)
//        addEvents()
//        render()
//    }
//
////    val wsConnection = launch {
//    if (true) {
//        PentaViz.gameState.initialize(playerSymbols.subList(0, playerCount))
//    } else {
//        client.ws(method = HttpMethod.Get, host = "127.0.0.1", port = 55555, path = "/replay") {
//            // this: DefaultClientWebSocketSession
//            println("starting websocket connection")
//            outgoing.send(Frame.Text(replayGame))
//
//            incoming.consumeEach {
//                val textFrame = it as? Frame.Text ?: return@consumeEach
//                val text = textFrame.readText()
//
//                println(text)
//
//                val notation = json.parse(SerialNotation.serializer(), text)
//
//                SerialNotation.toMoves(listOf(notation), PentaViz.gameState, false) { move ->
//                    PentaViz.gameState.processMove(move)
//                }
//            }
//            println("replay over")
//        }
////        }
//    }
//
//}