package com.tharunbirla.librecuts.ai

import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiGatewayWindowTest {
    @Test fun keyInputHasSecureDialogWindow() {
        ActivityScenario.launch(AiGatewayActivity::class.java).use {
            onView(withText("Chave: Groq")).perform(click())
            onView(withText("Cadastrar / substituir")).inRoot(isDialog()).perform(click())
            onView(withHint("Chave de API")).inRoot(isDialog()).check(matches(
                object : BaseMatcher<android.view.View>() {
                    override fun matches(item: Any?): Boolean {
                        val params = (item as? android.view.View)?.rootView?.layoutParams as? WindowManager.LayoutParams
                        return params != null && params.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
                    }
                    override fun describeTo(description: Description) {
                        description.appendText("key dialog root window has FLAG_SECURE")
                    }
                }
            ))
        }
    }
}
