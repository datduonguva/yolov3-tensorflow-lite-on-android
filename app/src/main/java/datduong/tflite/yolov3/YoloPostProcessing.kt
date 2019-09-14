package datduong.tflite.yolov3

import kotlin.random.Random
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

typealias Array2 = Array<FloatArray>
typealias Array3 = Array<Array<FloatArray>>
typealias Array4 = Array<Array<Array<FloatArray>>>
typealias Array5 = Array<Array<Array<Array<FloatArray>>>>
data class YoloAllPredictions(val boxXY: Array5,
                              val boxWH: Array5,
                              val boxConfidence: Array5,
                              val boxClassProbs: Array5)

data class BoxIndexScoreCategory(val box: FloatArray, val score:Float, val index:Int, val category:Int)
data class YoloBoxesAndScores(val boxes: Array2, val scores: Array2)
data class PredictedBoxes(val boxes:Array2,
                          val scores:FloatArray,
                          val classes: IntArray)

fun sigmoid(x:Float):Float{
    return 1.0f/(1.0f + exp(-x))
}

fun getAllYoloPrediction(feature:Array4, anchors:Array2): YoloAllPredictions{
    val numAnchors = anchors.size
    val gridH = feature[0].size
    val gridW = feature[0][0].size
    val inputW = 416.0f
    val inputH = 416.0f
    val numFeature = feature[0][0][0].size / numAnchors

    // Reshape the feature:
    val reshapedFeature = Array(1){Array(gridH){Array(gridW){Array(numAnchors){FloatArray(numFeature){0.0f}}}}} 
    for(i in 0 until gridH){
        for(j in 0 until gridW){
            for (k in 0 until numAnchors){
                for(l in 0 until numFeature){
                    reshapedFeature[0][i][j][k][l] = feature[0][i][j][k*numFeature + l]
                }
            }
        }
    }
    // Extract Box center
    val boxXY = Array(1){Array(gridH){Array(gridW){Array(numAnchors){FloatArray(2){0.0f}}}}}
    for (i in 0 until gridH){
        for(j in 0 until gridW){
            for (k in 0 until numAnchors){
                // X coordinate
                boxXY[0][i][j][k][0] = (sigmoid(reshapedFeature[0][i][j][k][0]) + j.toFloat())/gridW.toFloat()
                // Y coordinate
                boxXY[0][i][j][k][1] = (sigmoid(reshapedFeature[0][i][j][k][1]) + i.toFloat())/gridH.toFloat()
            }
        }
    }

    // Extract Box size
    val boxWH = Array(1){Array(gridH){Array(gridW){Array(numAnchors){FloatArray(2){0.0f}}}}}
    for (i in 0 until gridH){
        for(j in 0 until gridW){
            for (k in 0 until numAnchors){
                // X coordinate
                boxWH[0][i][j][k][0] = exp(reshapedFeature[0][i][j][k][2])*anchors[k][0]/inputW
                // Y coordinate
                boxWH[0][i][j][k][1] = exp(reshapedFeature[0][i][j][k][3])*anchors[k][1]/inputH
            }
        }
    }

    // Extract Box confidence
    val boxConfidence = Array(1){Array(gridH){Array(gridW){Array(numAnchors){FloatArray(1){0.0f}}}}}
    for (i in 0 until gridH){
        for(j in 0 until gridW){
            for (k in 0 until numAnchors){
                boxConfidence[0][i][j][k][0] = sigmoid(reshapedFeature[0][i][j][k][4])
            }
        }
    }

    // Extract Class Probability
    val boxClassProbs = Array(1){Array(gridH){Array(gridW){Array(numAnchors){FloatArray(numFeature - 5){0.0f}}}}}
    for (i in 0 until gridH){
        for(j in 0 until gridW){
            for (k in 0 until numAnchors){
                for(l in 0 until numFeature-5){
                    boxClassProbs[0][i][j][k][l] = sigmoid(reshapedFeature[0][i][j][k][5 + l])
                }
            }
        }
    }
    return YoloAllPredictions(boxXY, boxWH, boxConfidence, boxClassProbs)
}

fun getYoloCorrectBoxes(yoloAllPredictions:YoloAllPredictions, imageH:Float, imageW:Float):Array5{
    val inputW = 416f
    val inputH = 416f
    val scale = minOf(inputH/imageH, inputW/imageW)
    val prepH = scale*imageH
    val prepW = scale*imageW
    val offsetX = (inputW - prepW)/2.0f/inputW
    val offsetY = (inputH - prepH)/2.0f/inputH
    val scaleX = inputW/prepW
    val scaleY = inputH/prepH
    val gridH = yoloAllPredictions.boxXY[0].size
    val gridW = yoloAllPredictions.boxXY[0][0].size
    val numAnchors = yoloAllPredictions.boxXY[0][0][0].size
    for (i in 0 until gridH){
        for(j in 0 until gridW){
            for (k in 0 until numAnchors){
                yoloAllPredictions.boxXY[0][i][j][k][0] = (yoloAllPredictions.boxXY[0][i][j][k][0] - offsetX)*scaleX
                yoloAllPredictions.boxXY[0][i][j][k][1] = (yoloAllPredictions.boxXY[0][i][j][k][1] - offsetY)*scaleY
                yoloAllPredictions.boxWH[0][i][j][k][0] *= scaleX
                yoloAllPredictions.boxWH[0][i][j][k][1] *= scaleY
            }
        }
    }
    val boxes = Array(1){Array(gridH){Array(gridW){Array(numAnchors){FloatArray(4){0.0f}}}}}
    for (i in 0 until gridH){
        for(j in 0 until gridW){
            for (k in 0 until numAnchors){
                val x = yoloAllPredictions.boxXY[0][i][j][k][0]
                val y = yoloAllPredictions.boxXY[0][i][j][k][1]
                val w = yoloAllPredictions.boxWH[0][i][j][k][0]
                val h = yoloAllPredictions.boxWH[0][i][j][k][1]
                boxes[0][i][j][k][0] = (y - h/2.0f)*imageH
                boxes[0][i][j][k][1] = (x - w/2.0f)*imageW
                boxes[0][i][j][k][2] = (y + h/2.0f)*imageH
                boxes[0][i][j][k][3] = (y + w/2.0f)*imageW
            }
        }
    }
    return boxes
}

