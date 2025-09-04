package ai.runow  // <-- lascia questo uguale a com'è nel tuo progetto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import ai.runow.ui.renderer.DesignerRoot   // <-- importa il DesignerRoot definito in UiRenderer.kt

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      // Se hai un tema tuo (es. RunowTheme), puoi usarlo al posto di MaterialTheme
      MaterialTheme {
        DesignerRoot()
      }
    }
  }
}
