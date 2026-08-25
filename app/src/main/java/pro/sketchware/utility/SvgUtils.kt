package pro.sketchware.utility

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.ImageView
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.load
import coil.request.ImageRequest
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlSerializer
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.math.roundToInt


const val SIZE_MULTIPLIER = 2

class SvgUtils(private val context: Context) {
    private var imageLoader: ImageLoader? = null

    init {
        initImageLoader()
    }

    fun initImageLoader() {
        imageLoader = ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }


    fun loadImage(imageView: ImageView, filePath: String) {
        val file = File(filePath)
        if (file.exists()) {
            try {
                val request: ImageRequest = ImageRequest.Builder(context)
                    .data(file)
                    .target(imageView)
                    .build()

                imageLoader!!.enqueue(request)
            } catch (e: Exception) {
                Log.e("SvgUtils", "Failed to load image: $filePath", e)
            }
        } else {
            Log.e("SvgUtils", "Image file does not exist: $filePath")
        }
    }

    fun loadImage(imageView: ImageView, filePath: String, width: Int, height: Int) {
        val file = File(filePath)
        if (file.exists()) {
            try {
                val request: ImageRequest = ImageRequest.Builder(context)
                    .allowConversionToBitmap(true)
                    .data(file)
                    .target(imageView)
                    .build()

                imageLoader!!.enqueue(request)
            } catch (e: Exception) {
                Log.e("SvgUtils", "Failed to load sized image: $filePath", e)
            }
        } else {
            Log.e("SvgUtils", "Image file does not exist: $filePath")
        }
    }

    fun loadScaledSvgIntoImageView(
        imageView: ImageView,
        svgPath: String,
        scaleFactor: Float = 0.5f // Default scaling factor if none is provided
    ) {
        // Create an ImageLoader with SVG support
        val imageLoader = ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()

        // Load the SVG image and apply scaling
        val request = ImageRequest.Builder(context)
            .data(svgPath)
            .target { drawable ->
                drawable.let {
                    // Get density scaling
                    val densityScale =
                        (context.resources.displayMetrics.density * scaleFactor).roundToInt()
                    val bitmap = drawable.toBitmap()
                    // Apply scaling to width and height
                    val scaledBitmap = Bitmap.createScaledBitmap(
                        bitmap,
                        48 * densityScale,
                        48 * densityScale,
                        true
                    )
                    // Set the scaled image on the ImageView
                    imageView.setImageBitmap(scaledBitmap)
                }
            }
            .build()

        imageLoader.enqueue(request)
    }

    fun loadWithoutQueue(imageView: ImageView, filePath: String) {
        imageView.load(filePath) {
            decoderFactory { result, options, _ -> SvgDecoder(result.source, options) }
        }
    }

    fun convert(inputFilePath: String, outputDir: String, fillColor: String?) {
        try {
            Files.createDirectories(Paths.get(outputDir))
            convertToVector(inputFilePath, outputDir, fillColor)
        } catch (e: Exception) {
            Log.e("SvgUtils", "convert failed input=$inputFilePath outputDir=$outputDir", e)
            throw e
        }
    }

    private fun createModifiedSvg(
        inputFilePath: String,
        inputData: String,
        fillColor: String
    ): String {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true

        val inputParser = factory.newPullParser()
        inputParser.setInput(StringReader(inputData))

        val writer = StringWriter()
        val serializer = factory.newSerializer()
        serializer.setOutput(writer)

        // Start document
        serializer.startDocument("UTF-8", true)

        var depth = 0
        while (inputParser.eventType != XmlPullParser.END_DOCUMENT) {
            when (inputParser.eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = inputParser.name
                    serializer.startTag(inputParser.namespace, tag)

                    // Copy all attributes
                    for (i in 0 until inputParser.attributeCount) {
                        val attrName = inputParser.getAttributeName(i)
                        val attrValue = inputParser.getAttributeValue(i)

                        // Replace fill color if this is a path, circle, rect, or ellipse
                        if (attrName == "fill" && shouldApplyFillColor(tag)) {
                            serializer.attribute(null, attrName, fillColor)
                        } else {
                            serializer.attribute(null, attrName, attrValue)
                        }
                    }

                    // Add fill attribute if it doesn't exist for relevant tags
                    if (shouldApplyFillColor(tag) && !hasFillAttribute(inputParser)) {
                        serializer.attribute(null, "fill", fillColor)
                    }

                    depth++
                }

                XmlPullParser.END_TAG -> {
                    serializer.endTag(inputParser.namespace, inputParser.name)
                    depth--
                }

                XmlPullParser.TEXT -> {
                    serializer.text(inputParser.text)
                }
            }
            inputParser.next()
        }