fun getYoloBoxesAndScores(yoloAllPredictions:YoloAllPredictions):YoloBoxesAndScores{
    val boxes = getYoloCorrectBoxes(yoloAllPredictions, 1080f, 1080f)
    val gridH = yoloAllPredictions.boxXY[0].size
    val gridW = yoloAllPredictions.boxXY[0][0].size
    val numAnchors = yoloAllPredictions.boxXY[0][0][0].size
    val numClasses = yoloAllPredictions.boxClassProbs[0][0][0][0].size
    val reshapedBoxes = Array(gridH*gridW*numAnchors){FloatArray(4){0.0f}}
    val reshapedScores = Array(gridH*gridW*numAnchors){FloatArray(numClasses){0.0f}}
    for (i in 0 until gridH){
        for(j in 0 until gridW){
            for (k in 0 until numAnchors){
                for (l in 0 until 4){
                    reshapedBoxes[k + j*numAnchors + i*numAnchors*gridW][l] = boxes[0][i][j][k][l]
                }
                for (l in 0 until numClasses){
                    reshapedScores[k+ j*numAnchors + i*numAnchors*gridW][l] = 
                        yoloAllPredictions.boxClassProbs[0][i][j][k][l]*yoloAllPredictions.boxConfidence[0][i][j][k][0]
                }
            }
        }
    }
    return YoloBoxesAndScores(reshapedBoxes, reshapedScores)
}

fun printArray2(arr:Array2){
    for (i in 0 until arr.size){
        print("$i: ")
        for(j in arr[i]){
            print("$j, ")
        }
        println("")
    }
}

fun getIOU(box1:FloatArray, box2:FloatArray):Float{
    val top = max(box1[0], box2[0])
    val left = max(box1[1], box2[1])
    val bottom = min(box1[2], box2[2])
    val right = min(box1[3], box2[3])
    val width = max(right - left, 0.0f)
    val height = max(bottom - top, 0.0f)
    val area1 = max((box1[3]-box1[1])*(box1[2] - box1[0]), 0.0f)
    val area2 = max((box2[3]-box2[1])*(box2[2] - box2[0]), 0.0f)
    return width*height/(area1 + area2 - width*height + 0.00001f)
}

fun nonMaxSuppress(boxes:Array2, scores:FloatArray, maxBoxes:Int, iouThreshold:Float, category:Int):
        MutableList<BoxIndexScoreCategory>{
    var boxIndexScoreCategory = mutableListOf<BoxIndexScoreCategory>()
    for(i in 0 until boxes.size){
        boxIndexScoreCategory.add(BoxIndexScoreCategory(boxes[i], scores[i], i, category))
    }
    boxIndexScoreCategory = boxIndexScoreCategory.sortedWith(compareBy({it.score})).toMutableList()

    val selected = mutableListOf<BoxIndexScoreCategory>()
    while ((selected.size < maxBoxes) && (boxIndexScoreCategory.size != 0)){
        val candidate = boxIndexScoreCategory.last()
        boxIndexScoreCategory = boxIndexScoreCategory.dropLast(1).toMutableList()
        var shouldSelect = true
        for(i in selected.size -1  downTo 0){
            val iou = getIOU(candidate.box, selected[i].box)
            if (iou >= iouThreshold){
                shouldSelect = false
                break
            }
        }

        if (shouldSelect){
            selected.add(candidate)
        }
    }
    return selected
}

fun yoloEval(output1:Array4, output2: Array4, anchors: Array2, score_threshold:Float):MutableList<BoxIndexScoreCategory>{
    val head1 = getAllYoloPrediction(output1, anchors.slice(3..5).toTypedArray())
    val head2 = getAllYoloPrediction(output2, anchors.slice(0..2).toTypedArray())

    val yoloBoxesAndScores1 = getYoloBoxesAndScores(head1)
    val yoloBoxesAndScores2 = getYoloBoxesAndScores(head2)

    var boxes = yoloBoxesAndScores1.boxes + yoloBoxesAndScores2.boxes
    var scores = yoloBoxesAndScores1.scores + yoloBoxesAndScores2.scores

    val numclass = head1.boxClassProbs[0][0][0][0].size
    val result = mutableListOf<BoxIndexScoreCategory>()
    for(classIndex in 0 until numclass){
        val mBoxes = (boxes.filterIndexed {s, _ -> scores[s][classIndex] > score_threshold}).toTypedArray()
        val tmpScore = (scores.filterIndexed {s, _ -> scores[s][classIndex] > score_threshold}).toTypedArray()
        val mScore = FloatArray(tmpScore.size){it -> tmpScore[it][classIndex]}
        
        val nmsIndices = nonMaxSuppress(mBoxes, mScore, 3, 0.5f,  classIndex)
        result.addAll(nmsIndices)
    }
    return result
}
