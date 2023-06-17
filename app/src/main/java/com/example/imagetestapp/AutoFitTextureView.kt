package com.example.imagetestapp

import android.content.Context
import android.util.AttributeSet
import android.view.TextureView

// MainActivity에서 바로 이미지를 처리할 수 있게 만들기 위해 커스텀 뷰를 만든다.
/*
AutoFitTextureView는 동영상이나 3D렌더링 화면 등 지속적으로
바뀌는 콘텐츠를 화면에 출력하기 위한 TextureView를 상속한 클래스이다.
이미지의 가로세로 비율과 TextureView의 가로세로 비율이 맞지 않으면
카메라를 통해 들어오는 이미지가 왜곡될 수 있으므로 이를 맞추기 위해 사용된다.
 */
class AutoFitTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr) {
    private var ratioWidth = 0
    private var ratioHeight = 0

    fun setAspectRation(width: Int, height: Int) {
        if (width < 0 || height < 0) throw IllegalArgumentException("Size cannot be negative!")
        ratioHeight = width
        ratioHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        if (ratioWidth == 0 || ratioHeight == 0) {
            setMeasuredDimension(width, height)
        } else {
            if (width < height * ratioWidth / ratioHeight) {
                setMeasuredDimension(width, width * ratioHeight / ratioWidth)
            } else {
                setMeasuredDimension(height * ratioWidth / ratioHeight, height)
            }
        }
    }

}