package com.example.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.EmeraldPrimary
import com.example.util.image.ImageCropUtils
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class CropShape(val label: String) {
    PROFILE_CIRCLE("Perfil / Logo (1:1)"),
    COVER_BANNER("Foto de Capa (Banner)")
}

@Composable
fun ImageCropperDialog(
    imageSource: String,
    cropShape: CropShape,
    title: String = if (cropShape == CropShape.PROFILE_CIRCLE) "Ajustar Foto do Perfil" else "Ajustar Foto de Capa",
    onDismiss: () -> Unit,
    onCropSuccess: (Uri) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingImage by remember { mutableStateOf(true) }
    var isProcessingCrop by remember { mutableStateOf(false) }

    // Transform states
    var extraRotation by remember { mutableFloatStateOf(0f) }
    var userScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var isCircularMask by remember { mutableStateOf(cropShape == CropShape.PROFILE_CIRCLE) }

    // Carrega o bitmap
    LaunchedEffect(imageSource) {
        isLoadingImage = true
        val bmp = ImageCropUtils.loadBitmap(context, imageSource)
        sourceBitmap = bmp
        isLoadingImage = false
        if (bmp == null) {
            Toast.makeText(context, "Não foi possível carregar a imagem para ajuste.", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = { if (!isProcessingCrop) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isProcessingCrop,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0F1115) // Fundo grafite profundo para foco máximo na imagem
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // 1. TOP BAR DE AÇÕES
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isProcessingCrop,
                        modifier = Modifier.testTag("crop_cancel_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancelar",
                            tint = Color.White
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = Color.White
                        )
                        Text(
                            text = if (cropShape == CropShape.PROFILE_CIRCLE) "Arraste e aproxime para enquadrar" else "Posicione o banner no ângulo ideal",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    // Botão Confirmar
                    Button(
                        onClick = {
                            val bmp = sourceBitmap ?: return@Button
                            isProcessingCrop = true
                            // Os cálculos de viewport serão efetuados com base nos valores memorizados
                        },
                        enabled = !isLoadingImage && !isProcessingCrop && sourceBitmap != null,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = Color.White
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("crop_confirm_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Concluir", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // 2. ÁREA CENTRAL DE RECORTE INTERATIVO
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingImage) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = EmeraldPrimary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Carregando imagem...", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                        }
                    } else if (sourceBitmap != null) {
                        val bmp = sourceBitmap!!

                        BoxWithConstraints(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            val boxWidthPx = with(density) { maxWidth.toPx() }
                            val boxHeightPx = with(density) { maxHeight.toPx() }

                            // Determina as dimensões do viewport de recorte
                            val (vpWidthPx, vpHeightPx) = remember(maxWidth, maxHeight, cropShape) {
                                if (cropShape == CropShape.PROFILE_CIRCLE) {
                                    val size = min(boxWidthPx * 0.84f, boxHeightPx * 0.80f)
                                    size to size
                                } else {
                                    // Banner de Capa (~2.3:1)
                                    val w = boxWidthPx * 0.92f
                                    var h = w / 2.3f
                                    if (h > boxHeightPx * 0.75f) {
                                        h = boxHeightPx * 0.75f
                                    }
                                    w to h
                                }
                            }

                            val vpLeft = (boxWidthPx - vpWidthPx) / 2f
                            val vpTop = (boxHeightPx - vpHeightPx) / 2f
                            val vpRight = vpLeft + vpWidthPx
                            val vpBottom = vpTop + vpHeightPx

                            // Dimensões do bitmap ajustadas pela rotação
                            val rotNorm = ((extraRotation.toInt() % 360) + 360) % 360
                            val isSwapped = rotNorm == 90 || rotNorm == 270
                            val currentBw = if (isSwapped) bmp.height.toFloat() else bmp.width.toFloat()
                            val currentBh = if (isSwapped) bmp.width.toFloat() else bmp.height.toFloat()

                            // Escala base para cobrir o viewport por completo
                            val baseScale = max(vpWidthPx / currentBw, vpHeightPx / currentBh)
                            val dispW = currentBw * baseScale * userScale
                            val dispH = currentBh * baseScale * userScale

                            // Limites de deslocamento (pan) para nunca expor bordas vazias
                            val maxPanX = max(0f, (dispW - vpWidthPx) / 2f)
                            val maxPanY = max(0f, (dispH - vpHeightPx) / 2f)

                            val clampedPanX = panOffset.x.coerceIn(-maxPanX, maxPanX)
                            val clampedPanY = panOffset.y.coerceIn(-maxPanY, maxPanY)

                            // Callback para o botão de corte executar com as medidas calculadas
                            if (isProcessingCrop) {
                                LaunchedEffect(Unit) {
                                    scope.launch {
                                        val prefix = if (cropShape == CropShape.PROFILE_CIRCLE) "profile_cropped" else "cover_cropped"
                                        val resultUri = ImageCropUtils.cropBitmap(
                                            context = context,
                                            sourceBitmap = bmp,
                                            extraRotationDegrees = extraRotation,
                                            viewportWidthPx = vpWidthPx,
                                            viewportHeightPx = vpHeightPx,
                                            displayedImageWidthPx = dispW,
                                            displayedImageHeightPx = dispH,
                                            panOffsetX = clampedPanX,
                                            panOffsetY = clampedPanY,
                                            prefix = prefix
                                        )
                                        isProcessingCrop = false
                                        if (resultUri != null) {
                                            onCropSuccess(resultUri)
                                            onDismiss()
                                        } else {
                                            Toast.makeText(context, "Erro ao recortar imagem.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }

                            // 2.1 Imagem base manipulável com gestos
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(extraRotation) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            userScale = (userScale * zoom).coerceIn(1f, 5f)
                                            panOffset = Offset(
                                                x = (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                                                y = (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                                            )
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Imagem sendo enquadrada",
                                    modifier = Modifier
                                        .size(
                                            width = with(density) { bmp.width.toDp() },
                                            height = with(density) { bmp.height.toDp() }
                                        )
                                        .graphicsLayer {
                                            rotationZ = extraRotation
                                            scaleX = baseScale * userScale
                                            scaleY = baseScale * userScale
                                            translationX = clampedPanX
                                            translationY = clampedPanY
                                        }
                                )
                            }

                            // 2.2 Máscara escura com recorte vazado + Grid dos Terços
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // Máscara escura com recorte vazado
                                val maskPath = Path().apply {
                                    fillType = PathFillType.EvenOdd
                                    addRect(Rect(0f, 0f, size.width, size.height))
                                    if (cropShape == CropShape.PROFILE_CIRCLE && isCircularMask) {
                                        addOval(Rect(vpLeft, vpTop, vpRight, vpBottom))
                                    } else {
                                        addRoundRect(
                                            RoundRect(
                                                rect = Rect(vpLeft, vpTop, vpRight, vpBottom),
                                                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                                            )
                                        )
                                    }
                                }
                                drawPath(maskPath, color = Color.Black.copy(alpha = 0.74f))

                                // Borda do viewport
                                if (cropShape == CropShape.PROFILE_CIRCLE && isCircularMask) {
                                    drawCircle(
                                        color = Color.White,
                                        radius = vpWidthPx / 2f,
                                        center = Offset(boxWidthPx / 2f, boxHeightPx / 2f),
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                } else {
                                    drawRoundRect(
                                        color = Color.White,
                                        topLeft = Offset(vpLeft, vpTop),
                                        size = Size(vpWidthPx, vpHeightPx),
                                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                }

                                // Linhas guias dos terços
                                val stepX = vpWidthPx / 3f
                                val stepY = vpHeightPx / 3f
                                val gridColor = Color.White.copy(alpha = 0.35f)
                                val strokeW = 1.dp.toPx()

                                drawLine(gridColor, Offset(vpLeft + stepX, vpTop), Offset(vpLeft + stepX, vpBottom), strokeWidth = strokeW)
                                drawLine(gridColor, Offset(vpLeft + stepX * 2, vpTop), Offset(vpLeft + stepX * 2, vpBottom), strokeWidth = strokeW)
                                drawLine(gridColor, Offset(vpLeft, vpTop + stepY), Offset(vpRight, vpTop + stepY), strokeWidth = strokeW)
                                drawLine(gridColor, Offset(vpLeft, vpTop + stepY * 2), Offset(vpRight, vpTop + stepY * 2), strokeWidth = strokeW)
                            }
                        }
                    }

                    // Indicador de processamento do recorte
                    if (isProcessingCrop) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = Color.Black.copy(alpha = 0.75f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = EmeraldPrimary, strokeWidth = 3.dp)
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text("Salvando recorte perfeito...", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 3. BARRA DE CONTROLE DE AJUSTES (Girar, Zoom, Resetar, Máscara)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF161920),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        // Slider de Zoom
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { userScale = (userScale - 0.2f).coerceAtLeast(1f) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Menos zoom", tint = Color.White.copy(alpha = 0.8f))
                            }

                            Slider(
                                value = userScale,
                                onValueChange = { userScale = it },
                                valueRange = 1f..4f,
                                colors = SliderDefaults.colors(
                                    thumbColor = EmeraldPrimary,
                                    activeTrackColor = EmeraldPrimary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                                    .testTag("crop_zoom_slider")
                            )

                            IconButton(
                                onClick = { userScale = (userScale + 0.2f).coerceAtMost(4f) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Mais zoom", tint = Color.White.copy(alpha = 0.8f))
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = "${(userScale * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.width(42.dp),
                                textAlign = TextAlign.End
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Botões de Ação de Ferramentas
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Girar 90 graus
                            ToolActionButton(
                                icon = Icons.Default.RotateRight,
                                label = "Girar 90°",
                                onClick = {
                                    extraRotation = (extraRotation + 90f) % 360f
                                    panOffset = Offset.Zero
                                },
                                testTag = "crop_rotate_btn"
                            )

                            // 2. Centralizar / Redefinir
                            ToolActionButton(
                                icon = Icons.Default.FilterCenterFocus,
                                label = "Centralizar",
                                onClick = {
                                    userScale = 1f
                                    panOffset = Offset.Zero
                                    extraRotation = 0f
                                },
                                testTag = "crop_reset_btn"
                            )

                            // 3. Alternar Máscara (Circular ou Quadrado no perfil)
                            if (cropShape == CropShape.PROFILE_CIRCLE) {
                                ToolActionButton(
                                    icon = if (isCircularMask) Icons.Default.CropSquare else Icons.Default.Crop,
                                    label = if (isCircularMask) "Modo Círculo" else "Modo Quadrado",
                                    isActive = true,
                                    onClick = { isCircularMask = !isCircularMask },
                                    testTag = "crop_mask_toggle_btn"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit,
    testTag: String = ""
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag(testTag),
        color = if (isActive) EmeraldPrimary.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) EmeraldPrimary else Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isActive) EmeraldPrimary else Color.White
            )
        }
    }
}
