package com.webkam.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val market: String, val name: String, val size: String,
    val price: String, val wholesalePrice: String, val hash: Long
)

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE market=:m") suspend fun byMarket(m: String): List<Product>
    @Query("SELECT * FROM products") suspend fun all(): List<Product>
    @Insert suspend fun insert(p: Product)
    @Update suspend fun update(p: Product)
    @Delete suspend fun delete(p: Product)
}

@Database(entities = [Product::class], version = 1, exportSchema = false)
abstract class AppDB : RoomDatabase() { abstract fun dao(): ProductDao }

object HashUtil {
    fun dHash(bmp: Bitmap): Long {
        val small = Bitmap.createScaledBitmap(bmp, 9, 8, true)
        var hash = 0L
        for (y in 0 until 8) for (x in 0 until 8) {
            val left = (small.getPixel(x, y) shr 16) and 0xff
            val right = (small.getPixel(x + 1, y) shr 16) and 0xff
            hash = (hash shl 1) or if (left > right) 1 else 0
        }
        return hash
    }
    fun distance(a: Long, b: Long) = java.lang.Long.bitCount(a xor b)
}

private val marketsAr = listOf("سوق غذائية","سوق مواشي","سوق تنظيف","سوق ملابس","سوق عطار","سوق هواتف","سوق أحذية","سوق بقوليات","سوق صيدلية","سوق ذهب","سوق منزلية","سوق ألعاب","سوق كهربائية","جملة")
private val marketsEn = listOf("Grocery","Livestock","Cleaning","Clothing","Herbalist","Phones","Shoes","Legumes","Pharmacy","Gold","Home","Games","Electrical","Wholesale")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BkamApp() }
    }
}

@Composable
fun BkamApp() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("bkam", 0) }
    var lang by remember { mutableStateOf(prefs.getString("lang", "") ?: "") }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
    var fontScale by remember { mutableFloatStateOf(prefs.getFloat("font", 1f)) }
    var market by remember { mutableStateOf("") }
    var screen by remember { mutableStateOf(if (lang.isBlank()) "lang" else "home") }
    val db = remember { Room.databaseBuilder(ctx, AppDB::class.java, "bkam.db").build() }
    val markets = if (lang == "en") marketsEn else marketsAr
    val bg = if (dark) Color(0xFF0B1E3A) else Color(0xFFEAF4FF)
    val fg = if (dark) Color.White else Color(0xFF0D2A4A)
    val card = if (dark) Color(0xFF13294B) else Color.White

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize(), color = bg) {
            when (screen) {
                "lang" -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("HELLO", fontSize = 48.sp, fontWeight = FontWeight.Black, color = fg)
                    Text("اختر اللغة / Choose Language", color = fg)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { lang = "ar"; prefs.edit().putString("lang", lang).apply(); screen = "home" }, Modifier.fillMaxWidth()) { Text("العربية") }
                    Button(onClick = { lang = "en"; prefs.edit().putString("lang", lang).apply(); screen = "home" }, Modifier.fillMaxWidth()) { Text("English") }
                }
                "home" -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text(if (lang == "en") "Bakam?" else "بكم؟", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = fg)
                    Text(if (lang == "en") "Programmer Ahmed Amlimidi" else "المبرمج احمد امليمدي", color = fg)
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(markets) { item ->
                            Card(onClick = { market = item; screen = "market" }, colors = CardDefaults.cardColors(containerColor = card)) {
                                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Storefront, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Text(item, color = fg, fontSize = (17 * fontScale).sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { screen = "settings" }, Modifier.fillMaxWidth()) { Text(if (lang == "en") "Settings" else "الإعدادات") }
                }
                "market" -> MarketPage(db, market, lang, fg, card, onBack = { screen = "home" })
                "settings" -> Column(Modifier.fillMaxSize().padding(20.dp)) {
                    Text(if (lang == "en") "Settings" else "الإعدادات", color = fg, fontSize = 24.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(if (lang == "en") "Theme" else "المظهر", color = fg)
                    Row {
                        FilterChip(selected = !dark, onClick = { dark = false; prefs.edit().putBoolean("dark", false).apply() }, label = { Text("Light") })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = dark, onClick = { dark = true; prefs.edit().putBoolean("dark", true).apply() }, label = { Text("Dark") })
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(if (lang == "en") "Font size" else "حجم الخط", color = fg)
                    Row {
                        FilterChip(selected = fontScale == .9f, onClick = { fontScale = .9f; prefs.edit().putFloat("font", fontScale).apply() }, label = { Text("Small") })
                        FilterChip(selected = fontScale == 1f, onClick = { fontScale = 1f; prefs.edit().putFloat("font", fontScale).apply() }, label = { Text("Medium") })
                        FilterChip(selected = fontScale == 1.2f, onClick = { fontScale = 1.2f; prefs.edit().putFloat("font", fontScale).apply() }, label = { Text("Large") })
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/218934274024"))) }) { Text("WhatsApp / المساعدة") }
                    TextButton(onClick = { screen = "home" }) { Text(if (lang == "en") "Back" else "رجوع") }
                }
            }
        }
    }
}

