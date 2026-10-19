// Nu asks: the phone's three questions of the day, one at a time on Home. Two choices stack as
// rows; four atoms sit two by two. Once answered, the right choice shows green (a wrong pick red)
// with what each choice is under it, and a line about the atom if there is one; then the next
// question. Done for the day, the card shows how many were right and the streak. No plate round
// it: only the choices, the things to tap, sit on one.

package app.buildatomos.watch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildatomos.core.Ink
import app.buildatomos.core.QuizOption
import app.buildatomos.watch.AppModel
import kotlinx.coroutines.delay

private const val SHOW_MS = 2200L
private const val NOTE_MS = 3600L

@Composable
fun QuizCard(model: AppModel, onAtom: (Int) -> Unit) {
    val qs = model.questions()
    val day = model.quizToday()
    /** The choice just made, shown until the next question; -1 for none. */
    var picked by remember { mutableIntStateOf(-1) }
    var shownAt by remember { mutableIntStateOf(day.answers.size) }
    val i = if (picked >= 0) shownAt else day.answers.size
    val q = qs.getOrNull(i)
    LaunchedEffect(picked) {
        if (picked >= 0) {
            delay(if (q?.note != null) NOTE_MS else SHOW_MS)
            picked = -1
        }
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(Modifier.size(10.dp)) { ComposePen(this, 10.0).dot(5.0, 5.0, 4.0, Ink.ROSE) }
                Label("Nu asks", size = 13.sp, color = Tone.text2)
                // One pip a question: green right, red wrong, gray to come.
                Canvas(Modifier.size(width = 30.dp, height = 8.dp)) {
                    val pen = ComposePen(this, 30.0, wide = true)
                    for (k in qs.indices) {
                        val a = day.answers.getOrNull(k)
                        pen.dot(5.0 + k * 10, 4.0, 3.0, when (a) { true -> Ink.GREEN; false -> Ink.RED; null -> Ink.K700 })
                    }
                }
            }
            if (q == null) {
                Label("Done for today: ${day.right} of ${qs.size} right", size = 14.sp)
                Label(day.streakLabel, size = 13.sp, color = Tone.text2)
                return@Column
            }
            Label(q.text, size = 14.sp, weight = FontWeight.Normal)
            val answered = picked >= 0
            fun tap(k: Int) {
                if (answered) return
                shownAt = day.answers.size
                picked = k
                model.answer(k == q.answer)
            }
            val four = q.options.size == 4
            val rows = if (four) q.options.indices.chunked(2) else q.options.indices.map { listOf(it) }
            for (row in rows) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (k in row) {
                    val o = q.options[k]
                    val tone = when {
                        !answered -> null
                        k == q.answer -> Tone.green
                        k == picked -> Color(Ink.RED)
                        else -> null
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .background(Color(Ink.K800), RoundedCornerShape(20.dp))
                            .then(if (tone != null) Modifier.border(2.dp, tone, RoundedCornerShape(20.dp)) else Modifier)
                            .clickable { if (answered && o is QuizOption.Card) onAtom(o.z) else tap(k) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when (o) {
                            is QuizOption.Card -> {
                                val a = model.atlas.atom(o.z)
                                if (four) {
                                    Label(a.symbol, size = 15.sp, weight = FontWeight.SemiBold, color = Color(a.color))
                                    Label(a.name, size = 13.sp, lines = 1)
                                } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Label(a.symbol, size = 15.sp, weight = FontWeight.SemiBold, color = Color(a.color))
                                    Label(a.name, size = 14.sp, lines = 1)
                                }
                            }
                            is QuizOption.Word -> Label(o.text, size = 14.sp)
                        }
                        val after = q.after.getOrNull(k).orEmpty()
                        if (answered && after.isNotBlank()) Label(after, size = 13.sp, color = tone ?: Tone.text2)
                    }
                }
            }
            val note = q.note
            if (answered && note != null) Label(note, Modifier.padding(top = 2.dp), size = 13.sp, color = Tone.text2, align = TextAlign.Center)
        }
    }
}
