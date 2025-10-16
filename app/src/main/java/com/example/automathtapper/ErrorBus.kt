package com.example.automathtapper

import android.widget.Toast
import androidx.lifecycle.MutableLiveData

object ErrorBus {
    val live: MutableLiveData<String> = MutableLiveData()
    fun post(message: String) {
        live.postValue(message)
        Toast.makeText(App.instance, message, Toast.LENGTH_SHORT).show()
        ErrorOverlay.show(message)
    }
    fun post(e: Throwable) {
        post(e.message ?: e.toString())
    }
}
