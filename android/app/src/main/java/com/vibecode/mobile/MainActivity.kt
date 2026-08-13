package com.vibecode.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.vibecode.mobile.ui.VibeCodeApp
import com.vibecode.mobile.ui.VibeTheme

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{VibeTheme{VibeCodeApp()}}}
}
