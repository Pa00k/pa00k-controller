package hr.vklabucar.Pa00k

import android.webkit.WebView
import android.content.Context

class GifWebView(context: Context, path: String) extends WebView(context) {
  loadUrl(path)
}
