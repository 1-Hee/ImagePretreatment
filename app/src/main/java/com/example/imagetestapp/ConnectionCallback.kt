package com.example.imagetestapp

import android.util.Size

// 우리가 미리보기 화면을 만들 때, 현재 화면의 사이즈와 화면 방향을 전달해주는 용도
interface ConnectionCallback {
    fun onPreviewSizeChosen(size: Size, cameraRotation: Int)
}