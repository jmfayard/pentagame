package penta.field

import io.data2viz.color.Color
import io.data2viz.geom.Point

data class JointField(
    override val id: String,
    override val pos: Point,
    override val color: Color
): IntersectionField() {
    override val connected: List<AbstractField>
        get() = connectedFields
}