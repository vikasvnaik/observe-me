package com.vikas.observeme.data.recognition

import android.graphics.BitmapFactory
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.content
import com.vikas.observeme.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiAnalyzer @Inject constructor() {

    // Labels that trigger a Gemini deep-analysis call.
    val interestingLabels = setOf(
        // Food
        "Food", "Fruit", "Vegetable", "Cuisine", "Dish", "Meal", "Snack",
        "Drink", "Beverage", "Fast food", "Baked goods", "Dessert", "Produce",
        "Ingredient", "Staple food",
        // Medicine / health
        "Bottle", "Pill", "Tablet", "Capsule", "Medicine", "Pharmaceutical",
        "Medical equipment", "Drug",
        // Nature / other
        "Plant", "Flower", "Animal", "Pet", "Herb"
    )

    private val model = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY,
        requestOptions = RequestOptions(apiVersion = "v1")
    )

    suspend fun analyze(jpegBytes: ByteArray, triggerLabel: String): String {
        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            ?: return "Could not decode image"
        return try {
            val response = model.generateContent(
                content {
                    image(bitmap)
                    text(
                        "An object detector flagged '$triggerLabel' in this image. " +
                        "Identify exactly what you see. " +
                        "If it is food: name it and give 1-2 key nutritional highlights. " +
                        "If it is medicine or a supplement: name it and describe its common use. " +
                        "Otherwise: describe what it is briefly. " +
                        "Be concise — 2 sentences maximum."
                    )
                }
            )
            response.text?.trim() ?: "Could not identify"
        } catch (e: com.google.ai.client.generativeai.type.QuotaExceededException) {
            Log.w("GeminiAnalyzer", "Quota exceeded")
            "Daily analysis limit reached. Try again tomorrow."
        } catch (e: Exception) {
            Log.e("GeminiAnalyzer", "Analysis failed [${e.javaClass.simpleName}]: ${e.message}", e)
            "Error: ${e.javaClass.simpleName} — ${e.message?.take(100)}"
        }
    }
}
