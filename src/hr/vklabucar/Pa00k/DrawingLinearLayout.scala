package hr.vklabucar.Pa00k

import android.widget.LinearLayout
import android.util.{Log, AttributeSet}
import android.content.Context
import android.graphics.{Canvas, Paint}
import android.graphics.Paint.Style._

class DrawingLinearLayout(context: Context, attrs: AttributeSet)
  extends LinearLayout(context, attrs) {

  var p1 = Point(0, 0) // centre of the first circle
  var p2 = Point(0, 0) // centre of the first circle
  var r = 255          // red
  var g = 255          // green
  var b = 255          // blue
  var a1 = 0           // aplha, first circle
  var a2 = 0           // aplha, second circle
  val radius = 120     // radius

  val paint = new Paint()
  paint.setAntiAlias(true)
  paint.setARGB(a1, r, g, b)
  paint.setStyle(STROKE)
  paint.setStrokeWidth(14f)
  setFocusable(true)

  Log.d("DrawingLinearLayout", "Instance created.")
  /**
   * DrawingLinearLayout method, overridden to draw a circles with custom Paint, radius and position.
   * @param canvas Android canvas
   */
  override def onDraw(canvas: Canvas) = {
    randColor()
    paint.setARGB(a1, r, g, b)
    canvas.drawCircle(p1.x, p1.y, radius, paint)

    randColor()
    paint.setARGB(a2, r, g, b)
    canvas.drawCircle(p2.x, p2.y, radius, paint)
    Log.d("onDraw", "Drawing circle.")
  }

  /**
   * Draws the circles on canvas.
   * @param p1 centre of the first circle
   * @param p2 centre of the second circle
   */
  def drawCircle(a1: Int, a2:Int, p1: Point, p2: Point): Unit = {
    this.p1 = p1
    this.p2 = p2
    this.a1 = a1
    this.a2 = a2
    invalidate()
  }

  /**
   * Sets alpha to 0, effectively hiding the circle.
   */
  def hideCircle(a1: Int, a2: Int): Unit = {
    this.a1 = a1
    this.a2 = a2
    invalidate()
  }

  /**
   * Randomizes r, g and b vars.
   */
  def randColor(): Unit = {
    r = (Math.random() * 200).toInt + 55
    g = (Math.random() * 200).toInt + 55
    b = (Math.random() * 200).toInt + 55
  }
}
