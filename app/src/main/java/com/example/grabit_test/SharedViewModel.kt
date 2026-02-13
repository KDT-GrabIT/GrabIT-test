package com.example.grabitTest

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * MainActivity/탭 간 공유 상태.
 * AdminFragment에서 객체 선택 시 HomeFragment로 타겟 전달 및 탐지 모드 진입 요청.
 * 탭 전환 시 전역 상태 초기화 트리거.
 */
class SharedViewModel : ViewModel() {

    /** 관리자에서 선택한 타겟 클래스. HomeFragment가 구독하여 탐지 시작 */
    private val _adminSelectedTarget = MutableLiveData<String?>()
    val adminSelectedTarget: LiveData<String?> = _adminSelectedTarget

    /** 관리자에서 객체 선택 시 호출. Home 탭으로 전환 후 탐지 시작 */
    fun selectTargetFromAdmin(targetLabel: String) {
        _adminSelectedTarget.value = targetLabel
    }

    /** HomeFragment가 이벤트를 처리한 후 소비 */
    fun consumeAdminSelectedTarget() {
        _adminSelectedTarget.value = null
    }

}
