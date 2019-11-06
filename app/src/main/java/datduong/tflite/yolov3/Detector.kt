package datduong.tflite.yolov3

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.lang.Exception
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class Detector (activity: Activity, numThread: Int ) {
    val DIM_BATCH_SIZE = 1
    val DIM_PIXEL_SIZE = 3
    val intValues = IntArray(416*416)

    val tfliteOptions = Interpreter.Options()
    var tfliteModel:MappedByteBuffer
    var labels = mutableListOf<String>()
    var anchors: Array<FloatArray>
    var gpuDelegate:GpuDelegate
    var tflite:Interpreter
    var imgData:ByteBuffer
    var output1 = Array(1){Array(13){Array(13){ FloatArray(36){0.0f} } } }
    var output2 = Array(1){Array(26){Array(26){FloatArray(36){0.0f} } } }
    val outputMapper = mutableMapOf<Int, Any>()
    var detectedBoxes = mutableListOf<BoxIndexScoreCategory>()

    init {
        tfliteModel = loadModelFile(activity)
        gpuDelegate = GpuDelegate()
        tfliteOptions.addDelegate(gpuDelegate)
        tfliteOptions.setNumThreads(numThread)
        tflite = Interpreter(tfliteModel, tfliteOptions)
        labels = loadLabelList(activity)
        anchors = loadAnchors(activity)
        for (i in 0 until  anchors.size) {
            println("Anchors: ${anchors[i][0]}, ${anchors[i][1]}")
        }
        imgData = ByteBuffer.allocateDirect(DIM_BATCH_SIZE*416*416*DIM_PIXEL_SIZE*getNumBytesPerChannel())

        imgData.order(ByteOrder.nativeOrder())
        outputMapper.put(0, output1)
        outputMapper.put(1, output2)
    }

    fun loadLabelList(activity: Activity):MutableList<String>{
        val result = mutableListOf<String>()
        val reader = BufferedReader(InputStreamReader(activity.assets.open(getLabelPath())))
        var line = reader.readLine()
        while (line != null){
            result.add(line)
            line = reader.readLine()
        }
        reader.close()
        return result
    }

    fun loadModelFile(activity: Activity):MappedByteBuffer{
        val fileDescriptor = activity.assets.openFd(getModelPath())
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        var startOffset = fileDescriptor.startOffset
        var declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun loadAnchors(activity: Activity):Array<FloatArray>{
        val reader = BufferedReader(InputStreamReader(activity.assets.open(getAnchorPath())))
        val text = mutableListOf<String>()
        var line = reader.readLine()
        while (line != null){
            text.add(line)
            line = reader.readLine()
        }
        if (text.size == 0) throw Exception("Error loading anchors")
        val anchorWidthHeight = text[0].trim().replace(" ", "").split(",").map{num -> num.toFloat()}
        val nAnchors = anchorWidthHeight.size /2
        val result = Array(nAnchors){FloatArray(2){0.0f}}
        for (i in 0 until  nAnchors){
            result[i][0] = anchorWidthHeight[i*2]
            result[i][1] = anchorWidthHeight[i*2 + 1]
        }
        return result
    }

    fun convertBitmapToByteBuffer(bitmap: Bitmap){
        if (imgData == null){
            return;
        }
        imgData.rewind()
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var pixel = 0
        for(i in 0 until 416){
            for(j in 0 until 416){
                val value = intValues[pixel++]
                addPixelValue(value)
            }
        }
    }



    fun close(){
        if (tflite != null){
            tflite.close()
        }
        if (gpuDelegate != null){
            gpuDelegate.close()
        }
    }

    fun getLabelPath():String{
        return "classes.txt"
    }

    fun getModelPath():String{
        return "model.tflite"
    }

    fun getAnchorPath():String{
        return "anchors.txt"
    }

    fun addPixelValue(value:Int){
        // This functions get the RGB values of the pixel
        imgData.putFloat(((value shr 16) and 0XFF).toFloat()/255.0f)
        imgData.putFloat(((value shr 8) and 0XFF).toFloat()/255.0f)
        imgData.putFloat(((value and 0XFF).toFloat()/255.0f))

    }

    fun getNumBytesPerChannel():Int{
        return 4
    }

    fun getNumLabels():Int{
        return labels.size
    }

    fun recognizeImage(bitmap: Bitmap){
        println("I am called")
        convertBitmapToByteBuffer(bitmap)
        tflite.runForMultipleInputsOutputs(Array(1){imgData}, outputMapper)
        detectedBoxes = yoloEval(output1, output2, anchors, 0.5f)
    }
    fun printOutput1(){
        for(n_i in output1){
            for(height in n_i){
                for(width in height){
                    for (n_feaure in 0 until  3){
                        print("${width[n_feaure]} ")
                    }
                    print("\n")
                }
            }
        }
    }

}