        serializer.endDocument()

        // Save modified SVG
        File(inputFilePath).nameWithoutExtension
        Files.write(Paths.get(inputFilePath), writer.toString().toByteArray())

        return inputFilePath
    }

    private fun convertToVector(inputFilePath: String, outputDir: String, fillColor: String?) {
        // Read the SVG file
        val data = String(Files.readAllBytes(Paths.get(inputFilePath)))

        // Create XML parser factory
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true

        // Parse the input SVG
        val inputParser = factory.newPullParser()
        inputParser.setInput(StringReader(data))

        // Extract SVG attributes
        val svgAttributes = extractSvgAttributes(inputParser)

        // Create output XML
        val writer = StringWriter()
        val serializer = factory.newSerializer()
        serializer.setOutput(writer)

        // Write the vector drawable
        writeVectorDrawable(serializer, svgAttributes, inputParser, fillColor)

        // Save the output file
        val outputFilePath = "$outputDir/${File(inputFilePath).nameWithoutExtension}.xml"
        Files.write(Paths.get(outputFilePath), writer.toString().toByteArray())

        Log.d("svgConverter", "Converted files saved to: $outputDir")
    }

    private fun shouldApplyFillColor(tag: String): Boolean {
        return tag in setOf("path", "circle", "rect", "ellipse", "polygon", "polyline")
    }

    private fun hasFillAttribute(parser: XmlPullParser): Boolean {
        return (0 until parser.attributeCount).any {
            parser.getAttributeName(it) == "fill"
        }
    }


    data class SvgAttributes(
        val width: Int = 24,
        val height: Int = 24,
        val viewportWidth: Float = 24f,
        val viewportHeight: Float = 24f
    )

    private fun extractSvgAttributes(parser: XmlPullParser): SvgAttributes {
        while (parser.eventType != XmlPullParser.START_TAG || parser.name != "svg") {
            parser.next()
        }

        // Get viewBox attributes
        val viewBox = parser.getAttributeValue(null, "viewBox")?.split(" ")
        val viewportWidth = viewBox?.getOrNull(2)?.toFloatOrNull() ?: 24f
        val viewportHeight = viewBox?.getOrNull(3)?.toFloatOrNull() ?: 24f

        // Get width and height
        val width = parser.getAttributeValue(null, "width")?.removeSuffix("px")?.toIntOrNull() ?: 24
        val height =
            parser.getAttributeValue(null, "height")?.removeSuffix("px")?.toIntOrNull() ?: 24

        return SvgAttributes(width, height, viewportWidth, viewportHeight)
    }

    private fun writeVectorDrawable(
        serializer: XmlSerializer,
        attributes: SvgAttributes,
        parser: XmlPullParser,
        customFillColor: String?
    ) {
        serializer.startDocument("UTF-8", true)

        serializer.startTag(null, "vector")
        serializer.attribute(null, "xmlns:android", "http://schemas.android.com/apk/res/android")
        serializer.attribute(null, "android:width", "${attributes.width * SIZE_MULTIPLIER}dp")
        serializer.attribute(null, "android:height", "${attributes.height * SIZE_MULTIPLIER}dp")
        serializer.attribute(null, "android:viewportWidth", attributes.viewportWidth.toString())
        serializer.attribute(null, "android:viewportHeight", attributes.viewportHeight.toString())

        var inheritedFill: String? = null
        var inheritedStroke: String? = null
        var inheritedStrokeWidth: String? = null
        var inheritedStrokeLineCap: String? = null
        var inheritedStrokeLineJoin: String? = null

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "svg", "g" -> {
                            inheritedFill = parser.getAttributeValue(null, "fill") ?: inheritedFill
                            inheritedStroke = parser.getAttributeValue(null, "stroke") ?: inheritedStroke
                            inheritedStrokeWidth = parser.getAttributeValue(null, "stroke-width") ?: inheritedStrokeWidth
                            inheritedStrokeLineCap = parser.getAttributeValue(null, "stroke-linecap") ?: inheritedStrokeLineCap
                            inheritedStrokeLineJoin = parser.getAttributeValue(null, "stroke-linejoin") ?: inheritedStrokeLineJoin
                        }

                        "path" -> {
                            writePath(
                                serializer,
                                parser.getAttributeValue(null, "d"),
                                parser.getAttributeValue(null, "fill") ?: inheritedFill,
                                parser.getAttributeValue(null, "stroke") ?: inheritedStroke,
                                parser.getAttributeValue(null, "stroke-width") ?: inheritedStrokeWidth,
                                parser.getAttributeValue(null, "stroke-linecap") ?: inheritedStrokeLineCap,
                                parser.getAttributeValue(null, "stroke-linejoin") ?: inheritedStrokeLineJoin,
                                parser.getAttributeValue(null, "opacity"),
                                customFillColor
                            )
                        }

                        "circle", "rect", "ellipse", "line", "polygon", "polyline" -> {
                            val pathData = convertShapeToPath(parser)
                            writePath(
                                serializer,
                                pathData,
                                parser.getAttributeValue(null, "fill") ?: inheritedFill,
                                parser.getAttributeValue(null, "stroke") ?: inheritedStroke,
                                parser.getAttributeValue(null, "stroke-width") ?: inheritedStrokeWidth,
                                parser.getAttributeValue(null, "stroke-linecap") ?: inheritedStrokeLineCap,
                                parser.getAttributeValue(null, "stroke-linejoin") ?: inheritedStrokeLineJoin,
                                parser.getAttributeValue(null, "opacity"),
                                customFillColor
                            )
                        }
                    }
                }
            }
            parser.next()
        }

        serializer.endTag(null, "vector")
        serializer.endDocument()
    }

    private fun writePath(
        serializer: XmlSerializer,
        pathData: String?,
        fill: String?,
        stroke: String?,
        strokeWidth: String?,
        strokeLineCap: String?,
        strokeLineJoin: String?,
        opacity: String?,
        customColor: String?
    ) {
        if (pathData.isNullOrBlank()) {
            return
        }

        val fillDisabled = fill.equals("none", ignoreCase = true)
        val hasStroke = !stroke.isNullOrBlank() && !stroke.equals("none", ignoreCase = true)
        val hasFill = !fillDisabled && (!fill.isNullOrBlank() || !hasStroke)
        if (!hasStroke && !hasFill) {
            return
        }

        serializer.startTag(null, "path")
        serializer.attribute(null, "android:pathData", pathData)
        serializer.attribute(
            null,
            "android:fillColor",
            if (hasFill) resolveSvgColor(fill, customColor) else "@android:color/transparent"
        )

        if (hasStroke) {
            serializer.attribute(null, "android:strokeColor", resolveSvgColor(stroke, customColor))
            serializer.attribute(null, "android:strokeWidth", normalizeStrokeWidth(strokeWidth))
            strokeLineCap?.takeIf { it.isNotBlank() }
                ?.let { serializer.attribute(null, "android:strokeLineCap", it) }
            strokeLineJoin?.takeIf { it.isNotBlank() }
                ?.let { serializer.attribute(null, "android:strokeLineJoin", it) }
        }

        opacity?.toFloatOrNull()?.let { alpha ->
            if (alpha < 1.0f) {
                serializer.attribute(null, "android:fillAlpha", alpha.toString())
                if (hasStroke) {
                    serializer.attribute(null, "android:strokeAlpha", alpha.toString())
                }
            }
        }

        serializer.endTag(null, "path")
    }

    private fun resolveSvgColor(color: String?, customColor: String?): String {
        val normalized = color?.trim().orEmpty()
        if (customColor != null && !normalized.equals("none", ignoreCase = true)) {
            return customColor
        }
        if (normalized.equals("currentColor", ignoreCase = true)) {
            return customColor ?: "#000000"
        }
        return normalized.ifBlank { customColor ?: "#000000" }
    }

    private fun normalizeStrokeWidth(strokeWidth: String?): String {
        return strokeWidth
            ?.trim()
            ?.removeSuffix("px")
            ?.takeIf { it.isNotBlank() }
            ?: "1"
    }

    private fun convertShapeToPath(parser: XmlPullParser): String {
        return when (parser.name) {
            "circle" -> {
                val cx = parser.getAttributeValue(null, "cx")?.toFloatOrNull() ?: 0f
                val cy = parser.getAttributeValue(null, "cy")?.toFloatOrNull() ?: 0f
                val r = parser.getAttributeValue(null, "r")?.toFloatOrNull() ?: 0f
                createCirclePath(cx, cy, r)
            }

            "rect" -> {
                val x = parser.getAttributeValue(null, "x")?.toFloatOrNull() ?: 0f
                val y = parser.getAttributeValue(null, "y")?.toFloatOrNull() ?: 0f
                val width = parser.getAttributeValue(null, "width")?.toFloatOrNull() ?: 0f
                val height = parser.getAttributeValue(null, "height")?.toFloatOrNull() ?: 0f
                val rx = parser.getAttributeValue(null, "rx")?.toFloatOrNull() ?: 0f
                val ry = parser.getAttributeValue(null, "ry")?.toFloatOrNull() ?: 0f
                createRectPath(x, y, width, height, rx, ry)
            }

            "ellipse" -> {
                val cx = parser.getAttributeValue(null, "cx")?.toFloatOrNull() ?: 0f
                val cy = parser.getAttributeValue(null, "cy")?.toFloatOrNull() ?: 0f
                val rx = parser.getAttributeValue(null, "rx")?.toFloatOrNull() ?: 0f
                val ry = parser.getAttributeValue(null, "ry")?.toFloatOrNull() ?: 0f
                createEllipsePath(cx, cy, rx, ry)
            }

            "line" -> {
                val x1 = parser.getAttributeValue(null, "x1")?.toFloatOrNull() ?: 0f
                val y1 = parser.getAttributeValue(null, "y1")?.toFloatOrNull() ?: 0f
                val x2 = parser.getAttributeValue(null, "x2")?.toFloatOrNull() ?: 0f
                val y2 = parser.getAttributeValue(null, "y2")?.toFloatOrNull() ?: 0f
                "M $x1,$y1 L $x2,$y2"
            }

            "polyline", "polygon" -> {
                createPointsPath(
                    parser.getAttributeValue(null, "points").orEmpty(),
                    parser.name == "polygon"
                )
            }

            else -> ""
        }
    }

    private fun createPointsPath(points: String, close: Boolean): String {
        val pairs = points
            .trim()
            .split(Regex("\\s+"))
            .mapNotNull { point ->
                val coordinates = point.split(",")
                if (coordinates.size != 2) {
                    null
                } else {
                    val x = coordinates[0].toFloatOrNull()
                    val y = coordinates[1].toFloatOrNull()
                    if (x == null || y == null) null else x to y
                }
            }
        if (pairs.isEmpty()) {
            return ""
        }

        return buildString {
            append("M ${pairs[0].first},${pairs[0].second}")
            for (i in 1 until pairs.size) {
                append(" L ${pairs[i].first},${pairs[i].second}")
            }
            if (close) {
                append(" Z")
            }
        }
    }

    private fun createCirclePath(cx: Float, cy: Float, r: Float): String {
        return "M ${cx - r},$cy " +
                "A $r,$r 0 0 1 ${cx + r},$cy " +
                "A $r,$r 0 0 1 ${cx - r},$cy Z"
    }

    private fun createRectPath(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        rx: Float,
        ry: Float
    ): String {
        return if (rx <= 0f && ry <= 0f) {
            "M $x,$y h $width v $height h ${-width} Z"
        } else {
            val effectiveRx = rx.coerceAtMost(width / 2)
            val effectiveRy = ry.coerceAtMost(height / 2)
            "M ${x + effectiveRx},$y " +
                    "h ${width - 2 * effectiveRx} " +
                    "a $effectiveRx,$effectiveRy 0 0 1 $effectiveRx,$effectiveRy " +
                    "v ${height - 2 * effectiveRy} " +
                    "a $effectiveRx,$effectiveRy 0 0 1 ${-effectiveRx},$effectiveRy " +
                    "h ${-(width - 2 * effectiveRx)} " +
                    "a $effectiveRx,$effectiveRy 0 0 1 ${-effectiveRx},${-effectiveRy} " +
                    "v ${-(height - 2 * effectiveRy)} " +
                    "a $effectiveRx,$effectiveRy 0 0 1 $effectiveRx,${-effectiveRy} Z"
        }
    }

    private fun createEllipsePath(cx: Float, cy: Float, rx: Float, ry: Float): String {
        return "M ${cx - rx},$cy " +
                "A $rx,$ry 0 0 1 ${cx + rx},$cy " +
                "A $rx,$ry 0 0 1 ${cx - rx},$cy Z"
    }

}