@Composable
fun MarketPage(db: AppDB, market: String, lang: String, fg: Color, card: Color, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Product?>(null) }
    var delete by remember { mutableStateOf<Product?>(null) }
    var name by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var wholesale by remember { mutableStateOf("") }
    var hash by remember { mutableLongStateOf(0L) }
    LaunchedEffect(market) { products = db.dao().byMarket(market) }
    val photoFile = remember { File(ctx.cacheDir, "camera/${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() } }
    val photoUri = remember { FileProvider.getUriForFile(ctx, ctx.packageName + ".provider", photoFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) BitmapFactory.decodeFile(photoFile.absolutePath)?.let { hash = HashUtil.dHash(it); showAdd = true }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selected ->
            ctx.contentResolver.openInputStream(selected)?.use { BitmapFactory.decodeStream(it) }?.let {
                hash = HashUtil.dHash(it); showAdd = true
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = fg) }
            Text(market, color = fg, fontSize = 22.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { exportPdf(ctx, market, products) }) { Icon(Icons.Default.PictureAsPdf, null) }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(if (lang == "en") "Search" else "بحث") })
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(products.filter { it.name.contains(query, true) }) { p ->
                Card(colors = CardDefaults.cardColors(containerColor = card)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = fg, fontWeight = FontWeight.Bold)
                            Text("${p.price} | ${p.size}", color = fg)
                            if (p.wholesalePrice.isNotBlank()) Text("Wholesale: ${p.wholesalePrice}", color = fg)
                        }
                        IconButton(onClick = { edit = p }) { Icon(Icons.Default.Edit, null) }
                        IconButton(onClick = { delete = p }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                    }
                }
            }
        }
        Button(onClick = { name = ""; size = ""; price = ""; wholesale = ""; showAdd = true }, Modifier.fillMaxWidth()) {
            Text(if (lang == "en") "Add product" else "إضافة منتج")
        }
    }
    if (showAdd) AlertDialog(
        onDismissRequest = { showAdd = false },
        title = { Text(if (lang == "en") "Add product" else "إضافة منتج") },
        text = {
            Column {
                Row {
                    Button(onClick = { camera.launch(photoUri) }) { Text(if (lang == "en") "Camera" else "تصوير") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { picker.launch("image/*") }) { Text(if (lang == "en") "Gallery" else "المعرض") }
                }
                OutlinedTextField(name, { name = it }, label = { Text(if (lang == "en") "Name" else "الاسم") })
                OutlinedTextField(size, { size = it }, label = { Text(if (lang == "en") "Size" else "الحجم") })
                OutlinedTextField(price, { price = it }, label = { Text(if (lang == "en") "Price" else "السعر") })
                OutlinedTextField(wholesale, { wholesale = it }, label = { Text(if (lang == "en") "Wholesale price (optional)" else "سعر الجملة (اختياري)") })
            }
        },
        confirmButton = { Button(onClick = {
            if (name.isNotBlank() && price.isNotBlank()) scope.launch {
                db.dao().insert(Product(market = market, name = name, size = size, price = price, wholesalePrice = wholesale, hash = hash))
                products = db.dao().byMarket(market); showAdd = false
            }
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
    )
    edit?.let { p ->
        var n by remember(p.id) { mutableStateOf(p.name) }
        var pr by remember(p.id) { mutableStateOf(p.price) }
        var sz by remember(p.id) { mutableStateOf(p.size) }
        var wh by remember(p.id) { mutableStateOf(p.wholesalePrice) }
        AlertDialog(onDismissRequest = { edit = null }, title = { Text("Edit product") },
            text = { Column {
                OutlinedTextField(n, { n = it }, label = { Text("Name") })
                OutlinedTextField(pr, { pr = it }, label = { Text("Price") })
                OutlinedTextField(sz, { sz = it }, label = { Text("Size") })
                OutlinedTextField(wh, { wh = it }, label = { Text("Wholesale") })
            } },
            confirmButton = { Button(onClick = { scope.launch {
                db.dao().update(p.copy(name = n, price = pr, size = sz, wholesalePrice = wh))
                products = db.dao().byMarket(market); edit = null
            } }) { Text("Save") } })
    }
    delete?.let { p ->
        AlertDialog(onDismissRequest = { delete = null }, title = { Text("Delete?") }, text = { Text(p.name) },
            confirmButton = { Button(onClick = { scope.launch {
                db.dao().delete(p); products = db.dao().byMarket(market); delete = null
            } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { delete = null }) { Text("Cancel") } })
    }
}

fun exportPdf(ctx: android.content.Context, market: String, products: List<Product>) {
    val doc = PdfDocument()
    val paint = Paint().apply { textSize = 16f; typeface = Typeface.DEFAULT }
    val titlePaint = Paint().apply { textSize = 22f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    var pageNo = 1
    var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
    var canvas = page.canvas
    var y = 55f
    canvas.drawText(market, 40f, y, titlePaint); y += 38f
    for (p in products) {
        if (y > 780f) {
            doc.finishPage(page); pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
            canvas = page.canvas; y = 55f
        }
        canvas.drawText("${p.name} | ${p.price} | ${p.size}", 40f, y, paint); y += 28f
        if (p.wholesalePrice.isNotBlank()) { canvas.drawText("Wholesale: ${p.wholesalePrice}", 50f, y, paint); y += 25f }
    }
    doc.finishPage(page)
    val folder = File(ctx.cacheDir, "pdf").apply { mkdirs() }
    val file = File(folder, "bkam-prices.pdf")
    FileOutputStream(file).use { doc.writeTo(it) }
    doc.close()
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".provider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, "Share PDF"))
}