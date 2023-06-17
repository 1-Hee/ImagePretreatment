package com.example.imagetestapp

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.support.label.TensorLabel
import org.tensorflow.lite.support.model.Model
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer


// 주석 출처
// https://medium.com/@sunminlee89/%ED%85%90%EC%84%9C%ED%94%8C%EB%A1%9C-%EB%9D%BC%EC%9D%B4%ED%8A%B8%EB%A5%BC-%ED%99%9C%EC%9A%A9%ED%95%9C-%EC%95%88%EB%93%9C%EB%A1%9C%EC%9D%B4%EB%93%9C-%EB%94%A5%EB%9F%AC%EB%8B%9D-7%EC%9E%A5-8c5af6401b5b
class Classifier(private var context: Context, private val modelName: String) {
    private lateinit var model: Model
    private lateinit var inputImage: TensorImage
    private lateinit var outputBuffer: TensorBuffer
    private var modelInputChannel = 0
    private var modelInputWidth = 0
    private var modelInputHeight = 0
    private val labels = mutableListOf<String>()
    private var isInitialized = false

    fun init() {
        model = Model.createModel(context, modelName)
        initModelShape()
        labels.addAll(FileUtil.loadLabels(context, LABEL_FILE))
        isInitialized = true
    }

    private fun initModelShape() {
        val inputTensor = model.getInputTensor(0)
        val inputShape = inputTensor.shape()
        modelInputChannel = inputShape[0]
        modelInputWidth = inputShape[1]
        modelInputHeight = inputShape[2]

        inputImage = TensorImage(inputTensor.dataType())

        val outputTensor = model.getOutputTensor(0)
        outputBuffer = TensorBuffer.createFixedSize(outputTensor.shape(), outputTensor.dataType())
    }

    fun classify(image: Bitmap, sensorOrientation: Int): Pair<String, Float> {
        inputImage = loadImage(image, sensorOrientation)
        val inputs = arrayOf<Any>(inputImage.buffer)
        val outputs = mutableMapOf<Int, Any>()
        outputs[0] = outputBuffer.buffer.rewind()
        model.run(inputs, outputs)
        val output = TensorLabel(labels, outputBuffer).mapWithFloatValue
        return argmax(output)
    }

    fun classify(image: Bitmap) = classify(image, 0)

    private fun loadImage(bitmap: Bitmap, sensorOrientation: Int): TensorImage {
        if (bitmap.config != Bitmap.Config.ARGB_8888) {
            inputImage.load(convertBitmapToARGB8888(bitmap))
        } else {
            inputImage.load(bitmap)
        }

        /*
            classify() 함수 안에서 회전을 적용하려면 ImageProcessor에 Rot90Op연산을 추가한다.
            Rot90Op연산을 적용하면 이미지가 전처리될 때 numRotation * -90만큼 회전된다.
            값이 1이면 -90회전하고 2이면 -180도 회전하는 식이다.
         */

        val cropSize = Math.min(bitmap.width, bitmap.height)
        val numRotation = sensorOrientation / 90


        val imageProcessor = ImageProcessor.Builder()
            /*
                회전을 추가하면서 이미지의 왜곡을 줄이도록 ResizeWithCropOrPadOp연산도 추가했다.
                이 연산은 딥러닝 모델이 입력받는 이미지의 가로세로 비율과
                카메라가 전달하는 이미지의 가로 세로 비율이 다를 때 유효하다.

                딥러닝 모델은 가로와 세로 길이가 같은 정사각형 이미지를 입력 받지만
                카메라가 전달하는 이미지는 가로와 세로 길이가 다르기 때문에
                가로와 세로 중 길이가 짧은 축을 기준으로 길이가 긴 축을 잘라서 정사각형 이미지를 만든다.

                Resize는 Crop을 적용하지 않고 입력 이미지 크기에 맞게 Resize만 적용한 이미지이고,
                Crop & Resize는 Crop을 적용한 후 Resize한 이미지이다.
                Resize만 적용한 이미지는 원본 이미지의 크기만 바뀌기 때문에
                이미지 위아래의 노란색 영역이 남아 있으며, 이미지의 가로세로 비율이 바뀜으로써
                세로 방향이 왜곡되어 이미지가 찌그러져 보인다.

                그러나 Crop & Resize 이미지는 짧은 축의 길이에 맞추어 Crop을 적용했기 때문에
                이미지 위아래의 노란색 영역이 잘리고 원본 이미지의 비율이 그대로 유지된다.

                실시간 이미지 분류의 특성상 대상 사물을 카메라로 비추며 추론 결과를
                확인하기 때문에 이처럼 Crop을 적용하면 카메라의 화면 비율에 따른
                왜곡을 방지하여 더욱 정확히 추론할 수 있다.
            */
            .add(ResizeWithCropOrPadOp(cropSize, cropSize))
            .add(ResizeOp(modelInputWidth, modelInputHeight, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
            .add(Rot90Op(numRotation))
            .add(NormalizeOp(0.0f, 255.0f))
            .build()

        return imageProcessor.process(inputImage)
    }

    private fun convertBitmapToARGB8888(bitmap: Bitmap) = bitmap.copy(Bitmap.Config.ARGB_8888, true)

    private fun argmax(map: Map<String, Float>) =
        map.entries.maxByOrNull { it.value }?.let {
            it.key to it.value
        } ?: "" to 0f

    fun finish() {
        if (::model.isInitialized) model.close()
        if (isInitialized) isInitialized = false
    }

    fun isInitialized() = isInitialized

    fun getModelInputSize(): Size = if (isInitialized.not()) Size(0, 0) else Size(modelInputWidth, modelInputHeight)

    companion object {
        const val IMAGENET_CLASSIFY_MODEL = "mobilenet_imagenet_model.tflite"
        const val LABEL_FILE = "labels.txt"
    }
}