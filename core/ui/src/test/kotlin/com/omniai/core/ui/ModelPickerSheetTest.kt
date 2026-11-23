package com.omniai.core.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.omniai.core.designsystem.theme.OmniTheme
import com.omniai.core.model.AIModel
import com.omniai.core.model.ApiProtocol
import com.omniai.core.model.AuthConfig
import com.omniai.core.model.ModelKey
import com.omniai.core.model.ProviderCategory
import com.omniai.core.model.ProviderConfig
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModelPickerSheetTest {
    @get:Rule val compose = createComposeRule()

    private val provider = ProviderConfig(
        id = "p1", definitionId = "openai", name = "OpenAI", category = ProviderCategory.OFFICIAL,
        protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS, baseUrl = "https://api.openai.com/v1", auth = AuthConfig.Bearer,
        chatPath = "chat/completions", modelsPath = "models", createdAt = 0, updatedAt = 0,
    )
    private val models = listOf(
        AIModel("p1", "gpt-a", "GPT A", contextWindow = 128_000),
        AIModel("p1", "gpt-b", "gpt-b", isFavorite = true),
    )

    @Test fun selectingListedModelReturnsItsKey() {
        var picked: ModelKey? = null
        compose.setContent {
            OmniTheme { ModelPickerSheet(listOf(provider), models, selected = null, onSelect = { picked = it }, onDismiss = {}) }
        }
        compose.onNodeWithText("GPT A").performClick()
        compose.waitForIdle()
        assertEquals(ModelKey("p1", "gpt-a"), picked)
    }

    @Test fun typingUnknownIdOffersManualEntry() {
        var picked: ModelKey? = null
        compose.setContent {
            OmniTheme { ModelPickerSheet(listOf(provider), models, selected = ModelKey("p1", "gpt-b"), onSelect = { picked = it }, onDismiss = {}) }
        }
        compose.onNodeWithText("Search or type a model ID").performTextInput("my-model")
        compose.onNodeWithText("Use \"my-model\" as model ID").performClick()
        compose.waitForIdle()
        assertEquals(ModelKey("p1", "my-model"), picked)
    }
}
