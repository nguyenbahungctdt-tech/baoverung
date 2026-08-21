package com.baoverung.app.util

import android.content.Context
import android.media.MediaScannerConnection
import com.baoverung.app.data.local.entity.DailyJournalEntity
import com.baoverung.app.data.local.entity.PatrolLogEntity
import com.baoverung.app.gis.CoordinateSystemConverter.toNonAccent
import org.apache.poi.xwpf.usermodel.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object WordExportHelper {

    private fun formatKtt(valcm: Double): String {
        val degrees = valcm.toInt()
        val minutes = Math.round((valcm - degrees) * 60).toInt()
        return "${degrees}°${if (minutes < 10) "0$minutes" else minutes}'"
    }

    private fun getExportDirectory(context: Context): File {
        val publicDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "BaoVeRung")
        if (publicDir.exists() || publicDir.mkdirs()) {
            return publicDir
        }
        val appDir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "BaoVeRung")
        if (!appDir.exists()) appDir.mkdirs()
        return appDir
    }

    private fun addProfessionalHeader(document: XWPFDocument, titleText: String) {
        val title = document.createParagraph().apply { alignment = ParagraphAlignment.CENTER }
        val titleRun = title.createRun()
        titleRun.isBold = true
        titleRun.fontSize = 16
        titleRun.fontFamily = "Times New Roman"
        titleRun.setText(titleText.toNonAccent().uppercase())
        
        val line = document.createParagraph().apply { alignment = ParagraphAlignment.CENTER }
        line.createRun().setText("--------------------------------------------------")
    }

    private fun addReporterInfo(document: XWPFDocument, officerName: String, unitName: String, cm: Double, email: String = "", phone: String = "") {
        val info = document.createParagraph()
        info.createRun().apply {
            fontSize = 11
            fontFamily = "Times New Roman"
            setText("Nguoi bao cao: ${officerName.toNonAccent()}")
            addBreak()
            setText("Don vi: ${unitName.toNonAccent()}")
            addBreak()
            if (email.isNotEmpty()) {
                setText("Email: $email")
                addBreak()
            }
            if (phone.isNotEmpty()) {
                setText("So dien thoai: $phone")
                addBreak()
            }
            setText("He toa do VN2000 (KTT ${formatKtt(cm)})")
            addBreak()
            setText("Thoi gian xuat file: ${SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault()).format(Date())}")
        }
        document.createParagraph().createRun().addBreak()
    }

    fun exportSummaryToWord(context: Context, title: String, content: String, outputFile: File): File {
        val document = XWPFDocument()
        
        val pTitle = document.createParagraph()
        pTitle.alignment = ParagraphAlignment.CENTER
        val rTitle = pTitle.createRun()
        rTitle.isBold = true
        rTitle.fontSize = 14
        rTitle.setText(title.toNonAccent().uppercase())

        content.split("\n").forEach { line ->
            val p = document.createParagraph()
            val r = p.createRun()
            r.fontSize = 12
            val nonAccentLine = line.toNonAccent()
            if (nonAccentLine.startsWith("I.") || nonAccentLine.startsWith("II.") || 
                nonAccentLine.startsWith("III.") || nonAccentLine.startsWith("IV.") || 
                nonAccentLine.contains("THONG TIN") || nonAccentLine.contains("DANH SACH")) {
                r.isBold = true
            }
            r.setText(nonAccentLine)
        }

        FileOutputStream(outputFile).use { document.write(it) }
        document.close()
        MediaScannerConnection.scanFile(context, arrayOf(outputFile.absolutePath), null, null)
        return outputFile
    }

    fun exportDailyJournalToWord(context: Context, journal: DailyJournalEntity, cm: Double, officerName: String, unitName: String, email: String = "", phone: String = "", outputFile: File? = null): File? {
        val fileName = "NhatKy_HangNgay_${journal.dateStr}_${System.currentTimeMillis()}".toNonAccent() + ".docx"
        val file = outputFile ?: File(getExportDirectory(context), fileName)
        try {
            val document = XWPFDocument()
            addProfessionalHeader(document, "NHAT KY TUAN TRA BAO VE RUNG HANG NGAY")
            addReporterInfo(document, officerName, unitName, cm, email, phone)
            
            val info = document.createParagraph()
            val infoRun = info.createRun()
            infoRun.fontFamily = "Times New Roman"
            infoRun.setText("Ngay bao cao: ${journal.dateStr}")
            infoRun.addBreak()

            if (journal.weather.isNotEmpty()) {
                infoRun.setText("Thoi tiet: ${journal.weather.toNonAccent()}")
                infoRun.addBreak()
            }
            if (journal.patrolTeam.isNotEmpty()) {
                infoRun.setText("Doan tuan tra: ${journal.patrolTeam.toNonAccent()}")
                infoRun.addBreak()
            }
            if (journal.patrolCompartment.isNotEmpty()) {
                infoRun.setText("Khu vuc (TK/K): ${journal.patrolCompartment.toNonAccent()}")
                infoRun.addBreak()
            }
            infoRun.addBreak()
            
            val contentTitle = document.createParagraph()
            val ctRun = contentTitle.createRun()
            ctRun.isBold = true
            ctRun.fontSize = 13
            ctRun.fontFamily = "Times New Roman"
            ctRun.setText("I. NOI DUNG CONG VIEC:")
            
            val content = document.createParagraph()
            val cRun = content.createRun()
            cRun.fontFamily = "Times New Roman"
            cRun.setText(journal.content.toNonAccent())
            
            if (journal.linkedDataJson.isNotEmpty()) {
                val dataTitle = document.createParagraph()
                val dtRun = dataTitle.createRun()
                dtRun.isBold = true
                dtRun.fontSize = 13
                dtRun.fontFamily = "Times New Roman"
                dtRun.addBreak()
                dtRun.setText("II. DU LIEU THUC DIA LIEN QUAN TRONG NGAY:")
                
                journal.linkedDataJson.split("\n").forEach { line ->
                    val dp = document.createParagraph()
                    val dr = dp.createRun()
                    dr.fontSize = 11
                    dr.fontFamily = "Times New Roman"
                    dr.setText(line.toNonAccent())
                }
            }
            
            if (journal.notes.isNotEmpty()) {
                val notesTitle = document.createParagraph()
                val ntRun = notesTitle.createRun()
                ntRun.isBold = true
                ntRun.fontSize = 13
                ntRun.fontFamily = "Times New Roman"
                ntRun.addBreak()
                ntRun.setText("III. GHI CHU BO SUNG:")
                
                val notes = document.createParagraph()
                val nRun = notes.createRun()
                nRun.fontFamily = "Times New Roman"
                nRun.setText(journal.notes.toNonAccent())
            }
            
            FileOutputStream(file).use { document.write(it) }
            document.close()
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun exportPatrolLogToWord(context: Context, log: PatrolLogEntity, cm: Double, unitName: String, email: String = "", phone: String = "", outputFile: File? = null): File? {
        val fileName = "NhatKy_SuVu_${log.id}_${System.currentTimeMillis()}".toNonAccent() + ".docx"
        val file = outputFile ?: File(getExportDirectory(context), fileName)
        try {
            val document = XWPFDocument()
            addProfessionalHeader(document, "NHAT KY SU VU VI PHAM LAM NGHIEP")
            addReporterInfo(document, log.leaderName, unitName, cm, email, phone)
            
            val info = document.createParagraph()
            val infoRun = info.createRun()
            infoRun.fontFamily = "Times New Roman"
            infoRun.setText("Su vu: ${log.incidentType.toNonAccent()}")
            infoRun.addBreak()
            infoRun.setText("Thoi gian phat hien: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(log.discoveryTimeUtc))}")
            infoRun.addBreak()
            infoRun.setText("Dia diem: ${log.violationLocation.toNonAccent()}")
            infoRun.addBreak()
            infoRun.setText("Toa do VN2000: X=${String.format("%.2f", log.vn2000X)}, Y=${String.format("%.2f", log.vn2000Y)}")
            infoRun.addBreak()
            
            val table = document.createTable(6, 2)
            fun setCell(r: Int, c: Int, t: String, b: Boolean = false) {
                val p = table.getRow(r).getCell(c).addParagraph()
                val run = p.createRun()
                run.isBold = b
                run.fontSize = 11
                run.fontFamily = "Times New Roman"
                run.setText(t.toNonAccent())
            }

            setCell(0, 0, "Doi tuong vi pham", true); setCell(0, 1, log.violatorName)
            setCell(1, 0, "CCCD/CMND", true); setCell(1, 1, log.violatorIdCard)
            setCell(2, 0, "Dia chi", true); setCell(2, 1, log.violatorAddress)
            setCell(3, 0, "Tang vat tam giu", true); setCell(3, 1, log.confiscatedTools)
            setCell(4, 0, "Bien phap xu ly", true); setCell(4, 1, log.onSiteAction)
            setCell(5, 0, "Ghi chu bo sung", true); setCell(5, 1, log.notes)
            
            FileOutputStream(file).use { document.write(it) }
            document.close()
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun exportFloraFaunaLogToWord(context: Context, log: com.baoverung.app.data.local.entity.FloraFaunaLogEntity, cm: Double, unitName: String, email: String = "", phone: String = "", outputFile: File? = null): File? {
        val fileName = "NhatKy_DongThucVat_${log.id}_${System.currentTimeMillis()}".toNonAccent() + ".docx"
        val file = outputFile ?: File(getExportDirectory(context), fileName)
        try {
            val document = XWPFDocument()
            addProfessionalHeader(document, "NHAT KY THEO DOI DONG THUC VAT RUNG")
            addReporterInfo(document, log.officerName, unitName, cm, email, phone)
            
            val info = document.createParagraph().createRun()
            info.fontFamily = "Times New Roman"
            info.setText("Mo ta doi tuong: ${log.appearanceDescription.toNonAccent()}")
            info.addBreak()
            info.setText("Thoi gian ghi nhan: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(log.timestampUtc))}")
            info.addBreak()
            info.setText("Toa do VN2000: X=${String.format("%.1f", log.vn2000X)}, Y=${String.format("%.1f", log.vn2000Y)}")
            info.addBreak()

            val table = document.createTable(8, 2)
            fun setCell(r: Int, c: Int, t: String, b: Boolean = false) {
                val p = table.getRow(r).getCell(c).addParagraph()
                p.createRun().apply { isBold = b; fontSize = 11; fontFamily = "Times New Roman"; setText(t.toNonAccent()) }
            }

            setCell(0, 0, "Dac diem hinh thai", true); setCell(0, 1, log.appearanceDescription)
            setCell(1, 0, "Bo phan dac trung", true); setCell(1, 1, log.features)
            setCell(2, 0, "So luong ca the", true); setCell(2, 1, log.count)
            setCell(3, 0, "Loai sinh canh", true); setCell(3, 1, log.habitatType)
            setCell(4, 0, "Nhiet do / Do am", true); setCell(4, 1, "${log.temperature} C / ${log.humidity}%")
            setCell(5, 0, "Do tan che", true); setCell(5, 1, log.canopyCover)
            setCell(6, 0, "Cac loai cay song quanh", true); setCell(6, 1, log.surroundingPlants)
            setCell(7, 0, "Mau vat thu thap", true); setCell(7, 1, log.specimens)

            FileOutputStream(file).use { document.write(it) }
            document.close()
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            return file
        } catch (e: Exception) { e.printStackTrace(); return null }
    }

    fun exportNaturalImpactLogToWord(context: Context, log: com.baoverung.app.data.local.entity.NaturalImpactLogEntity, cm: Double, unitName: String, email: String = "", phone: String = "", outputFile: File? = null): File? {
        val fileName = "NhatKy_TacDongTN_${log.id}_${System.currentTimeMillis()}".toNonAccent() + ".docx"
        val file = outputFile ?: File(getExportDirectory(context), fileName)
        try {
            val document = XWPFDocument()
            addProfessionalHeader(document, "NHAT KY TÁC DONG TU NHIEN DEN RUNG")
            addReporterInfo(document, log.officerName, unitName, cm, email, phone)
            
            val info = document.createParagraph().createRun()
            info.fontFamily = "Times New Roman"
            val finalCause = if (log.cause == "Khác") log.otherCause else log.cause
            info.setText("Nguyen nhan: ${finalCause.toNonAccent()}")
            info.addBreak()
            info.setText("Thoi gian ghi nhan: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(log.timestampUtc))}")
            info.addBreak()
            info.setText("Toa do VN2000: X=${String.format("%.1f", log.vn2000X)}, Y=${String.format("%.1f", log.vn2000Y)}")
            info.addBreak()

            val table = document.createTable(6, 2)
            fun setCell(r: Int, c: Int, t: String, b: Boolean = false) {
                val p = table.getRow(r).getCell(c).addParagraph()
                p.createRun().apply { isBold = b; fontSize = 11; fontFamily = "Times New Roman"; setText(t.toNonAccent()) }
            }

            setCell(0, 0, "Nguyen nhan tu nhien", true); setCell(0, 1, finalCause)
            setCell(1, 0, "Dien tich anh huong", true); setCell(1, 1, log.affectedArea)
            setCell(2, 0, "Trang thai rung (Truoc/Sau)", true); setCell(2, 1, "${log.statusBefore} / ${log.statusAfter}")
            setCell(3, 0, "Thiet hai tai nguyen", true); setCell(3, 1, log.resourceDamage)
            setCell(4, 0, "Thoi diem xay ra", true); setCell(4, 1, log.occurrenceTime)
            setCell(5, 0, "Phu luc / Ghi chu", true); setCell(5, 1, "")

            FileOutputStream(file).use { document.write(it) }
            document.close()
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            return file
        } catch (e: Exception) { e.printStackTrace(); return null }
    }
}
