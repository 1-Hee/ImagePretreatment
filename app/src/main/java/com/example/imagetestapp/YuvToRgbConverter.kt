package com.example.imagetestapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import android.renderscript.*

/*
이제 YuvToRgbConverter이름의 object 파일
코틀린에서 object는 싱글턴 방식의 클래스처럼 사용될 수 있다.

해당 object에서는 YUV 포맷의 이미지를 ARGB_8888 포맷의 Bitmap으로 변환해주는 역할을 한다.
우리는 카메라를 이용해서 실시간으로 이미지를 받아올 것인데,
이때 YUV_420_888이라는 이미지 포맷을 사용한다.

카메라로부터 스틸 이미지 1장을 가져올 때는 이미지 퀄리티가 높은 JPEG 포맷이 권장되지만,
연속된 이미지 프레임을 가져올 때는 효율성이 좋은 YUV_420_888이 권장된다.

YUV는 밝기(휘도)를 나타내는 Y와 색 정보를 나타내는 U(Blue), V(Red)로 구성된 이미지 포맷이다.
 */

object YuvToRgbConverter {

    fun yuvToRgb(context: Context, image: Image, output: Bitmap) {
        val rs = RenderScript.create(context)
        val scriptYuvToRGB = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))
        val pixelCount = image.cropRect.width() * image.cropRect.height()
        val pixelSizeBits = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888)
        val yuvBuffer = ByteArray(pixelCount * pixelSizeBits / 8)

        imageToByteArray(image, yuvBuffer, pixelCount)

        val elemType = Type.Builder(rs, Element.YUV(rs))
            .setYuvFormat(ImageFormat.NV21)
            .create()
        val inputAllocation = Allocation.createSized(rs, elemType.element, yuvBuffer.size)
        val outputAllocation = Allocation.createFromBitmap(rs, output)

        inputAllocation.copyFrom(yuvBuffer)
        scriptYuvToRGB.setInput(inputAllocation)
        scriptYuvToRGB.forEach(outputAllocation)
        outputAllocation.copyTo(output)
    }

    private fun imageToByteArray(image: Image, outputBuffer: ByteArray, pixelCount: Int) {
        val imageCrop = image.cropRect
        val imagePlanes = image.planes

        for (planeIndex in imagePlanes.indices) {
            val plane = imagePlanes[planeIndex]
            val outputStride: Int
            var outputOffset: Int
            when (planeIndex) {
                0 -> {
                    outputStride = 1
                    outputOffset = 0
                }
                1 -> {
                    outputStride = 2
                    outputOffset = pixelCount + 1
                }
                2 -> {
                    outputStride = 2
                    outputOffset = pixelCount
                }
                else -> {
                    return
                }
            }

            val planeBuffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride

            val planeCrop = if (planeIndex == 0) {
                imageCrop
            } else {
                imageCrop.run {
                    Rect(left / 2, top / 2, right / 2, bottom / 2)
                }
            }

            val planeWidth = planeCrop.width()
            val planeHeight = planeCrop.height()

            val rowBuffer = ByteArray(plane.rowStride)

            val rowLength = if (pixelStride == 1 && outputStride == 1) {
                planeWidth
            } else {
                (planeWidth - 1) * pixelStride + 1
            }

            for (row in 0 until planeHeight) {
                planeBuffer.position((row + planeCrop.top) * rowStride + planeCrop.left * pixelStride)
                if (pixelStride == 1 && outputStride == 1) {
                    planeBuffer.get(outputBuffer, outputOffset, rowLength)
                    outputOffset += rowLength
                } else {
                    planeBuffer.get(rowBuffer, 0, rowLength)
                    for (col in 0 until planeWidth) {
                        outputBuffer[outputOffset] = rowBuffer[col * pixelStride]
                        outputOffset += outputStride
                    }
                }
            }


        }
    }
}