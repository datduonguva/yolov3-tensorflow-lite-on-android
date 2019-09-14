package datduong.tflite.yolov3

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View

// link is here: https://stackoverflow.com/questions/31173476/android-sdk-camera2-draw-rectangle-over-textureview
class DrawerView:SurfaceView{
    constructor(context:Context): super(context)
    constructor(context: Context, attributeSet: AttributeSet): super(context, attributeSet)
    constructor(context: Context, attributeSet: AttributeSet, defStyleAttr:Int): super(context, attributeSet, defStyleAttr)

    val paint:Paint
    var boxList = mutableListOf<BoxIndexScoreCategory>()
    init {
        paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.RED
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.0f
        holder.setFormat(PixelFormat.TRANSPARENT)
        setZOrderOnTop(true)
        setWillNotDraw(false)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = View.MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, width)

    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        //canvas?.drawColor(Color.TRANSPARENT)
        for(item in boxList){
            println("There is a box")
            println(canvas?.width)
            println(canvas?.height)
            canvas?.drawRect(item.box[1], item.box[0], item.box[3], item.box[2], paint)
        }
    }
}