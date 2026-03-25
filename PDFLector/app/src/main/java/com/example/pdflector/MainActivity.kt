package com.example.pdflector

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.ImageType
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton



// Clase para representar un Libro
data class Libro(
    val id: String,
    val titulo: String,
    var ultimaPagina: Int = 0
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "inicio") {
        composable("inicio") { PantallaInicio(navController) }
        composable("lectura/{libroId}") { backStackEntry ->
            val libroId = backStackEntry.arguments?.getString("libroId") ?: ""
            PantallaLectura(navController, libroId)
        }
    }
}

data class TemaLectura(val fondo: Color, val texto: Color)
val ModoDia = TemaLectura(Color(0xFFFFFFFF), Color(0xFF1A1A1A))
val ModoSepia = TemaLectura(Color(0xFFF4ECD8), Color(0xFF5B4636))
val ModoNoche = TemaLectura(Color(0xFF121212), Color(0xFFE0E0E0))



@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class) // Agregado ExperimentalFoundationApi
@Composable
fun PantallaInicio(navController: NavController) {
    val contexto = LocalContext.current
    var biblioteca by remember { mutableStateOf(emptyList<Libro>()) }
    var estaCargando by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Estado para controlar qué libro se quiere eliminar y si el diálogo está visible
    var libroAEliminar by remember { mutableStateOf<Libro?>(null) }

    // Cargar biblioteca de forma asíncrona al iniciar
    LaunchedEffect(Unit) {
        val lista = withContext(Dispatchers.IO) { leerBiblioteca(contexto) }
        biblioteca = lista
    }

    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // ... (el código del lanzador se mantiene igual)
        if (uri != null) {
            estaCargando = true
            coroutineScope.launch(Dispatchers.IO) {
                val nombre = obtenerNombreArchivo(contexto, uri) ?: "Nuevo Libro"
                val id = "libro_${System.currentTimeMillis()}"

                val texto = extraerTextoDePdf(contexto, uri)
                guardarTextoEnArchivo(contexto, "$id.txt", texto)
                extraerYGuardarPortada(contexto, uri, id)

                val nuevoLibro = Libro(id, nombre)
                val nuevaLista = biblioteca + nuevoLibro
                guardarBiblioteca(contexto, nuevaLista)

                withContext(Dispatchers.Main) {
                    biblioteca = nuevaLista
                    estaCargando = false
                    navController.navigate("lectura/$id")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            // Cambiamos el título a algo en español aquí también (opcional, pero recomendado)
            CenterAlignedTopAppBar(
                title = { Text("PDF Lector Adaptable", fontWeight = FontWeight.Black, letterSpacing = 2.sp) }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Button(
                onClick = { lanzador.launch(arrayOf("application/pdf")) },
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
                shape = MaterialTheme.shapes.medium,
                enabled = !estaCargando
            ) {
                Text(if (estaCargando) "PROCESANDO PDF..." else "IMPORTAR NUEVO PDF", fontWeight = FontWeight.Bold)
            }

            Text(
                "Mi Biblioteca",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontWeight = FontWeight.Bold
            )

            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(biblioteca) { libro ->
                    ElevatedCard(
                        // AQUÍ ES EL CAMBIO PRINCIPAL: combinedClickable
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { navController.navigate("lectura/${libro.id}") },
                                onLongClick = { libroAEliminar = libro } // Mostramos el diálogo al mantener presionado
                            ),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
                    ) {
                        // ... (el contenido de la tarjeta se mantiene igual)
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            val archivoPortada = File(contexto.filesDir, "portada_${libro.id}.jpg")
                            Surface(
                                modifier = Modifier.size(60.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                if (archivoPortada.exists()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(archivoPortada)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Portada",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Default.Info, null, modifier = Modifier.padding(12.dp))
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(libro.titulo, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text("Página actual: ${libro.ultimaPagina + 1}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        // AQUÍ AGREGAMOS EL DIÁLOGO DE CONFIRMACIÓN
        libroAEliminar?.let { libro ->
            AlertDialog(
                onDismissRequest = { libroAEliminar = null },
                title = { Text("Quitar libro") },
                text = { Text("¿Estás seguro de que deseas quitar '${libro.titulo}' de tu biblioteca?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                // 1. Eliminar archivos locales
                                val archivoTexto = File(contexto.filesDir, "${libro.id}.txt")
                                val archivoPortada = File(contexto.filesDir, "portada_${libro.id}.jpg")
                                if (archivoTexto.exists()) archivoTexto.delete()
                                if (archivoPortada.exists()) archivoPortada.delete()

                                // 2. Actualizar la lista en memoria y guardarla
                                val nuevaLista = biblioteca.filter { it.id != libro.id }
                                guardarBiblioteca(contexto, nuevaLista)

                                // 3. Actualizar la UI
                                withContext(Dispatchers.Main) {
                                    biblioteca = nuevaLista
                                    libroAEliminar = null // Cerrar el diálogo
                                }
                            }
                        }
                    ) {
                        Text("Quitar", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { libroAEliminar = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaLectura(navController: NavController, libroId: String) {
    val contexto = LocalContext.current
    var temaActual by remember { mutableStateOf(ModoDia) }
    var paginas by remember { mutableStateOf(listOf<String>()) }
    var paginaActual by remember { mutableIntStateOf(0) }
    var tamanoLetra by remember { mutableStateOf(18.sp) }
    val estadoScroll = rememberScrollState()

    // Carga de texto y progreso
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val texto = leerTextoDeArchivo(contexto, "$libroId.txt")
            val paginasProcesadas = paginarTextoInteligente(texto, 600)
            val biblioteca = leerBiblioteca(contexto)
            val paginaGuardada = biblioteca.find { it.id == libroId }?.ultimaPagina ?: 0

            withContext(Dispatchers.Main) {
                paginas = paginasProcesadas
                paginaActual = if (paginaGuardada < paginasProcesadas.size) paginaGuardada else 0
            }
        }
    }

    // Guardar progreso y resetear scroll
    LaunchedEffect(paginaActual) {
        if (paginas.isNotEmpty()) {
            estadoScroll.scrollTo(0)
            withContext(Dispatchers.IO) {
                val biblioteca = leerBiblioteca(contexto)
                biblioteca.find { it.id == libroId }?.let {
                    if (it.ultimaPagina != paginaActual) {
                        it.ultimaPagina = paginaActual
                        guardarBiblioteca(contexto, biblioteca)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lectura", fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                },
                actions = {
                    IconButton(onClick = { temaActual = ModoDia }) { Surface(Modifier.size(20.dp), shape = CircleShape, color = Color.White, border = BorderStroke(1.dp, Color.Black)) {} }
                    IconButton(onClick = { temaActual = ModoSepia }) { Surface(Modifier.size(20.dp), shape = CircleShape, color = Color(0xFFF4ECD8)) {} }
                    IconButton(onClick = { temaActual = ModoNoche }) { Surface(Modifier.size(20.dp), shape = CircleShape, color = Color.Black) {} }
                }
            )
        },
        containerColor = temaActual.fondo,
        bottomBar = {
            BottomAppBar {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { if (paginaActual > 0) paginaActual-- }, enabled = paginaActual > 0) { Text("Ant.") }
                    Text("${paginaActual + 1} / ${if (paginas.isEmpty()) 1 else paginas.size}")
                    Button(onClick = { if (paginaActual < paginas.size - 1) paginaActual++ }, enabled = paginas.isNotEmpty() && paginaActual < paginas.size - 1) { Text("Sig.") }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text("Tamaño: ", color = temaActual.texto, fontSize = 14.sp)
                TextButton(onClick = { tamanoLetra = (tamanoLetra.value - 2).coerceAtLeast(12f).sp }) { Text("A-", color = temaActual.texto, fontWeight = FontWeight.Bold) }
                TextButton(onClick = { tamanoLetra = (tamanoLetra.value + 2).coerceAtMost(40f).sp }) { Text("A+", color = temaActual.texto, fontWeight = FontWeight.Bold) }
            }
            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(estadoScroll)) {
                if (paginas.isNotEmpty()) {
                    Text(text = paginas[paginaActual], fontSize = tamanoLetra, color = temaActual.texto, lineHeight = tamanoLetra * 1.6f)
                } else {
                    Text("Cargando...", color = temaActual.texto)
                }
            }
        }
    }
}

// --- UTILIDADES ---

fun extraerYGuardarPortada(contexto: Context, uri: Uri, idLibro: String) {
    try {
        contexto.contentResolver.openInputStream(uri)?.use { inputStream ->
            PDDocument.load(inputStream).use { documento ->
                if (documento.numberOfPages > 0) {
                    val renderer = PDFRenderer(documento)
                    val bitmap = renderer.renderImageWithDPI(0, 72f, ImageType.RGB)
                    val archivoImagen = File(contexto.filesDir, "portada_$idLibro.jpg")
                    FileOutputStream(archivoImagen).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun guardarBiblioteca(contexto: Context, libros: List<Libro>) {
    try {
        val json = Gson().toJson(libros)
        contexto.openFileOutput("biblioteca.json", Context.MODE_PRIVATE).use { it.write(json.toByteArray()) }
    } catch (e: Exception) { e.printStackTrace() }
}

fun leerBiblioteca(contexto: Context): List<Libro> {
    val archivo = File(contexto.filesDir, "biblioteca.json")
    if (!archivo.exists()) return emptyList()
    return try {
        val json = archivo.readText()
        val tipo = object : TypeToken<List<Libro>>() {}.type
        Gson().fromJson(json, tipo)
    } catch (e: Exception) { emptyList() }
}

fun obtenerNombreArchivo(contexto: Context, uri: Uri): String? {
    var nombre: String? = null
    contexto.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst()) nombre = cursor.getString(nameIndex)
    }
    return nombre
}

fun paginarTextoInteligente(texto: String, caracteresPorPagina: Int): List<String> {
    val paginas = mutableListOf<String>()
    var indice = 0
    while (indice < texto.length) {
        var fin = (indice + caracteresPorPagina).coerceAtMost(texto.length)
        if (fin < texto.length) {
            val ultimoEspacio = texto.lastIndexOf(' ', fin)
            if (ultimoEspacio > indice) fin = ultimoEspacio
        }
        paginas.add(texto.substring(indice, fin).trim())
        indice = fin
    }
    return if (paginas.isEmpty()) listOf("Sin contenido") else paginas
}

fun extraerTextoDePdf(contexto: Context, uri: Uri): String {
    return try {
        contexto.contentResolver.openInputStream(uri)?.use { inputStream ->
            PDDocument.load(inputStream).use { documento ->
                val stripper = PDFTextStripper()
                val textoExtraido = stripper.getText(documento)
                textoExtraido.ifBlank { "Este PDF no tiene texto extraíble." }
            }
        } ?: "Error: No se pudo abrir el archivo."
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}

fun guardarTextoEnArchivo(contexto: Context, nombre: String, contenido: String) {
    try { File(contexto.filesDir, nombre).writeText(contenido) } catch (e: Exception) { e.printStackTrace() }
}

fun leerTextoDeArchivo(contexto: Context, nombre: String): String {
    val archivo = File(contexto.filesDir, nombre)
    return if (archivo.exists()) {
        try { archivo.readText() } catch (e: Exception) { "" }
    } else ""
}