import io.data2viz.math.DEG_TO_RAD
import penta.PentaColor
import io.data2viz.geom.Point
import penta.field.AbstractField
import penta.field.ConnectionField
import penta.field.CornerField
import penta.field.IntersectionField
import penta.field.JointField
import penta.math.interpolate
import penta.math.length
import kotlin.math.cos
import kotlin.math.sin

object PentaBoard {
    val fields: List<AbstractField>
    val c: Array<CornerField>
    val j: Array<JointField>
    init {
        val corners = PentaColor.values().map { color ->
            //            val pos = PentaMath.fiveRoots(color.root) * PentaMath.r
            val angle = color.ordinal * -72.0
            val pos = Point(
                cos(angle * DEG_TO_RAD),
                sin(angle * DEG_TO_RAD)
            ) * PentaMath.r
            val id = color.ordinal * 2
            CornerField(
//                id.toString()
                id = "c${color.ordinal}",
                pos = pos / 2 + (Point(0.5, 0.5) * PentaMath.R_),
                color = color.color
            )
        }
        c = corners.toTypedArray()
        val joints = PentaColor.values().map { color ->
            //            val pos = PentaMath.fiveRoots(color.root) * -PentaMath.inner_r
            val id = ((color.ordinal + 2) % 5 * 2) + 1
            val angle = color.ordinal * -72.0
            val pos = Point(
                cos(angle * DEG_TO_RAD),
                sin(angle * DEG_TO_RAD)
            ) * -PentaMath.inner_r
            JointField(
                id = "j${color.ordinal}",
//                id.toString(),
                pos = pos / 2 + (Point(0.5, 0.5) * PentaMath.R_),
                color = color.color
            )
        }
        j = joints.toTypedArray()
        val outerSteps = 3
        var angle = 0.0
        val outerRing = (corners + corners.first()).zipWithNext { current, next ->
            current.connectIntersection(next)
            val interpolatedColors = current.color.interpolate(next.color, outerSteps)
            val connectingNodes = (0 until outerSteps).map { i ->
                angle -= 72.0 / 4
                val pos = Point(
                    (PentaMath.r * cos(angle * DEG_TO_RAD)),
                    (PentaMath.r * sin(angle * DEG_TO_RAD))
                )
                // TODO: connect
                ConnectionField(
                    id = "${current.id}${next.id}/${i + 1}",
                    altId = "${next.id}${current.id}/${outerSteps - i}",
                    pos = pos / 2 + (Point(0.5, 0.5) * PentaMath.R_),
//                    Colors.Web.white,
                    color = interpolatedColors[i]
                )
            }
            angle -= 72.0 / 4
            current.connect(connectingNodes.first())
            connectingNodes.zipWithNext { currentNode, nextNode ->
                currentNode.connect(nextNode)
            }
            connectingNodes.last().connect(next)
            connectingNodes
        }.flatten()
        val innerRing = (joints + joints.first()).zipWithNext { current, next ->
            connect(current, next, 3)
        }.flatten()
        val interConnections = joints.mapIndexed { index, current ->
            connect(corners[(index + 2) % corners.size], current, steps = 6) +
                    connect(corners[(index + 3) % corners.size], current, steps = 6)
        }.flatten()
        fields = corners + joints + outerRing + innerRing + interConnections
    }


    // lookup helper
    private val fieldMap = fields.associateBy { it.id } +
            fields.filterIsInstance<ConnectionField>().associateBy { it.altId }

    operator fun get(id: String) = fieldMap[id]

    private fun connect(current: IntersectionField, next: IntersectionField, steps: Int): List<ConnectionField> {
        current.connectIntersection(next)
        val interpolatedPos = current.pos.interpolate(
            next.pos, steps,
            skip = current.radius + (PentaMath.s / 2)
        )
        val interpolatedColors = current.color.interpolate(next.color, steps)
//        println("connecting colors: ${interpolatedColors.size}")
        val connectingNodes = (0 until steps).map { i ->
            val pos = interpolatedPos[i]
            ConnectionField(
                id = "${current.id}${next.id}/${i + 1}",
                altId = "${next.id}${current.id}/${steps - i}",
                pos = pos,
//                Colors.Web.white,
                color = interpolatedColors[i]
            )
        }
        current.connect(connectingNodes.first())
        connectingNodes.zipWithNext { currentNode, nextNode ->
            currentNode.connect(nextNode)
        }
        connectingNodes.last().connect(next)
        return connectingNodes
    }

    fun findFieldAtPos(mousePos: Point): AbstractField? = fields.find {
        (it.pos - mousePos).length < it.radius
    }
}