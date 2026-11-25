package com.example.nutritrack.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AiFood(val name: String, val grams: Double, val kcal: Double, val p: Double, val c: Double, val f: Double)
data class AiResult(val items: List<AiFood>, val notes: String)

object AiClient {
    const val MODEL = "claude-sonnet-5-5"

    private const val FOOD_SYSTEM = "Eres un nutricionista. El usuario te da una descripción o una foto de alimentos. " +
        "Responde SOLO con JSON válido, sin texto extra, con esta forma: " +
        "{\"items\":[{\"name\":\"\",\"grams\":0,\"kcal\":0,\"p\":0,\"c\":0,\"f\":0}],\"notes\":\"\"}. " +
        "Cada item es un alimento con la cantidad TOTAL estimada (grams en gramos o ml), sus kcal y gramos de proteína (p), " +
        "carbohidratos (c) y grasas (f) para esa cantidad. Si no hay cantidad, asume una porción típica y dilo en notes. " +
        "En fotos, estima porciones por el tamaño visible y menciona la incertidumbre en notes. Todo en español."

    private const val ADVICE_SYSTEM = "Eres un entrenador y nutricionista deportivo. Con los datos de las últimas semanas del usuario, " +
        "da recomendaciones concretas y breves en español (máximo 8 viñetas) alineadas a su objetivo: qué está bien, qué ajustar " +
        "(calorías, proteína, agua, constancia) y 2 o 3 acciones para la próxima semana. No diagnostiques ni prometas resultados. " +
        "Si las metas son extremas o hay señales de riesgo, sugiere consultar a un profesional de la salud."

    private suspend fun call(key: String, system: String, content: JSONArray, maxTokens: Int): String =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("model", MODEL).put("max_tokens", maxTokens).put("system", system)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            val conn = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 20000
            conn.readTimeout = 90000
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            conn.setRequestProperty("x-api-key", key)
            conn.setRequestProperty("anthropic-version", "2023-06-01")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val hint = when (code) { 401 -> "Clave de API inválida."; 429 -> "Demasiadas consultas, espera un momento."
                    400 -> "Solicitud rechazada (¿saldo insuficiente o modelo no disponible?)."; else -> "" }
                throw Exception("Error $code. $hint ${text.take(160)}")
            }
            val arr = JSONObject(text).getJSONArray("content")
            (0 until arr.length()).map { arr.getJSONObject(it) }
                .filter { it.optString("type") == "text" }.joinToString("") { it.getString("text") }
        }

    private fun parse(raw: String): AiResult {
        val s = raw.indexOf('{'); val e = raw.lastIndexOf('}')
        if (s < 0 || e <= s) throw Exception("Respuesta inesperada de la IA.")
        val o = JSONObject(raw.substring(s, e + 1))
        val a = o.optJSONArray("items") ?: JSONArray()
        val items = (0 until a.length()).map {
            val x = a.getJSONObject(it)
            AiFood(x.optString("name", "Alimento"), x.optDouble("grams", 0.0), x.optDouble("kcal", 0.0),
                x.optDouble("p", 0.0), x.optDouble("c", 0.0), x.optDouble("f", 0.0))
        }
        return AiResult(items, o.optString("notes", ""))
    }

    private fun textBlock(t: String) = JSONObject().put("type", "text").put("text", t)

    suspend fun analyzeText(key: String, query: String): AiResult =
        parse(call(key, FOOD_SYSTEM, JSONArray().put(textBlock("Alimento o plato: $query")), 900))

    suspend fun analyzePhoto(key: String, file: File): AiResult {
        val b64 = withContext(Dispatchers.IO) { loadScaled(file) }
        val img = JSONObject().put("type", "image").put("source",
            JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", b64))
        return parse(call(key, FOOD_SYSTEM, JSONArray().put(img).put(textBlock("Identifica los alimentos de la foto y estima porciones y nutrientes.")), 1200))
    }

    suspend fun advice(key: String, summary: String): String =
        call(key, ADVICE_SYSTEM, JSONArray().put(textBlock(summary)), 900)

    private fun loadScaled(file: File): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
        var bmp: Bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw Exception("No pude leer la foto.")
        val deg = when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
            6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f
        }
        if (deg != 0f) { val m = Matrix(); m.postRotate(deg); bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true) }
        val f = 1280f / maxOf(bmp.width, bmp.height)
        if (f < 1f) bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt(), (bmp.height * f).toInt(), true)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
